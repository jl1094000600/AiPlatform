package com.aipal.service;

import com.aipal.common.BizException;
import com.aipal.common.ErrorCode;
import com.aipal.dto.ProjectCollaborationDtos;
import com.aipal.dto.ProjectTaskDraftDtos;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AiMemoryProject;
import com.aipal.entity.ProjectTaskDraft;
import com.aipal.mapper.ProjectTaskDraftMapper;
import com.aipal.security.TenantContext;
import com.aipal.service.memory.MemoryProjectService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * M3-D task draft lifecycle: manual/coordinator drafts, explicit confirmation into a Team Agent Run
 * and the two structured assignments that follow confirmation.
 */
@Service
@RequiredArgsConstructor
public class ProjectTaskDraftService {
    public static final String BUSINESS_TYPE = "PROJECT_TASK_DRAFT";

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String TARGET_TASK_DRAFT = "TASK_DRAFT";
    private static final String TARGET_AGENT_RUN = "AGENT_RUN";
    private static final Set<String> SOURCE_TYPES = Set.of("DISCUSSION", "PRIVATE_ASK", "MANUAL");
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_DESCRIPTION_LENGTH = 4000;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final MemoryProjectService memoryProjectService;
    private final ProjectTaskDraftMapper draftMapper;
    private final AgentRunService agentRunService;
    private final ProjectAssignmentService projectAssignmentService;

    public List<ProjectTaskDraftDtos.TaskDraftView> list(String projectKey) {
        AiMemoryProject project = memoryProjectService.requireAccessibleWorkspaceProject(projectKey);
        return draftMapper.selectList(new LambdaQueryWrapper<ProjectTaskDraft>()
                        .eq(ProjectTaskDraft::getTenantId, TenantContext.tenantId())
                        .eq(ProjectTaskDraft::getProjectKey, project.getProjectKey())
                        .orderByDesc(ProjectTaskDraft::getUpdateTime)
                        .orderByDesc(ProjectTaskDraft::getId))
                .stream().map(this::view).toList();
    }

    @Transactional
    public ProjectTaskDraftDtos.TaskDraftView create(String projectKey,
                                                     ProjectTaskDraftDtos.CreateTaskDraftRequest request) {
        requireExecute();
        AiMemoryProject project = memoryProjectService.requireAccessibleWorkspaceProject(projectKey);
        DraftInput input = validateCreate(request);
        String idempotencyKey = checkedIdempotencyKey(request == null ? null : request.idempotencyKey());
        if (idempotencyKey != null) {
            ProjectTaskDraft existing = findByIdempotencyKey(project.getProjectKey(), idempotencyKey);
            if (existing != null) return replay(existing, input);
        }

        LocalDateTime now = LocalDateTime.now();
        ProjectTaskDraft draft = new ProjectTaskDraft();
        draft.setTenantId(TenantContext.tenantId());
        draft.setProjectKey(project.getProjectKey());
        draft.setSourceType(input.sourceType());
        draft.setSourceId(input.sourceId());
        draft.setTitle(input.title());
        draft.setDescription(input.description());
        draft.setSuggestedAgentId(input.suggestedAgentId());
        draft.setSuggestedAssigneeUserId(input.suggestedAssigneeUserId());
        draft.setStatus(STATUS_DRAFT);
        draft.setIdempotencyKey(idempotencyKey);
        draft.setVersion(0);
        draft.setCreatedBy(TenantContext.userId());
        draft.setCreateTime(now);
        draft.setUpdateTime(now);
        draft.setIsDeleted(0);
        try {
            draftMapper.insert(draft);
        } catch (DuplicateKeyException duplicate) {
            // A concurrent request may have won the idempotency race; replay only identical content.
            ProjectTaskDraft winner = findByIdempotencyKey(project.getProjectKey(), idempotencyKey);
            if (winner == null) throw idempotencyConflict();
            return replay(winner, input);
        }
        return view(draft);
    }

