package com.aipal.service;

import com.aipal.dto.A2AMessage;
import com.aipal.entity.AiAgent;
import com.aipal.entity.Workflow;
import com.aipal.entity.WorkflowVersion;
import com.aipal.entity.WorkflowExecution;
import com.aipal.mapper.WorkflowExecutionMapper;
import com.aipal.mapper.WorkflowMapper;
import com.aipal.security.TenantContext;
import com.aipal.service.memory.MemoryOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkflowExecutionServiceImplTest {
    private WorkflowExecutionServiceImpl service;

    @BeforeEach
    void setTenantContext() {
        TenantContext.set(new TenantContext.Context(
                7L, "tester", 42L, "tenant-42", false, java.util.Set.of(), java.util.Set.of()));
    }

    @AfterEach
    void tearDown() {
        if (service != null) service.shutdown();
        TenantContext.clear();
    }

    @Test
    void triggerRunsWorkflowAsynchronouslyToCompletion() throws Exception {
        AtomicReference<WorkflowExecution> stored = new AtomicReference<>();
        Workflow workflow = workflow("""
                {"timeoutSeconds":5,"nodes":[
                  {"id":"start","type":"START"},
                  {"id":"end","type":"END"}
                ],"edges":[{"source":"start","target":"end"}]}
                """);
        service = createService(workflow, stored, mock(AgentRegistry.class));

        String executionId = service.triggerWorkflow(1L, "MANUAL", Map.of("input", "hello"));
        WorkflowExecution completed = awaitStatus(stored, "COMPLETED", 2000);

        assertNotNull(executionId);
        assertEquals(executionId, completed.getExecutionId());
        assertNotNull(completed.getResult());
        assertNotNull(completed.getEndTime());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void runsFourNodePocWorkflowWithCondition(boolean approved) throws Exception {
        AtomicReference<WorkflowExecution> stored = new AtomicReference<>();
        Workflow workflow = workflow("""
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
                """);
        AgentRegistry registry = mock(AgentRegistry.class);
        AgentRegistry.AgentContext context = new AgentRegistry.AgentContext();
        AiAgent agent = new AiAgent();
        agent.setId(3L);
        agent.setAgentCode("writer");
        context.setAgent(agent);
        context.setHandler(message -> A2AMessage.builder().payload(Map.of("draft", "done")).build());
        when(registry.getAgent("writer")).thenReturn(context);
        service = createService(workflow, stored, registry);

        service.triggerWorkflow(1L, "MANUAL", Map.of("approved", approved));
        WorkflowExecution completed = awaitStatus(stored, "COMPLETED", 2000);

        var result = new ObjectMapper().readTree(completed.getResult());
        assertEquals("end", result.path("completedNode").asText());
        if (approved) {
            assertEquals("done", result.path("agent").path("draft").asText());
            verify(registry).getAgent("writer");
        } else {
            assertTrue(result.path("agent").isMissingNode());
            verify(registry, never()).getAgent("writer");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PARALLEL", "JOIN"})
    void triggerRejectsLegacyParallelWorkflowBeforeCreatingExecution(String type) {
        AtomicReference<WorkflowExecution> stored = new AtomicReference<>();
        Workflow workflow = workflow(deferredDefinition(type));
        AgentRegistry registry = mock(AgentRegistry.class);
        service = createService(workflow, stored, registry);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.triggerWorkflow(1L, "MANUAL", Map.of()));

        assertTrue(error.getMessage().contains("JOIN barrier"));
        assertNull(stored.get());
        assertNull(workflow.getLastTriggerTime());
        assertEquals(0, workflow.getTriggerCount());
        verifyNoInteractions(registry);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PARALLEL", "JOIN"})
    void startRejectsPreviouslyCreatedLegacyParallelExecution(String type) {
        WorkflowExecution pending = new WorkflowExecution();
        pending.setExecutionId("legacy-execution");
        pending.setWorkflowId(1L);
        pending.setStatus("PENDING");
        AtomicReference<WorkflowExecution> stored = new AtomicReference<>(pending);
        AgentRegistry registry = mock(AgentRegistry.class);
        service = createService(workflow(deferredDefinition(type)), stored, registry);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.startExecution("legacy-execution"));

        assertTrue(error.getMessage().contains("JOIN barrier"));
        assertEquals("PENDING", stored.get().getStatus());
        assertNull(stored.get().getStartTime());
        verifyNoInteractions(registry);
    }

    @Test
    void startRejectsExecutionWhenSnapshotHashMismatch() {
        WorkflowExecution pending = new WorkflowExecution();
        pending.setExecutionId("bound-execution");
        pending.setWorkflowId(1L);
        pending.setVersionId(11L);
        pending.setDefinitionHash("stale-hash");
        pending.setStatus("PENDING");
        AtomicReference<WorkflowExecution> stored = new AtomicReference<>(pending);
        service = createService(workflow(simpleDefinition()), stored, mock(AgentRegistry.class));

        com.aipal.common.BizException error = assertThrows(com.aipal.common.BizException.class,
                () -> service.startExecution("bound-execution"));

        assertEquals(409, error.getCode());
        assertEquals("PENDING", stored.get().getStatus());
    }

    @Test
    void cancelStopsRunningAgentWorkflow() throws Exception {
        AtomicReference<WorkflowExecution> stored = new AtomicReference<>();
        Workflow workflow = workflow("""
                {"timeoutSeconds":10,"nodes":[
                  {"id":"start","type":"START"},
                  {"id":"agent","type":"AGENT","targetAgent":"writer","timeout":10},
                  {"id":"end","type":"END"}
                ],"edges":[
                  {"source":"start","target":"agent"},
                  {"source":"agent","target":"end"}
                ]}
                """);
        AgentRegistry registry = mock(AgentRegistry.class);
        AgentRegistry.AgentContext context = new AgentRegistry.AgentContext();
        AiAgent agent = new AiAgent();
        agent.setId(3L);
        agent.setAgentCode("writer");
        context.setAgent(agent);
        context.setHandler(message -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return A2AMessage.builder().payload(Map.of("done", true)).build();
        });
        when(registry.getAgent("writer")).thenReturn(context);
        service = createService(workflow, stored, registry);

        String executionId = service.triggerWorkflow(1L, "MANUAL", Map.of());
        awaitStatus(stored, "RUNNING", 2000);
        service.cancelExecution(executionId);

        assertEquals("CANCELLED", awaitStatus(stored, "CANCELLED", 2000).getStatus());
    }

    private WorkflowExecutionServiceImpl createService(Workflow workflow,
                                                       AtomicReference<WorkflowExecution> stored,
                                                       AgentRegistry registry) {
        WorkflowMapper workflowMapper = mock(WorkflowMapper.class);
        WorkflowExecutionMapper executionMapper = mock(WorkflowExecutionMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        when(workflowMapper.selectById(1L)).thenReturn(workflow);
        when(workflowMapper.updateById(any())).thenReturn(1);
        when(executionMapper.insert(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });
        when(executionMapper.updateById(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });
        when(executionMapper.selectOne(any())).thenAnswer(invocation -> stored.get());
        WorkflowVersionService versionService = mock(WorkflowVersionService.class);
        WorkflowVersion snapshot = new WorkflowVersion();
        snapshot.setId(11L);
        snapshot.setWorkflowId(1L);
        snapshot.setVersionNo(1);
        snapshot.setDefinitionJson(workflow.getWorkflowDefinition());
        snapshot.setDefinitionHash("hash-1");
        snapshot.setStatus("ACTIVE");
        when(versionService.latestActive(any())).thenReturn(snapshot);
        when(versionService.requireActiveVersion(any(), any())).thenReturn(snapshot);
        return new WorkflowExecutionServiceImpl(
                executionMapper,
                workflowMapper,
                objectMapper,
                mock(A2AMessageService.class),
                registry,
                new WorkflowDefinitionService(objectMapper),
                versionService,
                mock(WorkflowAgentRunAdapter.class),
                mock(MemoryOrchestrator.class),
                mock(com.aipal.mapper.WorkflowNodeRunMapper.class)
        );
    }

    private String simpleDefinition() {
        return """
                {"nodes":[{"id":"1","type":"START"},{"id":"2","type":"END"}],
                 "edges":[{"source":"1","target":"2"}]}
                """;
    }

    private Workflow workflow(String definition) {
        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setWorkflowName("test workflow");
        workflow.setWorkflowDefinition(definition);
        workflow.setStatus(1);
        workflow.setTriggerCount(0);
        return workflow;
    }

    private String deferredDefinition(String type) {
        return """
                {"nodes":[
                  {"id":"start","type":"START"},
                  {"id":"blocked","type":"%s"},
                  {"id":"end","type":"END"}
                ],"edges":[
                  {"source":"start","target":"blocked"},
                  {"source":"blocked","target":"end"}
                ]}
                """.formatted(type);
    }

    @Test
    void startRejectsWhenConcurrencyLimitReached() throws Exception {
        AtomicReference<WorkflowExecution> stored = new AtomicReference<>();
        Workflow slow = workflow("""
                {"timeoutSeconds":10,"nodes":[
                  {"id":"start","type":"START"},
                  {"id":"agent","type":"AGENT","targetAgent":"writer","timeout":10},
                  {"id":"end","type":"END"}
                ],"edges":[
                  {"source":"start","target":"agent"},
                  {"source":"agent","target":"end"}
                ]}
                """);
        AgentRegistry registry = mock(AgentRegistry.class);
        AgentRegistry.AgentContext context = new AgentRegistry.AgentContext();
        AiAgent agent = new AiAgent();
        agent.setId(3L);
        agent.setAgentCode("writer");
        context.setAgent(agent);
        context.setHandler(message -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return A2AMessage.builder().payload(Map.of("done", true)).build();
        });
        when(registry.getAgent("writer")).thenReturn(context);
        service = createService(slow, stored, registry);
        java.lang.reflect.Field limit = WorkflowExecutionServiceImpl.class
                .getDeclaredField("maxConcurrentExecutions");
        limit.setAccessible(true);
        limit.setInt(service, 1);

        service.triggerWorkflow(1L, "MANUAL", Map.of());
        awaitStatus(stored, "RUNNING", 2000);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.triggerWorkflow(1L, "MANUAL", Map.of()));
        assertTrue(error.getMessage().contains("concurrency limit"));
    }

    private WorkflowExecution awaitStatus(AtomicReference<WorkflowExecution> stored,
                                          String status,
                                          long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            WorkflowExecution current = stored.get();
            if (current != null && status.equals(current.getStatus())
                    && ("RUNNING".equals(status) || current.getEndTime() != null)) return current;
            Thread.sleep(10);
        }
        WorkflowExecution current = stored.get();
        throw new AssertionError("Expected status " + status + " but was "
                + (current == null ? null : current.getStatus()));
    }
}
