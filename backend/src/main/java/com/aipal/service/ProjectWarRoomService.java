package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.common.BizException;
import com.aipal.dto.ProjectWarRoomDtos;
import com.aipal.dto.ProjectCollaborationDtos;
import com.aipal.entity.AgentArtifact;
import com.aipal.entity.AgentMemorySnapshot;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentRunEvent;
import com.aipal.entity.AiMemoryProject;
import com.aipal.entity.AutomationApproval;
import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.AutomationStageRun;
import com.aipal.mapper.AgentArtifactMapper;
import com.aipal.mapper.AgentMemorySnapshotMapper;
import com.aipal.mapper.AgentRunEventMapper;
import com.aipal.mapper.AutomationApprovalMapper;
import com.aipal.mapper.AutomationPipelineMapper;
import com.aipal.mapper.AutomationStageRunMapper;
import com.aipal.security.TenantContext;
import com.aipal.service.memory.MemoryProjectService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectWarRoomService {

    private static final int PIPELINE_LIMIT = 20;
    private static final int RUN_LIMIT = 20;
    private static final int EVENT_LIMIT = 80;
    private static final int ARTIFACT_LIMIT = 50;
    private static final int MEMORY_LIMIT = 50;

    private final MemoryProjectService memoryProjectService;
    private final AutomationPipelineMapper pipelineMapper;
    private final AutomationStageRunMapper stageRunMapper;
    private final AutomationApprovalMapper approvalMapper;
    private final AgentRunService agentRunService;
    private final AgentRunEventMapper eventMapper;
    private final AgentArtifactMapper artifactMapper;
    private final AgentMemorySnapshotMapper snapshotMapper;
    private final ProjectAssignmentService projectAssignmentService;

    public List<ProjectWarRoomDtos.ProjectWorkspaceView> listWorkspaces() {
        return memoryProjectService.listAccessibleWorkspaces().stream()
                .map(this::toProjectView)
                .toList();
    }

    public ProjectWarRoomDtos.WarRoomResponse getWarRoom(String projectKey) {
        AiMemoryProject project = memoryProjectService.requireAccessibleWorkspaceProject(projectKey);
        Long tenantId = TenantContext.tenantId();
        String normalizedProjectKey = project.getProjectKey();

        List<AutomationPipeline> pipelines = pipelineMapper.selectList(new LambdaQueryWrapper<AutomationPipeline>()
                .eq(AutomationPipeline::getTenantId, tenantId)
                .eq(AutomationPipeline::getProjectKey, normalizedProjectKey)
                .eq(AutomationPipeline::getIsDeleted, 0)
                .orderByDesc(AutomationPipeline::getCreateTime)
                .last("LIMIT " + PIPELINE_LIMIT));
        List<ProjectWarRoomDtos.WarRoomPipelineView> pipelineViews = pipelines.stream()
                .map(this::toPipelineView)
                .toList();

        List<AgentRun> runs = agentRunService.listByProjectKey(normalizedProjectKey, RUN_LIMIT);
        List<ProjectWarRoomDtos.TeamAgentQueueRunView> queue = runs.stream()
                .map(agentRunService::businessView)
                .map(this::toRunView)
                .toList();

        List<Long> runIds = runs.stream().map(AgentRun::getId).toList();
        List<ProjectWarRoomDtos.TimelineEventView> timeline = buildTimeline(pipelineViews, runIds, tenantId);
        List<ProjectWarRoomDtos.PendingArtifactView> pendingArtifacts = buildPendingArtifacts(runIds, tenantId);
        List<ProjectWarRoomDtos.MemoryReferenceView> memoryReferences = buildMemoryReferences(runIds, tenantId);
        List<ProjectWarRoomDtos.RiskCardView> risks = buildRisks(pipelineViews, queue, pendingArtifacts);
        ProjectWarRoomDtos.WarRoomSummary summary = buildSummary(pipelineViews, queue, pendingArtifacts, risks);

        return new ProjectWarRoomDtos.WarRoomResponse(toProjectView(project), pipelineViews, queue, timeline,
                pendingArtifacts, memoryReferences, risks, summary,
                memoryProjectService.canManageWorkspaceProject(normalizedProjectKey));
    }

    @Transactional
    public ProjectWarRoomDtos.TeamAgentQueueRunView createTeamAgentRun(String projectKey,
                                                                        ProjectWarRoomDtos.TeamAgentRunRequest request) {
        if (request == null || request.agentId() == null || blank(request.businessType()) || blank(request.businessId())) {
            throw new BizException(400, "agentId, businessType and businessId are required");
        }
        memoryProjectService.requireWorkspacePermission("project-workspace:execute");
        AiMemoryProject project = memoryProjectService.requireAccessibleWorkspaceProject(projectKey);
        if (request.assigneeUserId() != null) {
            projectAssignmentService.checkedReason(request.assignmentReason());
            projectAssignmentService.validateAssignableMember(project.getProjectKey(), request.assigneeUserId());
        }
        AgentRun run = agentRunService.createOrReuse(new AgentRunService.CreateRunCommand(
                request.agentId(), project.getProjectKey(), request.businessType(), request.businessId(), request.input()));
        if (request.assigneeUserId() != null) {
            projectAssignmentService.assign(project.getProjectKey(), new ProjectCollaborationDtos.AssignRequest(
                    "AGENT_RUN", run.getId(), request.assigneeUserId(), request.assignmentReason()));
        }
        return toRunView(agentRunService.businessView(run));
    }

    public List<ProjectWarRoomDtos.MentionableUserView> listMentionableUsers(String projectKey) {
        return memoryProjectService.listWorkspaceMentionableUsers(projectKey).stream()
                .map(member -> new ProjectWarRoomDtos.MentionableUserView(
                        member.userId(), member.username(), member.realName(), member.memberRole()))
                .toList();
    }

    private ProjectWarRoomDtos.ProjectWorkspaceView toProjectView(AiMemoryProject project) {
        return new ProjectWarRoomDtos.ProjectWorkspaceView(project.getId(), project.getProjectKey(),
                project.getProjectName(), project.getProjectType(), project.getOwnerUserId(),
                project.getStatus(), project.getUpdateTime(), memoryProjectService.canManageWorkspaceProject(project.getProjectKey()));
    }

    private ProjectWarRoomDtos.WarRoomPipelineView toPipelineView(AutomationPipeline pipeline) {
        List<ProjectWarRoomDtos.PipelineStageView> stages = stageRunMapper.selectList(new LambdaQueryWrapper<AutomationStageRun>()
                        .eq(AutomationStageRun::getTenantId, TenantContext.tenantId())
                        .eq(AutomationStageRun::getPipelineId, pipeline.getId())
                        .orderByAsc(AutomationStageRun::getStageOrder)
                        .orderByAsc(AutomationStageRun::getId))
                .stream()
                .map(stage -> new ProjectWarRoomDtos.PipelineStageView(stage.getId(), stage.getStageKey(),
                        stageLabel(stage.getStageKey()), stage.getStatus(), safeStageSummary(stage),
                        stage.getStartTime(), stage.getEndTime()))
                .toList();
        List<ProjectWarRoomDtos.PipelineApprovalView> approvals = approvalMapper.selectList(new LambdaQueryWrapper<AutomationApproval>()
                        .eq(AutomationApproval::getTenantId, TenantContext.tenantId())
                        .eq(AutomationApproval::getPipelineId, pipeline.getId())
                        .orderByDesc(AutomationApproval::getCreateTime)
                        .orderByDesc(AutomationApproval::getId))
                .stream()
                .map(approval -> new ProjectWarRoomDtos.PipelineApprovalView(approval.getId(), approval.getStageRunId(),
                        approval.getApprovalType(), approval.getReviewerRole(), approval.getStatus(),
                        safeText(approval.getComment(), 256), approval.getCreateTime()))
                .toList();
        return new ProjectWarRoomDtos.WarRoomPipelineView(pipeline.getId(), pipeline.getPipelineCode(),
                pipeline.getProjectKey(), pipeline.getRequirementTitle(), pipeline.getStatus(), pipeline.getCurrentStage(),
                stageLabel(pipeline.getCurrentStage()), pipeline.getPassedStages(), pipeline.getTotalStages(),
                pipeline.getUpdateTime(), stages, approvals);
    }

    private ProjectWarRoomDtos.TeamAgentQueueRunView toRunView(AgentRunService.RunView view) {
        return new ProjectWarRoomDtos.TeamAgentQueueRunView(view.id(), view.projectKey(), view.businessType(),
                view.businessId(), view.agentId(), view.status(), view.traceId(), view.memoryTraceId(),
                view.totalTokens(), view.errorMessage(), view.canCancel(), view.canApprove(),
                view.createTime(), view.startTime(), view.endTime());
    }

    private List<ProjectWarRoomDtos.TimelineEventView> buildTimeline(
            List<ProjectWarRoomDtos.WarRoomPipelineView> pipelines, List<Long> runIds, Long tenantId) {
        List<ProjectWarRoomDtos.TimelineEventView> timeline = new ArrayList<>();
        for (ProjectWarRoomDtos.WarRoomPipelineView pipeline : pipelines) {
            for (ProjectWarRoomDtos.PipelineStageView stage : pipeline.stages()) {
                LocalDateTime eventTime = stage.endTime() != null ? stage.endTime()
                        : stage.startTime() != null ? stage.startTime() : pipeline.updateTime();
                timeline.add(new ProjectWarRoomDtos.TimelineEventView("PIPELINE_STAGE", stage.id(),
                        pipeline.requirementTitle() + " / " + stage.businessStageLabel(), stage.status(),
                        stage.summary(), null, eventTime, null));
            }
        }
        if (!runIds.isEmpty()) {
            eventMapper.selectList(new LambdaQueryWrapper<AgentRunEvent>()
                            .eq(AgentRunEvent::getTenantId, tenantId)
                            .in(AgentRunEvent::getRunId, runIds)
                            .orderByDesc(AgentRunEvent::getCreateTime)
                            .orderByDesc(AgentRunEvent::getId)
                            .last("LIMIT " + EVENT_LIMIT))
                    .forEach(event -> timeline.add(new ProjectWarRoomDtos.TimelineEventView("AGENT_RUN",
                            event.getRunId(), agentRunEventTitle(event), event.getToStatus(),
                            safeText(event.getReason(), 256), event.getActorName(), event.getCreateTime(),
                            event.getTraceId())));
        }
        return timeline.stream()
                .sorted(Comparator.comparing(ProjectWarRoomDtos.TimelineEventView::eventTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(EVENT_LIMIT)
                .toList();
    }

    private List<ProjectWarRoomDtos.PendingArtifactView> buildPendingArtifacts(List<Long> runIds, Long tenantId) {
        if (runIds.isEmpty()) return List.of();
        return artifactMapper.selectList(new LambdaQueryWrapper<AgentArtifact>()
                        .eq(AgentArtifact::getTenantId, tenantId)
                        .in(AgentArtifact::getRunId, runIds)
                        .in(AgentArtifact::getStatus, List.of("PENDING", "PENDING_APPROVAL", "WAITING_APPROVAL"))
                        .orderByDesc(AgentArtifact::getUpdateTime)
                        .orderByDesc(AgentArtifact::getCreateTime)
                        .last("LIMIT " + ARTIFACT_LIMIT))
                .stream()
                .map(artifact -> new ProjectWarRoomDtos.PendingArtifactView(artifact.getId(), artifact.getRunId(),
                        artifact.getArtifactType(), safeArtifactTitle(artifact), artifact.getStatus(),
                        artifact.getUpdateTime() == null ? artifact.getCreateTime() : artifact.getUpdateTime()))
                .toList();
    }

    private List<ProjectWarRoomDtos.MemoryReferenceView> buildMemoryReferences(List<Long> runIds, Long tenantId) {
        if (runIds.isEmpty()) return List.of();
        return snapshotMapper.selectList(new LambdaQueryWrapper<AgentMemorySnapshot>()
                        .eq(AgentMemorySnapshot::getTenantId, tenantId)
                        .in(AgentMemorySnapshot::getRunId, runIds)
                        .orderByDesc(AgentMemorySnapshot::getCreateTime)
                        .last("LIMIT " + MEMORY_LIMIT))
                .stream()
                .map(snapshot -> new ProjectWarRoomDtos.MemoryReferenceView(snapshot.getMemoryCode(),
                        snapshot.getMemoryVersion(), snapshot.getSourceType(), snapshot.getScopeType(),
                        snapshot.getTokenCount(), snapshot.getPolicyVersion(), snapshot.getTraceId(), null))
                .toList();
    }

    private List<ProjectWarRoomDtos.RiskCardView> buildRisks(List<ProjectWarRoomDtos.WarRoomPipelineView> pipelines,
                                                             List<ProjectWarRoomDtos.TeamAgentQueueRunView> queue,
                                                             List<ProjectWarRoomDtos.PendingArtifactView> artifacts) {
        List<ProjectWarRoomDtos.RiskCardView> risks = new ArrayList<>();
        long activeRuns = queue.stream().filter(run -> !terminalRun(run.status())).count();
        if (activeRuns > 5) {
            risks.add(new ProjectWarRoomDtos.RiskCardView("WARNING", "Team Agent backlog",
                    activeRuns + " active runs need PM review.", "AGENT_RUN", null));
        }
        queue.stream()
                .filter(run -> Set.of("FAILED", "TIMEOUT", "CANCELLED").contains(safeUpper(run.status())))
                .limit(5)
                .forEach(run -> risks.add(new ProjectWarRoomDtos.RiskCardView("DANGER", "Agent run needs attention",
                        "Run " + run.id() + " is " + run.status() + ".", "AGENT_RUN", run.id())));
        if (!artifacts.isEmpty()) {
            risks.add(new ProjectWarRoomDtos.RiskCardView("WARNING", "Pending artifacts",
                    artifacts.size() + " artifacts are waiting for approval.", "ARTIFACT", null));
        }
        pipelines.stream()
                .filter(pipeline -> Set.of("FAILED", "BLOCKED", "REJECTED").contains(safeUpper(pipeline.status())))
                .limit(5)
                .forEach(pipeline -> risks.add(new ProjectWarRoomDtos.RiskCardView("DANGER", "Pipeline blocked",
                        pipeline.requirementTitle() + " is " + pipeline.status() + ".", "PIPELINE", pipeline.id())));
        pipelines.stream()
                .filter(pipeline -> pipeline.approvals().stream().anyMatch(approval ->
                        Set.of("PENDING", "WAITING_APPROVAL").contains(safeUpper(approval.status()))))
                .limit(5)
                .forEach(pipeline -> risks.add(new ProjectWarRoomDtos.RiskCardView("WARNING", "Approval waiting",
                        pipeline.requirementTitle() + " has pending review items.", "PIPELINE", pipeline.id())));
        return dedupeRisks(risks);
    }

    private ProjectWarRoomDtos.WarRoomSummary buildSummary(List<ProjectWarRoomDtos.WarRoomPipelineView> pipelines,
                                                           List<ProjectWarRoomDtos.TeamAgentQueueRunView> queue,
                                                           List<ProjectWarRoomDtos.PendingArtifactView> artifacts,
                                                           List<ProjectWarRoomDtos.RiskCardView> risks) {
        int activeRunCount = (int) queue.stream().filter(run -> !terminalRun(run.status())).count();
        int pendingApprovalCount = artifacts.size()
                + (int) pipelines.stream().flatMap(pipeline -> pipeline.approvals().stream())
                .filter(approval -> Set.of("PENDING", "WAITING_APPROVAL").contains(safeUpper(approval.status())))
                .count()
                + (int) queue.stream().filter(run -> "WAITING_APPROVAL".equals(safeUpper(run.status()))).count();
        String overallStatus = risks.stream().anyMatch(risk -> "DANGER".equals(risk.level())) ? "RED"
                : (!risks.isEmpty() || pendingApprovalCount > 0 ? "YELLOW" : "GREEN");
        return new ProjectWarRoomDtos.WarRoomSummary(pipelines.size(), activeRunCount, pendingApprovalCount,
                risks.size(), overallStatus);
    }

    private List<ProjectWarRoomDtos.RiskCardView> dedupeRisks(List<ProjectWarRoomDtos.RiskCardView> risks) {
        Set<String> seen = new HashSet<>();
        List<ProjectWarRoomDtos.RiskCardView> result = new ArrayList<>();
        for (ProjectWarRoomDtos.RiskCardView risk : risks) {
            String key = risk.level() + "|" + risk.title() + "|" + risk.sourceType() + "|" + risk.sourceId();
            if (seen.add(key)) {
                result.add(risk);
            }
        }
        return result;
    }

    private String safeStageSummary(AutomationStageRun stage) {
        if (!blank(stage.getErrorMessage())) return "执行详情已限制展示";
        return switch (safeUpper(stage.getStatus())) {
            case "SUCCESS", "COMPLETED" -> stageLabel(stage.getStageKey()) + "已完成";
            case "WAITING_APPROVAL" -> stageLabel(stage.getStageKey()) + "待审批";
            case "RUNNING" -> stageLabel(stage.getStageKey()) + "进行中";
            case "QUEUED" -> stageLabel(stage.getStageKey()) + "排队中";
            case "PENDING" -> stageLabel(stage.getStageKey()) + "待开始";
            case "REJECTED", "BLOCKED", "FAILED" -> stageLabel(stage.getStageKey()) + "需要处理";
            default -> null;
        };
    }

    private String stageLabel(String stageKey) {
        if (blank(stageKey)) return "未开始";
        return switch (stageKey) {
            case com.aipal.common.PipelineStages.REQUIREMENT_ANALYSIS -> "需求确认";
            case com.aipal.common.PipelineStages.CODE_GENERATION -> "方案/代码";
            case com.aipal.common.PipelineStages.CODE_QUALITY_EVALUATION -> "质量评估";
            case com.aipal.common.PipelineStages.BUILD_COMPILE -> "构建";
            case com.aipal.common.PipelineStages.TEST_EXECUTION -> "测试";
            case com.aipal.common.PipelineStages.DEPLOYMENT_RELEASE -> "发布";
            case com.aipal.common.PipelineStages.OPERATIONS_MONITORING -> "运维观察";
            case com.aipal.common.PipelineStages.DELIVERY_REPORT -> "交付报告";
            default -> stageKey.replace('_', ' ');
        };
    }

    private String agentRunEventTitle(AgentRunEvent event) {
        String toStatus = blank(event.getToStatus()) ? "已更新" : event.getToStatus();
        return "Team Agent #" + event.getRunId() + " -> " + toStatus;
    }

    private String safeArtifactTitle(AgentArtifact artifact) {
        if (!blank(artifact.getTitle())) return safeText(artifact.getTitle(), 120);
        String type = blank(artifact.getArtifactType()) ? "Artifact" : artifact.getArtifactType();
        return type + " #" + artifact.getId();
    }

    private boolean terminalRun(String status) {
        if (blank(status)) return false;
        try {
            return AgentRunStatus.valueOf(status).terminal();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String safeText(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
