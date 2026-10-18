package com.aipal.service;

import com.aipal.common.ErrorCode;
import com.aipal.dto.ProjectModelGatewayDtos.ContextAssignmentEvent;
import com.aipal.dto.ProjectModelGatewayDtos.ContextDiscussion;
import com.aipal.dto.ProjectModelGatewayDtos.ContextMember;
import com.aipal.dto.ProjectModelGatewayDtos.ContextPipeline;
import com.aipal.dto.ProjectModelGatewayDtos.ContextRisk;
import com.aipal.dto.ProjectModelGatewayDtos.ContextRun;
import com.aipal.dto.ProjectModelGatewayDtos.SafeProjectContext;
import com.aipal.dto.ProjectWarRoomDtos;
import com.aipal.entity.AiMemoryProject;
import com.aipal.entity.ProjectAssignmentEvent;
import com.aipal.entity.ProjectDiscussionMessage;
import com.aipal.mapper.ProjectAssignmentEventMapper;
import com.aipal.mapper.ProjectDiscussionMessageMapper;
import com.aipal.security.TenantContext;
import com.aipal.service.memory.MemoryProjectService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Builds the single business-safe project context that may be sent to the CHAT model.
 *
 * <p>Only allow-listed project metadata reaches the model. Raw run input/output, prompts,
 * definition snapshots, tool payloads, credentials, absolute paths, stack traces and private
 * ask content never leave their owning services. Every free-text value is length-capped and
 * scrubbed of sensitive markers before it enters the frozen {@link SafeProjectContext} contract.</p>
 */
@Service
@RequiredArgsConstructor
public class ProjectWorkspaceContextService {

    private static final int DISCUSSION_LIMIT = 50;
    private static final int ASSIGNMENT_EVENT_LIMIT = 20;
    private static final int PIPELINE_LIMIT = 20;
    private static final int RUN_LIMIT = 20;
    private static final int RISK_LIMIT = 20;
    private static final int MEMBER_LIMIT = 200;

    private static final int PROJECT_KEY_MAX = 100;
    private static final int PROJECT_NAME_MAX = 120;
    private static final int PROJECT_TYPE_MAX = 60;
    private static final int STATUS_MAX = 40;
    private static final int PIPELINE_TITLE_MAX = 160;
    private static final int STAGE_MAX = 80;
    private static final int BUSINESS_TYPE_MAX = 80;
    private static final int BUSINESS_ID_MAX = 120;
    private static final int RISK_TITLE_MAX = 160;
    private static final int RISK_DESCRIPTION_MAX = 500;
    private static final int SENDER_NAME_MAX = 80;
    private static final int MESSAGE_TYPE_MAX = 40;
    private static final int DISCUSSION_CONTENT_MAX = 1_000;
    private static final int EVENT_TYPE_MAX = 40;
    private static final int EVENT_REASON_MAX = 500;
    private static final int USERNAME_MAX = 80;
    private static final int REAL_NAME_MAX = 120;
    private static final int MEMBER_ROLE_MAX = 40;

    /**
     * Markers that must never be echoed into a model context: filesystem roots, serialized run
     * input/output keys, definition snapshots, credentials, prompt/tool payloads and stack traces.
     */
    private static final Pattern SENSITIVE_TEXT = Pattern.compile(
            "(?i)(?:"
                    + "/users/\\S*|/home/\\S*|/var/\\S*|/opt/\\S*|/tmp/\\S*|/etc/\\S*|/root/\\S*|/private/\\S*|/mnt/\\S*|/srv/\\S*"
                    + "|[a-z]:[\\\\/][^\\s\"']*"
                    + "|input\\s*json(?:\\s*[:=]\\s*[^\\s,;]+)?"
                    + "|result\\s*json(?:\\s*[:=]\\s*[^\\s,;]+)?"
                    + "|definition\\s*snapshot(?:\\s*[:=]\\s*[^\\s,;]+)?"
                    + "|(?:api[_-]?key|access[_-]?token|token|secret|password)(?:\\s*[:=]\\s*[^\\s,;]+)?"
                    + "|payload(?:\\s*[:=]\\s*[^\\s,;]+)?"
                    + "|prompt(?:\\s*[:=]\\s*[^\\s,;]+)?"
                    + "|stack\\s*trace(?:\\s*[:=]\\s*[^\\n]+)?"
                    + "|\\bat\\s+[a-z_$][\\w.$]*\\s*\\([^\\n]*\\)"
                    + "|bearer\\s+[a-z0-9._-]{8,}"
                    + "|sk-[a-z0-9_-]{8,}"
                    + ")");

