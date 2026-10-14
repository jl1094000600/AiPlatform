package com.aipal.agent.runtime.trace;

import com.aipal.entity.AgentRunSpan;
import com.aipal.mapper.AgentRunSpanMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunTracerTest {

    @Test
    void startPersistsSpanAndEndUpdatesDuration() {
        AgentRunSpanMapper mapper = mock(AgentRunSpanMapper.class);
        when(mapper.insert(any())).thenAnswer(invocation -> {
            AgentRunSpan span = invocation.getArgument(0);
            span.setId(11L);
            return 1;
        });
        RunTracer tracer = new RunTracer(mapper);

        Span span = tracer.start(1L, 99L, SpanType.TOOL_CALL, "memory.search", 5L, 6L, "call-1", "trace-1");
        assertEquals(11L, span.id());
        assertEquals(SpanType.TOOL_CALL, span.spanType());

        ArgumentCaptor<AgentRunSpan> inserted = ArgumentCaptor.forClass(AgentRunSpan.class);
        verify(mapper).insert(inserted.capture());
        assertEquals("WAITING", inserted.getValue().getStatus());
        assertEquals("memory.search", inserted.getValue().getSpanName());

        tracer.end(span, SpanStatus.OK, "completed", 3, 4);
        ArgumentCaptor<AgentRunSpan> updated = ArgumentCaptor.forClass(AgentRunSpan.class);
        verify(mapper).updateById(updated.capture());
        assertEquals("OK", updated.getValue().getStatus());
        assertEquals("completed", updated.getValue().getSummary());
        assertEquals(3, updated.getValue().getInputTokens());
        assertEquals(4, updated.getValue().getOutputTokens());
        assertNotNull(updated.getValue().getDurationMs());
    }

    @Test
    void timelineContainsNoPromptOrMemoryBodies() {
        AgentRunSpanMapper mapper = mock(AgentRunSpanMapper.class);
        AgentRunSpan span = new AgentRunSpan();
        span.setSpanId("span-1");
        span.setSpanType("REPLY");
        span.setSpanName("model-reply");
        span.setStatus("OK");
        span.setSummary("model reply completed");
        span.setInputTokens(1);
        span.setOutputTokens(2);
        span.setDurationMs(42L);
        when(mapper.selectList(any())).thenReturn(List.of(span));
        RunTracer tracer = new RunTracer(mapper);

        RunTracer.TraceTimeline timeline = tracer.timeline(1L, 99L, "trace-1", "SUCCEEDED");

        assertEquals(99L, timeline.runId());
        assertEquals(1, timeline.spans().size());
        RunTracer.SpanView view = timeline.spans().get(0);
        assertEquals("REPLY", view.spanType());
        assertEquals("OK", view.status());
        assertEquals("model reply completed", view.summary());
        // Span projection carries only identifiers/timings/summaries — no raw payloads.
        assertNull(getFieldOrNull(view));
    }

    @Test
    void summaryIsTruncated() {
        AgentRunSpanMapper mapper = mock(AgentRunSpanMapper.class);
        when(mapper.insert(any())).thenReturn(1);
        RunTracer tracer = new RunTracer(mapper);

        String longSummary = "x".repeat(500);
        tracer.record(1L, 99L, SpanType.TOOL_RESULT, "tool", null, null, null, "trace", SpanStatus.OK,
                longSummary, null, null, 0, 0);
        ArgumentCaptor<AgentRunSpan> inserted = ArgumentCaptor.forClass(AgentRunSpan.class);
        verify(mapper).insert(inserted.capture());
        assertTrue(inserted.getValue().getSummary().length() <= 256);
    }

    /** Guard: the REST projection type exposes no content-bearing field beyond summary. */
    private Object getFieldOrNull(RunTracer.SpanView view) {
        // SpanView is a record of (spanId, spanType, spanName, status, stepId, toolCallId, callId,
        // inputTokens, outputTokens, summary, startTime, endTime, durationMs) — none carry prompt/memory bodies.
        return view.getClass().getRecordComponents().length == 13 ? null : "unexpected shape";
    }
}
