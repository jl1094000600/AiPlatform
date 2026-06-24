package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.agent.runtime.AgentTaskStatus;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentTask;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.mapper.AgentTaskMapper;
import com.aipal.security.TenantContext;
import com.aipal.security.TenantTaskRunner;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Durable task lifecycle for on-demand subagents. A worker only owns a task while it holds
 * its lease; every state-changing write is constrained by tenant, task state and lease owner.
 */
@Service
@RequiredArgsConstructor
public class AgentTaskService {

    private final AgentTaskMapper taskMapper;
    private final AgentRunMapper runMapper;
    private final TenantTaskRunner tenantTaskRunner;

    @Transactional
    public AgentTask claimNext(String workerId, int leaseSeconds) {
        if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("workerId is required");
        if (leaseSeconds < 5 || leaseSeconds > 600) throw new IllegalArgumentException("leaseSeconds must be between 5 and 600");

        Long tenantId = TenantContext.tenantId();
        AgentTask task = taskMapper.selectNextClaimableForUpdate(tenantId);
        if (task == null) return null;

        AgentRun run = runMapper.selectById(task.getRunId());
        if (run == null || !tenantId.equals(run.getTenantId()) || isTerminal(run.getStatus())) {
            cancelTask(task.getId(), "Run is no longer executable");
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        AgentTask update = new AgentTask();
        update.setStatus(AgentTaskStatus.RUNNING.name());
        update.setLeaseOwner(workerId.trim());
        update.setLeaseUntil(now.plusSeconds(leaseSeconds));
        update.setAttemptCount(task.getAttemptCount() + 1);
        update.setStartTime(task.getStartTime() == null ? now : task.getStartTime());
        update.setUpdateTime(now);
        int claimed = taskMapper.update(update, new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getId, task.getId())
                .eq(AgentTask::getTenantId, tenantId)
                .eq(AgentTask::getStatus, AgentTaskStatus.QUEUED.name()));
        if (claimed != 1) return null;

        if (AgentRunStatus.QUEUED.name().equals(run.getStatus())) {
            AgentRun start = new AgentRun();
            start.setStatus(AgentRunStatus.RUNNING.name());
            start.setStartTime(now);
            start.setUpdateTime(now);
            runMapper.update(start, new LambdaUpdateWrapper<AgentRun>()
                    .eq(AgentRun::getId, run.getId())
                    .eq(AgentRun::getTenantId, tenantId)
                    .eq(AgentRun::getStatus, AgentRunStatus.QUEUED.name()));
        }
        task.setStatus(update.getStatus());
        task.setLeaseOwner(update.getLeaseOwner());
        task.setLeaseUntil(update.getLeaseUntil());
        task.setAttemptCount(update.getAttemptCount());
        task.setStartTime(update.getStartTime());
        return task;
    }