    private static final String REDACTED = "[filtered]";

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    private final MemoryProjectService memoryProjectService;
    private final ProjectWarRoomService projectWarRoomService;
    private final ProjectDiscussionMessageMapper discussionMessageMapper;
    private final ProjectAssignmentEventMapper assignmentEventMapper;

    public SafeProjectContext build(String projectKey) {
        if (projectKey == null || projectKey.isBlank()) {
            throw ErrorCode.PROJECT_KEY_REQUIRED.ex();
        }
        // Both checks live in MemoryProjectService: the workspace permission and a live membership.
        memoryProjectService.requireWorkspacePermission("project-workspace:view");
        AiMemoryProject project = memoryProjectService.requireAccessibleWorkspaceProject(projectKey);
        if (project == null) {
            throw ErrorCode.PROJECT_NOT_FOUND.ex();
        }
        String trustedProjectKey = project.getProjectKey();
        Long tenantId = project.getTenantId() != null ? project.getTenantId() : TenantContext.tenantId();

        ProjectWarRoomDtos.WarRoomResponse warRoom = projectWarRoomService.getWarRoom(trustedProjectKey);
        List<ProjectWarRoomDtos.WarRoomPipelineView> pipelineViews = warRoom == null
                ? List.of() : safeList(warRoom.pipelines());
        List<ProjectWarRoomDtos.TeamAgentQueueRunView> runViews = warRoom == null
                ? List.of() : safeList(warRoom.queue());
        List<ProjectWarRoomDtos.RiskCardView> riskViews = warRoom == null
                ? List.of() : safeList(warRoom.risks());
        ProjectWarRoomDtos.WarRoomSummary summary = warRoom == null ? null : warRoom.summary();

        List<ContextPipeline> pipelines = pipelineViews.stream()
                .limit(PIPELINE_LIMIT)
                .map(pipeline -> new ContextPipeline(
                        pipeline.id(),
                        safeText(pipeline.requirementTitle(), PIPELINE_TITLE_MAX),
                        safeText(pipeline.status(), STATUS_MAX),
                        safeText(stageLabel(pipeline), STAGE_MAX)))
                .toList();

        List<ContextRun> runs = runViews.stream()
                .limit(RUN_LIMIT)
                .map(run -> new ContextRun(
                        run.id(),
                        safeText(run.businessType(), BUSINESS_TYPE_MAX),
                        safeText(run.businessId(), BUSINESS_ID_MAX),
                        run.agentId(),
                        safeText(run.status(), STATUS_MAX),
                        run.createTime(),
                        run.endTime()))
                .toList();

        List<ContextRisk> risks = riskViews.stream()
                .limit(RISK_LIMIT)
                .map(risk -> new ContextRisk(
                        safeText(risk.level(), STATUS_MAX),
                        safeText(risk.title(), RISK_TITLE_MAX),
                        safeText(risk.description(), RISK_DESCRIPTION_MAX)))
                .toList();

        List<ContextDiscussion> discussions = loadDiscussions(tenantId, trustedProjectKey);
        List<ContextAssignmentEvent> assignmentEvents = loadAssignmentEvents(tenantId, trustedProjectKey);

        List<ContextMember> members = safeList(memoryProjectService.listWorkspaceMentionableUsers(trustedProjectKey)).stream()
                .limit(MEMBER_LIMIT)
                .map(member -> new ContextMember(
                        member.userId(),
                        safeText(member.username(), USERNAME_MAX),
                        safeText(member.realName(), REAL_NAME_MAX),
                        safeText(member.memberRole(), MEMBER_ROLE_MAX)))
                .toList();

        return new SafeProjectContext(
                safeText(trustedProjectKey, PROJECT_KEY_MAX),
                safeText(project.getProjectName(), PROJECT_NAME_MAX),
                safeText(project.getProjectType(), PROJECT_TYPE_MAX),
                safeText(project.getStatus(), STATUS_MAX),
                project.getOwnerUserId(),
                summary == null ? null : safeText(summary.overallStatus(), STATUS_MAX),
                summary == null ? pipelines.size() : summary.pipelineCount(),
                summary == null ? 0 : summary.activeRunCount(),
                summary == null ? 0 : summary.pendingApprovalCount(),
                summary == null ? risks.size() : summary.riskCount(),
                pipelines,
                runs,
                risks,
                discussions,
                assignmentEvents,
                members);
    }

