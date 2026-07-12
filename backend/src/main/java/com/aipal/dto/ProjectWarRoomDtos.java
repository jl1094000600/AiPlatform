package com.aipal.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class ProjectWarRoomDtos {
    private ProjectWarRoomDtos() {
    }

    public record ProjectWorkspaceView(Long id, String projectKey, String projectName, String projectType,
                                       Long ownerUserId, String status, LocalDateTime updateTime) {
    }

    public record WarRoomResponse(ProjectWorkspaceView project, List<WarRoomPipelineView> pipelines,
                                  List<TeamAgentQueueRunView> queue, List<TimelineEventView> timeline,
                                  List<PendingArtifactView> pendingArtifacts,
                                  List<MemoryReferenceView> memoryReferences,
                                  List<RiskCardView> risks, WarRoomSummary summary) {
    }

    public record WarRoomSummary(int pipelineCount, int activeRunCount, int pendingApprovalCount,
                                 int riskCount, String overallStatus) {
    }

    public record WarRoomPipelineView(Long id, String pipelineCode, String projectKey, String requirementTitle,
                                      String status, String currentStage, String businessStageLabel,
                                      Integer passedStages, Integer totalStages, LocalDateTime updateTime,
                                      List<PipelineStageView> stages, List<PipelineApprovalView> approvals) {
    }

    public record PipelineStageView(Long id, String stageKey, String businessStageLabel, String status,
                                    String summary, LocalDateTime startTime, LocalDateTime endTime) {
    }

    public record PipelineApprovalView(Long id, Long stageRunId, String approvalType, String reviewerRole,
                                       String status, String comment, LocalDateTime createTime) {
    }

    public record TeamAgentRunRequest(Long agentId, String businessType, String businessId, Object input) {
    }

    public record TeamAgentQueueRunView(Long id, String projectKey, String businessType, String businessId,
                                        Long agentId, String status, String traceId, String memoryTraceId,
                                        Integer totalTokens, String message, boolean canCancel,
                                        boolean canApprove, LocalDateTime createTime, LocalDateTime startTime,
                                        LocalDateTime endTime) {
    }

    public record TimelineEventView(String sourceType, Long sourceId, String title, String status,
                                    String description, String actorName, LocalDateTime eventTime,
                                    String traceId) {
    }

    public record PendingArtifactView(Long id, Long runId, String artifactType, String title, String status,
                                      LocalDateTime updateTime) {
    }

    public record MemoryReferenceView(String memoryCode, Integer memoryVersion, String sourceType,
                                      String scopeType, Integer tokenCount, Integer policyVersion,
                                      String traceId, String summary) {
    }

    public record RiskCardView(String level, String title, String description, String sourceType, Long sourceId) {
    }

    public record MentionableUserView(Long userId, String username, String realName, String memberRole) {
    }
}