    @Transactional
    public ProjectTaskDraftDtos.TaskDraftView cancel(String projectKey, Long draftId,
                                                     ProjectTaskDraftDtos.CancelTaskDraftRequest request) {
        requireExecute();
        AiMemoryProject project = memoryProjectService.requireAccessibleWorkspaceProject(projectKey);
        ProjectTaskDraft draft = requireDraft(project.getProjectKey(), draftId);
        if (!STATUS_DRAFT.equals(draft.getStatus())) throw ErrorCode.TASK_DRAFT_STATUS_CHANGED.ex();
        int currentVersion = currentVersion(draft);
        int expectedVersion = checkedVersion(request == null ? null : request.expectedVersion(), currentVersion);
        LocalDateTime now = LocalDateTime.now();
        int updated = draftMapper.update(null, new LambdaUpdateWrapper<ProjectTaskDraft>()
                .eq(ProjectTaskDraft::getTenantId, TenantContext.tenantId())
                .eq(ProjectTaskDraft::getProjectKey, project.getProjectKey())
                .eq(ProjectTaskDraft::getId, draft.getId())
                .eq(ProjectTaskDraft::getVersion, expectedVersion)
                .eq(ProjectTaskDraft::getStatus, STATUS_DRAFT)
                .set(ProjectTaskDraft::getStatus, STATUS_CANCELLED)
                .set(ProjectTaskDraft::getUpdateTime, now)
                .setSql("version = version + 1"));
        if (updated != 1) throw ErrorCode.TASK_DRAFT_VERSION_EXPIRED.ex();
        draft.setStatus(STATUS_CANCELLED);
        draft.setUpdateTime(now);
        draft.setVersion(expectedVersion + 1);
        return view(draft);
    }

    @Transactional
    public ProjectTaskDraftDtos.ConfirmTaskDraftResult confirm(String projectKey, Long draftId,
                                                               ProjectTaskDraftDtos.ConfirmTaskDraftRequest request) {
        requireExecute();
        AiMemoryProject project = memoryProjectService.requireAccessibleWorkspaceProject(projectKey);
        ProjectTaskDraft draft = requireDraft(project.getProjectKey(), draftId);
        if (STATUS_CONFIRMED.equals(draft.getStatus()) && draft.getConfirmedRunId() != null) {
            return replayConfirmed(project.getProjectKey(), draft);
        }
        if (request == null || request.agentId() == null) throw ErrorCode.TASK_DRAFT_AGENT_REQUIRED.ex();
        if (!STATUS_DRAFT.equals(draft.getStatus())) throw ErrorCode.TASK_DRAFT_STATUS_CHANGED.ex();
        // The confirm key is a client retry token. Authoritative replay comes from the
        // DRAFT->CONFIRMED CAS plus confirmedRunId; validate the key for contract consistency.
        checkedIdempotencyKey(request.idempotencyKey());
        int currentVersion = currentVersion(draft);
        int expectedVersion = checkedVersion(request.expectedVersion(), currentVersion);
        if (request.assigneeUserId() != null) {
            // The existing assignment service owns the manage-permission and active-member checks.
            projectAssignmentService.validateAssignableMember(project.getProjectKey(), request.assigneeUserId());
        }

        AgentRun run = agentRunService.createOrReuse(new AgentRunService.CreateRunCommand(
                request.agentId(), project.getProjectKey(), BUSINESS_TYPE, String.valueOf(draft.getId()),
                runInput(draft)));
        if (run == null || run.getId() == null) throw ErrorCode.RUN_CREATE_CONFLICT.ex();

        LocalDateTime now = LocalDateTime.now();
        int updated = draftMapper.update(null, new LambdaUpdateWrapper<ProjectTaskDraft>()
                .eq(ProjectTaskDraft::getTenantId, TenantContext.tenantId())
                .eq(ProjectTaskDraft::getProjectKey, project.getProjectKey())
                .eq(ProjectTaskDraft::getId, draft.getId())
                .eq(ProjectTaskDraft::getVersion, expectedVersion)
                .eq(ProjectTaskDraft::getStatus, STATUS_DRAFT)
                .set(ProjectTaskDraft::getStatus, STATUS_CONFIRMED)
                .set(ProjectTaskDraft::getConfirmedRunId, run.getId())
                .set(ProjectTaskDraft::getUpdateTime, now)
                .setSql("version = version + 1"));
        if (updated != 1) throw ErrorCode.TASK_DRAFT_VERSION_EXPIRED.ex();

        draft.setStatus(STATUS_CONFIRMED);
        draft.setConfirmedRunId(run.getId());
        draft.setUpdateTime(now);
        draft.setVersion(expectedVersion + 1);
        List<ProjectCollaborationDtos.AssignmentView> assignments = request.assigneeUserId() == null
                ? List.of()
                : assignConfirmed(project.getProjectKey(), draft.getId(), run.getId(), request.assigneeUserId());
        return new ProjectTaskDraftDtos.ConfirmTaskDraftResult(view(draft), run.getId(), run.getStatus(), assignments);
    }

