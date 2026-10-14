package com.aipal.agent.runtime.trace;

import com.aipal.entity.AgentRunSpan;
import com.aipal.mapper.AgentRunSpanMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Records reply / tool-call / tool-result / approval-wait spans for one agent run.
 * Span summaries are intentionally content-free (no prompt bodies, no memory bodies).
 */
@Service
@RequiredArgsConstructor
public class RunTracer {

    private static final int MAX_SUMMARY = 256;

    private final AgentRunSpanMapper spanMapper;

    public Span start(Long tenantId, Long runId, SpanType spanType, String spanName, Long stepId,
                      Long toolCallId, String callId, String traceId) {
        LocalDateTime now = LocalDateTime.now();
        AgentRunSpan span = new AgentRunSpan();
        span.setTenantId(tenantId);
        span.setRunId(runId);
        span.setSpanId(UUID.randomUUID().toString());
        span.setTraceId(truncate(traceId, 96));
        span.setSpanType(spanType.name());
        span.setSpanName(truncate(spanName, 128));
        span.setStepId(stepId);
        span.setToolCallId(toolCallId);
        span.setCallId(truncate(callId, 96));
        span.setStatus(SpanStatus.WAITING.name());
        span.setInputTokens(0);
        span.setOutputTokens(0);
        span.setStartTime(now);
        span.setCreateTime(now);
        span.setIsDeleted(0);
        spanMapper.insert(span);
        return new Span(span.getId(), tenantId, runId, span.getSpanId(), spanType, span.getSpanName(),
                stepId, toolCallId, span.getCallId(), span.getTraceId(), now);
    }

    public void end(Span span, SpanStatus status, String summary, Integer inputTokens, Integer outputTokens) {
        if (span == null || span.id() == null) return;
        LocalDateTime now = LocalDateTime.now();
        long durationMs = span.startTime() == null ? 0L : ChronoUnit.MILLIS.between(span.startTime(), now);
        AgentRunSpan update = new AgentRunSpan();
        update.setId(span.id());
        update.setStatus(status.name());
        update.setSummary(truncate(summary, MAX_SUMMARY));
        update.setInputTokens(inputTokens == null ? 0 : inputTokens);
        update.setOutputTokens(outputTokens == null ? 0 : outputTokens);
        update.setEndTime(now);
        update.setDurationMs(durationMs);
        spanMapper.updateById(update);
    }

    /** One-shot span covering an already-completed activity. */
    public void record(Long tenantId, Long runId, SpanType spanType, String spanName, Long stepId,
                       Long toolCallId, String callId, String traceId, SpanStatus status, String summary,
                       LocalDateTime startTime, LocalDateTime endTime, Integer inputTokens, Integer outputTokens) {
        LocalDateTime start = startTime == null ? LocalDateTime.now() : startTime;
        LocalDateTime end = endTime == null ? LocalDateTime.now() : endTime;
        AgentRunSpan span = new AgentRunSpan();
        span.setTenantId(tenantId);
        span.setRunId(runId);
        span.setSpanId(UUID.randomUUID().toString());
        span.setTraceId(truncate(traceId, 96));
        span.setSpanType(spanType.name());
        span.setSpanName(truncate(spanName, 128));
        span.setStepId(stepId);
        span.setToolCallId(toolCallId);
        span.setCallId(truncate(callId, 96));
        span.setStatus(status.name());
        span.setSummary(truncate(summary, MAX_SUMMARY));
        span.setInputTokens(inputTokens == null ? 0 : inputTokens);
        span.setOutputTokens(outputTokens == null ? 0 : outputTokens);
        span.setStartTime(start);
        span.setEndTime(end);
        span.setDurationMs(Math.max(0L, ChronoUnit.MILLIS.between(start, end)));
        span.setCreateTime(LocalDateTime.now());
        span.setIsDeleted(0);
        spanMapper.insert(span);
    }

    public List<AgentRunSpan> listForRun(Long tenantId, Long runId) {
        return spanMapper.selectList(new LambdaQueryWrapper<AgentRunSpan>()
                .eq(AgentRunSpan::getTenantId, tenantId)
                .eq(AgentRunSpan::getRunId, runId)
                .orderByAsc(AgentRunSpan::getStartTime)
                .orderByAsc(AgentRunSpan::getId));
    }

    /** Sanitized timeline projection for REST: identifiers, timings, token counts, short status summaries only. */
    public TraceTimeline timeline(Long tenantId, Long runId, String runTraceId, String runStatus) {
        List<SpanView> spans = listForRun(tenantId, runId).stream()
                .map(span -> new SpanView(span.getSpanId(), span.getSpanType(), span.getSpanName(),
                        span.getStatus(), span.getStepId(), span.getToolCallId(), span.getCallId(),
                        span.getInputTokens(), span.getOutputTokens(), span.getSummary(),
                        span.getStartTime(), span.getEndTime(), span.getDurationMs()))
                .toList();
        return new TraceTimeline(runId, runTraceId, runStatus, spans);
    }

    private String truncate(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), max));
    }

    public record SpanView(String spanId, String spanType, String spanName, String status, Long stepId,
                           Long toolCallId, String callId, Integer inputTokens, Integer outputTokens,
                           String summary, LocalDateTime startTime, LocalDateTime endTime, Long durationMs) {
    }

    public record TraceTimeline(Long runId, String traceId, String status, List<SpanView> spans) {
    }
}
