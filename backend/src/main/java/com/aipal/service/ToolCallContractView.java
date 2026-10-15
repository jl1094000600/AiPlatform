package com.aipal.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * REST projection for one tool call. Carries both frontend contract names and backend names
 * so the two sides stay aligned without a breaking rename.
 *
 * <p>Dual fields: {@code id} is the callId string the frontend posts back to
 * {@code /tools/{callId}/respond}; {@code startedAt|startTime}, {@code resultSummary|outputSummary}.
 * Status uses frontend spellings ({@code SUCCESS}/{@code FAILED}/{@code WAITING_USER}...) in
 * {@code status} and the raw task status in {@code statusRaw}.
 */
public record ToolCallContractView(
        String id,
        Long toolCallId,
        Long stepId,
        String callId,
        String toolName,
        String riskLevel,
        String permissionDecision,
        String status,
        String statusRaw,
        String argsSummary,
        String resultSummary,
        String outputSummary,
        String errorMessage,
        Integer inputTokens,
        Integer outputTokens,
        LocalDateTime startedAt,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long durationMs) {

    public static ToolCallContractView from(AgentRunService.ToolCallView view) {
        LocalDateTime start = view.startTime();
        LocalDateTime end = view.endTime();
        Long duration = view.durationMs() != null ? view.durationMs()
                : (start != null && end != null ? ChronoUnit.MILLIS.between(start, end) : null);
        return new ToolCallContractView(
                view.callId(),
                view.id(),
                view.stepId(),
                view.callId(),
                view.toolName(),
                view.riskLevel(),
                view.permissionDecision(),
                frontendStatus(view.status()),
                view.status(),
                view.argsSummary(),
                view.resultSummary() != null ? view.resultSummary() : view.outputSummary(),
                view.outputSummary(),
                view.errorMessage(),
                view.inputTokens(),
                view.outputTokens(),
                start, start, end,
                duration);
    }

    /**
     * Frontend tool-status vocabulary. Accepts both backend ({@code SUCCEEDED}/{@code FAILED})
     * and frontend ({@code SUCCESS}) spellings on the way in.
     */
    static String frontendStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) return null;
        return switch (rawStatus.trim().toUpperCase(Locale.ROOT)) {
            case "OK", "SUCCESS", "SUCCEEDED" -> "SUCCESS";
            case "ERROR", "FAILED" -> "FAILED";
            case "WAITING", "WAITING_USER_INPUT", "WAITING_USER" -> "WAITING_USER";
            case "RUNNING" -> "RUNNING";
            case "QUEUED", "PENDING" -> "PENDING";
            case "ANSWERED" -> "ANSWERED";
            case "TIMEOUT" -> "TIMEOUT";
            case "CANCELLED", "CANCELED" -> "CANCELLED";
            case "DENIED" -> "DENIED";
            default -> rawStatus.trim().toUpperCase(Locale.ROOT);
        };
    }
}
