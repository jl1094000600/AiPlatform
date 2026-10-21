package com.aipal.service;

import com.aipal.entity.WorkflowExecution;
import com.aipal.entity.WorkflowExecutionEvent;
import com.aipal.mapper.WorkflowExecutionEventMapper;
import com.aipal.mapper.WorkflowExecutionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * W4: CAS status transitions, leases, cancel idempotency, recovery, and event trail.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WorkflowReliabilityService {

    public static final List<String> TERMINAL_STATUSES =
            List.of("COMPLETED", "FAILED", "CANCELLED", "TIMEOUT",
                    "DRY_COMPLETED", "DRY_FAILED", "DRY_CANCELLED");

    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowExecutionEventMapper eventMapper;

    public void recordEvent(String executionId, String eventType, String nodeId, String message) {
        if (executionId == null || executionId.isBlank()) {
            return;
        }
        try {
            WorkflowExecutionEvent event = new WorkflowExecutionEvent();
            event.setExecutionId(executionId);
            event.setEventType(eventType == null ? "INFO" : eventType);
            event.setNodeId(nodeId);
            event.setMessage(message == null ? null : truncate(message, 1000));
            event.setCreateTime(LocalDateTime.now());
            eventMapper.insert(event);
        } catch (Exception e) {
            log.warn("Failed to persist workflow event {} for {}: {}", eventType, executionId, e.getMessage());
        }
    }

    /** CAS terminal transition. Returns true only for the winner. */
    public boolean finishCas(String executionId, String status, String result, String errorMessage) {
        if (executionId == null || status == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = executionMapper.update(null, new LambdaUpdateWrapper<WorkflowExecution>()
                .eq(WorkflowExecution::getExecutionId, executionId)
                .notIn(WorkflowExecution::getStatus, TERMINAL_STATUSES)
                .set(WorkflowExecution::getStatus, status)
                .set(WorkflowExecution::getResult, result)
                .set(WorkflowExecution::getErrorMessage, errorMessage)
                .set(WorkflowExecution::getEndTime, now)
                .set(WorkflowExecution::getLeaseUntil, now));
        if (updated > 0) {
            recordEvent(executionId, "FINISHED", null, status + (errorMessage == null ? "" : (": " + errorMessage)));
            return true;
        }
        return false;
    }

    /** Idempotent cancel. Returns the resulting status. */
    public String cancelCas(String executionId, String reason) {
        WorkflowExecution current = executionMapper.selectOne(new LambdaQueryWrapper<WorkflowExecution>()
                .eq(WorkflowExecution::getExecutionId, executionId)
                .last("LIMIT 1"));
        if (current == null) {
            throw new IllegalArgumentException("Workflow execution not found: " + executionId);
        }
        if (TERMINAL_STATUSES.contains(current.getStatus())) {
            return current.getStatus();
        }
        executionMapper.update(null, new LambdaUpdateWrapper<WorkflowExecution>()
                .eq(WorkflowExecution::getExecutionId, executionId)
                .set(WorkflowExecution::getCancelRequested, 1));
        boolean won = finishCas(executionId, "CANCELLED", null,
                reason == null || reason.isBlank() ? "Execution cancelled" : reason);
        if (won) {
            recordEvent(executionId, "CANCELLED", null, reason);
            return "CANCELLED";
        }
        WorkflowExecution refreshed = executionMapper.selectOne(new LambdaQueryWrapper<WorkflowExecution>()
                .eq(WorkflowExecution::getExecutionId, executionId)
                .last("LIMIT 1"));
        return refreshed == null ? "CANCELLED" : refreshed.getStatus();
    }

    public void acquireLease(String executionId, String owner, int leaseSeconds) {
        executionMapper.update(null, new LambdaUpdateWrapper<WorkflowExecution>()
                .eq(WorkflowExecution::getExecutionId, executionId)
                .set(WorkflowExecution::getOwner, owner)
                .set(WorkflowExecution::getLeaseUntil, LocalDateTime.now().plusSeconds(leaseSeconds)));
    }

    public void heartbeat(String executionId, String owner, int leaseSeconds) {
        executionMapper.update(null, new LambdaUpdateWrapper<WorkflowExecution>()
                .eq(WorkflowExecution::getExecutionId, executionId)
                .eq(WorkflowExecution::getOwner, owner)
                .eq(WorkflowExecution::getStatus, "RUNNING")
                .set(WorkflowExecution::getLeaseUntil, LocalDateTime.now().plusSeconds(leaseSeconds)));
    }

    public void releaseLease(String executionId, String owner) {
        executionMapper.update(null, new LambdaUpdateWrapper<WorkflowExecution>()
                .eq(WorkflowExecution::getExecutionId, executionId)
                .eq(WorkflowExecution::getOwner, owner)
                .set(WorkflowExecution::getLeaseUntil, null));
    }

    /**
     * Fail-closed recovery: RUNNING executions with expired leases become FAILED.
     * @return number of recovered rows
     */
    public int recoverExpiredLeases(String reason) {
        List<WorkflowExecution> expired = executionMapper.selectList(new LambdaQueryWrapper<WorkflowExecution>()
                .eq(WorkflowExecution::getStatus, "RUNNING")
                .isNotNull(WorkflowExecution::getLeaseUntil)
                .lt(WorkflowExecution::getLeaseUntil, LocalDateTime.now()));
        int recovered = 0;
        for (WorkflowExecution execution : expired) {
            boolean won = finishCas(execution.getExecutionId(), "FAILED", null,
                    reason == null ? "Recovered after lease expiry" : reason);
            if (won) {
                executionMapper.update(null, new LambdaUpdateWrapper<WorkflowExecution>()
                        .eq(WorkflowExecution::getExecutionId, execution.getExecutionId())
                        .setSql("recover_count = COALESCE(recover_count, 0) + 1"));
                recordEvent(execution.getExecutionId(), "RECOVERED", null, "lease expired");
                recovered++;
            }
        }
        return recovered;
    }

    public boolean isCancelRequested(String executionId) {
        WorkflowExecution current = executionMapper.selectOne(new LambdaQueryWrapper<WorkflowExecution>()
                .eq(WorkflowExecution::getExecutionId, executionId)
                .last("LIMIT 1"));
        return current != null && current.getCancelRequested() != null && current.getCancelRequested() == 1;
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
