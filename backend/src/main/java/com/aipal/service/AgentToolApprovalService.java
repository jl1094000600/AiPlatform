package com.aipal.service;

import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentToolCall;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.mapper.AgentToolCallMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * W5: durable waits for tool calls whose AgentTool permission decision is REQUIRE_APPROVAL.
 * Reuses the existing AgentTool approval semantics ({@link AgentToolPermissionEvaluator}
 * + {@link AgentToolCall} audit rows) — there is no second approval policy.
 *
 * <p>Call status transitions: {@code WAITING_TOOL_APPROVAL} → RUNNING (approved) →
 * SUCCEEDED/FAILED, or {@code WAITING_TOOL_APPROVAL} → FAILED (rejected / timed out).
 * A {@link Listener} may pause the owning workflow execution as WAITING_APPROVAL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentToolApprovalService {

    public static final String STATUS_WAITING = "WAITING_TOOL_APPROVAL";

    private final AgentToolCallMapper toolCallMapper;
    private final AgentRunMapper runMapper;

    /** Optional: pauses the owning workflow execution as WAITING_APPROVAL while a tool waits. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private WorkflowReliabilityService workflowReliabilityService;

    @Value("${aipal.agent-runtime.tool-approval.timeout-seconds:300}")
    private int defaultTimeoutSeconds = 300;
    @Value("${aipal.agent-runtime.tool-approval.poll-interval-ms:250}")
    private long pollIntervalMs = 250;

    private final Map<String, PendingApproval> pendingApprovals = new ConcurrentHashMap<>();
    private volatile Listener listener = new Listener() {
    };

    /** Notified when a tool-approval wait starts/resolves so a workflow can pause/resume. */
    public interface Listener {
        default void onWaiting(Long runId, String businessId, Long toolCallId, String toolName) {
        }

        default void onResolved(Long runId, Long toolCallId, boolean approved, String reason) {
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener == null ? new Listener() {
        } : listener;
    }

    /** Test/spring hook for the optional workflow reliability bridge. */
    public void setWorkflowReliabilityService(WorkflowReliabilityService workflowReliabilityService) {
        this.workflowReliabilityService = workflowReliabilityService;
    }

    /**
     * Marks the call WAITING_TOOL_APPROVAL and blocks until approve/reject/timeout.
     * Recovered terminal calls return their outcome immediately (restart-safe).
     */
    public ApprovalOutcome awaitApproval(AgentRun run, AgentToolCall call, Duration timeout) {
        Duration effective = timeout == null || timeout.isZero() || timeout.isNegative()
                ? Duration.ofSeconds(Math.max(1, defaultTimeoutSeconds)) : timeout;
        Long tenantId = run.getTenantId();
        Long runId = run.getId();
        Long toolCallId = call.getId();
        String key = key(tenantId, toolCallId);

        markWaiting(call);
        ApprovalOutcome recovered = recoverOutcome(call, tenantId, toolCallId);
        if (recovered != null) {
            return recovered;
        }

        PendingApproval pending = new PendingApproval(runId, toolCallId, call.getToolName(), effective);
        pendingApprovals.put(key, pending);
        try {
            pauseWorkflowIfBound(run, toolCallId, call.getToolName());
            listener.onWaiting(runId, run.getBusinessId(), toolCallId, call.getToolName());
            awaitWithDurablePoll(pending, tenantId, toolCallId, effective);
            if (pending.approved() != null) {
                boolean approved = pending.approved();
                resumeWorkflowIfBound(run, approved, pending.reason());
                listener.onResolved(runId, toolCallId, approved, pending.reason());
                return new ApprovalOutcome(approved, pending.reason());
            }
            if (pending.aborted()) {
                resumeWorkflowIfBound(run, false, pending.reason());
                listener.onResolved(runId, toolCallId, false, pending.reason());
                return new ApprovalOutcome(false, pending.reason() == null
                        ? "Tool approval aborted" : pending.reason());
            }
            boolean resolved = casStatus(tenantId, toolCallId, STATUS_WAITING, "FAILED",
                    "Tool approval timed out", null);
            if (resolved) {
                resumeWorkflowIfBound(run, false, "Tool approval timed out");
                listener.onResolved(runId, toolCallId, false, "Tool approval timed out");
            }
            return new ApprovalOutcome(false, "Tool approval timed out after " + effective.getSeconds() + "s");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            resumeWorkflowIfBound(run, false, "Interrupted while waiting for tool approval");
            listener.onResolved(runId, toolCallId, false, "Interrupted while waiting for tool approval");
            return new ApprovalOutcome(false, "Interrupted while waiting for tool approval");
        } finally {
            pendingApprovals.remove(key);
        }
    }

    /**
     * Approves a waiting tool call. Idempotent: only the first resolution of a
     * WAITING_TOOL_APPROVAL call takes effect.
     */
    public boolean approve(Long tenantId, Long toolCallId, String reason) {
        return resolve(tenantId, toolCallId, true,
                reason == null || reason.isBlank() ? "Tool call approved" : reason.trim());
    }

    /** Rejects a waiting tool call; the owning node/run then fails. */
    public boolean reject(Long tenantId, Long toolCallId, String reason) {
        return resolve(tenantId, toolCallId, false,
                reason == null || reason.isBlank() ? "Tool call rejected" : reason.trim());
    }

    /** Aborts every in-flight approval wait for a run (cancel/timeout). */
    public int abortPendingForRun(Long runId, String reason) {
        String trimmed = reason == null || reason.isBlank()
                ? "Cancelled while waiting for tool approval" : reason.trim();
        int aborted = 0;
        for (Map.Entry<String, PendingApproval> entry : pendingApprovals.entrySet()) {
            PendingApproval pending = entry.getValue();
            if (runId != null && runId.equals(pending.runId())) {
                pending.abort(trimmed);
                aborted++;
            }
        }
        return aborted;
    }

    /** Durable view of calls currently waiting for approval. */
    public List<AgentToolCall> listWaiting(Long tenantId, Long runId) {
        return toolCallMapper.selectList(new LambdaQueryWrapper<AgentToolCall>()
                .eq(AgentToolCall::getTenantId, tenantId)
                .eq(runId != null, AgentToolCall::getRunId, runId)
                .eq(AgentToolCall::getStatus, STATUS_WAITING));
    }

    public boolean resolve(Long tenantId, Long toolCallId, boolean approved, String reason) {
        AgentToolCall call = toolCallMapper.selectOne(new LambdaQueryWrapper<AgentToolCall>()
                .eq(AgentToolCall::getTenantId, tenantId)
                .eq(AgentToolCall::getId, toolCallId)
                .last("LIMIT 1"));
        if (call == null) {
            return false;
        }
        if (!STATUS_WAITING.equals(call.getStatus())) {
            // already resolved elsewhere
            return STATUS_WAITING.equals(call.getStatus());
        }
        String key = key(tenantId, toolCallId);
        PendingApproval pending = pendingApprovals.get(key);
        if (approved) {
            boolean won = casStatus(tenantId, toolCallId, STATUS_WAITING, "RUNNING", null, reason);
            if (!won) {
                return false;
            }
            if (pending != null) {
                pending.complete(true, reason);
            }
            notifyResolved(call.getRunId(), toolCallId, true, reason);
            return true;
        }
        boolean won = casStatus(tenantId, toolCallId, STATUS_WAITING, "FAILED", reason, reason);
        if (!won) {
            return false;
        }
        if (pending != null) {
            pending.complete(false, reason);
        }
        notifyResolved(call.getRunId(), toolCallId, false, reason);
        return true;
    }

    private void notifyResolved(Long runId, Long toolCallId, boolean approved, String reason) {
        try {
            listener.onResolved(runId, toolCallId, approved, reason);
        } catch (RuntimeException e) {
            log.warn("Tool approval listener failed for call {}: {}", toolCallId, e.getMessage());
        }
    }

    private void markWaiting(AgentToolCall call) {
        LocalDateTime now = LocalDateTime.now();
        AgentToolCall update = new AgentToolCall();
        update.setId(call.getId());
        update.setStatus(STATUS_WAITING);
        update.setUpdateTime(now);
        toolCallMapper.updateById(update);
        call.setStatus(STATUS_WAITING);
    }

    private ApprovalOutcome recoverOutcome(AgentToolCall call, Long tenantId, Long toolCallId) {
        AgentToolCall current = toolCallMapper.selectOne(new LambdaQueryWrapper<AgentToolCall>()
                .eq(AgentToolCall::getTenantId, tenantId)
                .eq(AgentToolCall::getId, toolCallId)
                .last("LIMIT 1"));
        if (current == null) {
            return new ApprovalOutcome(false, "Tool call not found");
        }
        String status = current.getStatus();
        if ("RUNNING".equals(status) || "SUCCEEDED".equals(status)) {
            return new ApprovalOutcome(true, "Tool call already approved");
        }
        if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
            return new ApprovalOutcome(false, current.getErrorMessage() == null
                    ? "Tool call already rejected" : current.getErrorMessage());
        }
        return null;
    }

    private void awaitWithDurablePoll(PendingApproval pending, Long tenantId, Long toolCallId, Duration timeout)
            throws InterruptedException {
        long deadlineMs = System.currentTimeMillis() + Math.max(0L, timeout.toMillis());
        long pollMs = Math.max(50L, pollIntervalMs);
        while (true) {
            long remainingMs = deadlineMs - System.currentTimeMillis();
            if (remainingMs <= 0) {
                return;
            }
            boolean woken = pending.latch().await(Math.min(remainingMs, pollMs), TimeUnit.MILLISECONDS);
            if (woken) {
                return;
            }
            AgentToolCall current = toolCallMapper.selectOne(new LambdaQueryWrapper<AgentToolCall>()
                    .eq(AgentToolCall::getTenantId, tenantId)
                    .eq(AgentToolCall::getId, toolCallId)
                    .last("LIMIT 1"));
            if (current == null) {
                continue;
            }
            String status = current.getStatus();
            if ("RUNNING".equals(status) || "SUCCEEDED".equals(status)) {
                pending.complete(true, current.getErrorMessage());
                return;
            }
            if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                pending.complete(false, current.getErrorMessage() == null
                        ? "Tool call rejected" : current.getErrorMessage());
                return;
            }
        }
    }

    /** CAS status flip; true only for the winner. */
    private boolean casStatus(Long tenantId, Long toolCallId, String expected, String next,
                              String errorMessage, String outputSummary) {
        AgentToolCall update = new AgentToolCall();
        update.setStatus(next);
        if (errorMessage != null) {
            update.setErrorMessage(errorMessage);
        }
        if (outputSummary != null) {
            update.setOutputSummary(outputSummary);
        }
        if ("FAILED".equals(next) || "CANCELLED".equals(next) || "SUCCEEDED".equals(next)) {
            update.setEndTime(LocalDateTime.now());
        }
        update.setUpdateTime(LocalDateTime.now());
        return toolCallMapper.update(update, new LambdaUpdateWrapper<AgentToolCall>()
                .eq(AgentToolCall::getTenantId, tenantId)
                .eq(AgentToolCall::getId, toolCallId)
                .eq(AgentToolCall::getStatus, expected)) > 0;
    }

    private String key(Long tenantId, Long toolCallId) {
        return tenantId + ":" + toolCallId;
    }

    /** Approves a tool call that belongs to the given workflow execution. */
    public boolean approveForExecution(String executionId, Long toolCallId, String reason) {
        AgentToolCall call = requireCallForExecution(executionId, toolCallId);
        return approve(call.getTenantId(), toolCallId, reason);
    }

    /** Rejects a tool call that belongs to the given workflow execution; the node then fails. */
    public boolean rejectForExecution(String executionId, Long toolCallId, String reason) {
        AgentToolCall call = requireCallForExecution(executionId, toolCallId);
        return reject(call.getTenantId(), toolCallId, reason);
    }

    /** Waiting tool calls whose run businessId maps to this execution. */
    public List<AgentToolCall> listWaitingForExecution(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return List.of();
        }
        List<AgentToolCall> result = new ArrayList<>();
        String prefix = executionId + ":";
        for (AgentRun run : runsOfExecution(executionId)) {
            result.addAll(listWaiting(run.getTenantId(), run.getId()));
        }
        // businessId lives on the run; filter defensively by prefix match on run lookup.
        result.removeIf(call -> {
            AgentRun run = runMapper.selectById(call.getRunId());
            return run == null || run.getBusinessId() == null || !run.getBusinessId().startsWith(prefix);
        });
        return result;
    }

    /** Aborts in-flight approval waits for every run of the execution. */
    public int abortPendingForExecution(String executionId, String reason) {
        int aborted = 0;
        for (AgentRun run : runsOfExecution(executionId)) {
            aborted += abortPendingForRun(run.getId(), reason);
        }
        return aborted;
    }

    private List<AgentRun> runsOfExecution(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return List.of();
        }
        return runMapper.selectList(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getBusinessType, "WORKFLOW_NODE")
                .likeRight(AgentRun::getBusinessId, executionId + ":"));
    }

    private AgentToolCall requireCallForExecution(String executionId, Long toolCallId) {
        AgentToolCall call = toolCallMapper.selectOne(new LambdaQueryWrapper<AgentToolCall>()
                .eq(AgentToolCall::getId, toolCallId)
                .last("LIMIT 1"));
        if (call == null) {
            throw new IllegalArgumentException("Tool call not found: " + toolCallId);
        }
        AgentRun run = runMapper.selectById(call.getRunId());
        String bound = workflowExecutionIdOf(run);
        if (bound == null || !bound.equals(executionId)) {
            throw new IllegalArgumentException("Tool call does not belong to execution: " + executionId);
        }
        return call;
    }

    /**
     * Maps a workflow-bound run ({@code businessId = executionId:nodeId}) to its execution id.
     * Returns null for non-workflow runs.
     */
    public static String workflowExecutionIdOf(AgentRun run) {
        if (run == null || !"WORKFLOW_NODE".equals(run.getBusinessType())
                || run.getBusinessId() == null) {
            return null;
        }
        int idx = run.getBusinessId().lastIndexOf(':');
        return idx <= 0 ? null : run.getBusinessId().substring(0, idx);
    }

    private void pauseWorkflowIfBound(AgentRun run, Long toolCallId, String toolName) {
        if (workflowReliabilityService == null) {
            return;
        }
        String executionId = workflowExecutionIdOf(run);
        if (executionId == null) {
            return;
        }
        try {
            workflowReliabilityService.markWaiting(executionId, "WAITING_APPROVAL", null,
                    "Tool approval required: " + toolName + " (callId=" + toolCallId + ")");
        } catch (RuntimeException e) {
            log.warn("Failed to pause workflow {} for tool approval: {}", executionId, e.getMessage());
        }
    }

    private void resumeWorkflowIfBound(AgentRun run, boolean approved, String reason) {
        if (workflowReliabilityService == null) {
            return;
        }
        String executionId = workflowExecutionIdOf(run);
        if (executionId == null) {
            return;
        }
        try {
            if (approved) {
                workflowReliabilityService.resumeRunning(executionId,
                        reason == null ? "Tool approval granted" : reason);
            }
            // On reject the node fails via the tool outcome and the executor writes the
            // terminal state; leaving WAITING_* here would strand the row if the failure
            // path is skipped, so force the execution back to RUNNING only on approve.
        } catch (RuntimeException e) {
            log.warn("Failed to resume workflow {} after tool approval: {}", executionId, e.getMessage());
        }
    }

    public record ApprovalOutcome(boolean approved, String reason) {
    }

    private static final class PendingApproval {
        private final Long runId;
        private final Long toolCallId;
        private final String toolName;
        private final Duration timeout;
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile Boolean approved;
        private volatile String reason;
        private volatile boolean aborted;

        private PendingApproval(Long runId, Long toolCallId, String toolName, Duration timeout) {
            this.runId = runId;
            this.toolCallId = toolCallId;
            this.toolName = toolName;
            this.timeout = timeout;
        }

        private void complete(boolean approved, String reason) {
            this.approved = approved;
            this.reason = reason;
            latch.countDown();
        }

        private void abort(String reason) {
            this.aborted = true;
            this.reason = reason;
            latch.countDown();
        }

        private Long runId() {
            return runId;
        }

        private CountDownLatch latch() {
            return latch;
        }

        private Boolean approved() {
            return approved;
        }

        private String reason() {
            return reason;
        }

        private boolean aborted() {
            return aborted;
        }
    }

    /** Test hook: current in-flight waits. */
    List<PendingApproval> pendingSnapshot() {
        return new ArrayList<>(pendingApprovals.values());
    }
}
