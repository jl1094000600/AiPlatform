package com.aipal.agent.runtime.trace;

import com.aipal.entity.AgentRunSpan;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * REST projection for one run span. Carries both frontend contract names and backend names
 * so the two sides stay aligned without a breaking rename.
 *
 * <p>Dual fields (same value, two names): {@code id|spanId}, {@code type|spanType},
 * {@code startedAt|startTime}, {@code detail|summary}.
 * Status uses frontend spellings in {@code status} and raw {@link SpanStatus} in {@code spanStatus};
 * {@code OK == SUCCESS} and {@code ERROR == FAILED}.
 */
public record SpanContractView(
        String id, String spanId,
        String type, String spanType,
        String spanName,
        String status, String spanStatus,
        Long stepId, Long toolCallId, String callId,
        Integer inputTokens, Integer outputTokens,
        String detail, String summary,
        LocalDateTime startedAt, LocalDateTime startTime,
        LocalDateTime endTime, Long durationMs) {

    public static SpanContractView from(AgentRunSpan span) {
        String rawType = span.getSpanType();
        String rawStatus = span.getStatus();
        return new SpanContractView(
                span.getSpanId(), span.getSpanId(),
                frontendType(rawType), rawType,
                span.getSpanName(),
                frontendStatus(rawStatus), rawStatus,
                span.getStepId(), span.getToolCallId(), span.getCallId(),
                span.getInputTokens(), span.getOutputTokens(),
                span.getSummary(), span.getSummary(),
                span.getStartTime(), span.getStartTime(),
                span.getEndTime(), span.getDurationMs());
    }

    /** Frontend timeline vocabulary: run / llm / tool / ask_user / budget. */
    static String frontendType(String spanType) {
        if (spanType == null || spanType.isBlank()) return null;
        return switch (spanType.trim().toUpperCase(Locale.ROOT)) {
            case "RUN" -> "run";
            case "REPLY" -> "llm";
            case "TOOL_CALL", "TOOL_RESULT" -> "tool";
            case "ASK_USER_WAIT", "APPROVAL_WAIT" -> "ask_user";
            case "BUDGET_CHECK" -> "budget";
            default -> spanType.trim().toLowerCase(Locale.ROOT);
        };
    }

    /**
     * Frontend status vocabulary. Accepts both backend ({@code OK}/{@code ERROR}) and
     * frontend ({@code SUCCESS}/{@code FAILED}) spellings on the way in.
     */
    static String frontendStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) return null;
        return switch (rawStatus.trim().toUpperCase(Locale.ROOT)) {
            case "OK", "SUCCESS", "SUCCEEDED" -> "SUCCESS";
            case "ERROR", "FAILED" -> "FAILED";
            case "WAITING", "WAITING_USER_INPUT", "WAITING_USER" -> "WAITING_USER";
            case "RUNNING" -> "RUNNING";
            case "TIMEOUT" -> "TIMEOUT";
            case "CANCELLED", "CANCELED" -> "CANCELLED";
            case "PENDING", "QUEUED" -> "PENDING";
            default -> rawStatus.trim().toUpperCase(Locale.ROOT);
        };
    }
}
