package com.aipal.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Frozen M3-B contract for shared discussion and the read-only Coordinator.
 */
public final class ProjectDiscussionDtos {
    private ProjectDiscussionDtos() {
    }

    public record PostDiscussionRequest(String content, String idempotencyKey) {
    }

    public record DiscussionMessageView(Long id, String projectKey, Long senderUserId, String senderType,
                                        String senderName, String messageType, String content,
                                        LocalDateTime createTime) {
    }

    public record CoordinatorRequest(String focus, String idempotencyKey) {
    }

    public record CoordinatorResponse(String summary, List<String> risks, List<String> questions,
                                      List<ProjectTaskDraftDtos.TaskDraftView> taskDrafts,
                                      boolean reused) {
        public CoordinatorResponse {
            risks = risks == null ? List.of() : List.copyOf(risks);
            questions = questions == null ? List.of() : List.copyOf(questions);
            taskDrafts = taskDrafts == null ? List.of() : List.copyOf(taskDrafts);
        }
    }
}
