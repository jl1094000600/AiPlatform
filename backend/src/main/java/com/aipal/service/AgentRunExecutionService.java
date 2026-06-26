package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.agent.runtime.AgentStepType;
import com.aipal.agent.runtime.AgentTaskStatus;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Executes the P0 root RUN task only. Tool loops and child delegation remain P1 work. */
@Service
@RequiredArgsConstructor
public class AgentRunExecutionService {
    private final AgentRunMapper runMapper;
    private final AgentTaskMapper taskMapper;
    private final AgentStepMapper stepMapper;
    private final AgentArtifactMapper artifactMapper;
    private final AgentTaskService taskService;
    private final AgentRunEventService eventService;
    private final AgentService agentService;
    private final AgentRunExecutionSnapshotService executionSnapshotService;
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
        ScheduledFuture<?> heartbeat = startHeartbeat(task, workerId);
        try {
            Object input = objectMapper.readValue(run.getInputJson(), Object.class);
            Map<String, Object> definition = executionSnapshotService.decrypt(run);
            Map<String, Object> runtime = mapValue(definition.get("runtimeConfig"));
            Long modelId = requiredLong(runtime, "modelId");
            Map<String, Object> result = agentService.callAgentFrozen(run.getAgentId(), input, run.getOwnerUserId(), null,
                    modelId, nullableLong(runtime.get("promptId")), nullableLong(runtime.get("promptVersionId")));
            String resultJson = objectMapper.writeValueAsString(result);
            int totalTokens = number(result.get("totalTokens"));
            if (run.getMaxTotalTokens() != null && totalTokens > run.getMaxTotalTokens()) {
                transactionTemplate.executeWithoutResult(ignored -> finalizeBudgetExceeded(run, task, step, workerId, totalTokens));
                return;
            }
            transactionTemplate.executeWithoutResult(ignored -> finalizeSuccess(run, task, step, workerId,
                    resultJson, totalTokens));
        } catch (Exception exception) {
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
            if (changed == 1) eventService.record(run, AgentRunStatus.RUNNING.name(), AgentRunStatus.FAILED.name(), reason);
        }
    }

    @Transactional
    protected void finalizeBudgetExceeded(AgentRun run, AgentTask task, AgentStep step, String workerId, int totalTokens) {
        if (!completeOwnedTask(task, workerId, AgentTaskStatus.FAILED, "Token budget exceeded")) {
            markStepCancelled(step);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        step.setStatus(AgentTaskStatus.FAILED.name());
        step.setOutputTokens(totalTokens);
        step.setErrorMessage("Token budget exceeded");
        step.setEndTime(now);
        step.setUpdateTime(now);
        stepMapper.updateById(step);
        AgentRun update = new AgentRun();
        update.setStatus(AgentRunStatus.FAILED.name());
        update.setErrorMessage("Token budget exceeded");
        update.setTotalTokens(totalTokens);
        update.setEndTime(now);
        update.setVersion(run.getVersion() + 1);
        update.setUpdateTime(now);
        int changed = runMapper.update(update, new LambdaUpdateWrapper<AgentRun>().eq(AgentRun::getId, run.getId())
                .eq(AgentRun::getTenantId, run.getTenantId()).eq(AgentRun::getStatus, AgentRunStatus.RUNNING.name()));
        if (changed == 1) eventService.record(run, AgentRunStatus.RUNNING.name(), AgentRunStatus.FAILED.name(), "Token budget exceeded");
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

    private int number(Object value) { return value instanceof Number number ? number.intValue() : 0; }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new IllegalStateException("Frozen runtime configuration is missing");
    }

    private Long requiredLong(Map<String, Object> value, String key) {
        Long result = nullableLong(value.get(key));
        if (result == null) throw new IllegalStateException("Frozen " + key + " is missing");
        return result;
    }

    private Long nullableLong(Object value) { return value instanceof Number number ? number.longValue() : null; }
}