    private List<ContextDiscussion> loadDiscussions(Long tenantId, String projectKey) {
        List<ProjectDiscussionMessage> messages = discussionMessageMapper.selectList(
                new LambdaQueryWrapper<ProjectDiscussionMessage>()
                        .eq(ProjectDiscussionMessage::getTenantId, tenantId)
                        .eq(ProjectDiscussionMessage::getProjectKey, projectKey)
                        .orderByDesc(ProjectDiscussionMessage::getCreateTime)
                        .orderByDesc(ProjectDiscussionMessage::getId)
                        .last("LIMIT " + DISCUSSION_LIMIT));
        return safeList(messages).stream()
                .limit(DISCUSSION_LIMIT)
                .map(message -> new ContextDiscussion(
                        message.getId(),
                        // sender_type is not persisted on this table; it is derived from the author.
                        message.getSenderUserId() == null ? "COORDINATOR" : "USER",
                        safeText(message.getSenderName(), SENDER_NAME_MAX),
                        safeText(message.getMessageType(), MESSAGE_TYPE_MAX),
                        safeText(message.getContent(), DISCUSSION_CONTENT_MAX),
                        message.getCreateTime()))
                .toList();
    }

    private List<ContextAssignmentEvent> loadAssignmentEvents(Long tenantId, String projectKey) {
        List<ProjectAssignmentEvent> events = assignmentEventMapper.selectList(
                new LambdaQueryWrapper<ProjectAssignmentEvent>()
                        .eq(ProjectAssignmentEvent::getTenantId, tenantId)
                        .eq(ProjectAssignmentEvent::getProjectKey, projectKey)
                        .orderByDesc(ProjectAssignmentEvent::getCreateTime)
                        .orderByDesc(ProjectAssignmentEvent::getId)
                        .last("LIMIT " + ASSIGNMENT_EVENT_LIMIT));
        return safeList(events).stream()
                .limit(ASSIGNMENT_EVENT_LIMIT)
                .map(event -> new ContextAssignmentEvent(
                        event.getId(),
                        safeText(event.getEventType(), EVENT_TYPE_MAX),
                        event.getFromAssigneeUserId(),
                        event.getToAssigneeUserId(),
                        safeText(event.getFromStatus(), STATUS_MAX),
                        safeText(event.getToStatus(), STATUS_MAX),
                        safeText(event.getReason(), EVENT_REASON_MAX),
                        event.getCreateTime()))
                .toList();
    }

    private String stageLabel(ProjectWarRoomDtos.WarRoomPipelineView pipeline) {
        if (pipeline.businessStageLabel() != null && !pipeline.businessStageLabel().isBlank()) {
            return pipeline.businessStageLabel();
        }
        return pipeline.currentStage();
    }

    /**
     * Length-caps arbitrary text and replaces sensitive markers. Applied to every free-text field
     * so neither {@code toString()} nor JSON serialization of the context can leak them.
     */
    private String safeText(String raw, int maxLength) {
        if (raw == null) return null;
        String value = raw.strip();
        if (value.isEmpty()) return null;
        value = SENSITIVE_TEXT.matcher(value).replaceAll(REDACTED);
        value = CONTROL_CHARS.matcher(value).replaceAll(" ").strip();
        if (value.isEmpty()) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength).strip();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
