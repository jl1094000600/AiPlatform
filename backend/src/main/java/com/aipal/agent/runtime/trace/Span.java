package com.aipal.agent.runtime.trace;

import java.time.LocalDateTime;

/**
 * In-memory handle for one persisted run span. Holds only identifiers and timing so it can be
 * passed around safely; span bodies are never carried here (prompt/memory content stays out).
 */
public record Span(Long id, Long tenantId, Long runId, String spanId, SpanType spanType, String spanName,
                   Long stepId, Long toolCallId, String callId, String traceId, LocalDateTime startTime) {
}
