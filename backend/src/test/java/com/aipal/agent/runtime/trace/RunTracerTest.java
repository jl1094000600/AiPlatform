package com.aipal.agent.runtime.trace;

import com.aipal.entity.AgentRunSpan;
import com.aipal.mapper.AgentRunSpanMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        span.setStartTime(LocalDateTime.of(2026, 10, 15, 10, 0, 0));
        when(mapper.selectList(any())).thenReturn(List.of(span));
        RunTracer tracer = new RunTracer(mapper);

        RunTracer.TraceTimeline timeline = tracer.timeline(1L, 99L, "trace-1", "SUCCEEDED");

        assertEquals(99L, timeline.runId());
        assertEquals(1, timeline.spans().size());
        SpanContractView view = timeline.spans().get(0);
        // Dual-field contract: frontend names and backend names carry the same value.
        assertEquals("span-1", view.id());
        assertEquals("span-1", view.spanId());
        assertEquals("llm", view.type());
        assertEquals("REPLY", view.spanType());
        assertEquals("SUCCESS", view.status());
        assertEquals("OK", view.spanStatus());
        assertEquals("model reply completed", view.detail());
        assertEquals("model reply completed", view.summary());
        assertEquals(view.startTime(), view.startedAt());
        assertEquals(42L, view.durationMs());
    }

    @Test
    void statusVocabularyAcceptsBothOkSuccessAndErrorFailed() {
        assertEquals("SUCCESS", SpanContractView.frontendStatus("OK"));
        assertEquals("SUCCESS", SpanContractView.frontendStatus("SUCCESS"));
        assertEquals("FAILED", SpanContractView.frontendStatus("ERROR"));
        assertEquals("FAILED", SpanContractView.frontendStatus("FAILED"));
        assertEquals("WAITING_USER", SpanContractView.frontendStatus("WAITING"));
    }

    @Test
    void typeVocabularyMapsBackendToFrontend() {
        assertEquals("run", SpanContractView.frontendType("RUN"));
        assertEquals("llm", SpanContractView.frontendType("REPLY"));
        assertEquals("tool", SpanContractView.frontendType("TOOL_CALL"));
        assertEquals("tool", SpanContractView.frontendType("TOOL_RESULT"));
        assertEquals("ask_user", SpanContractView.frontendType("ASK_USER_WAIT"));
        assertEquals("budget", SpanContractView.frontendType("BUDGET_CHECK"));
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
}
