package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.agent.runtime.AgentTaskStatus;
import com.aipal.agent.runtime.trace.RunTracer;
import com.aipal.agent.runtime.trace.Span;
import com.aipal.agent.runtime.trace.SpanStatus;
import com.aipal.agent.runtime.trace.SpanType;
import com.aipal.common.BizException;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentToolCall;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.mapper.AgentToolCallMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates ask_user pauses: registers the pending question, blocks the executing worker until
 * {@link #respondToToolCall} delivers an answer, and enforces the configured timeout.
 * Pipeline artifact approval (WAITING_APPROVAL) is untouched: ask_user uses WAITING_USER_INPUT.
 */
@Service
@RequiredArgsConstructor
public class AgentToolResponseService {

    private final AgentRunMapper runMapper;
    private final AgentToolCallMapper toolCallMapper;
    private final AgentRunEventService eventService;
    private final RunTracer runTracer;

    @Value("${aipal.agent-runtime.ask-user.timeout-seconds:300}")
    private int defaultTimeoutSeconds = 300;
    @Value("${aipal.agent-runtime.ask-user.min-timeout-seconds:1}")
    private int minTimeoutSeconds = 1;
    @Value("${aipal.agent-runtime.ask-user.max-timeout-seconds:3600}")
    private int maxTimeoutSeconds = 3600;

    private final Map<String, PendingAsk> pendingAsks = new ConcurrentHashMap<>();

    /**
     * Registers the ask, flips Run/ToolCall into WAITING_USER_INPUT and blocks until the answer
     * arrives or the timeout elapses.
     */
    public AskOutcome awaitAnswer(Long tenantId, Long runId, String callId, String question,
                                  Integer timeoutSeconds, Duration remainingBudget) {
        String key = key(tenantId, runId, callId);
        Duration timeout = resolveTimeout(timeoutSeconds, remainingBudget);
        PendingAsk pending = new PendingAsk(tenantId, runId, callId, question, LocalDateTime.now(), timeout);
        pendingAsks.put(key, pending);
        AgentRun run = requireRun(tenantId, runId);
        markWaiting(run, callId, question);
        Span span = runTracer.start(tenantId, runId, SpanType.ASK_USER_WAIT, "ask_user", null, null, callId,
                run.getTraceId());
        pending.span(span);
        try {
            boolean answered = pending.latch().await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (answered && pending.answer() != null) {
                runTracer.end(span, SpanStatus.OK, "ask_user answered", 0, 0);
                resumeRun(tenantId, runId);
                return new AskOutcome(true, pending.answer(), null);
            }
            if (pending.aborted()) {
                runTracer.end(span, SpanStatus.CANCELLED, "ask_user aborted", 0, 0);
                return new AskOutcome(false, null, pending.abortReason() == null
                        ? "Cancelled while waiting for user input" : pending.abortReason());
            }
            runTracer.end(span, SpanStatus.TIMEOUT, "ask_user timed out", 0, 0);
            markTimeoutOnCall(tenantId, callId);
            resumeRun(tenantId, runId);
            return new AskOutcome(false, null, "ask_user timed out after " + timeout.getSeconds() + "s");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            runTracer.end(span, SpanStatus.CANCELLED, "ask_user interrupted", 0, 0);
            return new AskOutcome(false, null, "Interrupted while waiting for user input");
        } finally {
            pendingAsks.remove(key);
        }
    }

    /**
     * Delivers the user's answer to an in-flight ask_user tool call.
     */
    public RespondResult respondToToolCall(Long runId, String callId, String answer) {
        if (callId == null || callId.isBlank()) throw new BizException(400, "callId is required");
        if (answer == null || answer.isBlank()) throw new BizException(400, "answer is required");
        AgentToolCall call = toolCallMapper.selectOne(new LambdaQueryWrapper<AgentToolCall>()
                .eq(AgentToolCall::getRunId, runId)
                .eq(AgentToolCall::getCallId, callId.trim())
                .last("LIMIT 1"));
        if (call == null) throw new BizException(404, "Tool call not found");
        AgentRun run = requireRun(call.getTenantId(), runId);
        if (!"ask_user".equals(call.getToolName())) {
            throw new BizException(409, "Tool call is not an ask_user call");
        }
        if (!AgentTaskStatus.RUNNING.name().equals(call.getStatus())
                && !AgentRunStatus.WAITING_USER_INPUT.name().equals(call.getStatus())) {
            throw new BizException(409, "Tool call is not waiting for user input");
        }
        String key = key(call.getTenantId(), runId, call.getCallId());
        PendingAsk pending = pendingAsks.get(key);
        if (pending == null) {
            // Worker is gone (e.g. restart); persist the answer so a retry can observe it.
            persistAnswer(call, answer.trim());
            resumeRun(call.getTenantId(), runId);
            return new RespondResult(runId, call.getCallId(), "ANSWER_RECORDED");
        }
        pending.complete(answer.trim());
        persistAnswer(call, answer.trim());
        resumeRun(call.getTenantId(), runId);
        eventService.record(run, AgentRunStatus.WAITING_USER_INPUT.name(), AgentRunStatus.RUNNING.name(),
                "User answered ask_user");
        return new RespondResult(runId, call.getCallId(), "ANSWERED");
    }

    /** Aborts every in-flight ask for a run (used when the run is cancelled). */
    public int abortPendingForRun(Long runId, String reason) {
        int aborted = 0;
        for (Map.Entry<String, PendingAsk> entry : pendingAsks.entrySet()) {
            PendingAsk pending = entry.getValue();
            if (runId != null && runId.equals(pending.runId())) {
                pending.abort(reason == null || reason.isBlank() ? "Cancelled while waiting for user input" : reason.trim());
                aborted++;
            }
        }
        return aborted;
    }

    public Optional<PendingAskView> findPending(Long runId, String callId) {
        return pendingAsks.values().stream()
                .filter(pending -> runId.equals(pending.runId())
                        && (callId == null || callId.equals(pending.callId())))
                .findFirst()
                .map(pending -> new PendingAskView(pending.runId(), pending.callId(), pending.askedAt(),
                        pending.timeout()));
    }

    private Duration resolveTimeout(Integer timeoutSeconds, Duration remainingBudget) {
        int requested = timeoutSeconds == null ? defaultTimeoutSeconds : timeoutSeconds;
        int bounded = Math.max(minTimeoutSeconds, Math.min(maxTimeoutSeconds, requested));
        Duration timeout = Duration.ofSeconds(bounded);
        if (remainingBudget != null && !remainingBudget.isZero() && remainingBudget.compareTo(timeout) < 0) {
            return remainingBudget;
        }
        return timeout;
    }

    private void markWaiting(AgentRun run, String callId, String question) {
        LocalDateTime now = LocalDateTime.now();
        AgentRun runUpdate = new AgentRun();
        runUpdate.setStatus(AgentRunStatus.WAITING_USER_INPUT.name());
        runUpdate.setVersion(run.getVersion() == null ? 1 : run.getVersion() + 1);
        runUpdate.setUpdateTime(now);
        runMapper.update(runUpdate, new LambdaUpdateWrapper<AgentRun>()
                .eq(AgentRun::getId, run.getId())
                .eq(AgentRun::getTenantId, run.getTenantId())
                .in(AgentRun::getStatus, AgentRunStatus.RUNNING.name(), AgentRunStatus.QUEUED.name()));
        AgentToolCall callUpdate = new AgentToolCall();
        callUpdate.setStatus(AgentRunStatus.WAITING_USER_INPUT.name());
        callUpdate.setUpdateTime(now);
        toolCallMapper.update(callUpdate, new LambdaUpdateWrapper<AgentToolCall>()
                .eq(AgentToolCall::getTenantId, run.getTenantId())
                .eq(AgentToolCall::getRunId, run.getId())
                .eq(AgentToolCall::getCallId, callId));
        eventService.record(run, AgentRunStatus.RUNNING.name(), AgentRunStatus.WAITING_USER_INPUT.name(),
                "Waiting for user input");
    }

    private void resumeRun(Long tenantId, Long runId) {
        LocalDateTime now = LocalDateTime.now();
        AgentRun runUpdate = new AgentRun();
        runUpdate.setStatus(AgentRunStatus.RUNNING.name());
        runUpdate.setUpdateTime(now);
        runMapper.update(runUpdate, new LambdaUpdateWrapper<AgentRun>()
                .eq(AgentRun::getId, runId)
                .eq(AgentRun::getTenantId, tenantId)
                .eq(AgentRun::getStatus, AgentRunStatus.WAITING_USER_INPUT.name()));
    }

    private void markTimeoutOnCall(Long tenantId, String callId) {
        AgentToolCall callUpdate = new AgentToolCall();
        callUpdate.setStatus(AgentTaskStatus.FAILED.name());
        callUpdate.setErrorMessage("ask_user timed out");
        callUpdate.setEndTime(LocalDateTime.now());
        callUpdate.setUpdateTime(LocalDateTime.now());
        toolCallMapper.update(callUpdate, new LambdaUpdateWrapper<AgentToolCall>()
                .eq(AgentToolCall::getTenantId, tenantId)
                .eq(AgentToolCall::getCallId, callId));
    }

    private void persistAnswer(AgentToolCall call, String answer) {
        AgentToolCall update = new AgentToolCall();
        update.setId(call.getId());
        update.setOutputJson("{\"answer\":" + jsonEscape(answer) + "}");
        update.setOutputSummary("ask_user answered");
        update.setUpdateTime(LocalDateTime.now());
        toolCallMapper.updateById(update);
    }

    private AgentRun requireRun(Long tenantId, Long runId) {
        AgentRun run = runMapper.selectById(runId);
        if (run == null || !tenantId.equals(run.getTenantId())) throw new BizException(404, "Agent Run不存在");
        return run;
    }

    private String key(Long tenantId, Long runId, String callId) {
        return tenantId + ":" + runId + ":" + callId;
    }

    private String jsonEscape(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) builder.append(String.format("\\u%04x", (int) c));
                    else builder.append(c);
                }
            }
        }
        return builder.append('"').toString();
    }

    static final class PendingAsk {
        private final Long tenantId;
        private final Long runId;
        private final String callId;
        private final String question;
        private final LocalDateTime askedAt;
        private final Duration timeout;
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile String answer;
        private volatile String abortReason;
        private volatile Span span;

        PendingAsk(Long tenantId, Long runId, String callId, String question, LocalDateTime askedAt, Duration timeout) {
            this.tenantId = tenantId;
            this.runId = runId;
            this.callId = callId;
            this.question = question;
            this.askedAt = askedAt;
            this.timeout = timeout;
        }

        void complete(String value) {
            this.answer = value;
            latch.countDown();
        }

        void abort(String reason) {
            this.abortReason = reason;
            latch.countDown();
        }

        boolean aborted() {
            return abortReason != null && answer == null;
        }

        void span(Span span) {
            this.span = span;
        }

        Span span() {
            return span;
        }

        CountDownLatch latch() {
            return latch;
        }

        String answer() {
            return answer;
        }

        String abortReason() {
            return abortReason;
        }

        Long tenantId() {
            return tenantId;
        }

        Long runId() {
            return runId;
        }

        String callId() {
            return callId;
        }

        String question() {
            return question;
        }

        LocalDateTime askedAt() {
            return askedAt;
        }

        Duration timeout() {
            return timeout;
        }
    }

    public record AskOutcome(boolean answered, String answer, String failureReason) {
    }

    public record RespondResult(Long runId, String callId, String status) {
    }

    public record PendingAskView(Long runId, String callId, LocalDateTime askedAt, Duration timeout) {
    }
}
