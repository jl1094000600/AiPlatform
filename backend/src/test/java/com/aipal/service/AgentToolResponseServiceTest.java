package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.agent.runtime.trace.RunTracer;
import com.aipal.agent.runtime.trace.Span;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentToolCall;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.mapper.AgentToolCallMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolResponseServiceTest {

    @Test
    void askUserWaitsUntilResponded() throws Exception {
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentToolCallMapper toolCallMapper = mock(AgentToolCallMapper.class);
        RunTracer tracer = mock(RunTracer.class);
        AgentRun run = run();
        AgentToolCall call = call();
        when(runMapper.selectById(99L)).thenReturn(run);
        when(toolCallMapper.selectOne(any())).thenReturn(call);
        when(tracer.start(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new Span(1L, 1L, 99L, "span-1", null, "ask_user", null, null, "call-1", "trace-1",
                        java.time.LocalDateTime.now()));
        AgentToolResponseService service = new AgentToolResponseService(runMapper, toolCallMapper,
                mock(AgentRunEventService.class), tracer);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<AgentToolResponseService.AskOutcome> waiting = executor.submit(() ->
                    service.awaitAnswer(1L, 99L, "call-1", "Which option?", 5, null));
            waitForPending(service, 99L, "call-1");
            AgentToolResponseService.RespondResult result =
                    service.respondToToolCall(99L, "call-1", "option-b");
            assertEquals("ANSWERED", result.status());
            AgentToolResponseService.AskOutcome outcome = waiting.get(3, TimeUnit.SECONDS);
            assertTrue(outcome.answered());
            assertEquals("option-b", outcome.answer());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void askUserTimesOutAndFails() {
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentToolCallMapper toolCallMapper = mock(AgentToolCallMapper.class);
        RunTracer tracer = mock(RunTracer.class);
        AgentRun run = run();
        when(runMapper.selectById(99L)).thenReturn(run);
        when(toolCallMapper.selectOne(any())).thenReturn(call());
        when(tracer.start(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new Span(1L, 1L, 99L, "span-1", null, "ask_user", null, null, "call-1", "trace-1",
                        java.time.LocalDateTime.now()));
        AgentToolResponseService service = new AgentToolResponseService(runMapper, toolCallMapper,
                mock(AgentRunEventService.class), tracer);

        AgentToolResponseService.AskOutcome outcome =
                service.awaitAnswer(1L, 99L, "call-1", "Which option?", 1, null);

        assertFalse(outcome.answered());
        assertNotNull(outcome.failureReason());
        assertTrue(outcome.failureReason().contains("timed out"));
    }

    @Test
    void respondRejectsUnknownCall() {
        AgentToolCallMapper toolCallMapper = mock(AgentToolCallMapper.class);
        when(toolCallMapper.selectOne(any())).thenReturn(null);
        AgentToolResponseService service = new AgentToolResponseService(mock(AgentRunMapper.class), toolCallMapper,
                mock(AgentRunEventService.class), mock(RunTracer.class));

        org.junit.jupiter.api.Assertions.assertThrows(com.aipal.common.BizException.class,
                () -> service.respondToToolCall(99L, "missing", "answer"));
    }

    @Test
    void respondRejectsBlankAnswer() {
        AgentToolResponseService service = new AgentToolResponseService(mock(AgentRunMapper.class),
                mock(AgentToolCallMapper.class), mock(AgentRunEventService.class), mock(RunTracer.class));

        org.junit.jupiter.api.Assertions.assertThrows(com.aipal.common.BizException.class,
                () -> service.respondToToolCall(99L, "call-1", " "));
    }

    private void waitForPending(AgentToolResponseService service, Long runId, String callId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (service.findPending(runId, callId).isPresent()) return;
            Thread.sleep(10);
        }
        throw new AssertionError("ask was not registered in time");
    }

    private AgentRun run() {
        AgentRun run = new AgentRun();
        run.setId(99L);
        run.setTenantId(1L);
        run.setAgentId(11L);
        run.setStatus(AgentRunStatus.RUNNING.name());
        run.setTraceId("trace-1");
        run.setVersion(1);
        return run;
    }

    private AgentToolCall call() {
        AgentToolCall call = new AgentToolCall();
        call.setId(55L);
        call.setTenantId(1L);
        call.setRunId(99L);
        call.setCallId("call-1");
        call.setToolName("ask_user");
        call.setStatus(AgentRunStatus.RUNNING.name());
        return call;
    }
}
