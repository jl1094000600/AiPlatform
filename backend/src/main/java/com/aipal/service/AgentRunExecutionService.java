package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.agent.runtime.AgentStepType;
import com.aipal.agent.runtime.AgentTaskStatus;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentStep;
import com.aipal.entity.AgentTask;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.mapper.AgentStepMapper;
import com.aipal.mapper.AgentTaskMapper;
import com.aipal.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;

/** Executes the P0 root RUN task only. Tool loops and child delegation remain P1 work. */
@Service
@RequiredArgsConstructor
public class AgentRunExecutionService {
    private final AgentRunMapper runMapper;
    private final AgentTaskMapper taskMapper;
    private final AgentStepMapper stepMapper;
    private final AgentService agentService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public void execute(AgentTask task, String workerId) {
        AgentRun run = runMapper.selectById(task.getRunId());
        if (run == null || !TenantContext.tenantId().equals(run.getTenantId()) || !AgentRunStatus.RUNNING.name().equals(run.getStatus())) return;
        AgentStep step = startStep(run, task);
        try {
            Object input = objectMapper.readValue(run.getInputJson(), Object.class);
            Map<String, Object> result = agentService.callAgent(run.getAgentId(), input, run.getOwnerUserId(), null);
            String resultJson = objectMapper.writeValueAsString(result);
            transactionTemplate.executeWithoutResult(ignored -> finalizeSuccess(run, task, step, workerId,
                    resultJson, number(result.get("totalTokens"))));
        } catch (Exception exception) {
            transactionTemplate.executeWithoutResult(ignored -> finalizeFailure(run, task, step, workerId,
                    exception.getClass().getSimpleName()));
        }
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
        update.setStatus(AgentRunStatus.SUCCEEDED.name());
        update.setResultJson(resultJson);
        update.setTotalTokens(totalTokens);
        update.setEndTime(now);
        update.setVersion(run.getVersion() + 1);
        update.setUpdateTime(now);
        runMapper.update(update, new LambdaUpdateWrapper<AgentRun>().eq(AgentRun::getId, run.getId())
                .eq(AgentRun::getTenantId, run.getTenantId()).eq(AgentRun::getStatus, AgentRunStatus.RUNNING.name()));
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
            runMapper.update(update, new LambdaUpdateWrapper<AgentRun>().eq(AgentRun::getId, run.getId())
                    .eq(AgentRun::getTenantId, run.getTenantId()).eq(AgentRun::getStatus, AgentRunStatus.RUNNING.name()));
        }
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

    private int number(Object value) { return value instanceof Number number ? number.intValue() : 0; }
}
