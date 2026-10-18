package com.aipal.service;

import com.aipal.common.ErrorCode;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.agent.runtime.AgentTaskStatus;
import com.aipal.agent.runtime.AskInteractionStatus;
import com.aipal.agent.runtime.trace.RunTracer;
import com.aipal.agent.runtime.trace.Span;
import com.aipal.agent.runtime.trace.SpanStatus;
import com.aipal.agent.runtime.trace.SpanType;
import com.aipal.common.BizException;
import com.aipal.entity.AgentAskInteraction;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentToolCall;
import com.aipal.mapper.AgentAskInteractionMapper;
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
 * Coordinates ask_user pauses. Every wait/answer is durable in {@code agent_ask_interaction}
 * so that restarts recover already-answered or already-timed-out asks without re-blocking,
 * duplicate responds are idempotent, and cancel/timeout release the wait deterministically.
 * Pipeline artifact approval (WAITING_APPROVAL) is untouched: ask_user uses WAITING_USER_INPUT.
 */
@Service
@RequiredArgsConstructor
public class AgentToolResponseService {

    private final AgentRunMapper runMapper;
    private final AgentToolCallMapper toolCallMapper;
    private final AgentRunEventService eventService;
    private final RunTracer runTracer;
    private final AgentAskInteractionMapper askInteractionMapper;

    @Value("${aipal.agent-runtime.ask-user.timeout-seconds:300}")
    private int defaultTimeoutSeconds = 300;
    @Value("${aipal.agent-runtime.ask-user.min-timeout-seconds:1}")
    private int minTimeoutSeconds = 1;
    @Value("${aipal.agent-runtime.ask-user.max-timeout-seconds:3600}")
    private int maxTimeoutSeconds = 3600;
    /** How often a waiter re-checks durable state so a remote/responding instance can release it. */
    @Value("${aipal.agent-runtime.ask-user.poll-interval-ms:250}")
    private long pollIntervalMs = 250;

    private final Map<String, PendingAsk> pendingAsks = new ConcurrentHashMap<>();

