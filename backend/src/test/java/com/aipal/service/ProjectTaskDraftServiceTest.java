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
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectTaskDraftServiceTest {

    private static final String VIEW = "project-workspace:view";
    private static final String EXECUTE = "project-workspace:execute";

    private MemoryProjectService memoryProjectService;
    private ProjectTaskDraftMapper draftMapper;
    private AgentRunService agentRunService;
    private ProjectAssignmentService assignmentService;
    private ProjectTaskDraftService service;
    private AiMemoryProject project;

    @BeforeAll
    static void initMybatisMetadata() {
        // Lambda wrappers resolve entity columns through MyBatis-Plus global metadata.
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "task-draft-service-test"),
                ProjectTaskDraft.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(new TenantContext.Context(1L, "pm", 1L, "tenant-1", false,
                Set.of(), Set.of(VIEW, EXECUTE)));
        memoryProjectService = mock(MemoryProjectService.class);
        draftMapper = mock(ProjectTaskDraftMapper.class);
        agentRunService = mock(AgentRunService.class);
        assignmentService = mock(ProjectAssignmentService.class);
        project = new AiMemoryProject();
        project.setId(7L);
        project.setTenantId(1L);
        project.setProjectKey("project-a");
        project.setStatus("ACTIVE");
        when(memoryProjectService.requireAccessibleWorkspaceProject(anyString())).thenReturn(project);
        service = new ProjectTaskDraftService(memoryProjectService, draftMapper, agentRunService, assignmentService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listUsesTrustedProjectKeyAndTenantScope() {
        when(draftMapper.selectList(any())).thenReturn(List.of(draft(5L, "project-a", "DRAFT", 0)));

        List<ProjectTaskDraftDtos.TaskDraftView> views = service.list("path-project-key");

        assertEquals(1, views.size());
        assertEquals("project-a", views.get(0).projectKey());
        Wrapper<?> wrapper = captureListWrapper();
        assertTrue(wrapper.getTargetSql().contains("tenant_id"));
        assertTrue(wrapper.getTargetSql().contains("project_key"));
        Map<String, Object> params = params(wrapper);
        assertTrue(params.containsValue(1L), "tenant id must be part of the query");
        assertTrue(params.containsValue("project-a"), "trusted project key must be part of the query");
        assertFalse(params.containsValue("path-project-key"), "path key must not leak into storage queries");
    }

    @Test
    void createPersistsDraftWithNormalizedContent() {
        when(draftMapper.insert(any())).thenAnswer(invocation -> {
            ((ProjectTaskDraft) invocation.getArgument(0)).setId(11L);
            return 1;
        });

        ProjectTaskDraftDtos.TaskDraftView view = service.create("project-a",
                new ProjectTaskDraftDtos.CreateTaskDraftRequest("  Ship release  ", "  do it  ", 4L, 2L,
                        " discussion ", 88L, " key-1 "));

        ProjectTaskDraft saved = captureInsertedDraft();
        assertEquals(1L, saved.getTenantId());
        assertEquals("project-a", saved.getProjectKey());
        assertEquals("Ship release", saved.getTitle());
        assertEquals("do it", saved.getDescription());
        assertEquals("DISCUSSION", saved.getSourceType());
        assertEquals(88L, saved.getSourceId());
        assertEquals(4L, saved.getSuggestedAgentId());
        assertEquals(2L, saved.getSuggestedAssigneeUserId());
        assertEquals("DRAFT", saved.getStatus());
        assertEquals(0, saved.getVersion());
        assertEquals(1L, saved.getCreatedBy());
        assertEquals("key-1", saved.getIdempotencyKey());
        assertEquals("DRAFT", view.status());
        assertEquals(11L, view.id());
    }

    @Test
    void createDefaultsSourceToManualAndAllowsNullDescription() {
        when(draftMapper.insert(any())).thenAnswer(invocation -> {
            ((ProjectTaskDraft) invocation.getArgument(0)).setId(12L);
            return 1;
        });

        service.create("project-a", new ProjectTaskDraftDtos.CreateTaskDraftRequest(
                "Manual task", "   ", null, null, "   ", null, "  "));

        ProjectTaskDraft saved = captureInsertedDraft();
        assertEquals("MANUAL", saved.getSourceType());
        assertNull(saved.getDescription());
        assertNull(saved.getIdempotencyKey());
    }

    @Test
    void createReplaysIdenticalIdempotencyKeyAndRejectsDifferentContent() {
        ProjectTaskDraft existing = draft(5L, "project-a", "DRAFT", 0);
        existing.setTitle("Task");
        existing.setDescription("desc");
        existing.setSourceType("MANUAL");
        when(draftMapper.selectOne(any())).thenReturn(existing);

        ProjectTaskDraftDtos.TaskDraftView replayed = service.create("project-a",
                new ProjectTaskDraftDtos.CreateTaskDraftRequest("Task", "desc", null, null, "MANUAL", null, "k1"));
        assertEquals(5L, replayed.id());
        verify(draftMapper, never()).insert(any());

        BizException conflict = assertThrows(BizException.class, () -> service.create("project-a",
                new ProjectTaskDraftDtos.CreateTaskDraftRequest("Other", "desc", null, null, "MANUAL", null, "k1")));
        assertEquals(409, conflict.getCode());
    }

    @Test
    void createRejectsInvalidSourceType() {
        BizException invalid = assertThrows(BizException.class, () -> service.create("project-a",
                new ProjectTaskDraftDtos.CreateTaskDraftRequest("Task", null, null, null, "PRIVATE_ASK_CONVERSION", null, null)));
        assertEquals(400, invalid.getCode());
        verifyNoInteractions(draftMapper);
    }

    @Test
    void cancelOnlyTransitionsDraftToCancelledAndBumpsVersion() {
        when(draftMapper.selectOne(any())).thenReturn(draft(5L, "project-a", "DRAFT", 2));
        when(draftMapper.update(isNull(), any())).thenReturn(1);

        ProjectTaskDraftDtos.TaskDraftView view = service.cancel("project-a", 5L,
                new ProjectTaskDraftDtos.CancelTaskDraftRequest(2, "no longer needed"));

        assertEquals("CANCELLED", view.status());
        assertEquals(3, view.version());
        Wrapper<?> wrapper = captureUpdateWrapper();
        assertTrue(wrapper.getSqlSet().contains("status"));
        Map<String, Object> params = params(wrapper);
        assertTrue(params.containsValue("CANCELLED"));
        assertTrue(params.containsValue(2));
        assertTrue(params.containsValue(5L));
    }

    @Test
    void cancelRejectsNonDraftStatus() {
        when(draftMapper.selectOne(any())).thenReturn(draft(6L, "project-a", "CONFIRMED", 1));

        BizException changed = assertThrows(BizException.class, () -> service.cancel("project-a", 6L,
                new ProjectTaskDraftDtos.CancelTaskDraftRequest(1, null)));
        assertEquals(409, changed.getCode());
        verify(draftMapper, never()).update(isNull(), any());
    }

    @Test
    void cancelRejectsStaleVersion() {
        when(draftMapper.selectOne(any())).thenReturn(draft(5L, "project-a", "DRAFT", 2));

        BizException expired = assertThrows(BizException.class, () -> service.cancel("project-a", 5L,
                new ProjectTaskDraftDtos.CancelTaskDraftRequest(1, null)));
        assertEquals(409, expired.getCode());
        verify(draftMapper, never()).update(isNull(), any());
    }

    @Test
    void confirmCreatesRunConfirmsDraftAndBuildsBothAssignments() {
        ProjectTaskDraft draft = draft(5L, "project-a", "DRAFT", 0);
        draft.setTitle("Ship release");
        draft.setDescription("do it");
        draft.setSourceType("DISCUSSION");
        draft.setSourceId(88L);
        when(draftMapper.selectOne(any())).thenReturn(draft);
        when(draftMapper.update(isNull(), any())).thenReturn(1);
        AgentRun run = new AgentRun();
        run.setId(99L);
        run.setProjectKey("project-a");
        run.setStatus("QUEUED");
        when(agentRunService.createOrReuse(any())).thenReturn(run);
        when(assignmentService.validateAssignableMember("project-a", 2L)).thenReturn(project);
        when(assignmentService.assign(eq("project-a"), any())).thenReturn(
                assignment(1L, "TASK_DRAFT", 5L), assignment(2L, "AGENT_RUN", 99L));

        ProjectTaskDraftDtos.ConfirmTaskDraftResult result = service.confirm("project-a", 5L,
                new ProjectTaskDraftDtos.ConfirmTaskDraftRequest(3L, 2L, 0, "confirm-key"));

        ArgumentCaptor<AgentRunService.CreateRunCommand> command =
                ArgumentCaptor.forClass(AgentRunService.CreateRunCommand.class);
        verify(agentRunService).createOrReuse(command.capture());
        assertEquals(3L, command.getValue().agentId());
        assertEquals("project-a", command.getValue().projectKey());
        assertEquals("PROJECT_TASK_DRAFT", command.getValue().businessType());
        assertEquals("5", command.getValue().businessId());
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) command.getValue().input();
        assertEquals(Set.of("title", "description", "sourceType", "sourceId"), input.keySet());
        assertEquals("Ship release", input.get("title"));
        assertEquals("DISCUSSION", input.get("sourceType"));
        assertEquals(88L, input.get("sourceId"));
        assertFalse(command.getValue().input().toString().contains("model"), "raw model output must stay out of the run");

        Wrapper<?> update = captureUpdateWrapper();
        assertTrue(update.getSqlSet().contains("status"));
        assertTrue(update.getSqlSet().contains("confirmed_run_id"));
        Map<String, Object> params = params(update);
        assertTrue(params.containsValue("CONFIRMED"));
        assertTrue(params.containsValue(99L));
        assertTrue(params.containsValue(0));

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<ProjectCollaborationDtos.AssignRequest> assign =
                ArgumentCaptor.forClass(ProjectCollaborationDtos.AssignRequest.class);
        verify(assignmentService, times(2)).assign(eq("project-a"), assign.capture());
        assertEquals(List.of("TASK_DRAFT", "AGENT_RUN"),
                assign.getAllValues().stream().map(ProjectCollaborationDtos.AssignRequest::targetType).toList());
        assertEquals(5L, assign.getAllValues().get(0).targetId());
        assertEquals(99L, assign.getAllValues().get(1).targetId());
        assertTrue(assign.getAllValues().stream().allMatch(request -> request.assigneeUserId().equals(2L)));

        assertEquals("CONFIRMED", result.draft().status());
        assertEquals(99L, result.draft().confirmedRunId());
        assertEquals(1, result.draft().version());
        assertEquals(99L, result.runId());
        assertEquals("QUEUED", result.runStatus());
        assertEquals(2, result.assignments().size());
    }

    @Test
    void confirmWithoutAssigneeCreatesNoAssignment() {
        when(draftMapper.selectOne(any())).thenReturn(draft(5L, "project-a", "DRAFT", 0));
        when(draftMapper.update(isNull(), any())).thenReturn(1);
        AgentRun run = new AgentRun();
        run.setId(99L);
        run.setStatus("QUEUED");
        when(agentRunService.createOrReuse(any())).thenReturn(run);

        ProjectTaskDraftDtos.ConfirmTaskDraftResult result = service.confirm("project-a", 5L,
                new ProjectTaskDraftDtos.ConfirmTaskDraftRequest(3L, null, 0, null));

        verify(assignmentService, never()).assign(any(), any());
        assertTrue(result.assignments().isEmpty());
    }

    @Test
    void confirmRetryReturnsTheAlreadyConfirmedRun() {
        ProjectTaskDraft draft = draft(5L, "project-a", "CONFIRMED", 3);
        draft.setConfirmedRunId(99L);
        when(draftMapper.selectOne(any())).thenReturn(draft);
        AgentRun run = new AgentRun();
        run.setId(99L);
        run.setStatus("SUCCEEDED");
        when(agentRunService.get(99L)).thenReturn(run);
        when(assignmentService.listProject("project-a")).thenReturn(List.of(
                assignment(1L, "TASK_DRAFT", 5L),
                assignment(2L, "AGENT_RUN", 99L),
                assignment(3L, "TASK_DRAFT", 999L)));

        ProjectTaskDraftDtos.ConfirmTaskDraftResult result = service.confirm("project-a", 5L,
                new ProjectTaskDraftDtos.ConfirmTaskDraftRequest(3L, 2L, 0, null));

        verify(agentRunService, never()).createOrReuse(any());
        verify(draftMapper, never()).update(isNull(), any());
        verify(draftMapper, never()).insert(any());
        assertEquals(99L, result.runId());
        assertEquals("SUCCEEDED", result.runStatus());
        assertEquals(2, result.assignments().size());
    }

    @Test
    void confirmRequiresAgentId() {
        when(draftMapper.selectOne(any())).thenReturn(draft(5L, "project-a", "DRAFT", 0));

        BizException missingAgent = assertThrows(BizException.class, () -> service.confirm("project-a", 5L,
                new ProjectTaskDraftDtos.ConfirmTaskDraftRequest(null, 2L, 0, null)));

        assertEquals(400, missingAgent.getCode());
        verify(agentRunService, never()).createOrReuse(any());
    }

    @Test
    void confirmRejectsStaleVersionBeforeCreatingTheRun() {
        when(draftMapper.selectOne(any())).thenReturn(draft(5L, "project-a", "DRAFT", 2));

        BizException expired = assertThrows(BizException.class, () -> service.confirm("project-a", 5L,
                new ProjectTaskDraftDtos.ConfirmTaskDraftRequest(3L, null, 1, null)));

        assertEquals(409, expired.getCode());
        verify(agentRunService, never()).createOrReuse(any());
        verify(draftMapper, never()).update(isNull(), any());
    }

    @Test
    void confirmCasFailureIsReportedAsVersionConflict() {
        when(draftMapper.selectOne(any())).thenReturn(draft(5L, "project-a", "DRAFT", 0));
        AgentRun run = new AgentRun();
        run.setId(99L);
        run.setStatus("QUEUED");
        when(agentRunService.createOrReuse(any())).thenReturn(run);
        when(draftMapper.update(isNull(), any())).thenReturn(0);

        BizException conflict = assertThrows(BizException.class, () -> service.confirm("project-a", 5L,
                new ProjectTaskDraftDtos.ConfirmTaskDraftRequest(3L, null, 0, null)));

        assertEquals(409, conflict.getCode());
        assertEquals(ErrorCode.TASK_DRAFT_VERSION_EXPIRED.getCode(), conflict.getCode());
    }

    @Test
    void confirmRejectsCancelledDraft() {
        when(draftMapper.selectOne(any())).thenReturn(draft(5L, "project-a", "CANCELLED", 1));

        BizException changed = assertThrows(BizException.class, () -> service.confirm("project-a", 5L,
                new ProjectTaskDraftDtos.ConfirmTaskDraftRequest(3L, null, 1, null)));

        assertEquals(409, changed.getCode());
        verify(agentRunService, never()).createOrReuse(any());
    }

    @Test
    void missingExecutePermissionFailsClosed() {
        TenantContext.set(new TenantContext.Context(1L, "viewer", 1L, "tenant-1", false,
                Set.of(), Set.of(VIEW)));
        doCallRealMethod().when(memoryProjectService).requireWorkspacePermission(anyString());

        BizException denied = assertThrows(BizException.class, () -> service.create("project-a",
                new ProjectTaskDraftDtos.CreateTaskDraftRequest("Task", null, null, null, null, null, null)));

        assertEquals(403, denied.getCode());
        verifyNoInteractions(draftMapper);
    }

    @Test
    void missingViewPermissionFailsClosedOnList() {
        when(memoryProjectService.requireAccessibleWorkspaceProject("project-a"))
                .thenThrow(ErrorCode.PROJECT_ACTIVE_MEMBER_REQUIRED.ex());

        BizException denied = assertThrows(BizException.class, () -> service.list("project-a"));

        assertEquals(403, denied.getCode());
        verifyNoInteractions(draftMapper);
    }

    @Test
    void draftFromAnotherProjectOrTenantIsNotFound() {
        when(draftMapper.selectOne(any())).thenReturn(null);

        BizException missing = assertThrows(BizException.class, () -> service.cancel("project-a", 5L,
                new ProjectTaskDraftDtos.CancelTaskDraftRequest(0, null)));

        assertEquals(404, missing.getCode());
        Wrapper<?> wrapper = captureSelectWrapper();
        assertTrue(params(wrapper).containsValue("project-a"));
        assertTrue(params(wrapper).containsValue(1L));
    }

    private ProjectTaskDraft draft(Long id, String projectKey, String status, int version) {
        ProjectTaskDraft draft = new ProjectTaskDraft();
        draft.setId(id);
        draft.setTenantId(1L);
        draft.setProjectKey(projectKey);
        draft.setSourceType("MANUAL");
        draft.setTitle("Task " + id);
        draft.setStatus(status);
        draft.setVersion(version);
        draft.setCreatedBy(1L);
        draft.setCreateTime(LocalDateTime.now());
        draft.setUpdateTime(LocalDateTime.now());
        return draft;
    }

    private ProjectCollaborationDtos.AssignmentView assignment(Long id, String targetType, Long targetId) {
        return new ProjectCollaborationDtos.AssignmentView(id, "project-a", targetType, targetId, 2L,
                "Member 2", "ASSIGNED", 0, LocalDateTime.now());
    }

    private ProjectTaskDraft captureInsertedDraft() {
        ArgumentCaptor<ProjectTaskDraft> captor = ArgumentCaptor.forClass(ProjectTaskDraft.class);
        verify(draftMapper).insert(captor.capture());
        return captor.getValue();
    }

    private Wrapper<?> captureListWrapper() {
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(draftMapper).selectList(captor.capture());
        return captor.getValue();
    }

    private Wrapper<?> captureSelectWrapper() {
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(draftMapper).selectOne(captor.capture());
        return captor.getValue();
    }

    private Wrapper<?> captureUpdateWrapper() {
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(draftMapper).update(isNull(), captor.capture());
        return captor.getValue();
    }

    private Map<String, Object> params(Wrapper<?> wrapper) {
        // Conditions and set-values populate their parameter map lazily when SQL is rendered.
        wrapper.getTargetSql();
        wrapper.getSqlSet();
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs();
    }
}
