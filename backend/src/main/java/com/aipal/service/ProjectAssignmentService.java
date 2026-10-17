package com.aipal.service;

import com.aipal.common.BizException;
import com.aipal.dto.ProjectCollaborationDtos;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AiMemoryProject;
import com.aipal.entity.AutomationApproval;
import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.ProjectAssignment;
import com.aipal.entity.ProjectAssignmentEvent;
import com.aipal.entity.ProjectTaskDraft;
import com.aipal.entity.SysAuditLog;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.mapper.AutomationApprovalMapper;
import com.aipal.mapper.AutomationPipelineMapper;
import com.aipal.mapper.ProjectAssignmentEventMapper;
import com.aipal.mapper.ProjectAssignmentMapper;
import com.aipal.mapper.ProjectTaskDraftMapper;
import com.aipal.mapper.SysAuditLogMapper;
import com.aipal.security.TenantContext;
import com.aipal.service.memory.MemoryProjectService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectAssignmentService {
    private static final Set<String> TARGET_TYPES = Set.of("AGENT_RUN", "AUTOMATION_APPROVAL", "TASK_DRAFT");
    private static final Set<String> ACTIVE_STATUSES = Set.of("ASSIGNED", "ACCEPTED");

    private final MemoryProjectService memoryProjectService;
    private final ProjectAssignmentMapper assignmentMapper;
    private final ProjectAssignmentEventMapper eventMapper;
    private final AgentRunMapper runMapper;
    private final AutomationApprovalMapper approvalMapper;
    private final AutomationPipelineMapper pipelineMapper;
    private final ProjectTaskDraftMapper draftMapper;
    private final SysAuditLogMapper auditLogMapper;

    @Transactional
    public ProjectCollaborationDtos.AssignmentView assign(String projectKey, ProjectCollaborationDtos.AssignRequest request) {
        if (request == null || !TARGET_TYPES.contains(request.targetType() == null ? "" : request.targetType())
                || request.targetId() == null || request.targetId() <= 0) {
            throw new BizException(400, "指派目标类型或编号无效");
        }
        String reason = checkedReason(request.reason());
        AiMemoryProject project = validateAssignableMember(projectKey, request.assigneeUserId());
        requireTarget(project.getProjectKey(), request.targetType(), request.targetId());
        ProjectAssignment existing = findTarget(project.getProjectKey(), request.targetType(), request.targetId(), false);
        if (existing != null) return reusedView(existing, request.assigneeUserId());

        ProjectAssignment assignment = new ProjectAssignment();
        assignment.setTenantId(TenantContext.tenantId());
        assignment.setProjectKey(project.getProjectKey());
        assignment.setTargetType(request.targetType());
        assignment.setTargetId(request.targetId());
        assignment.setAssigneeUserId(request.assigneeUserId());
        assignment.setStatus("ASSIGNED");
        assignment.setVersion(0);
        assignment.setCreatedBy(TenantContext.userId());
        assignment.setCreateTime(LocalDateTime.now());
        assignment.setUpdateTime(assignment.getCreateTime());
        assignment.setIsDeleted(0);
        try {
            assignmentMapper.insert(assignment);
        } catch (DuplicateKeyException duplicate) {
            // A locking read sees the concurrently committed winner even under repeatable read.
            ProjectAssignment winner = findTarget(project.getProjectKey(), request.targetType(), request.targetId(), true);
            if (winner == null) throw new BizException(409, "指派目标已变更，请刷新后重试");
            return reusedView(winner, request.assigneeUserId());
        }
        appendEvent(assignment, "ASSIGNED", null, null, reason);
        return view(assignment, true);
    }

    /** Team Run invokes this before creating any execution state. */
    public AiMemoryProject validateAssignableMember(String projectKey, Long assigneeUserId) {
        AiMemoryProject project = requireManager(projectKey);
        memoryProjectService.requireActiveMember(project.getProjectKey(), assigneeUserId);
        return project;
    }

    @Transactional
    public ProjectCollaborationDtos.AssignmentView reassign(String projectKey, Long assignmentId,
                                                           ProjectCollaborationDtos.ReassignRequest request) {
        if (request == null) throw new BizException(400, "改派请求不能为空");
        String reason = checkedReason(request.reason());
        AiMemoryProject project = validateAssignableMember(projectKey, request.assigneeUserId());
        ProjectAssignment assignment = requireAssignment(project.getProjectKey(), assignmentId);
        checkVersion(assignment, request.expectedVersion());
        if (!ACTIVE_STATUSES.contains(assignment.getStatus())) throw new BizException(409, "已结束的指派不能改派");
        requireTarget(project.getProjectKey(), assignment.getTargetType(), assignment.getTargetId());
        if (Objects.equals(assignment.getAssigneeUserId(), request.assigneeUserId())) return view(assignment, true);
        return change(assignment, request.assigneeUserId(), "ASSIGNED", "REASSIGNED", request.expectedVersion(), reason, true);
    }

    @Transactional
    public ProjectCollaborationDtos.AssignmentView accept(String projectKey, Long assignmentId,
                                                         ProjectCollaborationDtos.AssignmentActionRequest request) {
        return act(projectKey, assignmentId, request, "ASSIGNED", "ACCEPTED");
    }

    @Transactional
    public ProjectCollaborationDtos.AssignmentView complete(String projectKey, Long assignmentId,
                                                           ProjectCollaborationDtos.AssignmentActionRequest request) {
        return act(projectKey, assignmentId, request, "ACCEPTED", "COMPLETED");
    }

    public List<ProjectCollaborationDtos.AssignmentView> listProject(String projectKey) {
        requirePermission("project-workspace:view");
        AiMemoryProject project = memoryProjectService.requireAccessibleWorkspaceProject(projectKey);
        boolean manager = memoryProjectService.canManageWorkspaceProject(project.getProjectKey());
        return assignmentMapper.selectList(new LambdaQueryWrapper<ProjectAssignment>()
                        .eq(ProjectAssignment::getTenantId, TenantContext.tenantId())
                        .eq(ProjectAssignment::getProjectKey, project.getProjectKey())
                        .orderByDesc(ProjectAssignment::getUpdateTime).orderByDesc(ProjectAssignment::getId))
                .stream().map(assignment -> view(assignment, manager)).toList();
    }

    public List<ProjectCollaborationDtos.AssignmentView> listMine() {
        requirePermission("project-workspace:view");
        return assignmentMapper.selectList(new LambdaQueryWrapper<ProjectAssignment>()
                        .eq(ProjectAssignment::getTenantId, TenantContext.tenantId())
                        .eq(ProjectAssignment::getAssigneeUserId, TenantContext.userId())
                        .orderByDesc(ProjectAssignment::getUpdateTime).orderByDesc(ProjectAssignment::getId))
                .stream()
                .filter(assignment -> memoryProjectService.isActiveWorkspaceMember(assignment.getProjectKey(), TenantContext.userId()))
                .map(assignment -> view(assignment, memoryProjectService.canManageWorkspaceProject(assignment.getProjectKey())))
                .toList();
    }

    private ProjectCollaborationDtos.AssignmentView act(String projectKey, Long assignmentId,
                                                       ProjectCollaborationDtos.AssignmentActionRequest request,
                                                       String fromStatus, String toStatus) {
        requirePermission("project-workspace:execute");
        if (request == null) throw new BizException(400, "指派操作请求不能为空");
        String reason = checkedReason(request.reason());
        AiMemoryProject project = memoryProjectService.requireAccessibleWorkspaceProject(projectKey);
        ProjectAssignment assignment = requireAssignment(project.getProjectKey(), assignmentId);
        if (!Objects.equals(TenantContext.userId(), assignment.getAssigneeUserId())) {
            throw new BizException(403, "只有被指派人可以接受或完成指派");
        }
        memoryProjectService.requireActiveMember(project.getProjectKey(), TenantContext.userId());
        checkVersion(assignment, request.expectedVersion());
        if (!fromStatus.equals(assignment.getStatus())) throw new BizException(409, "指派状态已变更，请刷新后重试");
        requireTarget(project.getProjectKey(), assignment.getTargetType(), assignment.getTargetId());
        return change(assignment, assignment.getAssigneeUserId(), toStatus, toStatus,
                request.expectedVersion(), reason, memoryProjectService.canManageWorkspaceProject(project.getProjectKey()));
    }

    private ProjectCollaborationDtos.AssignmentView change(ProjectAssignment assignment, Long assigneeUserId,
                                                          String status, String eventType, Integer expectedVersion,
                                                          String reason, boolean manager) {
        Long previousAssignee = assignment.getAssigneeUserId();
        String previousStatus = assignment.getStatus();
        LocalDateTime now = LocalDateTime.now();
        int updated = assignmentMapper.update(null, new LambdaUpdateWrapper<ProjectAssignment>()
                .eq(ProjectAssignment::getTenantId, TenantContext.tenantId())
                .eq(ProjectAssignment::getProjectKey, assignment.getProjectKey())
                .eq(ProjectAssignment::getId, assignment.getId())
                .eq(ProjectAssignment::getVersion, expectedVersion)
                .eq(ProjectAssignment::getStatus, previousStatus)
                .set(ProjectAssignment::getAssigneeUserId, assigneeUserId)
                .set(ProjectAssignment::getStatus, status)
                .set(ProjectAssignment::getUpdateTime, now)
                .setSql("version = version + 1"));
        if (updated != 1) throw new BizException(409, "指派版本已过期，请刷新后重试");
        assignment.setAssigneeUserId(assigneeUserId);
        assignment.setStatus(status);
        assignment.setVersion(expectedVersion + 1);
        assignment.setUpdateTime(now);
        appendEvent(assignment, eventType, previousAssignee, previousStatus, reason);
        return view(assignment, manager);
    }

    private AiMemoryProject requireManager(String projectKey) {
        requirePermission("project-workspace:view");
        AiMemoryProject project = memoryProjectService.requireAccessibleWorkspaceProject(projectKey);
        if (!memoryProjectService.canManageWorkspaceProject(project.getProjectKey())) {
            throw new BizException(403, "只有项目负责人、项目管理员或具备项目管理权限的用户可以指派");
        }
        return project;
    }

    private ProjectAssignment requireAssignment(String projectKey, Long id) {
        if (id == null || id <= 0) throw new BizException(400, "指派编号无效");
        ProjectAssignment assignment = assignmentMapper.selectOne(new LambdaQueryWrapper<ProjectAssignment>()
                .eq(ProjectAssignment::getTenantId, TenantContext.tenantId())
                .eq(ProjectAssignment::getProjectKey, projectKey)
                .eq(ProjectAssignment::getId, id).last("LIMIT 1"));
        if (assignment == null) throw new BizException(404, "指派不存在");
        return assignment;
    }

    private ProjectAssignment findTarget(String projectKey, String targetType, Long targetId, boolean locking) {
        return assignmentMapper.selectOne(new LambdaQueryWrapper<ProjectAssignment>()
                .eq(ProjectAssignment::getTenantId, TenantContext.tenantId())
                .eq(ProjectAssignment::getProjectKey, projectKey)
                .eq(ProjectAssignment::getTargetType, targetType)
                .eq(ProjectAssignment::getTargetId, targetId).last(locking ? "LIMIT 1 FOR UPDATE" : "LIMIT 1"));
    }

    private void requireTarget(String projectKey, String targetType, Long targetId) {
        boolean matches = switch (targetType) {
            case "AGENT_RUN" -> {
                AgentRun run = runMapper.selectOne(new LambdaQueryWrapper<AgentRun>()
                        .eq(AgentRun::getTenantId, TenantContext.tenantId())
                        .eq(AgentRun::getProjectKey, projectKey).eq(AgentRun::getId, targetId).last("LIMIT 1"));
                yield run != null;
            }
            case "TASK_DRAFT" -> {
                ProjectTaskDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<ProjectTaskDraft>()
                        .eq(ProjectTaskDraft::getTenantId, TenantContext.tenantId())
                        .eq(ProjectTaskDraft::getProjectKey, projectKey).eq(ProjectTaskDraft::getId, targetId).last("LIMIT 1"));
                yield draft != null;
            }
            case "AUTOMATION_APPROVAL" -> {
                AutomationApproval approval = approvalMapper.selectOne(new LambdaQueryWrapper<AutomationApproval>()
                        .eq(AutomationApproval::getTenantId, TenantContext.tenantId())
                        .eq(AutomationApproval::getId, targetId).last("LIMIT 1"));
                AutomationPipeline pipeline = approval == null ? null : pipelineMapper.selectOne(new LambdaQueryWrapper<AutomationPipeline>()
                        .eq(AutomationPipeline::getTenantId, TenantContext.tenantId())
                        .eq(AutomationPipeline::getProjectKey, projectKey)
                        .eq(AutomationPipeline::getIsDeleted, 0)
                        .eq(AutomationPipeline::getId, approval.getPipelineId()).last("LIMIT 1"));
                yield pipeline != null;
            }
            default -> false;
        };
        if (!matches) throw new BizException(400, "指派目标不属于当前租户和项目");
    }

    private ProjectCollaborationDtos.AssignmentView view(ProjectAssignment assignment, boolean manager) {
        String assigneeName = "用户 " + assignment.getAssigneeUserId();
        boolean activeMember = false;
        try {
            MemoryProjectService.ProjectMemberView member = memoryProjectService.requireActiveMember(
                    assignment.getProjectKey(), assignment.getAssigneeUserId());
            assigneeName = member.realName() == null || member.realName().isBlank() ? member.username() : member.realName();
            activeMember = true;
        } catch (BizException invalidMember) {
            if (invalidMember.getCode() != 400) throw invalidMember;
        }
        boolean ownExecutable = activeMember && Objects.equals(TenantContext.userId(), assignment.getAssigneeUserId())
                && TenantContext.hasPermission("project-workspace:execute");
        return new ProjectCollaborationDtos.AssignmentView(assignment.getId(), assignment.getProjectKey(),
                assignment.getTargetType(), assignment.getTargetId(), assignment.getAssigneeUserId(), assigneeName,
                assignment.getStatus(), assignment.getVersion(), assignment.getUpdateTime(),
                manager && ACTIVE_STATUSES.contains(assignment.getStatus()),
                ownExecutable && "ASSIGNED".equals(assignment.getStatus()),
                ownExecutable && "ACCEPTED".equals(assignment.getStatus()));
    }

    private ProjectCollaborationDtos.AssignmentView reusedView(ProjectAssignment assignment, Long assigneeUserId) {
        if (!Objects.equals(assignment.getAssigneeUserId(), assigneeUserId)) {
            throw new BizException(409, "该目标已有其他处理人，请刷新后使用改派操作");
        }
        return view(assignment, true);
    }

    private void appendEvent(ProjectAssignment assignment, String eventType, Long fromAssignee,
                             String fromStatus, String reason) {
        ProjectAssignmentEvent event = new ProjectAssignmentEvent();
        event.setTenantId(TenantContext.tenantId());
        event.setProjectKey(assignment.getProjectKey());
        event.setAssignmentId(assignment.getId());
        event.setEventType(eventType);
        event.setFromAssigneeUserId(fromAssignee);
        event.setToAssigneeUserId(assignment.getAssigneeUserId());
        event.setFromStatus(fromStatus);
        event.setToStatus(assignment.getStatus());
        event.setActorUserId(TenantContext.userId());
        event.setActorName(TenantContext.username());
        event.setReason(reason);
        event.setCreateTime(LocalDateTime.now());
        eventMapper.insert(event);
        // This durable business audit participates in the same transaction as the state and event.
        // The HTTP permission aspect remains a separate best-effort access audit.
        SysAuditLog audit = new SysAuditLog();
        audit.setTenantId(TenantContext.tenantId());
        audit.setUserId(TenantContext.userId());
        audit.setUsername(TenantContext.username());
        audit.setPermissionCode(Set.of("ASSIGNED", "REASSIGNED").contains(eventType)
                ? "project-workspace:manage" : "project-workspace:execute");
        audit.setRequestPath("/api/v1/project-workspaces/" + assignment.getProjectKey() + "/assignments/" + assignment.getId());
        audit.setOperation(eventType);
        audit.setResourceType("PROJECT_ASSIGNMENT");
        audit.setResourceId(assignment.getId());
        audit.setResourceCode(assignment.getTargetType() + ":" + assignment.getTargetId());
        audit.setBeforeValue(JsonNodeFactory.instance.objectNode()
                .put("projectKey", assignment.getProjectKey())
                .put("assigneeUserId", fromAssignee).put("status", fromStatus).toString());
        audit.setAfterValue(JsonNodeFactory.instance.objectNode()
                .put("projectKey", assignment.getProjectKey())
                .put("assigneeUserId", assignment.getAssigneeUserId())
                .put("status", assignment.getStatus()).put("version", assignment.getVersion()).toString());
        audit.setResult(1);
        audit.setCreateTime(event.getCreateTime());
        auditLogMapper.insert(audit);
    }

    private void checkVersion(ProjectAssignment assignment, Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) throw new BizException(400, "expectedVersion 必须为非负整数");
        if (!Objects.equals(expectedVersion, assignment.getVersion())) throw new BizException(409, "指派版本已过期，请刷新后重试");
    }

    public String checkedReason(String reason) {
        String value = reason == null ? null : reason.trim();
        if (value != null && value.length() > 500) throw new BizException(400, "指派原因不能超过 500 字符");
        return value;
    }

    private void requirePermission(String permission) {
        memoryProjectService.requireWorkspacePermission(permission);
    }
}
