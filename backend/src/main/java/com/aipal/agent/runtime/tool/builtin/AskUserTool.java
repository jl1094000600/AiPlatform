package com.aipal.agent.runtime.tool.builtin;

import com.aipal.agent.runtime.budget.RunBudgetGuard;
import com.aipal.agent.runtime.tool.AgentTool;
import com.aipal.agent.runtime.tool.ToolInvocationContext;
import com.aipal.agent.runtime.tool.ToolResult;
import com.aipal.entity.AgentRun;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.service.AgentToolResponseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * ask_user: pauses the executing run until the user answers or the configured timeout elapses.
 * Timeout failure exits the run without producing a final deliverable.
 */
@Component
@RequiredArgsConstructor
public class AskUserTool implements AgentTool {

    public static final String TOOL_NAME = "ask_user";

    private final AgentToolResponseService responseService;
    private final AgentRunMapper runMapper;
    private final RunBudgetGuard runBudgetGuard;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public ToolResult execute(ToolInvocationContext context, Object input) {
        Map<?, ?> payload = asMap(input);
        String question = requiredString(payload, "question");
        Integer timeoutSeconds = optionalInt(payload.get("timeoutSeconds"));
        Duration remainingBudget = remainingBudget(context.runId());
        AgentToolResponseService.AskOutcome outcome = responseService.awaitAnswer(
                context.tenantId(), context.runId(), context.callId(), question, timeoutSeconds, remainingBudget);
        if (!outcome.answered()) {
            throw new AskUserException(outcome.failureReason() == null
                    ? "ask_user failed" : outcome.failureReason());
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("answer", outcome.answer());
        output.put("status", "ANSWERED");
        return new ToolResult(output, "ask_user answered", estimateTokens(question),
                estimateTokens(outcome.answer()));
    }

    private Duration remainingBudget(Long runId) {
        AgentRun run = runId == null ? null : runMapper.selectById(runId);
        Optional<Duration> remaining = runBudgetGuard.remainingTime(run);
        return remaining.orElse(null);
    }

    private Map<?, ?> asMap(Object input) {
        if (input instanceof Map<?, ?> map) return map;
        throw new IllegalArgumentException("ask_user input must be an object");
    }

    private String requiredString(Map<?, ?> payload, String field) {
        Object value = payload.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return String.valueOf(value).trim();
    }

    private Integer optionalInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("timeoutSeconds must be an integer");
        }
    }

    private int estimateTokens(String value) {
        return value == null ? 0 : Math.max(1, value.length() / 4);
    }

    /** Failure of an ask_user wait (timeout / cancel). Fails the tool call and the run. */
    public static class AskUserException extends RuntimeException {
        public AskUserException(String message) {
            super(message);
        }
    }
}
