package com.aipal.service;

import com.aipal.agent.runtime.AgentStepType;
import com.aipal.agent.runtime.AgentRunStreamEventType;
import com.aipal.agent.runtime.AgentTaskStatus;
import com.aipal.agent.runtime.AgentToolPermissionDecision;
import com.aipal.agent.runtime.tool.AgentTool;
import com.aipal.agent.runtime.tool.ToolInvocationContext;
import com.aipal.agent.runtime.tool.ToolResult;
import com.aipal.agent.runtime.trace.RunTracer;
import com.aipal.agent.runtime.trace.Span;
import com.aipal.agent.runtime.trace.SpanStatus;
import com.aipal.agent.runtime.trace.SpanType;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentStep;
import com.aipal.entity.AgentToolCall;
import com.aipal.entity.AgentToolDefinition;
import com.aipal.mapper.AgentStepMapper;
import com.aipal.mapper.AgentToolCallMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AgentToolExecutor {
    private final AgentToolRegistryService registryService;
    private final AgentToolPermissionEvaluator permissionEvaluator;
    private final AgentToolCallMapper toolCallMapper;
    private final AgentStepMapper stepMapper;
    private final AgentRunStreamEventService streamEventService;
    private final RunTracer runTracer;
    private final ObjectMapper objectMapper;
    private final Map<String, AgentTool> tools;

    public AgentToolExecutor(AgentToolRegistryService registryService,
                             AgentToolPermissionEvaluator permissionEvaluator,
                             AgentToolCallMapper toolCallMapper,
                             AgentStepMapper stepMapper,
                             AgentRunStreamEventService streamEventService,
                             RunTracer runTracer,
                             ObjectMapper objectMapper,
                             List<AgentTool> tools) {
        this.registryService = registryService;
        this.permissionEvaluator = permissionEvaluator;
        this.toolCallMapper = toolCallMapper;
        this.stepMapper = stepMapper;
        this.streamEventService = streamEventService;
        this.runTracer = runTracer;
        this.objectMapper = objectMapper;
        this.tools = tools == null ? Map.of() : tools.stream()
                .collect(Collectors.toUnmodifiableMap(AgentTool::name, Function.identity(), (left, ignored) -> left));
    }

    /**
     * Deliberately not transactional: ask_user blocks on the user's answer and must not hold a
     * DB connection. Audit writes are individual inserts/updates that auto-commit.
     */
    public ToolExecutionResult execute(AgentRun run, String callId, String toolName, Object input) {
        AgentToolDefinition definition = registryService.requireEnabled(toolName);
        boolean allowedByVersion = allowedByRunSnapshot(run, definition.getToolName());
        AgentToolPermissionEvaluator.ToolPermissionResult permission = allowedByVersion
                ? permissionEvaluator.evaluate(run, definition)
                : new AgentToolPermissionEvaluator.ToolPermissionResult(AgentToolPermissionDecision.DENY,
                definition.getRiskLevel(), false, null);
        String resolvedCallId = blank(callId) ? UUID.randomUUID().toString() : callId.trim();
        String inputJson = json(input == null ? Map.of() : input);
        AgentStep step = createStep(run, resolvedCallId, definition, permission, inputJson);
        AgentToolCall call = createCall(run, step, resolvedCallId, definition, permission, inputJson);
        step.setToolCallId(call.getId());
        stepMapper.updateById(step);
        recordEvent(run, step, call, AgentRunStreamEventType.TOOL_REQUESTED, payload(step, call, definition, permission));
        recordEvent(run, step, call, AgentRunStreamEventType.PERMISSION_DECIDED, payload(step, call, definition, permission));

        if (permission.decision() != AgentToolPermissionDecision.ALLOW) {
            String reason = allowedByVersion ? "Tool permission " + permission.decision().name()
                    : "Tool is not allowed by Agent Version whitelist";
            markFailed(step, call, reason);
            recordEvent(run, step, call, AgentRunStreamEventType.TOOL_FAILED, failurePayload(step, call, reason));
            runTracer.record(run.getTenantId(), run.getId(), SpanType.TOOL_CALL, definition.getToolName(),
                    step.getId(), call.getId(), resolvedCallId, run.getTraceId(), SpanStatus.ERROR, reason,
                    step.getStartTime(), LocalDateTime.now(), 0, 0);
            return new ToolExecutionResult(false, permission.decision().name(), null, reason, call.getId(), step.getId());
        }

        AgentTool tool = tools.get(definition.getToolName());
        if (tool == null) {
            String reason = "Tool implementation not registered";
            markFailed(step, call, reason);
            recordEvent(run, step, call, AgentRunStreamEventType.TOOL_FAILED, failurePayload(step, call, reason));
            runTracer.record(run.getTenantId(), run.getId(), SpanType.TOOL_CALL, definition.getToolName(),
                    step.getId(), call.getId(), resolvedCallId, run.getTraceId(), SpanStatus.ERROR, reason,
                    step.getStartTime(), LocalDateTime.now(), 0, 0);
            return new ToolExecutionResult(false, AgentTaskStatus.FAILED.name(), null, reason, call.getId(), step.getId());
        }

        Span span = runTracer.start(run.getTenantId(), run.getId(), SpanType.TOOL_CALL, definition.getToolName(),
                step.getId(), call.getId(), resolvedCallId, run.getTraceId());
        try {
            recordEvent(run, step, call, AgentRunStreamEventType.TOOL_STARTED, payload(step, call, definition, permission));
            RuntimeSnapshot runtime = runtimeSnapshot(run);
            ToolResult result = tool.execute(new ToolInvocationContext(run.getTenantId(), run.getId(), run.getAgentId(),
                    run.getAgentVersionId(), step.getId(), run.getOwnerUserId(), run.getProjectKey(), run.getTraceId(),
                    resolvedCallId, definition.getToolName(), runtime.modelId(), runtime.promptId(),
                    runtime.promptVersionId()), input);
            markSucceeded(step, call, result);
            recordEvent(run, step, call, AgentRunStreamEventType.TOOL_COMPLETED, successPayload(step, call, result));
            runTracer.end(span, SpanStatus.OK, "tool " + definition.getToolName() + " completed",
                    result == null ? 0 : result.inputTokens(), result == null ? 0 : result.outputTokens());
            runTracer.record(run.getTenantId(), run.getId(), SpanType.TOOL_RESULT, definition.getToolName(),
                    step.getId(), call.getId(), resolvedCallId, run.getTraceId(), SpanStatus.OK,
                    result == null ? null : result.outputSummary(), span.startTime(), LocalDateTime.now(),
                    result == null ? 0 : result.inputTokens(), result == null ? 0 : result.outputTokens());
            return new ToolExecutionResult(true, AgentTaskStatus.SUCCEEDED.name(), result == null ? null : result.output(),
                    null, call.getId(), step.getId());
        } catch (Exception exception) {
            String reason = exception.getClass().getSimpleName();
            markFailed(step, call, reason);
            recordEvent(run, step, call, AgentRunStreamEventType.TOOL_FAILED, failurePayload(step, call, reason));
            runTracer.end(span, SpanStatus.ERROR, "tool " + definition.getToolName() + " failed: " + reason, 0, 0);
            return new ToolExecutionResult(true, AgentTaskStatus.FAILED.name(), null, reason, call.getId(), step.getId());
        }
    }

    private AgentStep createStep(AgentRun run, String callId, AgentToolDefinition definition,
                                 AgentToolPermissionEvaluator.ToolPermissionResult permission, String inputJson) {
        LocalDateTime now = LocalDateTime.now();
        AgentStep step = new AgentStep();
        step.setTenantId(run.getTenantId());
        step.setRunId(run.getId());
        step.setStepNo(nextStepNo(run));
        step.setStepType(AgentStepType.TOOL_CALL.name());
        step.setStatus(AgentTaskStatus.RUNNING.name());
        step.setToolName(definition.getToolName());
        step.setToolIdempotencyKey(idempotencyKey(run, callId));
        step.setInputJson(inputJson);
        step.setTraceId(run.getTraceId());
        step.setPermissionDecision(permission.decision().name());
        step.setRiskLevel(permission.riskLevel());
        step.setStreamEventCount(0);
        step.setStartTime(now);
        step.setCreateTime(now);
        step.setUpdateTime(now);
        step.setIsDeleted(0);
        stepMapper.insert(step);
        return step;
    }

    private AgentToolCall createCall(AgentRun run, AgentStep step, String callId, AgentToolDefinition definition,
                                     AgentToolPermissionEvaluator.ToolPermissionResult permission, String inputJson) {
        LocalDateTime now = LocalDateTime.now();
        AgentToolCall call = new AgentToolCall();
        call.setTenantId(run.getTenantId());
        call.setRunId(run.getId());
        call.setStepId(step.getId());
        call.setCallId(callId);
        call.setToolName(definition.getToolName());
        call.setRiskLevel(permission.riskLevel());
        call.setPermissionDecision(permission.decision().name());
        call.setStatus(AgentTaskStatus.RUNNING.name());
        call.setIdempotencyKey(idempotencyKey(run, callId));
        call.setInputJson(inputJson);
        call.setInputTokens(0);
        call.setOutputTokens(0);
        call.setStartTime(now);
        call.setCreateTime(now);
        call.setUpdateTime(now);
        call.setIsDeleted(0);
        toolCallMapper.insert(call);
        return call;
    }

    private void markSucceeded(AgentStep step, AgentToolCall call, ToolResult result) {
        LocalDateTime now = LocalDateTime.now();
        String outputJson = json(result == null ? Map.of() : result.output());
        int inputTokens = result == null ? 0 : result.inputTokens();
        int outputTokens = result == null ? 0 : result.outputTokens();
        step.setStatus(AgentTaskStatus.SUCCEEDED.name());
        step.setOutputJson(outputJson);
        step.setInputTokens(inputTokens);
        step.setOutputTokens(outputTokens);
        step.setEndTime(now);
        step.setUpdateTime(now);
        stepMapper.updateById(step);

        call.setStatus(AgentTaskStatus.SUCCEEDED.name());
        call.setOutputJson(outputJson);
        call.setOutputSummary(result == null ? null : result.outputSummary());
        call.setInputTokens(inputTokens);
        call.setOutputTokens(outputTokens);
        call.setEndTime(now);
        call.setUpdateTime(now);
        toolCallMapper.updateById(call);
    }

    private void markFailed(AgentStep step, AgentToolCall call, String reason) {
        LocalDateTime now = LocalDateTime.now();
        step.setStatus(AgentTaskStatus.FAILED.name());
        step.setErrorMessage(reason);
        step.setEndTime(now);
        step.setUpdateTime(now);
        stepMapper.updateById(step);

        call.setStatus(AgentTaskStatus.FAILED.name());
        call.setErrorMessage(reason);
        call.setEndTime(now);
        call.setUpdateTime(now);
        toolCallMapper.updateById(call);
    }

    private int nextStepNo(AgentRun run) {
        Long count = stepMapper.selectCount(new LambdaQueryWrapper<AgentStep>()
                .eq(AgentStep::getTenantId, run.getTenantId())
                .eq(AgentStep::getRunId, run.getId()));
        return count == null ? 1 : count.intValue() + 1;
    }

    private void recordEvent(AgentRun run, AgentStep step, AgentToolCall call, AgentRunStreamEventType type,
                             Map<String, Object> payload) {
        streamEventService.record(run.getTenantId(), run.getId(), type, payload, true);
        step.setStreamEventCount((step.getStreamEventCount() == null ? 0 : step.getStreamEventCount()) + 1);
        step.setUpdateTime(LocalDateTime.now());
        stepMapper.updateById(step);
    }

    private Map<String, Object> payload(AgentStep step, AgentToolCall call, AgentToolDefinition definition,
                                        AgentToolPermissionEvaluator.ToolPermissionResult permission) {
        Map<String, Object> payload = basePayload(step, call);
        payload.put("toolName", definition.getToolName());
        payload.put("riskLevel", permission.riskLevel());
        payload.put("permissionDecision", permission.decision().name());
        payload.put("requiresApproval", permission.requiresApproval());
        payload.put("policyId", permission.policyId());
        return payload;
    }

    private Map<String, Object> successPayload(AgentStep step, AgentToolCall call, ToolResult result) {
        Map<String, Object> payload = basePayload(step, call);
        payload.put("status", AgentTaskStatus.SUCCEEDED.name());
        payload.put("outputSummary", result == null ? null : result.outputSummary());
        payload.put("inputTokens", result == null ? 0 : result.inputTokens());
        payload.put("outputTokens", result == null ? 0 : result.outputTokens());
        return payload;
    }

    private Map<String, Object> failurePayload(AgentStep step, AgentToolCall call, String reason) {
        Map<String, Object> payload = basePayload(step, call);
        payload.put("status", AgentTaskStatus.FAILED.name());
        payload.put("errorMessage", reason);
        return payload;
    }

    private Map<String, Object> basePayload(AgentStep step, AgentToolCall call) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stepId", step.getId());
        payload.put("toolCallId", call.getId());
        payload.put("callId", call.getCallId());
        payload.put("toolName", call.getToolName());
        payload.put("status", call.getStatus());
        return payload;
    }

    private String idempotencyKey(AgentRun run, String callId) {
        return run.getId() + ":" + callId;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Tool payload cannot be serialized", e);
        }
    }

    private boolean allowedByRunSnapshot(AgentRun run, String toolName) {
        if (blank(run.getDefinitionSnapshot())) return true;
        try {
            Map<String, Object> snapshot = objectMapper.readValue(run.getDefinitionSnapshot(), new TypeReference<>() {});
            Object tools = snapshot.get(AgentToolSnapshotService.SNAPSHOT_KEY);
            if (!(tools instanceof Map<?, ?> toolSnapshot)) return true;
            Object mode = toolSnapshot.get("whitelistMode");
            if (!AgentToolSnapshotService.MODE_AGENT_VERSION.equals(mode)) return true;
            Object allowed = toolSnapshot.get("allowedToolNames");
            if (!(allowed instanceof List<?> names)) return false;
            return names.stream().anyMatch(name -> toolName.equals(String.valueOf(name)));
        } catch (Exception ignored) {
            return false;
        }
    }

    private RuntimeSnapshot runtimeSnapshot(AgentRun run) {
        if (blank(run.getDefinitionSnapshot())) return RuntimeSnapshot.empty();
        try {
            Map<String, Object> snapshot = objectMapper.readValue(run.getDefinitionSnapshot(), new TypeReference<>() {});
            Object runtime = snapshot.get("runtimeConfig");
            if (!(runtime instanceof Map<?, ?> runtimeConfig)) return RuntimeSnapshot.empty();
            return new RuntimeSnapshot(longValue(runtimeConfig.get("modelId")), longValue(runtimeConfig.get("promptId")),
                    longValue(runtimeConfig.get("promptVersionId")));
        } catch (Exception ignored) {
            return RuntimeSnapshot.empty();
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record RuntimeSnapshot(Long modelId, Long promptId, Long promptVersionId) {
        private static RuntimeSnapshot empty() {
            return new RuntimeSnapshot(null, null, null);
        }
    }

    public record ToolExecutionResult(boolean executed, String status, Object output, String errorMessage,
                                      Long toolCallId, Long stepId) {
    }
}
