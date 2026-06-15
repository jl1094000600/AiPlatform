package com.aipal.service;

import com.aipal.dto.A2AMessage;
import com.aipal.entity.AiAgent;
import com.aipal.entity.Workflow;
import com.aipal.entity.WorkflowExecution;
import com.aipal.mapper.WorkflowExecutionMapper;
import com.aipal.mapper.WorkflowMapper;
import com.aipal.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
        return new WorkflowExecutionServiceImpl(
                executionMapper,
                workflowMapper,
                objectMapper,
                mock(A2AMessageService.class),
                registry,
                new WorkflowDefinitionService(objectMapper)
        );
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

    private WorkflowExecution awaitStatus(AtomicReference<WorkflowExecution> stored,
                                          String status,
                                          long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            WorkflowExecution current = stored.get();
            if (current != null && status.equals(current.getStatus())) return current;
            Thread.sleep(10);
        }
        WorkflowExecution current = stored.get();
        throw new AssertionError("Expected status " + status + " but was "
                + (current == null ? null : current.getStatus()));
    }
}
