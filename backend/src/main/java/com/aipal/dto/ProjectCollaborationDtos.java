package com.aipal.dto;

import java.time.LocalDateTime;

public final class ProjectCollaborationDtos {
    private ProjectCollaborationDtos() {
    }

    public record AssignRequest(String targetType, Long targetId, Long assigneeUserId, String reason) {
    }

    public record ReassignRequest(Long assigneeUserId, Integer expectedVersion, String reason) {
    }

    public record AssignmentActionRequest(Integer expectedVersion, String reason) {
    }

    public record AssignmentView(Long id, String projectKey, String targetType, Long targetId,
                                 Long assigneeUserId, String assigneeName, String status,
                                 Integer version, LocalDateTime updateTime,
                                 boolean canReassign, boolean canAccept, boolean canComplete) {
        public AssignmentView(Long id, String projectKey, String targetType, Long targetId,
                              Long assigneeUserId, String assigneeName, String status,
                              Integer version, LocalDateTime updateTime) {
            this(id, projectKey, targetType, targetId, assigneeUserId, assigneeName, status,
                    version, updateTime, false, false, false);
        }
    }
}
