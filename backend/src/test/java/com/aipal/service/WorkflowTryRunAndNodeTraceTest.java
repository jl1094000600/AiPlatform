package com.aipal.service;

import com.aipal.common.BizException;
import com.aipal.common.ErrorCode;
import com.aipal.dto.A2AMessage;
import com.aipal.dto.WorkflowTryRunDtos;
import com.aipal.entity.AiAgent;
import com.aipal.entity.Workflow;
import com.aipal.entity.WorkflowExecution;
import com.aipal.entity.WorkflowNodeRun;
import com.aipal.entity.WorkflowVersion;
import com.aipal.mapper.WorkflowExecutionMapper;
import com.aipal.mapper.WorkflowMapper;
import com.aipal.mapper.WorkflowNodeRunMapper;
import com.aipal.security.TenantContext;
import com.aipal.service.memory.MemoryOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T20 try-run API + T21 node-level trace persistence.
 */
class WorkflowTryRunAndNodeTraceTest {

    private static final String SIMPLE_DEFINITION = """
            {"timeoutSeconds":5,"nodes":[
              {"id":"start","type":"START"},
              {"id":"agent","type":"AGENT","targetAgent":"writer"},
              {"id":"end","type":"END"}
            ],"edges":[
              {"source":"start","target":"agent"},
              {"source":"agent","target":"end"}
            ]}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<WorkflowExecution> storedExecution = new AtomicReference<>();
    private final List<WorkflowNodeRun> nodeRuns = new ArrayList<>();
    private final AtomicLong nodeRunIds = new AtomicLong(0);
    private final AtomicLong idSeq = new AtomicLong(100);

    private WorkflowMapper workflowMapper;
    private WorkflowExecutionMapper executionMapper;
    private WorkflowNodeRunMapper nodeRunMapper;
    private WorkflowVersionService versionService;
    private AgentRegistry agentRegistry;
    private WorkflowExecutionServiceImpl service;
    private Workflow workflow;

    @BeforeEach
    void setUp() {
        TenantContext.set(new TenantContext.Context(
                7L, "tester", 42L, "tenant-42", false, java.util.Set.of(), java.util.Set.of()));

        workflow = new Workflow();
        workflow.setId(1L);
        workflow.setWorkflowName("try-run workflow");
        workflow.setWorkflowDefinition(SIMPLE_DEFINITION);
        workflow.setStatus(1);
        workflow.setTriggerCount(0);

        workflowMapper = mock(WorkflowMapper.class);
        executionMapper = mock(WorkflowExecutionMapper.class);
        nodeRunMapper = mock(WorkflowNodeRunMapper.class);
        versionService = mock(WorkflowVersionService.class);
        agentRegistry = mock(AgentRegistry.class);

        when(workflowMapper.selectById(1L)).thenReturn(workflow);
        when(workflowMapper.updateById(any())).thenReturn(1);
        when(executionMapper.insert(any())).thenAnswer(invocation -> {
            storedExecution.set(invocation.getArgument(0));
            return 1;
        });
        when(executionMapper.updateById(any())).thenAnswer(invocation -> {
            storedExecution.set(invocation.getArgument(0));
            return 1;
        });
        when(executionMapper.selectOne(any())).thenAnswer(invocation -> storedExecution.get());
        when(nodeRunMapper.insert(any(WorkflowNodeRun.class))).thenAnswer(invocation -> {
            WorkflowNodeRun run = invocation.getArgument(0);
            run.setId(nodeRunIds.incrementAndGet());
            nodeRuns.add(run);
            return 1;
        });
        when(nodeRunMapper.updateById(any(WorkflowNodeRun.class))).thenReturn(1);
        when(nodeRunMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(nodeRuns));

        AgentRegistry.AgentContext agentContext = new AgentRegistry.AgentContext();
        AiAgent agent = new AiAgent();
        agent.setId(3L);
        agent.setAgentCode("writer");
        agentContext.setAgent(agent);
        agentContext.setHandler(message -> A2AMessage.builder().payload(Map.of("draft", "done")).build());
        when(agentRegistry.getAgent("writer")).thenReturn(agentContext);

        service = new WorkflowExecutionServiceImpl(
                executionMapper,
                workflowMapper,
                objectMapper,
                mock(A2AMessageService.class),
                agentRegistry,
                new WorkflowDefinitionService(objectMapper),
                versionService,
                mock(WorkflowAgentRunAdapter.class),
                mock(MemoryOrchestrator.class),
                nodeRunMapper);
    }

    @AfterEach
    void tearDown() {
        if (service != null) service.shutdown();
        TenantContext.clear();
    }

    @Test
    void tryRunUsesPublishedSnapshotAndReturnsPerNodeResults() {
        when(versionService.latestActive(1L)).thenReturn(snapshot(SIMPLE_DEFINITION, "hash-1"));

        WorkflowTryRunDtos.TryRunResult result = service.tryRun(1L,
                new WorkflowTryRunDtos.TryRunRequest(Map.of("topic", "hello"), null));

        assertNotNull(result.executionId());
        assertEquals("DRY_COMPLETED", result.status());
        assertNull(result.errorMessage());
        assertEquals(3, result.nodeRuns().size());
        assertEquals(List.of("start", "agent", "end"),
                result.nodeRuns().stream().map(WorkflowTryRunDtos.NodeResult::nodeId).toList());
        result.nodeRuns().forEach(node -> {
            assertEquals("COMPLETED", node.status());
            assertNotNull(node.durationMs());
            assertTrue(node.durationMs() >= 0);
            assertNull(node.error());
        });
        WorkflowTryRunDtos.NodeResult agentNode = result.nodeRuns().get(1);
        assertEquals("done", agentNode.outputs().get("draft"));
        assertEquals("end", result.result().get("completedNode"));
        // Dry-run must not touch production trigger metrics.
        assertNull(workflow.getLastTriggerTime());
        assertEquals(0, workflow.getTriggerCount());
        assertEquals("DRY_RUN", storedExecution.get().getTriggerType());
        assertEquals("DRY_COMPLETED", storedExecution.get().getStatus());
    }

    @Test
    void tryRunAcceptsDraftDefinitionJsonWithoutPersistingIt() {
        String draft = """
                {"timeoutSeconds":5,"nodes":[
                  {"id":"start","type":"START"},
                  {"id":"end","type":"END"}
                ],"edges":[{"source":"start","target":"end"}]}
                """;
        when(versionService.canonicalHash(draft)).thenReturn("draft-hash");
        when(versionService.latestActive(1L)).thenReturn(null);

        WorkflowTryRunDtos.TryRunResult result = service.tryRun(1L,
                new WorkflowTryRunDtos.TryRunRequest(Map.of("input", 1), draft));

        assertEquals("DRY_COMPLETED", result.status());
        assertEquals(2, result.nodeRuns().size());
        assertEquals("draft-hash", storedExecution.get().getDefinitionHash());
        assertNull(storedExecution.get().getVersionId());
        // Draft definition must not be written back to the workflow row.
        assertEquals(SIMPLE_DEFINITION, workflow.getWorkflowDefinition());
    }

    @Test
    void tryRunRejectsMissingDefinitionWithErrorCode() {
        when(versionService.latestActive(1L)).thenReturn(null);

        BizException error = assertThrows(BizException.class,
                () -> service.tryRun(1L, new WorkflowTryRunDtos.TryRunRequest(Map.of(), null)));

        assertEquals(ErrorCode.WORKFLOW_TRY_RUN_NO_DEFINITION.getCode(), error.getCode());
        assertEquals(ErrorCode.WORKFLOW_TRY_RUN_NO_DEFINITION.getMessage(), error.getMessage());
        assertNull(storedExecution.get());
    }

    @Test
    void tryRunRecordsNodeRunDigestsWithoutRawValues() {
        when(versionService.latestActive(1L)).thenReturn(snapshot(SIMPLE_DEFINITION, "hash-1"));

        service.tryRun(1L, new WorkflowTryRunDtos.TryRunRequest(Map.of("secret", "password"), null));

        assertFalse(nodeRuns.isEmpty());
        WorkflowNodeRun start = nodeRuns.stream()
                .filter(run -> "start".equals(run.getNodeId())).findFirst().orElseThrow();
        WorkflowNodeRun agent = nodeRuns.stream()
                .filter(run -> "agent".equals(run.getNodeId())).findFirst().orElseThrow();
        assertEquals("START", start.getNodeType());
        assertEquals("COMPLETED", start.getStatus());
        assertEquals(1, start.getAttempt());
        assertNotNull(start.getInputDigest());
        assertEquals(64, start.getInputDigest().length());
        assertEquals("COMPLETED", agent.getStatus());
        assertNotNull(agent.getOutputDigest());
        assertEquals(64, agent.getOutputDigest().length());
        assertNotNull(agent.getDurationMs());
        // Digests only — raw secret/output values never land in the trace row.
        assertNull(agent.getErrorMessage());
    }

    @Test
    void failedAgentNodeIsStampedFailedInNodeRun() {
        String failing = """
                {"timeoutSeconds":5,"nodes":[
                  {"id":"start","type":"START"},
                  {"id":"agent","type":"AGENT","targetAgent":"writer"},
                  {"id":"end","type":"END"}
                ],"edges":[
                  {"source":"start","target":"agent"},
                  {"source":"agent","target":"end"}
                ]}
                """;
        when(versionService.latestActive(1L)).thenReturn(snapshot(failing, "hash-2"));
        AgentRegistry.AgentContext broken = new AgentRegistry.AgentContext();
        AiAgent agent = new AiAgent();
        agent.setId(3L);
        agent.setAgentCode("writer");
        broken.setAgent(agent);
        broken.setHandler(message -> {
            throw new IllegalStateException("boom");
        });
        when(agentRegistry.getAgent("writer")).thenReturn(broken);

        WorkflowTryRunDtos.TryRunResult result = service.tryRun(1L,
                new WorkflowTryRunDtos.TryRunRequest(Map.of(), null));

        assertEquals("DRY_FAILED", result.status());
        assertNotNull(result.errorMessage());
        WorkflowTryRunDtos.NodeResult agentNode = result.nodeRuns().stream()
                .filter(n -> "agent".equals(n.nodeId())).findFirst().orElseThrow();
        assertEquals("FAILED", agentNode.status());
        assertNotNull(agentNode.error());

        WorkflowNodeRun failed = nodeRuns.stream()
                .filter(run -> "agent".equals(run.getNodeId())).findFirst().orElseThrow();
        assertEquals("FAILED", failed.getStatus());
        assertNotNull(failed.getErrorMessage());
        assertNotNull(failed.getDurationMs());
    }

    @Test
    void listNodeRunsReturnsTracesForExecution() {
        when(versionService.latestActive(1L)).thenReturn(snapshot(SIMPLE_DEFINITION, "hash-1"));
        WorkflowTryRunDtos.TryRunResult result = service.tryRun(1L,
                new WorkflowTryRunDtos.TryRunRequest(Map.of(), null));

        List<WorkflowTryRunDtos.NodeRunView> views = service.listNodeRuns(result.executionId());

        assertEquals(3, views.size());
        assertEquals(List.of("start", "agent", "end"),
                views.stream().map(WorkflowTryRunDtos.NodeRunView::nodeId).toList());
        views.forEach(view -> {
            assertEquals(result.executionId(), view.executionId());
            assertEquals("COMPLETED", view.status());
            assertNotNull(view.inputDigest());
        });
    }

    @Test
    void listNodeRunsRejectsUnknownExecutionWithErrorCode() {
        storedExecution.set(null);

        BizException error = assertThrows(BizException.class,
                () -> service.listNodeRuns("missing-exec"));

        assertEquals(ErrorCode.WORKFLOW_EXECUTION_NOT_FOUND.getCode(), error.getCode());
    }

    @Test
    void tryRunConditionBranchIsTraced() {
        String conditional = """
                {"timeoutSeconds":5,"nodes":[
                  {"id":"start","type":"START"},
                  {"id":"check","type":"CONDITION","conditionField":"approved","operator":"EQ","conditionValue":true},
                  {"id":"agent","type":"AGENT","targetAgent":"writer"},
                  {"id":"end","type":"END"}
                ],"edges":[
                  {"source":"start","target":"check"},
                  {"source":"check","target":"agent","condition":"true"},
                  {"source":"check","target":"end","condition":"false"},
                  {"source":"agent","target":"end"}
                ]}
                """;
        when(versionService.latestActive(1L)).thenReturn(snapshot(conditional, "hash-3"));

        WorkflowTryRunDtos.TryRunResult approved = service.tryRun(1L,
                new WorkflowTryRunDtos.TryRunRequest(Map.of("approved", true), null));
        assertEquals("DRY_COMPLETED", approved.status());
        assertEquals(4, approved.nodeRuns().size());
        assertTrue(approved.nodeRuns().stream().anyMatch(n -> "agent".equals(n.nodeId())));

        nodeRuns.clear();
        storedExecution.set(null);
        WorkflowTryRunDtos.TryRunResult rejected = service.tryRun(1L,
                new WorkflowTryRunDtos.TryRunRequest(Map.of("approved", false), null));
        assertEquals("DRY_COMPLETED", rejected.status());
        assertEquals(3, rejected.nodeRuns().size());
        assertTrue(rejected.nodeRuns().stream().noneMatch(n -> "agent".equals(n.nodeId())));
    }

    private WorkflowVersion snapshot(String definitionJson, String hash) {
        WorkflowVersion version = new WorkflowVersion();
        version.setId(idSeq.incrementAndGet());
        version.setWorkflowId(1L);
        version.setVersionNo(1);
        version.setDefinitionJson(definitionJson);
        version.setDefinitionHash(hash);
        version.setStatus("ACTIVE");
        return version;
    }
}
