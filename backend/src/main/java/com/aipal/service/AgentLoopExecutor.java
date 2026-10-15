package com.aipal.service;

import com.aipal.agent.runtime.budget.BudgetExceededException;
import com.aipal.agent.runtime.budget.RunBudgetGuard;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentTask;
import com.aipal.mapper.AgentTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentLoopExecutor {
    private final AgentService agentService;
    private final AgentToolExecutor toolExecutor;
    private final RunBudgetGuard runBudgetGuard;
    private final AgentTaskMapper taskMapper;

    public LoopResult execute(AgentRun run, Object input, Map<String, Object> definition) {
        Map<String, Object> runtime = mapValue(definition.get("runtimeConfig"));
        if ("v2".equalsIgnoreCase(run.getLoopVersion())) {
            return executeV2(run, input, runtime);
        }
        if (run.getLoopVersion() != null && !"v1".equalsIgnoreCase(run.getLoopVersion())) {
            throw new UnsupportedOperationException("Agent loop " + run.getLoopVersion() + " is not enabled yet");
        }
        return executeV1(run, input, runtime);
    }

    private LoopResult executeV1(AgentRun run, Object input, Map<String, Object> runtime) {
        Long modelId = requiredLong(runtime, "modelId");
        LocalDateTime clockStart = run.getStartTime() != null ? run.getStartTime() : LocalDateTime.now();
        runBudgetGuard.assertBeforeModelCall(run, 0, 0, Duration.between(clockStart, LocalDateTime.now()));
        Map<String, Object> output = agentService.callAgentFrozen(run.getAgentId(), input, run.getOwnerUserId(), null,
                modelId, nullableLong(runtime.get("promptId")), nullableLong(runtime.get("promptVersionId")));
        int totalTokens = number(output.get("totalTokens"));
        runBudgetGuard.assertBeforeModelCall(run, totalTokens, 1, Duration.between(clockStart, LocalDateTime.now()));
        return new LoopResult(output, totalTokens, 1);
    }

    private LoopResult executeV2(AgentRun run, Object input, Map<String, Object> runtime) {
        Long modelId = requiredLong(runtime, "modelId");
        int maxSteps = runBudgetGuard.effectiveMaxSteps(run);
        int totalTokens = 0;
        Object turnInput = input;
        LocalDateTime clockStart = run.getStartTime() != null ? run.getStartTime() : LocalDateTime.now();
        for (int turn = 1; turn <= maxSteps; turn++) {
            Duration elapsed = Duration.between(clockStart, LocalDateTime.now());
            // Pre-call budget gate: never issue another model call after a breach.
            runBudgetGuard.assertBeforeModelCall(run, totalTokens, turn - 1, elapsed);

            Map<String, Object> modelOutput = agentService.callAgentFrozen(run.getAgentId(), turnInput,
                    run.getOwnerUserId(), null, modelId, nullableLong(runtime.get("promptId")),
                    nullableLong(runtime.get("promptVersionId")));
            totalTokens += number(modelOutput.get("totalTokens"));
            AgentAction action = AgentAction.from(modelOutput);
            elapsed = Duration.between(clockStart, LocalDateTime.now());
            if (action.type() == AgentActionType.FINAL) {
                // No FINAL deliverable is emitted once the budget is exhausted.
                runBudgetGuard.assertBeforeModelCall(run, totalTokens, turn, elapsed);
                return new LoopResult(action.output(), totalTokens, turn);
            }
            if (action.type() != AgentActionType.TOOL_CALL) {
                throw new UnsupportedOperationException("Agent action " + action.type() + " is not enabled yet");
            }
            // Pre-side-effect budget gate: never run another tool after a breach.
            runBudgetGuard.assertBeforeToolCall(run, totalTokens, turn, elapsed, currentChildTasks(run));

            AgentToolExecutor.ToolExecutionResult toolResult = toolExecutor.execute(run, action.callId(),
                    action.toolName(), action.input());
            if (!toolResult.executed() || !"SUCCEEDED".equals(toolResult.status())) {
                throw new IllegalStateException("Tool call failed: " + toolResult.status());
            }
            turnInput = nextTurnInput(input, modelOutput, toolResult, turn);
        }
        throw new BudgetExceededException("STEPS", "Step budget exceeded");
    }

    private long currentChildTasks(AgentRun run) {
        if (run == null || run.getId() == null || run.getTenantId() == null) return 0L;
        Long count = taskMapper.selectCount(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getTenantId, run.getTenantId())
                .eq(AgentTask::getRunId, run.getId())
                .ne(AgentTask::getTaskType, "RUN"));
        return count == null ? 0L : count;
    }

    private Map<String, Object> nextTurnInput(Object originalInput, Map<String, Object> modelOutput,
                                              AgentToolExecutor.ToolExecutionResult toolResult, int turn) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("originalInput", originalInput);
        payload.put("previousModelOutput", modelOutput);
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("status", toolResult.status());
        observation.put("output", toolResult.output() == null ? Map.of() : toolResult.output());
        observation.put("toolCallId", toolResult.toolCallId());
        observation.put("stepId", toolResult.stepId());
        payload.put("toolResult", observation);
        payload.put("turn", turn + 1);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new IllegalStateException("Frozen runtime configuration is missing");
    }

    private Long requiredLong(Map<String, Object> value, String key) {
        Long result = nullableLong(value.get(key));
        if (result == null) throw new IllegalStateException("Frozen " + key + " is missing");
        return result;
    }

    private Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    public record LoopResult(Map<String, Object> output, int totalTokens, int steps) {
        public LoopResult(Map<String, Object> output, int totalTokens) {
            this(output, totalTokens, 1);
        }
    }

    private enum AgentActionType {
        TOOL_CALL,
        DELEGATE,
        WAIT_APPROVAL,
        FINAL
    }

    private record AgentAction(AgentActionType type, String callId, String toolName, Object input,
                               Map<String, Object> output) {
        static AgentAction from(Map<String, Object> modelOutput) {
            Map<String, Object> actionPayload = payload(modelOutput.get("action"));
            String typeValue = firstString(actionPayload, "type", "action");
            if (typeValue == null) typeValue = firstString(modelOutput, "type", "action");
            AgentActionType type = parseType(typeValue, modelOutput);
            if (type == AgentActionType.TOOL_CALL) {
                String toolName = firstString(actionPayload, "toolName", "tool_name", "name");
                if (toolName == null) toolName = firstString(modelOutput, "toolName", "tool_name", "name");
                if (toolName == null || toolName.isBlank()) {
                    throw new IllegalArgumentException("Tool call action missing toolName");
                }
                String callId = firstString(actionPayload, "callId", "call_id");
                if (callId == null) callId = firstString(modelOutput, "callId", "call_id");
                Object input = firstPresent(actionPayload, "input", "arguments", "args");
                if (input == null) input = firstPresent(modelOutput, "input", "arguments", "args");
                return new AgentAction(type, callId, toolName, input == null ? Map.of() : input, Map.of());
            }
            return new AgentAction(type, null, null, null, outputFrom(modelOutput));
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> payload(Object value) {
            if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
            return Map.of();
        }

        private static AgentActionType parseType(String typeValue, Map<String, Object> modelOutput) {
            if (typeValue == null || typeValue.isBlank()) {
                return hasToolShape(modelOutput) ? AgentActionType.TOOL_CALL : AgentActionType.FINAL;
            }
            String normalized = typeValue.trim().toUpperCase().replace('-', '_');
            return AgentActionType.valueOf(normalized);
        }

        private static boolean hasToolShape(Map<String, Object> modelOutput) {
            return firstString(modelOutput, "toolName", "tool_name", "name") != null
                    && (modelOutput.containsKey("input") || modelOutput.containsKey("arguments")
                    || modelOutput.containsKey("args"));
        }

        private static Map<String, Object> outputFrom(Map<String, Object> modelOutput) {
            Object output = firstPresent(modelOutput, "output", "result");
            if (output == null) return modelOutput;
            if (output instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((key, value) -> result.put(String.valueOf(key), value));
                return result;
            }
            return Map.of("result", output);
        }

        private static String firstString(Map<String, Object> map, String... keys) {
            Object value = firstPresent(map, keys);
            return value == null ? null : String.valueOf(value);
        }

        private static Object firstPresent(Map<String, Object> map, String... keys) {
            for (String key : keys) {
                if (map.containsKey(key)) return map.get(key);
            }
            return null;
        }
    }
}
