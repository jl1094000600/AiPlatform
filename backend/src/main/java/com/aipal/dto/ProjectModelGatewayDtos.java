package com.aipal.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Frozen M3-A contract: the only project data allowed to reach the CHAT model,
 * plus the strict response shapes returned by that model.
 */
public final class ProjectModelGatewayDtos {
    private ProjectModelGatewayDtos() {
    }

    public record SafeProjectContext(
            String projectKey,
            String projectName,
            String projectType,
            String status,
            Long ownerUserId,
            String overallStatus,
            int pipelineCount,
            int activeRunCount,
            int pendingApprovalCount,
            int riskCount,
            List<ContextPipeline> pipelines,
            List<ContextRun> runs,
            List<ContextRisk> risks,
            List<ContextDiscussion> discussions,
            List<ContextAssignmentEvent> assignmentEvents,
            List<ContextMember> members) {
        public SafeProjectContext {
            pipelines = pipelines == null ? List.of() : List.copyOf(pipelines);
            runs = runs == null ? List.of() : List.copyOf(runs);
            risks = risks == null ? List.of() : List.copyOf(risks);
            discussions = discussions == null ? List.of() : List.copyOf(discussions);
            assignmentEvents = assignmentEvents == null ? List.of() : List.copyOf(assignmentEvents);
            members = members == null ? List.of() : List.copyOf(members);
        }
    }

    public record ContextPipeline(Long id, String title, String status, String currentStage) {
    }

    public record ContextRun(Long id, String businessType, String businessId, Long agentId,
                             String status, LocalDateTime createTime, LocalDateTime endTime) {
    }

    public record ContextRisk(String level, String title, String description) {
    }

    public record ContextDiscussion(Long id, String senderType, String senderName, String messageType,
                                    String content, LocalDateTime createTime) {
    }

    public record ContextAssignmentEvent(Long id, String eventType, Long fromUserId, Long toUserId,
                                         String fromStatus, String toStatus, String reason,
                                         LocalDateTime createTime) {
    }

    public record ContextMember(Long userId, String username, String realName, String memberRole) {
    }

    public record CoordinatorResult(String summary, List<String> risks, List<String> questions,
                                    List<GeneratedTaskDraft> taskDrafts) {
        public CoordinatorResult {
            risks = risks == null ? List.of() : List.copyOf(risks);
            questions = questions == null ? List.of() : List.copyOf(questions);
            taskDrafts = taskDrafts == null ? List.of() : List.copyOf(taskDrafts);
        }
    }

    public record GeneratedTaskDraft(String title, String description, Long suggestedAgentId) {
    }

    public record PrivateAskTurn(String role, String content) {
    }

    public record PrivateAskSource(String type, String reference) {
    }

    public record PrivateAskResult(String answer, List<PrivateAskSource> sources, List<String> limitations) {
        public PrivateAskResult {
            sources = sources == null ? List.of() : List.copyOf(sources);
            limitations = limitations == null ? List.of() : List.copyOf(limitations);
        }
    }
}
