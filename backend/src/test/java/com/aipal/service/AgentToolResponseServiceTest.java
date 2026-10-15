package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.agent.runtime.AskInteractionStatus;
import com.aipal.agent.runtime.trace.RunTracer;
import com.aipal.agent.runtime.trace.Span;
import com.aipal.common.BizException;
import com.aipal.entity.AgentAskInteraction;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentToolCall;
import com.aipal.mapper.AgentAskInteractionMapper;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.mapper.AgentToolCallMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolResponseServiceTest {

    @Test
    void askUserWaitsUntilResponded() throws Exception {
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentToolCallMapper toolCallMapper = mock(AgentToolCallMapper.class);
        AgentAskInteractionMapper askMapper = mock(AgentAskInteractionMapper.class);
        RunTracer tracer = mock(RunTracer.class);
        AgentRun run = run();
        AgentToolCall call = call();
        when(runMapper.selectById(99L)).thenReturn(run);
        when(toolCallMapper.selectOne(any())).thenReturn(call);
        when(askMapper.selectOne(any())).thenReturn(null);
        when(askMapper.insert(any())).thenAnswer(invocation -> {
            AgentAskInteraction interaction = invocation.getArgument(0);
            interaction.setId(1L);
            return 1;
        });
        when(askMapper.update(any(), any())).thenReturn(1);
        when(tracer.start(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(span());
        AgentToolResponseService service = new AgentToolResponseService(runMapper, toolCallMapper,
                mock(AgentRunEventService.class), tracer, askMapper);

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
        AgentAskInteractionMapper askMapper = mock(AgentAskInteractionMapper.class);
        RunTracer tracer = mock(RunTracer.class);
        AgentRun run = run();
        when(runMapper.selectById(99L)).thenReturn(run);
        when(toolCallMapper.selectOne(any())).thenReturn(call());
        when(askMapper.selectOne(any())).thenReturn(null);
        when(askMapper.insert(any())).thenAnswer(invocation -> {
            AgentAskInteraction interaction = invocation.getArgument(0);
            interaction.setId(1L);
            return 1;
        });
        when(askMapper.update(any(), any())).thenReturn(1);
        when(tracer.start(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(span());
        AgentToolResponseService service = new AgentToolResponseService(runMapper, toolCallMapper,
                mock(AgentRunEventService.class), tracer, askMapper);

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
                mock(AgentRunEventService.class), mock(RunTracer.class), mock(AgentAskInteractionMapper.class));

        org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> service.respondToToolCall(99L, "missing", "answer"));
    }

    @Test
    void respondRejectsBlankAnswer() {
        AgentToolResponseService service = new AgentToolResponseService(mock(AgentRunMapper.class),
                mock(AgentToolCallMapper.class), mock(AgentRunEventService.class), mock(RunTracer.class),
                mock(AgentAskInteractionMapper.class));

        org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> service.respondToToolCall(99L, "call-1", " "));
    }

    /** FI-11 / G12: after a restart a persisted answer is consumed without re-blocking. */
    @Test
    void restartRecoversPersistedAnswerWithoutBlocking() {
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentToolCallMapper toolCallMapper = mock(AgentToolCallMapper.class);
        AgentAskInteractionMapper askMapper = mock(AgentAskInteractionMapper.class);
        RunTracer tracer = mock(RunTracer.class);
        AgentRun run = run();
        when(runMapper.selectById(99L)).thenReturn(run);
        AgentToolCall answeredCall = call();
        answeredCall.setStatus(AgentRunStatus.WAITING_USER_INPUT.name());
        when(toolCallMapper.selectOne(any())).thenReturn(answeredCall);
        when(askMapper.selectOne(any())).thenReturn(interaction(AskInteractionStatus.ANSWERED, "option-b", null));
        when(tracer.start(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(span());
        // fresh instance = restarted process
        AgentToolResponseService service = new AgentToolResponseService(runMapper, toolCallMapper,
                mock(AgentRunEventService.class), tracer, askMapper);

        AgentToolResponseService.AskOutcome outcome =
                service.awaitAnswer(1L, 99L, "call-1", "Which option?", 30, null);

        assertTrue(outcome.answered());
        assertEquals("option-b", outcome.answer());
        // must not register an in-process waiter
        assertFalse(service.findPending(99L, "call-1").isPresent());
        verify(toolCallMapper).updateById(any());
    }

    /** FI-11: a persisted timeout is honored on recovery; the run is never re-blocked. */
    @Test
    void restartRecoversTimeoutWithoutBlocking() {
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentToolCallMapper toolCallMapper = mock(AgentToolCallMapper.class);
        AgentAskInteractionMapper askMapper = mock(AgentAskInteractionMapper.class);
        RunTracer tracer = mock(RunTracer.class);
        when(runMapper.selectById(99L)).thenReturn(run());
        when(toolCallMapper.selectOne(any())).thenReturn(call());
        when(askMapper.selectOne(any())).thenReturn(interaction(AskInteractionStatus.TIMED_OUT, null,
                "ask_user timed out"));
        when(tracer.start(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(span());
        AgentToolResponseService service = new AgentToolResponseService(runMapper, toolCallMapper,
                mock(AgentRunEventService.class), tracer, askMapper);

        AgentToolResponseService.AskOutcome outcome =
                service.awaitAnswer(1L, 99L, "call-1", "Which option?", 30, null);

        assertFalse(outcome.answered());
        assertTrue(outcome.failureReason().contains("timed out"));
        assertFalse(service.findPending(99L, "call-1").isPresent());
    }

    /** A leftover PENDING past its deadline resolves as timeout instead of re-waiting. */
    @Test
    void stalePendingPastDeadlineTimesOutWithoutBlocking() {
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentToolCallMapper toolCallMapper = mock(AgentToolCallMapper.class);
        AgentAskInteractionMapper askMapper = mock(AgentAskInteractionMapper.class);
        RunTracer tracer = mock(RunTracer.class);
        when(runMapper.selectById(99L)).thenReturn(run());
        when(toolCallMapper.selectOne(any())).thenReturn(call());
        AgentAskInteraction stale = interaction(AskInteractionStatus.PENDING, null, null);
        stale.setDeadline(LocalDateTime.now().minusMinutes(1));
        when(askMapper.selectOne(any())).thenReturn(stale);
        when(askMapper.update(any(), any())).thenReturn(1);
        when(tracer.start(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(span());
        AgentToolResponseService service = new AgentToolResponseService(runMapper, toolCallMapper,
                mock(AgentRunEventService.class), tracer, askMapper);

        AgentToolResponseService.AskOutcome outcome =
                service.awaitAnswer(1L, 99L, "call-1", "Which option?", 30, null);

        assertFalse(outcome.answered());
        assertTrue(outcome.failureReason().contains("timed out"));
        assertFalse(service.findPending(99L, "call-1").isPresent());
    }

    /** Idempotent respond: the second respond on the same callId is a no-op. */
    @Test
    void duplicateRespondOnlyTakesEffectOnce() {
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentToolCallMapper toolCallMapper = mock(AgentToolCallMapper.class);
        AgentAskInteractionMapper askMapper = mock(AgentAskInteractionMapper.class);
        RunTracer tracer = mock(RunTracer.class);
        when(runMapper.selectById(99L)).thenReturn(run());
        when(toolCallMapper.selectOne(any())).thenReturn(call());
        java.util.concurrent.atomic.AtomicReference<AgentAskInteraction> store =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(askMapper.selectOne(any())).thenAnswer(invocation -> store.get());
        when(askMapper.insert(any())).thenAnswer(invocation -> {
            AgentAskInteraction interaction = invocation.getArgument(0);
            interaction.setId(1L);
            store.set(interaction);
            return 1;
        });
        when(askMapper.update(any(), any())).thenAnswer(invocation -> {
            AgentAskInteraction current = store.get();
            if (current == null || !AskInteractionStatus.PENDING.name().equals(current.getStatus())) return 0;
            AgentAskInteraction update = invocation.getArgument(0);
            current.setStatus(update.getStatus());
            if (update.getAnswer() != null) current.setAnswer(update.getAnswer());
            store.set(current);
            return 1;
        });
        AgentToolResponseService service = new AgentToolResponseService(runMapper, toolCallMapper,
                mock(AgentRunEventService.class), tracer, askMapper);

        AgentToolResponseService.RespondResult first = service.respondToToolCall(99L, "call-1", "first");
        // no in-process waiter: the answer is durably recorded for later recovery
        assertEquals("ANSWER_RECORDED", first.status());
        assertEquals("first", store.get().getAnswer());

        AgentToolResponseService.RespondResult second = service.respondToToolCall(99L, "call-1", "second");
        assertEquals("ALREADY_ANSWERED", second.status());
        // only the first respond took effect
        assertEquals("first", store.get().getAnswer());
    }

    /** Cancel releases waiters and durably marks the interaction CANCELLED. */
    @Test
    void cancelReleasesWaiterAndMarksInteraction() throws Exception {
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentToolCallMapper toolCallMapper = mock(AgentToolCallMapper.class);
        AgentAskInteractionMapper askMapper = mock(AgentAskInteractionMapper.class);
        RunTracer tracer = mock(RunTracer.class);
        when(runMapper.selectById(99L)).thenReturn(run());
        when(toolCallMapper.selectOne(any())).thenReturn(call());
        when(askMapper.selectOne(any())).thenReturn(null);
        when(askMapper.insert(any())).thenAnswer(invocation -> {
            AgentAskInteraction interaction = invocation.getArgument(0);
            interaction.setId(1L);
            return 1;
        });
        when(askMapper.update(any(), any())).thenReturn(1);
        when(tracer.start(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(span());
        AgentToolResponseService service = new AgentToolResponseService(runMapper, toolCallMapper,
                mock(AgentRunEventService.class), tracer, askMapper);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<AgentToolResponseService.AskOutcome> waiting = executor.submit(() ->
                    service.awaitAnswer(1L, 99L, "call-1", "Which option?", 10, null));
            waitForPending(service, 99L, "call-1");
            int released = service.abortPendingForRun(99L, "Run cancelled");
            assertEquals(1, released);
            AgentToolResponseService.AskOutcome outcome = waiting.get(3, TimeUnit.SECONDS);
            assertFalse(outcome.answered());
            assertEquals("Run cancelled", outcome.failureReason());
        } finally {
            executor.shutdownNow();
        }
    }

    /** Restart path when a remote instance already recorded the answer: poll-based release. */
    @Test
    void remoteAnswerIsObservedViaDurablePoll() throws Exception {
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentToolCallMapper toolCallMapper = mock(AgentToolCallMapper.class);
        AgentAskInteractionMapper askMapper = mock(AgentAskInteractionMapper.class);
        RunTracer tracer = mock(RunTracer.class);
        when(runMapper.selectById(99L)).thenReturn(run());
        when(toolCallMapper.selectOne(any())).thenReturn(call());
        AtomicReference<AgentAskInteraction> store = new AtomicReference<>(
                interaction(AskInteractionStatus.PENDING, null, null));
        store.get().setDeadline(LocalDateTime.now().plusSeconds(10));
        when(askMapper.selectOne(any())).thenAnswer(invocation -> store.get());
        when(askMapper.insert(any())).thenAnswer(invocation -> {
            AgentAskInteraction interaction = invocation.getArgument(0);
            interaction.setId(1L);
            store.set(interaction);
            return 1;
        });
        when(askMapper.update(any(), any())).thenAnswer(invocation -> {
            store.get().setStatus(AskInteractionStatus.ANSWERED.name());
            store.get().setAnswer("from-other-instance");
            return 1;
        });
        when(tracer.start(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(span());
        AgentToolResponseService service = new AgentToolResponseService(runMapper, toolCallMapper,
                mock(AgentRunEventService.class), tracer, askMapper);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<AgentToolResponseService.AskOutcome> waiting = executor.submit(() ->
                    service.awaitAnswer(1L, 99L, "call-1", "Which option?", 5, null));
            waitForPending(service, 99L, "call-1");
            // simulate another instance persisting the answer without a local latch
            store.get().setStatus(AskInteractionStatus.ANSWERED.name());
            store.get().setAnswer("from-other-instance");
            AgentToolResponseService.AskOutcome outcome = waiting.get(5, TimeUnit.SECONDS);
            assertTrue(outcome.answered());
            assertEquals("from-other-instance", outcome.answer());
        } finally {
            executor.shutdownNow();
        }
    }

    private void waitForPending(AgentToolResponseService service, Long runId, String callId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (service.findPending(runId, callId).isPresent()) return;
            Thread.sleep(10);
        }
        throw new AssertionError("ask was not registered in time");
    }

    private Span span() {
        return new Span(1L, 1L, 99L, "span-1", null, "ask_user", null, null, "call-1", "trace-1",
                LocalDateTime.now());
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

    private AgentAskInteraction interaction(AskInteractionStatus status, String answer, String reason) {
        AgentAskInteraction interaction = new AgentAskInteraction();
        interaction.setId(9L);
        interaction.setTenantId(1L);
        interaction.setRunId(99L);
        interaction.setCallId("call-1");
        interaction.setQuestion("Which option?");
        interaction.setStatus(status.name());
        interaction.setAnswer(answer);
        interaction.setResolutionReason(reason);
        interaction.setAskedAt(LocalDateTime.now().minusSeconds(5));
        interaction.setDeadline(LocalDateTime.now().plusSeconds(30));
        return interaction;
    }
}
