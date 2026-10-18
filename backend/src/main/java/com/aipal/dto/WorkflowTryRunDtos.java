package com.aipal.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * T20 try-run and T21 node-trace contracts.
 */
public final class WorkflowTryRunDtos {
    private WorkflowTryRunDtos() {
    }

    /**
     * POST /api/v1/workflows/{workflowId}/try-run body.
     * definitionJson is validated in-memory only (draft try-run) and never persisted.
     */
    public record TryRunRequest(Map<String, Object> inputs, String definitionJson) {
    }

    /** Per-node result returned by try-run and embedded in TryRunResult. */
    public record NodeResult(String nodeId, String nodeType, String status,
                             Map<String, Object> outputs, Long durationMs, String error,
                             Integer attempt) {
    }

    /** Response body of a try-run. */
    public record TryRunResult(String executionId, String status, Long durationMs,
                               List<NodeResult> nodeRuns, Map<String, Object> result,
                               String errorMessage) {
    }

    /**
     * Persistent node trace row (GET /executions/{executionId}/node-runs).
     * input/output payloads are intentionally absent — only digests are stored.
     */
    public record NodeRunView(Long id, String executionId, String nodeId, String nodeType,
                              String status, Integer attempt, String inputDigest,
                              String outputDigest, Long durationMs, String errorMessage,
                              LocalDateTime createTime, LocalDateTime updateTime) {
    }
}