    /**
     * Registers the ask, flips Run/ToolCall into WAITING_USER_INPUT and blocks until the answer
     * arrives or the timeout elapses. Recovered terminal interactions return immediately.
     */
    public AskOutcome awaitAnswer(Long tenantId, Long runId, String callId, String question,
                                  Integer timeoutSeconds, Duration remainingBudget) {
        Duration timeout = resolveTimeout(timeoutSeconds, remainingBudget);
        AgentAskInteraction existing = findInteraction(tenantId, runId, callId);
        if (existing != null) {
            AskOutcome recovered = recoverOutcome(existing, tenantId, runId, callId, timeout);
            if (recovered != null) return recovered;
            Duration remaining = remainingUntilDeadline(existing);
            if (remaining != null && !remaining.isZero() && remaining.compareTo(timeout) < 0) {
                timeout = remaining;
            }
        } else {
            registerInteraction(tenantId, runId, callId, question, timeout);
        }

        String key = key(tenantId, runId, callId);
        PendingAsk pending = new PendingAsk(tenantId, runId, callId, question, LocalDateTime.now(), timeout);
        pendingAsks.put(key, pending);
        AgentRun run = requireRun(tenantId, runId);
        markWaiting(run, callId, question);
        Span span = runTracer.start(tenantId, runId, SpanType.ASK_USER_WAIT, "ask_user", null, null, callId,
                run.getTraceId());
        pending.span(span);
        try {
            awaitWithDurablePoll(pending, tenantId, runId, callId, timeout);
            if (pending.answer() != null) {
                runTracer.end(span, SpanStatus.OK, "ask_user answered", 0, 0);
                ensureToolCallAnswered(tenantId, runId, callId, pending.answer());
                resumeRun(tenantId, runId);
                return new AskOutcome(true, pending.answer(), null);
            }
            if (pending.aborted()) {
                runTracer.end(span, SpanStatus.CANCELLED, "ask_user aborted", 0, 0);
                return new AskOutcome(false, null, pending.abortReason() == null
                        ? "Cancelled while waiting for user input" : pending.abortReason());
            }
            runTracer.end(span, SpanStatus.TIMEOUT, "ask_user timed out", 0, 0);
            boolean resolved = resolveInteraction(tenantId, runId, callId, AskInteractionStatus.TIMED_OUT, null,
                    "ask_user timed out");
            if (resolved) {
                markTimeoutOnCall(tenantId, callId);
                resumeRun(tenantId, runId);
            }
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
     * Idempotent: only the first respond against a PENDING interaction takes effect.
     */
    public RespondResult respondToToolCall(Long runId, String callId, String answer) {
        if (callId == null || callId.isBlank()) throw ErrorCode.CALL_ID_REQUIRED.ex();
        if (answer == null || answer.isBlank()) throw ErrorCode.ANSWER_REQUIRED.ex();
        AgentToolCall call = toolCallMapper.selectOne(new LambdaQueryWrapper<AgentToolCall>()
                .eq(AgentToolCall::getRunId, runId)
                .eq(AgentToolCall::getCallId, callId.trim())
                .last("LIMIT 1"));
        if (call == null) throw ErrorCode.TOOL_CALL_NOT_FOUND.ex();
        AgentRun run = requireRun(call.getTenantId(), runId);
        if (!"ask_user".equals(call.getToolName())) {
            throw ErrorCode.TOOL_CALL_NOT_ASK_USER.ex();
        }
        String trimmed = answer.trim();
        AgentAskInteraction interaction = findInteraction(call.getTenantId(), runId, call.getCallId());
        if (interaction != null && AskInteractionStatus.ANSWERED.name().equals(interaction.getStatus())) {
            return new RespondResult(runId, call.getCallId(), "ALREADY_ANSWERED");
        }
        if (interaction != null && AskInteractionStatus.TIMED_OUT.name().equals(interaction.getStatus())) {
            throw ErrorCode.ASK_USER_ALREADY_TIMED_OUT.ex();
        }
        if (interaction != null && AskInteractionStatus.CANCELLED.name().equals(interaction.getStatus())) {
            throw ErrorCode.ASK_USER_ALREADY_CANCELLED.ex();
        }
        if (!AgentTaskStatus.RUNNING.name().equals(call.getStatus())
                && !AgentRunStatus.WAITING_USER_INPUT.name().equals(call.getStatus())
                && !AgentTaskStatus.SUCCEEDED.name().equals(call.getStatus())) {
            throw ErrorCode.TOOL_CALL_NOT_WAITING_INPUT.ex();
        }

        boolean won;
        if (interaction == null) {
            insertInteraction(call.getTenantId(), runId, call.getCallId(), questionFrom(call),
                    AskInteractionStatus.ANSWERED, trimmed, null);
            won = true;
        } else {
            won = resolveInteraction(call.getTenantId(), runId, call.getCallId(), AskInteractionStatus.ANSWERED,
                    trimmed, null);
            if (!won) {
                AgentAskInteraction current = findInteraction(call.getTenantId(), runId, call.getCallId());
                if (current != null && AskInteractionStatus.ANSWERED.name().equals(current.getStatus())) {
                    return new RespondResult(runId, call.getCallId(), "ALREADY_ANSWERED");
                }
                throw ErrorCode.ASK_USER_NOT_WAITING_INPUT.ex();
            }
        }

        persistAnswer(call, trimmed);
        String key = key(call.getTenantId(), runId, call.getCallId());
        PendingAsk pending = pendingAsks.get(key);
        if (pending != null) {
            pending.complete(trimmed);
            resumeRun(call.getTenantId(), runId);
            eventService.record(run, AgentRunStatus.WAITING_USER_INPUT.name(), AgentRunStatus.RUNNING.name(),
                    "User answered ask_user");
            return new RespondResult(runId, call.getCallId(), "ANSWERED");
        }
        resumeRun(call.getTenantId(), runId);
        return new RespondResult(runId, call.getCallId(), "ANSWER_RECORDED");
    }

    /** Aborts every in-flight ask for a run (used when the run is cancelled). */
    public int abortPendingForRun(Long runId, String reason) {
        String trimmed = reason == null || reason.isBlank()
                ? "Cancelled while waiting for user input" : reason.trim();
        int aborted = 0;
        for (Map.Entry<String, PendingAsk> entry : pendingAsks.entrySet()) {
            PendingAsk pending = entry.getValue();
            if (runId != null && runId.equals(pending.runId())) {
                pending.abort(trimmed);
                aborted++;
            }
        }
        resolveInteractionsForRun(runId, AskInteractionStatus.CANCELLED, trimmed);
        markCallsCancelledForRun(runId);
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

    /** Durable view of one ask interaction; used by recovery checks and tests. */
    public Optional<AskInteractionView> findAskInteraction(Long tenantId, Long runId, String callId) {
        return Optional.ofNullable(findInteraction(tenantId, runId, callId)).map(this::toView);
    }

    // ---- recovery ----

    /**
     * Maps a durable interaction to the outcome a waiting worker must observe after restart.
     * Returns null when the ask is still PENDING and the caller should resume waiting.
     */
    private AskOutcome recoverOutcome(AgentAskInteraction interaction, Long tenantId, Long runId, String callId,
                                      Duration timeout) {
        AskInteractionStatus status = AskInteractionStatus.valueOf(interaction.getStatus());
        switch (status) {
            case ANSWERED -> {
                ensureToolCallAnswered(tenantId, runId, callId, interaction.getAnswer());
                resumeRun(tenantId, runId);
                return new AskOutcome(true, interaction.getAnswer(), null);
            }
            case TIMED_OUT -> {
                markTimeoutOnCall(tenantId, callId);
                return new AskOutcome(false, null, interaction.getResolutionReason() != null
                        ? interaction.getResolutionReason()
                        : "ask_user timed out after " + timeout.getSeconds() + "s");
            }
            case CANCELLED -> {
                return new AskOutcome(false, null, interaction.getResolutionReason() != null
                        ? interaction.getResolutionReason() : "Cancelled while waiting for user input");
            }
            case PENDING -> {
                Duration remaining = remainingUntilDeadline(interaction);
                if (remaining == null || remaining.isNegative() || remaining.isZero()) {
                    boolean resolved = resolveInteraction(tenantId, runId, callId, AskInteractionStatus.TIMED_OUT,
                            null, "ask_user timed out");
                    if (resolved) {
                        markTimeoutOnCall(tenantId, callId);
                        resumeRun(tenantId, runId);
                    }
                    return new AskOutcome(false, null, "ask_user timed out after " + timeout.getSeconds() + "s");
                }
                return null;
            }
            default -> throw new IllegalStateException("Unknown ask status " + interaction.getStatus());
        }
    }

    private void awaitWithDurablePoll(PendingAsk pending, Long tenantId, Long runId, String callId, Duration timeout)
            throws InterruptedException {
        long deadlineMs = System.currentTimeMillis() + Math.max(0L, timeout.toMillis());
        long pollMs = Math.max(50L, pollIntervalMs);
        while (true) {
            long remainingMs = deadlineMs - System.currentTimeMillis();
            if (remainingMs <= 0) return;
            boolean woken = pending.latch().await(Math.min(remainingMs, pollMs), TimeUnit.MILLISECONDS);
            if (woken) return;
            AgentAskInteraction current = findInteraction(tenantId, runId, callId);
            if (current == null) continue;
            AskInteractionStatus status = AskInteractionStatus.valueOf(current.getStatus());
            if (status == AskInteractionStatus.ANSWERED) {
                pending.complete(current.getAnswer());
                return;
            }
            if (status.terminal()) {
                pending.abort(current.getResolutionReason() == null
                        ? "ask_user " + status.name().toLowerCase() : current.getResolutionReason());
                return;
            }
        }
    }

    // ---- durable helpers ----

    private AgentAskInteraction findInteraction(Long tenantId, Long runId, String callId) {
        return askInteractionMapper.selectOne(new LambdaQueryWrapper<AgentAskInteraction>()
                .eq(AgentAskInteraction::getTenantId, tenantId)
                .eq(AgentAskInteraction::getRunId, runId)
                .eq(AgentAskInteraction::getCallId, callId)
                .last("LIMIT 1"));
    }

    private void registerInteraction(Long tenantId, Long runId, String callId, String question, Duration timeout) {
        LocalDateTime now = LocalDateTime.now();
        AgentAskInteraction interaction = new AgentAskInteraction();
        interaction.setTenantId(tenantId);
        interaction.setRunId(runId);
        interaction.setCallId(callId);
        interaction.setQuestion(question == null ? "" : question);
        interaction.setStatus(AskInteractionStatus.PENDING.name());
        interaction.setTimeoutSeconds((int) timeout.getSeconds());
        interaction.setDeadline(now.plus(timeout));
        interaction.setAskedAt(now);
        interaction.setCreateTime(now);
        interaction.setUpdateTime(now);
        interaction.setIsDeleted(0);
        askInteractionMapper.insert(interaction);
    }

    private void insertInteraction(Long tenantId, Long runId, String callId, String question,
                                   AskInteractionStatus status, String answer, String resolutionReason) {
        LocalDateTime now = LocalDateTime.now();
        AgentAskInteraction interaction = new AgentAskInteraction();
        interaction.setTenantId(tenantId);
        interaction.setRunId(runId);
        interaction.setCallId(callId);
        interaction.setQuestion(question == null ? "" : question);
        interaction.setStatus(status.name());
        interaction.setAnswer(answer);
        interaction.setResolutionReason(resolutionReason);
        if (status == AskInteractionStatus.ANSWERED) {
            interaction.setAnsweredAt(now);
        } else {
            interaction.setResolvedAt(now);
        }
        interaction.setCreateTime(now);
        interaction.setUpdateTime(now);
        interaction.setIsDeleted(0);
        askInteractionMapper.insert(interaction);
    }

    /**
     * CAS transition out of PENDING. Returns true only for the caller that won the transition;
     * every later respond/cancel/timeout observes false and stays idempotent.
     */
    private boolean resolveInteraction(Long tenantId, Long runId, String callId, AskInteractionStatus target,
                                       String answer, String resolutionReason) {
        LocalDateTime now = LocalDateTime.now();
        AgentAskInteraction update = new AgentAskInteraction();
        update.setStatus(target.name());
        update.setUpdateTime(now);
        if (target == AskInteractionStatus.ANSWERED) {
            update.setAnswer(answer);
            update.setAnsweredAt(now);
        } else {
            update.setAnswer(answer);
            update.setResolvedAt(now);
            update.setResolutionReason(resolutionReason);
        }
        int changed = askInteractionMapper.update(update, new LambdaUpdateWrapper<AgentAskInteraction>()
                .eq(AgentAskInteraction::getTenantId, tenantId)
                .eq(AgentAskInteraction::getRunId, runId)
                .eq(AgentAskInteraction::getCallId, callId)
                .eq(AgentAskInteraction::getStatus, AskInteractionStatus.PENDING.name()));
        return changed == 1;
    }

    private int resolveInteractionsForRun(Long runId, AskInteractionStatus target, String reason) {
        LocalDateTime now = LocalDateTime.now();
        AgentAskInteraction update = new AgentAskInteraction();
        update.setStatus(target.name());
        update.setResolvedAt(now);
        update.setResolutionReason(reason);
        update.setUpdateTime(now);
        return askInteractionMapper.update(update, new LambdaUpdateWrapper<AgentAskInteraction>()
                .eq(AgentAskInteraction::getRunId, runId)
                .eq(AgentAskInteraction::getStatus, AskInteractionStatus.PENDING.name()));
    }

    private Duration remainingUntilDeadline(AgentAskInteraction interaction) {
        if (interaction.getDeadline() == null) return null;
        Duration remaining = Duration.between(LocalDateTime.now(), interaction.getDeadline());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private AskInteractionView toView(AgentAskInteraction interaction) {
        return new AskInteractionView(interaction.getTenantId(), interaction.getRunId(), interaction.getCallId(),
                interaction.getStatus(), interaction.getAnswer(), interaction.getQuestion(),
                interaction.getDeadline(), interaction.getResolutionReason());
    }

    private String questionFrom(AgentToolCall call) {
        try {
            String inputJson = call.getInputJson();
            if (inputJson == null) return "";
            int idx = inputJson.indexOf("\"question\"");
            if (idx < 0) return "";
            int colon = inputJson.indexOf(':', idx);
            int start = inputJson.indexOf('"', colon + 1);
            int end = inputJson.indexOf('"', start + 1);
            return start >= 0 && end > start ? inputJson.substring(start + 1, end) : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    // ---- run / tool-call status (WAITING_USER_INPUT, never WAITING_APPROVAL) ----

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

    private void markCallsCancelledForRun(Long runId) {
        AgentToolCall callUpdate = new AgentToolCall();
        callUpdate.setStatus(AgentTaskStatus.CANCELLED.name());
        callUpdate.setErrorMessage("ask_user cancelled");
        callUpdate.setEndTime(LocalDateTime.now());
        callUpdate.setUpdateTime(LocalDateTime.now());
        toolCallMapper.update(callUpdate, new LambdaUpdateWrapper<AgentToolCall>()
                .eq(AgentToolCall::getRunId, runId)
                .eq(AgentToolCall::getToolName, "ask_user")
                .in(AgentToolCall::getStatus, AgentRunStatus.WAITING_USER_INPUT.name(),
                        AgentTaskStatus.RUNNING.name()));
    }

    private void ensureToolCallAnswered(Long tenantId, Long runId, String callId, String answer) {
        AgentToolCall call = toolCallMapper.selectOne(new LambdaQueryWrapper<AgentToolCall>()
                .eq(AgentToolCall::getTenantId, tenantId)
                .eq(AgentToolCall::getRunId, runId)
                .eq(AgentToolCall::getCallId, callId)
                .last("LIMIT 1"));
        if (call == null) return;
        if (AgentTaskStatus.SUCCEEDED.name().equals(call.getStatus())) return;
        persistAnswer(call, answer == null ? "" : answer);
    }

    private void persistAnswer(AgentToolCall call, String answer) {
        AgentToolCall update = new AgentToolCall();
        update.setId(call.getId());
        update.setStatus(AgentTaskStatus.SUCCEEDED.name());
        update.setOutputJson("{\"answer\":" + jsonEscape(answer) + "}");
        update.setOutputSummary("ask_user answered");
        update.setEndTime(LocalDateTime.now());
        update.setUpdateTime(LocalDateTime.now());
        toolCallMapper.updateById(update);
    }

    private AgentRun requireRun(Long tenantId, Long runId) {
        AgentRun run = runMapper.selectById(runId);
        if (run == null || !tenantId.equals(run.getTenantId())) throw ErrorCode.AGENT_RUN_NOT_FOUND.ex();
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

    public record AskInteractionView(Long tenantId, Long runId, String callId, String status, String answer,
                                     String question, LocalDateTime deadline, String resolutionReason) {
    }
}