    private ProjectTaskDraftDtos.ConfirmTaskDraftResult replayConfirmed(String projectKey, ProjectTaskDraft draft) {
        AgentRun run = agentRunService.get(draft.getConfirmedRunId());
        if (run == null) throw ErrorCode.TASK_DRAFT_CONFIRM_CONFLICT.ex();
        List<ProjectCollaborationDtos.AssignmentView> assignments = projectAssignmentService.listProject(projectKey)
                .stream()
                .filter(assignment -> targetsDraftOrRun(assignment, draft.getId(), run.getId()))
                .toList();
        return new ProjectTaskDraftDtos.ConfirmTaskDraftResult(view(draft), run.getId(), run.getStatus(), assignments);
    }

    private List<ProjectCollaborationDtos.AssignmentView> assignConfirmed(String projectKey, Long draftId,
                                                                         Long runId, Long assigneeUserId) {
        List<ProjectCollaborationDtos.AssignmentView> assignments = new ArrayList<>(2);
        addAssignment(assignments, projectAssignmentService.assign(projectKey,
                new ProjectCollaborationDtos.AssignRequest(TARGET_TASK_DRAFT, draftId, assigneeUserId, null)));
        addAssignment(assignments, projectAssignmentService.assign(projectKey,
                new ProjectCollaborationDtos.AssignRequest(TARGET_AGENT_RUN, runId, assigneeUserId, null)));
        return List.copyOf(assignments);
    }

    private void addAssignment(List<ProjectCollaborationDtos.AssignmentView> assignments,
                               ProjectCollaborationDtos.AssignmentView assignment) {
        if (assignment != null) assignments.add(assignment);
    }

    private boolean targetsDraftOrRun(ProjectCollaborationDtos.AssignmentView assignment, Long draftId, Long runId) {
        return (TARGET_TASK_DRAFT.equals(assignment.targetType()) && Objects.equals(draftId, assignment.targetId()))
                || (TARGET_AGENT_RUN.equals(assignment.targetType()) && Objects.equals(runId, assignment.targetId()));
    }

    private Map<String, Object> runInput(ProjectTaskDraft draft) {
        // Deliberately excludes raw model output and any private conversation content.
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("title", draft.getTitle());
        input.put("description", draft.getDescription());
        input.put("sourceType", draft.getSourceType());
        input.put("sourceId", draft.getSourceId());
        return input;
    }

