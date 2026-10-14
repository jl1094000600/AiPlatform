package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.agent.runtime.AgentRunStreamEventType;
import com.aipal.agent.runtime.AgentStepType;
import com.aipal.agent.runtime.AgentTaskStatus;
import com.aipal.agent.runtime.budget.BudgetExceededException;
import com.aipal.agent.runtime.budget.RunBudgetGuard;
import com.aipal.agent.runtime.trace.RunTracer;
import com.aipal.agent.runtime.trace.Span;
import com.aipal.agent.runtime.trace.SpanStatus;
import com.aipal.agent.runtime.trace.SpanType;
import com.aipal.entity.AgentArtifact;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentStep;
import com.aipal.entity.AgentTask;
import com.aipal.mapper.AgentArtifactMapper;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.mapper.AgentStepMapper;
import com.aipal.mapper.AgentTaskMapper;
import com.aipal.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Executes the root RUN task. v1 remains a single model call; v2 is delegated to AgentLoopExecutor. */
@Service
@RequiredArgsConstructor
public class AgentRunExecutionService {
    private final AgentRunMapper runMapper;
    private final AgentTaskMapper taskMapper;
    private final AgentStepMapper stepMapper;
    private final AgentArtifactMapper artifactMapper;
    private final AgentTaskService taskService;
    private final AgentRunEventService eventService;
    private final AgentRunStreamEventService streamEventService;
    private final AgentLoopExecutor loopExecutor;
    private final AgentRunExecutionSnapshotService executionSnapshotService;
    private final RunBudgetGuard runBudgetGuard;
    private final RunTracer runTracer;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "agent-runtime-heartbeat");
        thread.setDaemon(true);
        return thread;
    });
    @Value("${aipal.agent-runtime.lease-seconds:60}")
    private int leaseSeconds;
    @Value("${aipal.agent-runtime.heartbeat-interval-ms:20000}")
    private long heartbeatIntervalMs;

    public void execute(AgentTask task, String workerId) {
        AgentRun run = runMapper.selectById(task.getRunId());
        if (run == null || !TenantContext.tenantId().equals(run.getTenantId()) || !AgentRunStatus.RUNNING.name().equals(run.getStatus())) return;
        AgentStep step = startStep(run, task);
        recordRunStarted(run, step);
        Span runSpan = runTracer.start(run.getTenantId(), run.getId(), SpanType.RUN, "run", step.getId(), null, null,
                run.getTraceId());
        ScheduledFuture<?> heartbeat = startHeartbeat(task, workerId);
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            Object input = objectMapper.readValue(run.getInputJson(), Object.class);
            Map<String, Object> definition = executionSnapshotService.decrypt(run);
            Span replySpan = runTracer.start(run.getTenantId(), run.getId(), SpanType.REPLY, "model-reply",
                    step.getId(), null, null, run.getTraceId());
            AgentLoopExecutor.LoopResult loopResult = loopExecutor.execute(run, input, definition);
            runTracer.end(replySpan, SpanStatus.OK, "model reply completed",
                    0, loopResult.totalTokens());
            Map<String, Object> result = loopResult.output();
            String resultJson = objectMapper.writeValueAsString(result);
            int totalTokens = loopResult.totalTokens();
            Duration elapsed = Duration.between(startedAt, LocalDateTime.now());
            Span budgetSpan = runTracer.start(run.getTenantId(), run.getId(), SpanType.BUDGET_CHECK, "budget",
                    step.getId(), null, null, run.getTraceId());
            Optional<RunBudgetGuard.BudgetViolation> violation =
                    runBudgetGuard.check(run, totalTokens, loopResult.steps(), elapsed);
            if (violation.isPresent()) {
                runTracer.end(budgetSpan, SpanStatus.ERROR, violation.get().reason(), 0, 0);
                transactionTemplate.executeWithoutResult(ignored ->
                        finalizeBudgetExceeded(run, task, step, workerId, totalTokens, violation.get().reason()));
                runTracer.end(runSpan, SpanStatus.ERROR, violation.get().reason(), 0, totalTokens);
                return;
            }
            runTracer.end(budgetSpan, SpanStatus.OK, "within budget", 0, 0);
            transactionTemplate.executeWithoutResult(ignored -> finalizeSuccess(run, task, step, workerId,
                    resultJson, totalTokens));
            runTracer.end(runSpan, SpanStatus.OK, "run completed", 0, totalTokens);
        } catch (BudgetExceededException budget) {
            transactionTemplate.executeWithoutResult(ignored -> finalizeBudgetExceeded(run, task, step, workerId,
                    run.getTotalTokens() == null ? 0 : run.getTotalTokens(), budget.getMessage()));
            runTracer.end(runSpan, SpanStatus.ERROR, budget.getMessage(), 0, 0);
        } catch (Exception exception) {
            runTracer.end(runSpan, SpanStatus.ERROR, exception.getClass().getSimpleName(), 0, 0);
            transactionTemplate.executeWithoutResult(ignored -> finalizeFailure(run, task, step, workerId,
                    exception.getClass().getSimpleName()));
        } finally {
            heartbeat.cancel(false);
        }
    }

    @PreDestroy
    void shutdownHeartbeatScheduler() {
        heartbeatScheduler.shutdownNow();
    }

    private AgentStep startStep(AgentRun run, AgentTask task) {
        AgentStep step = new AgentStep();
        step.setTenantId(run.getTenantId());
        step.setRunId(run.getId());
        step.setStepNo(task.getAttemptCount() + 1);
        step.setStepType(AgentStepType.FINAL.name());
        step.setStatus(AgentTaskStatus.RUNNING.name());
        step.setTraceId(run.getTraceId());
        step.setStartTime(LocalDateTime.now());
        step.setCreateTime(LocalDateTime.now());
        step.setUpdateTime(LocalDateTime.now());
        step.setIsDeleted(0);
        stepMapper.insert(step);
        return step;
    }

    @Transactional
    protected void finalizeSuccess(AgentRun run, AgentTask task, AgentStep step, String workerId, String resultJson, int totalTokens) {
        if (!completeOwnedTask(task, workerId, AgentTaskStatus.SUCCEEDED, null)) {
            markStepCancelled(step);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        step.setStatus(AgentTaskStatus.SUCCEEDED.name());
        step.setOutputJson(resultJson);
        step.setOutputTokens(totalTokens);
        step.setEndTime(now);
        step.setUpdateTime(now);
        stepMapper.updateById(step);
        AgentRun update = new AgentRun();
        update.setStatus(AgentRunStatus.WAITING_APPROVAL.name());
        update.setResultJson(resultJson);
        update.setTotalTokens(totalTokens);
        update.setVersion(run.getVersion() + 1);
        update.setUpdateTime(now);
        int changed = runMapper.update(update, new LambdaUpdateWrapper<AgentRun>().eq(AgentRun::getId, run.getId())
                .eq(AgentRun::getTenantId, run.getTenantId()).eq(AgentRun::getStatus, AgentRunStatus.RUNNING.name()));
        if (changed == 1) {
            createFinalArtifact(run, step, resultJson);
            eventService.record(run, AgentRunStatus.RUNNING.name(), AgentRunStatus.WAITING_APPROVAL.name(), "Awaiting artifact approval");
            streamEventService.record(run.getTenantId(), run.getId(), AgentRunStreamEventType.ARTIFACT_CREATED,
                    Map.of("status", "PENDING_APPROVAL", "stepId", step.getId(), "artifactType", "AGENT_RESULT"), true);
            streamEventService.record(run.getTenantId(), run.getId(), AgentRunStreamEventType.APPROVAL_REQUIRED,
                    Map.of("status", AgentRunStatus.WAITING_APPROVAL.name(), "stepId", step.getId(),
                            "totalTokens", totalTokens, "artifactStatus", "PENDING_APPROVAL"), true);
            runTracer.record(run.getTenantId(), run.getId(), SpanType.APPROVAL_WAIT, "artifact-approval",
                    step.getId(), null, null, run.getTraceId(), SpanStatus.WAITING,
                    "Awaiting artifact approval", now, null, 0, totalTokens);
        }
    }

    @Transactional
    protected void finalizeFailure(AgentRun run, AgentTask task, AgentStep step, String workerId, String reason) {
        boolean retry = task.getAttemptCount() < task.getMaxAttempts();
        if (!completeOwnedTask(task, workerId, retry ? AgentTaskStatus.QUEUED : AgentTaskStatus.FAILED, reason)) {
            markStepCancelled(step);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        step.setStatus(AgentTaskStatus.FAILED.name());
        step.setErrorMessage("Execution failed");
        step.setEndTime(now);
        step.setUpdateTime(now);
        stepMapper.updateById(step);
        if (!retry) {
            AgentRun update = new AgentRun();
            update.setStatus(AgentRunStatus.FAILED.name());
            update.setErrorMessage(reason);
            update.setEndTime(now);
            update.setVersion(run.getVersion() + 1);
            update.setUpdateTime(now);
            int changed = runMapper.update(update, new LambdaUpdateWrapper<AgentRun>().eq(AgentRun::getId, run.getId())
                    .eq(AgentRun::getTenantId, run.getTenantId()).eq(AgentRun::getStatus, AgentRunStatus.RUNNING.name()));
            if (changed == 1) {
                eventService.record(run, AgentRunStatus.RUNNING.name(), AgentRunStatus.FAILED.name(), reason);
                streamEventService.record(run.getTenantId(), run.getId(), AgentRunStreamEventType.RUN_FAILED,
                        Map.of("status", AgentRunStatus.FAILED.name(), "stepId", step.getId(),
                                "reason", safeFailureReason(reason)), true);
            }
        }
    }

    @Transactional
    protected void finalizeBudgetExceeded(AgentRun run, AgentTask task, AgentStep step, String workerId, int totalTokens,
                                          String reason) {
        String budgetReason = reason == null || reason.isBlank() ? "Token budget exceeded" : reason.trim();
        if (!completeOwnedTask(task, workerId, AgentTaskStatus.FAILED, budgetReason)) {
            markStepCancelled(step);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        step.setStatus(AgentTaskStatus.FAILED.name());
        step.setOutputTokens(totalTokens);
        step.setErrorMessage(budgetReason);
        step.setEndTime(now);
        step.setUpdateTime(now);
        stepMapper.updateById(step);
        AgentRun update = new AgentRun();
        update.setStatus(AgentRunStatus.FAILED.name());
        update.setErrorMessage(budgetReason);
        update.setTotalTokens(totalTokens);
        update.setEndTime(now);
        update.setVersion(run.getVersion() + 1);
        update.setUpdateTime(now);
        int changed = runMapper.update(update, new LambdaUpdateWrapper<AgentRun>().eq(AgentRun::getId, run.getId())
                .eq(AgentRun::getTenantId, run.getTenantId()).eq(AgentRun::getStatus, AgentRunStatus.RUNNING.name()));
        if (changed == 1) {
            eventService.record(run, AgentRunStatus.RUNNING.name(), AgentRunStatus.FAILED.name(), budgetReason);
            streamEventService.record(run.getTenantId(), run.getId(), AgentRunStreamEventType.RUN_FAILED,
                    Map.of("status", AgentRunStatus.FAILED.name(), "stepId", step.getId(),
                            "reason", budgetReason, "totalTokens", totalTokens), true);
        }
    }

    private void recordRunStarted(AgentRun run, AgentStep step) {
        streamEventService.record(run.getTenantId(), run.getId(), AgentRunStreamEventType.RUN_STARTED,
                Map.of("status", AgentRunStatus.RUNNING.name(), "stepId", step.getId(),
                        "loopVersion", run.getLoopVersion() == null ? "v1" : run.getLoopVersion()), true);
    }

    private boolean completeOwnedTask(AgentTask task, String workerId, AgentTaskStatus status, String reason) {
        LocalDateTime now = LocalDateTime.now();
        AgentTask update = new AgentTask();
        update.setStatus(status.name());
        update.setLeaseOwner(null);
        update.setLeaseUntil(null);
        update.setAvailableAt(status == AgentTaskStatus.QUEUED ? now : task.getAvailableAt());
        update.setEndTime(status == AgentTaskStatus.QUEUED ? null : now);
        update.setErrorMessage(reason);
        update.setUpdateTime(now);
        return taskMapper.update(update, new LambdaUpdateWrapper<AgentTask>().eq(AgentTask::getId, task.getId())
                .eq(AgentTask::getTenantId, task.getTenantId()).eq(AgentTask::getStatus, AgentTaskStatus.RUNNING.name())
                .eq(AgentTask::getLeaseOwner, workerId).gt(AgentTask::getLeaseUntil, now)) == 1;
    }

    private void markStepCancelled(AgentStep step) {
        step.setStatus(AgentTaskStatus.CANCELLED.name());
        step.setEndTime(LocalDateTime.now());
        step.setUpdateTime(LocalDateTime.now());
        stepMapper.updateById(step);
    }

    private ScheduledFuture<?> startHeartbeat(AgentTask task, String workerId) {
        TenantContext.Context context = TenantContext.get();
        long interval = Math.max(1000L, heartbeatIntervalMs);
        return heartbeatScheduler.scheduleAtFixedRate(() -> TenantContext.runWithContext(context,
                () -> taskService.heartbeat(task.getId(), workerId, leaseSeconds)), interval, interval, TimeUnit.MILLISECONDS);
    }

    private void createFinalArtifact(AgentRun run, AgentStep step, String resultJson) {
        AgentArtifact artifact = new AgentArtifact();
        artifact.setTenantId(run.getTenantId());
        artifact.setRunId(run.getId());
        artifact.setStepId(step.getId());
        artifact.setArtifactType("AGENT_RESULT");
        artifact.setTitle("Final result");
        artifact.setContentJson(resultJson);
        artifact.setStatus("PENDING_APPROVAL");
        artifact.setCreateTime(LocalDateTime.now());
        artifact.setUpdateTime(LocalDateTime.now());
        artifact.setIsDeleted(0);
        artifactMapper.insert(artifact);
    }

    private String safeFailureReason(String reason) {
        if (reason == null || reason.isBlank()) return "Execution failed";
        return reason.length() <= 128 ? reason : reason.substring(0, 128);
    }

}
