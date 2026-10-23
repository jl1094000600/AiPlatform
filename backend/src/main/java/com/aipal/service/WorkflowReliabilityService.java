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
import java.util.ArrayList;
import java.util.List;

/**
 * W4: CAS status transitions, leases, cancel idempotency, recovery, and event trail.
 * W5: terminal cleanup hook (runningTasks/timeout + workspace lease release) and
 * non-terminal WAITING_APPROVAL / WAITING_TOOL_APPROVAL pause transitions.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WorkflowReliabilityService {

    public static final List<String> TERMINAL_STATUSES =
            List.of("COMPLETED", "FAILED", "CANCELLED", "TIMEOUT",
                    "DRY_COMPLETED", "DRY_FAILED", "DRY_CANCELLED");

    /** Non-terminal pause states introduced by W5 tool-approval waits. */
    public static final List<String> WAITING_STATUSES =
            List.of("WAITING_APPROVAL", "WAITING_TOOL_APPROVAL");

    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowExecutionEventMapper eventMapper;

    /** Invoked exactly once for the CAS winner of a terminal transition. */
    @FunctionalInterface
    public interface TerminalCleanup {
        void onTerminal(String executionId, String status);
    }

    private volatile TerminalCleanup terminalCleanup;

    public void setTerminalCleanup(TerminalCleanup terminalCleanup) {
        this.terminalCleanup = terminalCleanup;
    }

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
                // W5: terminal rows drop the worker lease so recovery scanners skip them.
                .set(WorkflowExecution::getLeaseUntil, null)
                .set(WorkflowExecution::getOwner, null));
        if (updated > 0) {
            recordEvent(executionId, "FINISHED", null, status + (errorMessage == null ? "" : (": " + errorMessage)));
            TerminalCleanup cleanup = terminalCleanup;
            if (cleanup != null) {
                try {
                    cleanup.onTerminal(executionId, status);
                } catch (RuntimeException e) {
                    log.warn("Terminal cleanup failed for {}: {}", executionId, e.getMessage());
                }
            }
            return true;
        }
        return false;
    }

    /**
     * W5: pause a non-terminal execution while a tool call awaits approval.
     * Returns true when the pause was recorded (RUNNING/PENDING → waiting).
     */
    public boolean markWaiting(String executionId, String waitingStatus, String nodeId, String message) {
        if (executionId == null || waitingStatus == null || !WAITING_STATUSES.contains(waitingStatus)) {
            return false;
        }
        int updated = executionMapper.update(null, new LambdaUpdateWrapper<WorkflowExecution>()
                .eq(WorkflowExecution::getExecutionId, executionId)
                .notIn(WorkflowExecution::getStatus, TERMINAL_STATUSES)
                .notIn(WorkflowExecution::getStatus, WAITING_STATUSES)
                .set(WorkflowExecution::getStatus, waitingStatus));
        if (updated > 0) {
            recordEvent(executionId, "WAITING_APPROVAL", nodeId, message);
            return true;
        }
        return false;
    }

    /**
     * W5: resume after tool approval (WAITING_* → RUNNING). Returns true when resumed.
     */
    public boolean resumeRunning(String executionId, String message) {
        if (executionId == null) {
            return false;
        }
        int updated = executionMapper.update(null, new LambdaUpdateWrapper<WorkflowExecution>()
                .eq(WorkflowExecution::getExecutionId, executionId)
                .in(WorkflowExecution::getStatus, WAITING_STATUSES)
                .set(WorkflowExecution::getStatus, "RUNNING"));
        if (updated > 0) {
            recordEvent(executionId, "RESUMED", null, message);
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
     * Fail-closed recovery: RUNNING/WAITING executions with expired leases become FAILED.
     * WAITING_* rows are included so a crash during a tool-approval pause cannot strand
     * the execution forever.
     * @return number of recovered rows
     */
    public int recoverExpiredLeases(String reason) {
        List<String> recoverable = new ArrayList<>(List.of("RUNNING"));
        recoverable.addAll(WAITING_STATUSES);
        List<WorkflowExecution> expired = executionMapper.selectList(new LambdaQueryWrapper<WorkflowExecution>()
                .in(WorkflowExecution::getStatus, recoverable)
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
