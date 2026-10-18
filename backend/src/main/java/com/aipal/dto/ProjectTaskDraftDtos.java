package com.aipal.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Frozen M3-D contract for task drafts, confirmation, Run creation and assignments.
 */
public final class ProjectTaskDraftDtos {
    private ProjectTaskDraftDtos() {
    }

    public record CreateTaskDraftRequest(String title, String description, Long suggestedAgentId,
                                         Long suggestedAssigneeUserId, String sourceType, Long sourceId,
                                         String idempotencyKey) {
    }

    public record TaskDraftView(Long id, String projectKey, String sourceType, Long sourceId,
                                String title, String description, Long suggestedAgentId,
                                Long suggestedAssigneeUserId, String status, Long confirmedRunId,
                                Integer version, LocalDateTime createTime, LocalDateTime updateTime) {
    }

    public record CancelTaskDraftRequest(Integer expectedVersion, String reason) {
    }

    public record ConfirmTaskDraftRequest(Long agentId, Long assigneeUserId, Integer expectedVersion,
                                          String idempotencyKey) {
    }

    public record ConfirmTaskDraftResult(TaskDraftView draft, Long runId, String runStatus,
                                         List<ProjectCollaborationDtos.AssignmentView> assignments) {
        public ConfirmTaskDraftResult {
            assignments = assignments == null ? List.of() : List.copyOf(assignments);
        }
    }
}
