package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.agent.runtime.AgentTaskStatus;
import com.aipal.agent.runtime.budget.RunBudgetGuard;
import com.aipal.common.BizException;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentTask;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.mapper.AgentTaskMapper;
import com.aipal.security.TenantContext;
import com.aipal.security.TenantTaskRunner;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final AgentRunEventService eventService;
    private final RunBudgetGuard runBudgetGuard;
    private final ObjectMapper objectMapper;

    private static final Set<String> MODEL_TASK_STATUSES = Set.of(
            AgentTaskStatus.QUEUED.name(), AgentTaskStatus.RUNNING.name(),
            AgentTaskStatus.SUCCEEDED.name(), AgentTaskStatus.FAILED.name(), AgentTaskStatus.CANCELLED.name());

    // ---- Model-facing task CRUD (task.create / task.list / task.get / task.update) ----

    /**
     * Creates a child task owned by the run. Worker claim only picks up root RUN tasks, so
     * model-created tasks are never leased to workers.
     */
    @Transactional
    public AgentTask createModelTask(Long tenantId, Long runId, String taskType, Object payload) {
        AgentRun run = requireRun(tenantId, runId);
        AgentTask root = rootTask(runId, tenantId);
        long existing = countChildTasks(runId, tenantId);
        runBudgetGuard.checkChildTaskCount(run, existing).ifPresent(violation -> {
            throw new BizException(409, violation.reason());
        });
        String normalizedType = taskType == null || taskType.isBlank() ? "MODEL_TASK" : taskType.trim();
        if (normalizedType.length() > 64) throw new BizException(400, "taskType too long");
        AgentTask task = new AgentTask();
        task.setTenantId(tenantId);
        task.setRunId(runId);
        task.setParentTaskId(root == null ? null : root.getId());
        task.setTaskType(normalizedType);
        task.setStatus(AgentTaskStatus.QUEUED.name());
        task.setPayloadJson(payload == null ? null : json(payload));
        task.setAttemptCount(0);
        task.setMaxAttempts(1);
        task.setAvailableAt(LocalDateTime.now());
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        task.setIsDeleted(0);
        taskMapper.insert(task);
        return task;
    }

    public List<AgentTask> listModelTasks(Long tenantId, Long runId, String status) {
        requireRun(tenantId, runId);
        LambdaQueryWrapper<AgentTask> query = new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getTenantId, tenantId)
                .eq(AgentTask::getRunId, runId)
                .orderByAsc(AgentTask::getCreateTime)
                .orderByAsc(AgentTask::getId);
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase();
            if (!MODEL_TASK_STATUSES.contains(normalized)) throw new BizException(400, "Invalid task status");
            query.eq(AgentTask::getStatus, normalized);
        }
        return taskMapper.selectList(query);
    }

    public AgentTask getModelTask(Long tenantId, Long runId, Long taskId) {
        requireRun(tenantId, runId);
        AgentTask task = taskMapper.selectById(taskId);
        if (task == null || !tenantId.equals(task.getTenantId()) || !runId.equals(task.getRunId())) {
            throw new BizException(404, "Task not found");
        }
        return task;
    }

    /** Model-driven status transition for a task it can see on this run. */
    @Transactional
    public AgentTask updateModelTaskStatus(Long tenantId, Long runId, Long taskId, String status, String note) {
        AgentTask task = getModelTask(tenantId, runId, taskId);
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (normalized == null || !MODEL_TASK_STATUSES.contains(normalized)) {
            throw new BizException(400, "Invalid task status");
        }
        if (task.getStatus() != null && isClosedTaskStatus(task.getStatus())
                && !task.getStatus().equals(normalized)) {
            throw new BizException(409, "Task is already closed");
        }
        LocalDateTime now = LocalDateTime.now();
        AgentTask update = new AgentTask();
        update.setStatus(normalized);
        update.setErrorMessage(note == null || note.isBlank() ? task.getErrorMessage()
                : note.trim().substring(0, Math.min(512, note.trim().length())));
        if (AgentTaskStatus.RUNNING.name().equals(normalized) && task.getStartTime() == null) {
            update.setStartTime(now);
        }
        if (isClosedTaskStatus(normalized)) {
            update.setEndTime(now);
        }
        update.setUpdateTime(now);
        taskMapper.update(update, new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getId, taskId)
                .eq(AgentTask::getTenantId, tenantId));
        return taskMapper.selectById(taskId);
    }

    private AgentTask rootTask(Long runId, Long tenantId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getTenantId, tenantId)
                .eq(AgentTask::getRunId, runId)
                .eq(AgentTask::getTaskType, "RUN")
                .isNull(AgentTask::getParentTaskId)
                .last("LIMIT 1"));
    }

    private long countChildTasks(Long runId, Long tenantId) {
        Long count = taskMapper.selectCount(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getTenantId, tenantId)
                .eq(AgentTask::getRunId, runId)
                .ne(AgentTask::getTaskType, "RUN"));
        return count == null ? 0L : count;
    }

    private AgentRun requireRun(Long tenantId, Long runId) {
        AgentRun run = runMapper.selectById(runId);
        if (run == null || !tenantId.equals(run.getTenantId())) throw new BizException(404, "Agent Run不存在");
        return run;
    }

    private boolean isClosedTaskStatus(String status) {
        return AgentTaskStatus.SUCCEEDED.name().equals(status)
                || AgentTaskStatus.FAILED.name().equals(status)
                || AgentTaskStatus.CANCELLED.name().equals(status);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(400, "Task payload cannot be serialized");
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
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
            int changed = runMapper.update(start, new LambdaUpdateWrapper<AgentRun>()
                    .eq(AgentRun::getId, run.getId())
                    .eq(AgentRun::getTenantId, tenantId)
                    .eq(AgentRun::getStatus, AgentRunStatus.QUEUED.name()));
            if (changed == 1) eventService.record(run, AgentRunStatus.QUEUED.name(), AgentRunStatus.RUNNING.name(), "Worker claimed root task");
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

    public boolean releaseClaim(Long taskId, String workerId, String reason) {
        if (taskId == null || workerId == null || workerId.isBlank()) return false;
        LocalDateTime now = LocalDateTime.now();
        AgentTask update = new AgentTask();
        update.setStatus(AgentTaskStatus.QUEUED.name());
        update.setLeaseOwner(null);
        update.setLeaseUntil(null);
        update.setAvailableAt(now);
        update.setErrorMessage(normalizeReason(reason));
        update.setUpdateTime(now);
        return taskMapper.update(update, ownedRunningTask(taskId, workerId, now)) == 1;
    }

    public long runningRootTaskCount() {
        return taskMapper.selectCount(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getTenantId, TenantContext.tenantId())
                .eq(AgentTask::getStatus, AgentTaskStatus.RUNNING.name())
                .eq(AgentTask::getTaskType, "RUN")
                .isNull(AgentTask::getParentTaskId));
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
