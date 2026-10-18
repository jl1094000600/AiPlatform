package com.aipal.service;

import com.aipal.common.BizException;
import com.aipal.common.ErrorCode;
import com.aipal.dto.ProjectModelGatewayDtos.PrivateAskResult;
import com.aipal.dto.ProjectModelGatewayDtos.PrivateAskSource;
import com.aipal.dto.ProjectModelGatewayDtos.PrivateAskTurn;
import com.aipal.dto.ProjectModelGatewayDtos.SafeProjectContext;
import com.aipal.dto.ProjectPrivateAskDtos.ConvertPrivateAskRequest;
import com.aipal.dto.ProjectPrivateAskDtos.PrivateAskRequest;
import com.aipal.dto.ProjectPrivateAskDtos.PrivateAskResponse;
import com.aipal.dto.ProjectTaskDraftDtos.CreateTaskDraftRequest;
import com.aipal.dto.ProjectTaskDraftDtos.TaskDraftView;
import com.aipal.entity.AiMemoryProject;
import com.aipal.security.TenantContext;
import com.aipal.service.memory.MemoryProjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectPrivateAskServiceTest {

    private static final String VIEW = "project-workspace:view";
    private static final String EXECUTE = "project-workspace:execute";
    private static final String PROJECT_KEY = "project-a";

    private ProjectWorkspaceContextService workspaceContextService;
    private ProjectCollaborationModelService collaborationModelService;
    private ProjectTaskDraftService projectTaskDraftService;
    private MemoryProjectService memoryProjectService;
    private ProjectPrivateAskService service;
    private SafeProjectContext context;

    @BeforeEach
    void setUp() {
        TenantContext.set(new TenantContext.Context(1L, "alice", 1L, "tenant-1", false,
                Set.of(), Set.of(VIEW, EXECUTE)));
        workspaceContextService = mock(ProjectWorkspaceContextService.class);
        collaborationModelService = mock(ProjectCollaborationModelService.class);
        projectTaskDraftService = mock(ProjectTaskDraftService.class);
        memoryProjectService = mock(MemoryProjectService.class);
        service = new ProjectPrivateAskService(workspaceContextService, collaborationModelService,
                projectTaskDraftService, memoryProjectService);

        // The real permission helper only reads the request context, so it can run without a DB.
        doCallRealMethod().when(memoryProjectService).requireWorkspacePermission(anyString());
        AiMemoryProject project = new AiMemoryProject();
        project.setId(7L);
        project.setTenantId(1L);
        project.setProjectKey(PROJECT_KEY);
        project.setStatus("ACTIVE");
        when(memoryProjectService.requireAccessibleWorkspaceProject(anyString())).thenReturn(project);

        context = new SafeProjectContext(PROJECT_KEY, "Project A", "CUSTOMER_PROJECT", "ACTIVE", 9L,
                "GREEN", 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(workspaceContextService.build(anyString())).thenReturn(context);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void askReturnsPrivateAnswerWithoutTouchingAnyWritePath() {
        List<PrivateAskTurn> requestHistory = List.of(
                new PrivateAskTurn("user", "  what changed?  "),
                new PrivateAskTurn("ASSISTANT", "  a little  "));
        PrivateAskResult modelResult = new PrivateAskResult("only answer",
                List.of(new PrivateAskSource("PROJECT_SUMMARY", "summary")), List.of("limitation"));
        when(collaborationModelService.privateAsk(context, "what is the status?",
                List.of(new PrivateAskTurn("USER", "what changed?"),
                        new PrivateAskTurn("ASSISTANT", "a little")))).thenReturn(modelResult);

        PrivateAskResponse response = service.ask(PROJECT_KEY,
                new PrivateAskRequest("  what is the status?  ", requestHistory));

        assertEquals("only answer", response.answer());
        assertEquals(List.of(new PrivateAskSource("PROJECT_SUMMARY", "summary")), response.sources());
        assertEquals(List.of("limitation"), response.limitations());
        // The conversation history never reaches the project context builder.
        verify(workspaceContextService).build(PROJECT_KEY);
        verify(collaborationModelService).privateAsk(context, "what is the status?",
                List.of(new PrivateAskTurn("USER", "what changed?"),
                        new PrivateAskTurn("ASSISTANT", "a little")));
        // Ask only performs the read-only permission check; no membership load and no writes.
        verify(memoryProjectService).requireWorkspacePermission(VIEW);
        verify(memoryProjectService, never()).requireAccessibleWorkspaceProject(anyString());
        verifyNoInteractions(projectTaskDraftService);
    }

    @Test
    void askDoesNotUseAnyWriteMapperAndIsNotTransactional() throws Exception {
        assertTrue(Arrays.stream(ProjectPrivateAskService.class.getDeclaredFields())
                        .noneMatch(field -> field.getType().getName().startsWith("com.aipal.mapper.")),
                "Private Ask must not hold a write mapper");
        assertNull(ProjectPrivateAskService.class
                        .getMethod("ask", String.class, PrivateAskRequest.class)
                        .getAnnotation(Transactional.class),
                "ask must stay non-transactional because it performs no writes");
    }

    @Test
    void askResponseOnlyExposesAnswerSourcesAndLimitations() {
        assertEquals(List.of("answer", "sources", "limitations"),
                Arrays.stream(PrivateAskResponse.class.getRecordComponents())
                        .map(RecordComponent::getName).toList());
    }

    @Test
    void askAcceptsTwentyHistoryTurnsAndFourThousandCharacterBounds() {
        when(collaborationModelService.privateAsk(any(), anyString(), any())).thenReturn(modelResult());
        List<PrivateAskTurn> history = IntStream.range(0, 20)
                .mapToObj(index -> new PrivateAskTurn(index % 2 == 0 ? "user" : "assistant", "h".repeat(4_000)))
                .toList();
        List<PrivateAskTurn> expectedHistory = IntStream.range(0, 20)
                .mapToObj(index -> new PrivateAskTurn(index % 2 == 0 ? "USER" : "ASSISTANT", "h".repeat(4_000)))
                .toList();

        PrivateAskResponse response = service.ask(PROJECT_KEY,
                new PrivateAskRequest("q".repeat(4_000), history));

        assertEquals("answer", response.answer());
        verify(collaborationModelService).privateAsk(context, "q".repeat(4_000), expectedHistory);
    }

    @Test
    void askTreatsNullHistoryAsEmpty() {
        when(collaborationModelService.privateAsk(any(), anyString(), any())).thenReturn(modelResult());

        service.ask(PROJECT_KEY, new PrivateAskRequest("question", null));

        verify(collaborationModelService).privateAsk(context, "question", List.of());
    }

    @Test
    void askRejectsQuestionAndHistoryOverBounds() {
        List<PrivateAskTurn> twentyOneTurns = IntStream.range(0, 21)
                .mapToObj(index -> new PrivateAskTurn("USER", "turn"))
                .toList();

        assertBiz(ErrorCode.PRIVATE_ASK_QUESTION_REQUIRED, () -> service.ask(PROJECT_KEY, null));
        assertBiz(ErrorCode.PRIVATE_ASK_QUESTION_REQUIRED,
                () -> service.ask(PROJECT_KEY, new PrivateAskRequest("   ", List.of())));
        assertBiz(ErrorCode.PRIVATE_ASK_QUESTION_TOO_LONG,
                () -> service.ask(PROJECT_KEY, new PrivateAskRequest("q".repeat(4_001), List.of())));
        assertBiz(ErrorCode.PRIVATE_ASK_HISTORY_INVALID,
                () -> service.ask(PROJECT_KEY, new PrivateAskRequest("question", twentyOneTurns)));
        assertBiz(ErrorCode.PRIVATE_ASK_HISTORY_INVALID,
                () -> service.ask(PROJECT_KEY, new PrivateAskRequest("question",
                        List.of(new PrivateAskTurn("USER", "h".repeat(4_001))))));
        assertBiz(ErrorCode.PRIVATE_ASK_HISTORY_INVALID,
                () -> service.ask(PROJECT_KEY, new PrivateAskRequest("question",
                        List.of(new PrivateAskTurn("USER", "   ")))));
        assertBiz(ErrorCode.PRIVATE_ASK_HISTORY_INVALID,
                () -> service.ask(PROJECT_KEY, new PrivateAskRequest("question",
                        List.of(new PrivateAskTurn("SYSTEM", "hi")))));
        assertBiz(ErrorCode.PRIVATE_ASK_HISTORY_INVALID,
                () -> service.ask(PROJECT_KEY, new PrivateAskRequest("question",
                        Arrays.asList((PrivateAskTurn) null))));

        verifyNoInteractions(collaborationModelService, projectTaskDraftService);
    }

    @Test
    void askFailsWith403WithoutViewPermission() {
        TenantContext.set(new TenantContext.Context(1L, "alice", 1L, "tenant-1", false,
                Set.of(), Set.of()));

        BizException error = assertThrows(BizException.class,
                () -> service.ask(PROJECT_KEY, new PrivateAskRequest("question", List.of())));

        assertEquals(ErrorCode.PERMISSION_DENIED.getCode(), error.getCode());
        assertEquals(403, error.getCode());
        verifyNoInteractions(workspaceContextService, collaborationModelService, projectTaskDraftService);
    }

    @Test
    void askFailsClosedWhenMembershipIsRejectedByContextBuild() {
        when(workspaceContextService.build(PROJECT_KEY))
                .thenThrow(ErrorCode.PROJECT_ACTIVE_MEMBER_REQUIRED.ex());

        BizException error = assertThrows(BizException.class,
                () -> service.ask(PROJECT_KEY, new PrivateAskRequest("question", List.of())));

        assertEquals(ErrorCode.PROJECT_ACTIVE_MEMBER_REQUIRED.getCode(), error.getCode());
        assertEquals(403, error.getCode());
        verifyNoInteractions(collaborationModelService, projectTaskDraftService);
    }

    @Test
    void askPersistsNothingWhenTheModelFails() {
        when(collaborationModelService.privateAsk(any(), anyString(), any()))
                .thenThrow(ErrorCode.MODEL_CALL_FAILED.ex());

        BizException error = assertThrows(BizException.class,
                () -> service.ask(PROJECT_KEY, new PrivateAskRequest("question", List.of())));

        assertEquals(ErrorCode.MODEL_CALL_FAILED.getCode(), error.getCode());
        assertEquals(502, error.getCode());
        verify(memoryProjectService).requireWorkspacePermission(VIEW);
        verify(memoryProjectService, never()).requireAccessibleWorkspaceProject(anyString());
        verifyNoInteractions(projectTaskDraftService);
    }

    @Test
    void convertCreatesDraftFromExplicitSummaryWithPrivateAskSource() {
        TaskDraftView view = new TaskDraftView(11L, PROJECT_KEY, "PRIVATE_ASK", null, "Fix login",
                "only the selected summary", 5L, 6L, "DRAFT", null, 0, null, null);
        when(projectTaskDraftService.create(eq(PROJECT_KEY), any())).thenReturn(view);

        TaskDraftView returned = service.convertToDraft("path-project-key",
                new ConvertPrivateAskRequest("  Fix login  ", "  only the selected summary  ",
                        5L, 6L, "key-1"));

        ArgumentCaptor<CreateTaskDraftRequest> captor = ArgumentCaptor.forClass(CreateTaskDraftRequest.class);
        verify(projectTaskDraftService).create(eq(PROJECT_KEY), captor.capture());
        CreateTaskDraftRequest saved = captor.getValue();
        assertEquals("Fix login", saved.title());
        assertEquals("only the selected summary", saved.description());
        assertEquals(5L, saved.suggestedAgentId());
        assertEquals(6L, saved.suggestedAssigneeUserId());
        assertEquals("PRIVATE_ASK", saved.sourceType());
        assertNull(saved.sourceId(), "private ask conversations have no persisted source id");
        assertEquals("key-1", saved.idempotencyKey());
        assertSame(view, returned);
        // Conversion never reads the private conversation or sends anything to the model.
        verifyNoInteractions(workspaceContextService, collaborationModelService);
        verify(memoryProjectService).requireWorkspacePermission(EXECUTE);
        verify(memoryProjectService).requireAccessibleWorkspaceProject("path-project-key");
    }

    @Test
    void convertRejectsInvalidTitleAndDescription() {
        assertBiz(ErrorCode.TASK_DRAFT_TITLE_REQUIRED,
                () -> service.convertToDraft(PROJECT_KEY, null));
        assertBiz(ErrorCode.TASK_DRAFT_TITLE_REQUIRED,
                () -> service.convertToDraft(PROJECT_KEY,
                        new ConvertPrivateAskRequest("   ", "description", null, null, null)));
        assertBiz(ErrorCode.TASK_DRAFT_TITLE_TOO_LONG,
                () -> service.convertToDraft(PROJECT_KEY,
                        new ConvertPrivateAskRequest("t".repeat(256), "description", null, null, null)));
        assertBiz(ErrorCode.TASK_DRAFT_DESCRIPTION_TOO_LONG,
                () -> service.convertToDraft(PROJECT_KEY,
                        new ConvertPrivateAskRequest("title", "d".repeat(4_001), null, null, null)));

        verifyNoInteractions(projectTaskDraftService);
    }

    @Test
    void convertRequiresExecuteEvenForAProjectViewer() {
        TenantContext.set(new TenantContext.Context(1L, "alice", 1L, "tenant-1", false,
                Set.of(), Set.of(VIEW)));

        BizException error = assertThrows(BizException.class,
                () -> service.convertToDraft(PROJECT_KEY,
                        new ConvertPrivateAskRequest("title", "description", null, null, null)));

        assertEquals(ErrorCode.PERMISSION_DENIED.getCode(), error.getCode());
        assertEquals(403, error.getCode());
        verify(memoryProjectService, never()).requireAccessibleWorkspaceProject(anyString());
        verifyNoInteractions(workspaceContextService, collaborationModelService, projectTaskDraftService);
    }

    private PrivateAskResult modelResult() {
        return new PrivateAskResult("answer", List.of(), List.of());
    }

    private void assertBiz(ErrorCode code, Executable executable) {
        BizException error = assertThrows(BizException.class, executable);
        assertEquals(code.getCode(), error.getCode(), () -> "expected " + code + " but got " + error.getMessage());
    }
}