    public boolean heartbeat(Long taskId, String workerId, int leaseSeconds) {
        if (taskId == null || workerId == null || workerId.isBlank()) return false;
        if (leaseSeconds < 5 || leaseSeconds > 600) return false;
        LocalDateTime now = LocalDateTime.now();
        AgentTask update = new AgentTask();
        update.setLeaseUntil(now.plusSeconds(leaseSeconds));
        update.setUpdateTime(now);
        return taskMapper.update(update, new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getId, taskId)
                .eq(AgentTask::getTenantId, TenantContext.tenantId())
                .eq(AgentTask::getStatus, AgentTaskStatus.RUNNING.name())
                .eq(AgentTask::getLeaseOwner, workerId.trim())
                .gt(AgentTask::getLeaseUntil, now)) == 1;
    }

    public boolean complete(Long taskId, String workerId) {
        return finish(taskId, workerId, AgentTaskStatus.SUCCEEDED, null);
    }

    public boolean fail(Long taskId, String workerId, String reason) {
        if (taskId == null || workerId == null || workerId.isBlank()) return false;
        AgentTask task = taskMapper.selectById(taskId);
        if (task == null || !TenantContext.tenantId().equals(task.getTenantId())) return false;
        LocalDateTime now = LocalDateTime.now();
        boolean retry = task.getAttemptCount() < task.getMaxAttempts();
        AgentTask update = new AgentTask();
        update.setStatus((retry ? AgentTaskStatus.QUEUED : AgentTaskStatus.FAILED).name());
        update.setLeaseOwner(null);
        update.setLeaseUntil(null);
        update.setAvailableAt(retry ? now : task.getAvailableAt());
        update.setEndTime(retry ? null : now);
        update.setErrorMessage(normalizeReason(reason));
        update.setUpdateTime(now);
        return taskMapper.update(update, ownedRunningTask(taskId, workerId, now)) == 1;
    }

    @Transactional
    public int cancelTasksForRun(Long runId, String reason) {
        if (runId == null) return 0;
        LocalDateTime now = LocalDateTime.now();
        AgentTask update = new AgentTask();
        update.setStatus(AgentTaskStatus.CANCELLED.name());
        update.setLeaseOwner(null);
        update.setLeaseUntil(null);
        update.setEndTime(now);
        update.setErrorMessage(normalizeReason(reason));
        update.setUpdateTime(now);
        return taskMapper.update(update, new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getTenantId, TenantContext.tenantId())
                .eq(AgentTask::getRunId, runId)
                .in(AgentTask::getStatus, AgentTaskStatus.QUEUED.name(), AgentTaskStatus.RUNNING.name()));
    }

    @Scheduled(fixedDelayString = "${aipal.agent-runtime.lease-recovery-delay-ms:30000}",
            initialDelayString = "${aipal.agent-runtime.lease-recovery-initial-delay-ms:60000}")
    public void recoverExpiredLeasesForAllTenants() {
        tenantTaskRunner.forEachActiveTenant("agent-task-lease-recovery", tenant -> recoverExpiredLeases());
    }

    @Transactional
    public int recoverExpiredLeases() {
        Long tenantId = TenantContext.tenantId();
        LocalDateTime now = LocalDateTime.now();
        List<AgentTask> expired = taskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getTenantId, tenantId)
                .eq(AgentTask::getStatus, AgentTaskStatus.RUNNING.name())
                .lt(AgentTask::getLeaseUntil, now));
        int recovered = 0;
        for (AgentTask task : expired) {
            boolean retry = task.getAttemptCount() < task.getMaxAttempts();
            AgentTask update = new AgentTask();
            update.setStatus((retry ? AgentTaskStatus.QUEUED : AgentTaskStatus.FAILED).name());
            update.setLeaseOwner(null);
            update.setLeaseUntil(null);
            update.setAvailableAt(retry ? now : task.getAvailableAt());
            update.setEndTime(retry ? null : now);
            update.setErrorMessage(retry ? "Worker lease expired; task requeued" : "Worker lease expired; retries exhausted");
            update.setUpdateTime(now);
            recovered += taskMapper.update(update, new LambdaUpdateWrapper<AgentTask>()
                    .eq(AgentTask::getId, task.getId())
                    .eq(AgentTask::getTenantId, tenantId)
                    .eq(AgentTask::getStatus, AgentTaskStatus.RUNNING.name())
                    .le(AgentTask::getLeaseUntil, now));
        }
        return recovered;
    }

    private boolean finish(Long taskId, String workerId, AgentTaskStatus status, String reason) {
        if (taskId == null || workerId == null || workerId.isBlank()) return false;
        LocalDateTime now = LocalDateTime.now();
        AgentTask update = new AgentTask();
        update.setStatus(status.name());
        update.setLeaseOwner(null);
        update.setLeaseUntil(null);
        update.setEndTime(now);
        update.setErrorMessage(normalizeReason(reason));
        update.setUpdateTime(now);
        return taskMapper.update(update, ownedRunningTask(taskId, workerId, now)) == 1;
    }

    private LambdaUpdateWrapper<AgentTask> ownedRunningTask(Long taskId, String workerId, LocalDateTime now) {
        return new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getId, taskId)
                .eq(AgentTask::getTenantId, TenantContext.tenantId())
                .eq(AgentTask::getStatus, AgentTaskStatus.RUNNING.name())
                .eq(AgentTask::getLeaseOwner, workerId.trim())
                .gt(AgentTask::getLeaseUntil, now);
    }

    private void cancelTask(Long taskId, String reason) {
        AgentTask update = new AgentTask();
        update.setStatus(AgentTaskStatus.CANCELLED.name());
        update.setErrorMessage(reason);
        update.setEndTime(LocalDateTime.now());
        update.setUpdateTime(LocalDateTime.now());
        taskMapper.update(update, new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getId, taskId)
                .eq(AgentTask::getTenantId, TenantContext.tenantId())
                .eq(AgentTask::getStatus, AgentTaskStatus.QUEUED.name()));
    }

    private boolean isTerminal(String status) {
        try {
            return AgentRunStatus.valueOf(status).terminal();
        } catch (IllegalArgumentException ignored) {
            return true;
        }
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim().substring(0, Math.min(512, reason.trim().length()));
    }
}