    private DraftInput validateCreate(ProjectTaskDraftDtos.CreateTaskDraftRequest request) {
        if (request == null) throw ErrorCode.TASK_DRAFT_TITLE_REQUIRED.ex();
        String title = trimmedToNull(request.title());
        if (title == null) throw ErrorCode.TASK_DRAFT_TITLE_REQUIRED.ex();
        if (title.length() > MAX_TITLE_LENGTH) throw ErrorCode.TASK_DRAFT_TITLE_TOO_LONG.ex();
        String description = trimmedToNull(request.description());
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw ErrorCode.TASK_DRAFT_DESCRIPTION_TOO_LONG.ex();
        }
        return new DraftInput(title, description, checkedSourceType(request.sourceType()), request.sourceId(),
                request.suggestedAgentId(), request.suggestedAssigneeUserId());
    }

    private String checkedSourceType(String sourceType) {
        String normalized = sourceType == null || sourceType.isBlank()
                ? "MANUAL" : sourceType.trim().toUpperCase(Locale.ROOT);
        if (!SOURCE_TYPES.contains(normalized)) throw ErrorCode.TASK_DRAFT_SOURCE_INVALID.ex();
        return normalized;
    }

    private String checkedIdempotencyKey(String idempotencyKey) {
        String key = trimmedToNull(idempotencyKey);
        if (key != null && key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw ErrorCode.IDEMPOTENCY_KEY_TOO_LONG.ex();
        }
        return key;
    }

    private int checkedVersion(Integer expectedVersion, int actualVersion) {
        if (expectedVersion == null || expectedVersion < 0) throw ErrorCode.EXPECTED_VERSION_REQUIRED.ex();
        if (!Objects.equals(expectedVersion, actualVersion)) throw ErrorCode.TASK_DRAFT_VERSION_EXPIRED.ex();
        return expectedVersion;
    }

    private int currentVersion(ProjectTaskDraft draft) {
        return draft.getVersion() == null ? 0 : draft.getVersion();
    }

    private ProjectTaskDraft requireDraft(String projectKey, Long draftId) {
        if (draftId == null || draftId <= 0) throw ErrorCode.TASK_DRAFT_NOT_FOUND.ex();
        ProjectTaskDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<ProjectTaskDraft>()
                .eq(ProjectTaskDraft::getTenantId, TenantContext.tenantId())
                .eq(ProjectTaskDraft::getProjectKey, projectKey)
                .eq(ProjectTaskDraft::getId, draftId)
                .last("LIMIT 1"));
        if (draft == null) throw ErrorCode.TASK_DRAFT_NOT_FOUND.ex();
        return draft;
    }

    private ProjectTaskDraft findByIdempotencyKey(String projectKey, String idempotencyKey) {
        return draftMapper.selectOne(new LambdaQueryWrapper<ProjectTaskDraft>()
                .eq(ProjectTaskDraft::getTenantId, TenantContext.tenantId())
                .eq(ProjectTaskDraft::getProjectKey, projectKey)
                .eq(ProjectTaskDraft::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"));
    }

    private ProjectTaskDraftDtos.TaskDraftView replay(ProjectTaskDraft existing, DraftInput input) {
        boolean identical = Objects.equals(existing.getTitle(), input.title())
                && Objects.equals(existing.getDescription(), input.description())
                && Objects.equals(existing.getSourceType(), input.sourceType())
                && Objects.equals(existing.getSourceId(), input.sourceId())
                && Objects.equals(existing.getSuggestedAgentId(), input.suggestedAgentId())
                && Objects.equals(existing.getSuggestedAssigneeUserId(), input.suggestedAssigneeUserId());
        if (!identical) throw idempotencyConflict();
        return view(existing);
    }

    private BizException idempotencyConflict() {
        return ErrorCode.TASK_DRAFT_IDEMPOTENCY_CONFLICT.ex();
    }

    private void requireExecute() {
        memoryProjectService.requireWorkspacePermission("project-workspace:execute");
    }

    private ProjectTaskDraftDtos.TaskDraftView view(ProjectTaskDraft draft) {
        return new ProjectTaskDraftDtos.TaskDraftView(draft.getId(), draft.getProjectKey(), draft.getSourceType(),
                draft.getSourceId(), draft.getTitle(), draft.getDescription(), draft.getSuggestedAgentId(),
                draft.getSuggestedAssigneeUserId(), draft.getStatus(), draft.getConfirmedRunId(), draft.getVersion(),
                draft.getCreateTime(), draft.getUpdateTime());
    }

    private String trimmedToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record DraftInput(String title, String description, String sourceType, Long sourceId,
                              Long suggestedAgentId, Long suggestedAssigneeUserId) {
    }
}
