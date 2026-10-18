package com.aipal.service;

import com.aipal.common.BizException;
import com.aipal.common.ErrorCode;
import com.aipal.dto.ProjectDiscussionDtos.CoordinatorRequest;
import com.aipal.dto.ProjectDiscussionDtos.CoordinatorResponse;
import com.aipal.dto.ProjectDiscussionDtos.DiscussionMessageView;
import com.aipal.dto.ProjectDiscussionDtos.PostDiscussionRequest;
import com.aipal.dto.ProjectModelGatewayDtos.CoordinatorResult;
import com.aipal.dto.ProjectModelGatewayDtos.GeneratedTaskDraft;
import com.aipal.dto.ProjectModelGatewayDtos.SafeProjectContext;
import com.aipal.dto.ProjectTaskDraftDtos.CreateTaskDraftRequest;
import com.aipal.dto.ProjectTaskDraftDtos.TaskDraftView;
import com.aipal.entity.AiMemoryProject;
import com.aipal.entity.ProjectDiscussionMessage;
import com.aipal.mapper.ProjectDiscussionMessageMapper;
import com.aipal.security.TenantContext;
import com.aipal.service.memory.MemoryProjectService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectDiscussionServiceTest {

    private static final String VIEW = "project-workspace:view";
    private static final String EXECUTE = "project-workspace:execute";
    private static final ObjectMapper JSON = new ObjectMapper();

    private MemoryProjectService memoryProjectService;
    private ProjectDiscussionMessageMapper messageMapper;
    private ProjectWorkspaceContextService contextService;
    private ProjectCollaborationModelService modelService;
    private ProjectTaskDraftService draftService;
    private TransactionTemplate transactionTemplate;
    private ProjectDiscussionService service;
    private AiMemoryProject project;

    @BeforeAll
    static void initMybatisMetadata() {
        // Lambda wrappers resolve entity columns through MyBatis-Plus global metadata.
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "discussion-service-test"),
                ProjectDiscussionMessage.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(new TenantContext.Context(1L, "alice", 1L, "tenant-1", false,
                Set.of(), Set.of(VIEW, EXECUTE)));
        memoryProjectService = mock(MemoryProjectService.class);
        messageMapper = mock(ProjectDiscussionMessageMapper.class);
        contextService = mock(ProjectWorkspaceContextService.class);
        modelService = mock(ProjectCollaborationModelService.class);
        draftService = mock(ProjectTaskDraftService.class);
        transactionTemplate = mock(TransactionTemplate.class);
        project = new AiMemoryProject();
        project.setId(7L);
        project.setTenantId(1L);
        project.setProjectKey("project-a");
        project.setStatus("ACTIVE");

        when(memoryProjectService.requireAccessibleWorkspaceProject(anyString())).thenReturn(project);
        // Default transaction template mock runs the callback inline; individual tests can assert
        // that the Coordinator writes happen inside this callback.
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        service = new ProjectDiscussionService(memoryProjectService, messageMapper, contextService,
                modelService, draftService, transactionTemplate, JSON);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listRejectsLimitOutsideOneToFifty() {
        assertError(400, () -> service.list("project-a", 0));
        assertError(400, () -> service.list("project-a", 51));
        verifyNoInteractions(messageMapper);
    }

    @Test
    void listUsesTrustedProjectKeyAndTenantThenReturnsAscending() {
        LocalDateTime base = LocalDateTime.of(2026, 7, 28, 10, 0);
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message(4L, 1L, "project-a", "TEXT", "fourth", base.plusMinutes(3)),
                message(3L, 1L, "project-a", "TEXT", "third", base.plusMinutes(2)),
                message(2L, 1L, "project-a", "TEXT", "second", base.plusMinutes(1)),
                message(1L, 2L, "project-a", "TEXT", "first", base)));

        List<DiscussionMessageView> views = service.list("untrusted-path-key", 3);

        // Newest three messages, displayed oldest-first.
        assertEquals(List.of(2L, 3L, 4L), views.stream().map(DiscussionMessageView::id).toList());
        Wrapper<?> wrapper = captureListWrapper();
        Map<String, Object> params = params(wrapper);
        assertTrue(params.containsValue(1L), "tenant id must scope the query");
        assertTrue(params.containsValue("project-a"), "trusted project key must scope the query");
        assertFalse(params.containsValue("untrusted-path-key"), "path key must not reach storage");
        assertTrue(wrapper.getTargetSql().contains("tenant_id"));
        assertTrue(wrapper.getTargetSql().contains("project_key"));
    }

    @Test
    void listDerivesSenderTypeAndExtractsSummaryText() throws Exception {
        ProjectDiscussionMessage summary = message(1L, null, "project-a", "SUMMARY",
                JSON.writeValueAsString(new CoordinatorResult("The summary", List.of(), List.of(), List.of())),
                LocalDateTime.now());
        summary.setSenderName("Coordinator");
        ProjectDiscussionMessage risk = message(2L, null, "project-a", "RISK", "Risk A", LocalDateTime.now());
        ProjectDiscussionMessage text = message(3L, 2L, "project-a", "TEXT", "hello", LocalDateTime.now());
        when(messageMapper.selectList(any())).thenReturn(List.of(summary, risk, text));

        List<DiscussionMessageView> views = service.list("project-a", 50);

        assertEquals("COORDINATOR", views.get(0).senderType());
        assertEquals("The summary", views.get(0).content());
        assertEquals("COORDINATOR", views.get(1).senderType());
        assertEquals("Risk A", views.get(1).content());
        assertEquals("USER", views.get(2).senderType());
        assertEquals("hello", views.get(2).content());
    }

    @Test
    void postPersistsTrimmedTextForCurrentUser() {
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(messageMapper.insert(any())).thenAnswer(invocation -> {
            ((ProjectDiscussionMessage) invocation.getArgument(0)).setId(11L);
            return 1;
        });

        DiscussionMessageView view = service.post("project-a",
                new PostDiscussionRequest("  hello world  ", " key-1 "));

        ProjectDiscussionMessage saved = captureInserted();
        assertEquals(1L, saved.getTenantId());
        assertEquals("project-a", saved.getProjectKey());
        assertEquals(1L, saved.getSenderUserId());
        assertEquals("alice", saved.getSenderName());
        assertEquals("TEXT", saved.getMessageType());
        assertEquals("hello world", saved.getContent());
        assertEquals("key-1", saved.getIdempotencyKey());
        assertEquals(0, saved.getIsDeleted());
        assertEquals(11L, view.id());
        assertEquals("USER", view.senderType());
        assertEquals("hello world", view.content());
        verify(memoryProjectService).requireWorkspacePermission(VIEW);
    }

    @Test
    void postValidatesContentAndIdempotencyKey() {
        assertError(400, () -> service.post("project-a", new PostDiscussionRequest("   ", null)));
        assertError(400, () -> service.post("project-a", new PostDiscussionRequest("x".repeat(4_001), null)));
        assertError(400, () -> service.post("project-a", new PostDiscussionRequest("hello", "k".repeat(129))));
        verifyNoInteractions(messageMapper);
    }

    @Test
    void postReplaysIdenticalContentForSameKeyAndRejectsDifferentContent() {
        ProjectDiscussionMessage existing = message(5L, 1L, "project-a", "TEXT", "hello",
                LocalDateTime.now());
        existing.setIdempotencyKey("key-1");
        when(messageMapper.selectOne(any())).thenReturn(existing);

        DiscussionMessageView replayed = service.post("project-a",
                new PostDiscussionRequest("hello", "key-1"));

        assertEquals(5L, replayed.id());
        verify(messageMapper, never()).insert(any());

        assertError(409, () -> service.post("project-a",
                new PostDiscussionRequest("different", "key-1")));
        verify(messageMapper, never()).insert(any());
    }

    @Test
    void postHandlesConcurrentDuplicateKeyByRecheckingTheWinner() {
        ProjectDiscussionMessage winner = message(5L, 1L, "project-a", "TEXT", "hello",
                LocalDateTime.now());
        winner.setIdempotencyKey("key-1");
        when(messageMapper.selectOne(any())).thenReturn(null, winner);
        when(messageMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));

        DiscussionMessageView replayed = service.post("project-a",
                new PostDiscussionRequest("hello", "key-1"));

        assertEquals(5L, replayed.id());
        verify(messageMapper, times(1)).insert(any());
    }

    @Test
    void postRequiresViewPermission() {
        when(memoryProjectService.requireAccessibleWorkspaceProject("project-a"))
                .thenThrow(ErrorCode.PROJECT_ACTIVE_MEMBER_REQUIRED.ex());

        assertError(403, () -> service.post("project-a", new PostDiscussionRequest("hello", null)));
        verifyNoInteractions(messageMapper);
    }

    @Test
    void coordinateWritesSummaryRisksSuggestionsAndDiscussionDrafts() throws Exception {
        SafeProjectContext context = context();
        CoordinatorResult result = new CoordinatorResult("Plan the release", List.of("Risk 1", "Risk 2"),
                List.of("Question 1"), List.of(
                new GeneratedTaskDraft("Task 1", "Do first", 4L),
                new GeneratedTaskDraft("Task 2", null, null)));
        when(contextService.build("project-a")).thenReturn(context);
        when(modelService.coordinate(context)).thenReturn(result);
        when(messageMapper.selectOne(any())).thenReturn(null);
        AtomicLong messageIds = new AtomicLong(100);
        when(messageMapper.insert(any())).thenAnswer(invocation -> {
            ((ProjectDiscussionMessage) invocation.getArgument(0)).setId(messageIds.getAndIncrement());
            return 1;
        });
        AtomicLong draftIds = new AtomicLong(200);
        when(draftService.create(eq("project-a"), any())).thenAnswer(invocation -> {
            CreateTaskDraftRequest request = invocation.getArgument(1);
            long id = draftIds.getAndIncrement();
            return new TaskDraftView(id, "project-a", request.sourceType(), request.sourceId(),
                    request.title(), request.description(), request.suggestedAgentId(), null,
                    "DRAFT", null, 0, LocalDateTime.now(), LocalDateTime.now());
        });

        CoordinatorResponse response = service.coordinate("untrusted-path-key",
                new CoordinatorRequest("focus hint", " coord-key "));

        assertFalse(response.reused());
        assertEquals("Plan the release", response.summary());
        assertEquals(List.of("Risk 1", "Risk 2"), response.risks());
        assertEquals(List.of("Question 1"), response.questions());
        assertEquals(2, response.taskDrafts().size());
        assertEquals(200L, response.taskDrafts().get(0).id());
        assertEquals(201L, response.taskDrafts().get(1).id());

        List<ProjectDiscussionMessage> writes = captureInsertedMessages(5);
        ProjectDiscussionMessage summary = writes.get(0);
        assertEquals("SUMMARY", summary.getMessageType());
        assertNull(summary.getSenderUserId());
        assertEquals("Coordinator", summary.getSenderName());
        assertEquals("coord-key", summary.getIdempotencyKey());
        assertEquals("project-a", summary.getProjectKey());
        assertEquals(0, summary.getIsDeleted());
        JsonNode summaryJson = JSON.readTree(summary.getContent());
        assertEquals("Plan the release", summaryJson.get("summary").asText());
        assertEquals(2, summaryJson.get("risks").size());

        assertEquals(List.of("RISK", "RISK", "TASK_SUGGESTION", "TASK_SUGGESTION"),
                writes.subList(1, 5).stream().map(ProjectDiscussionMessage::getMessageType).toList());
        assertEquals(List.of("coord-key:risk:0", "coord-key:risk:1",
                        "coord-key:suggestion:0", "coord-key:suggestion:1"),
                writes.subList(1, 5).stream().map(ProjectDiscussionMessage::getIdempotencyKey).toList());
        assertEquals(List.of("Risk 1", "Risk 2", "Task 1", "Task 2"),
                writes.subList(1, 5).stream().map(ProjectDiscussionMessage::getContent).toList());
        assertTrue(writes.subList(1, 5).stream().allMatch(message -> message.getSenderUserId() == null));
        assertTrue(writes.stream().noneMatch(message -> "COORDINATOR".equals(message.getMessageType())),
                "sender type is derived; COORDINATOR must never be a message type");

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<CreateTaskDraftRequest> draftRequests = ArgumentCaptor.forClass(CreateTaskDraftRequest.class);
        verify(draftService, times(2)).create(eq("project-a"), draftRequests.capture());
        List<CreateTaskDraftRequest> requests = draftRequests.getAllValues();
        assertEquals(List.of("coord-key:draft:0", "coord-key:draft:1"),
                requests.stream().map(CreateTaskDraftRequest::idempotencyKey).toList());
        assertTrue(requests.stream().allMatch(request -> "DISCUSSION".equals(request.sourceType())));
        assertTrue(requests.stream().allMatch(request -> Long.valueOf(100L).equals(request.sourceId())),
                "draft source id must be the SUMMARY message id");
        assertEquals("Task 1", requests.get(0).title());
        assertEquals("Do first", requests.get(0).description());
        assertEquals(4L, requests.get(0).suggestedAgentId());

        verify(modelService, times(1)).coordinate(context);
        verify(transactionTemplate, times(1)).execute(any());
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(modelService, transactionTemplate);
        order.verify(modelService).coordinate(context);
        order.verify(transactionTemplate).execute(any());
    }

    @Test
    void coordinateReplaysExistingSummaryWithoutCallingTheModel() throws Exception {
        CoordinatorResult previous = new CoordinatorResult("Previous summary", List.of("Previous risk"),
                List.of("Previous question"), List.of(new GeneratedTaskDraft("Previous task", "desc", 4L)));
        ProjectDiscussionMessage summary = message(100L, null, "project-a", "SUMMARY",
                JSON.writeValueAsString(previous), LocalDateTime.now());
        summary.setSenderName("Coordinator");
        summary.setIdempotencyKey("coord-key");
        when(messageMapper.selectOne(any())).thenReturn(summary);
        when(draftService.list("project-a")).thenReturn(List.of(
                draftView(201L, "project-a", 100L, "Previous task"),
                draftView(202L, "project-a", 999L, "Unrelated task")));

        CoordinatorResponse response = service.coordinate("project-a",
                new CoordinatorRequest(null, "coord-key"));

        assertTrue(response.reused());
        assertEquals("Previous summary", response.summary());
        assertEquals(List.of("Previous risk"), response.risks());
        assertEquals(List.of("Previous question"), response.questions());
        assertEquals(List.of(201L), response.taskDrafts().stream().map(TaskDraftView::id).toList());
        verifyNoInteractions(modelService, contextService);
        verify(messageMapper, never()).insert(any());
        verify(draftService, never()).create(anyString(), any());
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    void coordinateRejectsKeyReusedByNonSummaryMessage() {
        ProjectDiscussionMessage text = message(5L, 1L, "project-a", "TEXT", "hello", LocalDateTime.now());
        text.setIdempotencyKey("shared-key");
        when(messageMapper.selectOne(any())).thenReturn(text);

        assertError(409, () -> service.coordinate("project-a",
                new CoordinatorRequest(null, "shared-key")));
        verifyNoInteractions(modelService, contextService);
        verify(messageMapper, never()).insert(any());
    }

    @Test
    void coordinateRequiresExecutePermission() {
        TenantContext.set(new TenantContext.Context(1L, "viewer", 1L, "tenant-1", false,
                Set.of(), Set.of(VIEW)));
        doCallRealMethod().when(memoryProjectService).requireWorkspacePermission(anyString());

        assertError(403, () -> service.coordinate("project-a",
                new CoordinatorRequest(null, "coord-key")));
        verifyNoInteractions(contextService, modelService, messageMapper, draftService, transactionTemplate);
    }

    @Test
    void coordinateModelCallFailureWritesNothing() {
        when(contextService.build("project-a")).thenReturn(context());
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(modelService.coordinate(any())).thenThrow(ErrorCode.MODEL_CALL_FAILED.ex());

        assertError(502, () -> service.coordinate("project-a",
                new CoordinatorRequest(null, "coord-key")));

        verifyNoInteractions(transactionTemplate);
        verify(messageMapper, never()).insert(any());
        verify(draftService, never()).create(anyString(), any());
    }

    @Test
    void coordinateInvalidModelResponseWritesNothing() {
        when(contextService.build("project-a")).thenReturn(context());
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(modelService.coordinate(any())).thenThrow(ErrorCode.MODEL_RESPONSE_INVALID.ex());

        assertError(502, () -> service.coordinate("project-a",
                new CoordinatorRequest(null, "coord-key")));

        verifyNoInteractions(transactionTemplate);
        verify(messageMapper, never()).insert(any());
        verify(draftService, never()).create(anyString(), any());
    }

    @Test
    void coordinateRejectsOversizedSummaryJsonBeforeWriting() {
        List<GeneratedTaskDraft> drafts = java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> new GeneratedTaskDraft("Task " + index, "d".repeat(4_000), null))
                .toList();
        CoordinatorResult huge = new CoordinatorResult("s".repeat(4_000), List.of(), List.of(), drafts);
        when(contextService.build("project-a")).thenReturn(context());
        when(modelService.coordinate(any())).thenReturn(huge);
        when(messageMapper.selectOne(any())).thenReturn(null);

        assertError(502, () -> service.coordinate("project-a",
                new CoordinatorRequest(null, "big-key")));

        verifyNoInteractions(transactionTemplate);
        verify(messageMapper, never()).insert(any());
        verify(draftService, never()).create(anyString(), any());
    }

    @Test
    void coordinateRejectsOversizedIdempotencyKeyBeforeCallingTheModel() {
        assertError(400, () -> service.coordinate("project-a",
                new CoordinatorRequest(null, "k".repeat(129))));
        verifyNoInteractions(contextService, modelService, messageMapper, draftService, transactionTemplate);
    }

    @Test
    void coordinateDraftFailureRollsBackTheWriteTransaction() {
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(txManager.getTransaction(any())).thenReturn(status);
        TransactionTemplate realTemplate = new TransactionTemplate(txManager);
        service = new ProjectDiscussionService(memoryProjectService, messageMapper, contextService,
                modelService, draftService, realTemplate, JSON);

        SafeProjectContext context = context();
        when(contextService.build("project-a")).thenReturn(context);
        when(modelService.coordinate(context)).thenReturn(new CoordinatorResult("Summary",
                List.of("Risk"), List.of(), List.of(new GeneratedTaskDraft("Task", null, null))));
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(draftService.create(anyString(), any())).thenThrow(ErrorCode.TASK_DRAFT_STATUS_CHANGED.ex());

        assertError(409, () -> service.coordinate("project-a",
                new CoordinatorRequest(null, "coord-key")));

        verify(txManager).rollback(status);
        verify(txManager, never()).commit(status);
    }

    private SafeProjectContext context() {
        return new SafeProjectContext("project-a", "Project A", "CUSTOMER_PROJECT", "ACTIVE", 1L,
                "GREEN", 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private TaskDraftView draftView(Long id, String projectKey, Long sourceId, String title) {
        return new TaskDraftView(id, projectKey, "DISCUSSION", sourceId, title, null, null, null,
                "DRAFT", null, 0, LocalDateTime.now(), LocalDateTime.now());
    }

    private ProjectDiscussionMessage message(Long id, Long senderUserId, String projectKey,
                                            String messageType, String content, LocalDateTime createTime) {
        ProjectDiscussionMessage message = new ProjectDiscussionMessage();
        message.setId(id);
        message.setTenantId(1L);
        message.setProjectKey(projectKey);
        message.setSenderUserId(senderUserId);
        message.setSenderName(senderUserId == null ? "Coordinator" : "Alice");
        message.setMessageType(messageType);
        message.setContent(content);
        message.setCreateTime(createTime);
        message.setUpdateTime(createTime);
        message.setIsDeleted(0);
        return message;
    }

    private ProjectDiscussionMessage captureInserted() {
        ArgumentCaptor<ProjectDiscussionMessage> captor =
                ArgumentCaptor.forClass(ProjectDiscussionMessage.class);
        verify(messageMapper).insert(captor.capture());
        return captor.getValue();
    }

    private List<ProjectDiscussionMessage> captureInsertedMessages(int count) {
        ArgumentCaptor<ProjectDiscussionMessage> captor =
                ArgumentCaptor.forClass(ProjectDiscussionMessage.class);
        verify(messageMapper, times(count)).insert(captor.capture());
        return captor.getAllValues();
    }

    private Wrapper<?> captureListWrapper() {
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(messageMapper).selectList(captor.capture());
        return captor.getValue();
    }

    private Map<String, Object> params(Wrapper<?> wrapper) {
        wrapper.getTargetSql();
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs();
    }

    private void assertError(int code, Runnable action) {
        BizException failure = assertThrows(BizException.class, action::run);
        assertEquals(code, failure.getCode());
    }
}
