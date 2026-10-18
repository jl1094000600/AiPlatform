package com.aipal.service;

import com.aipal.dto.WorkflowDefinition;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentTask;
import com.aipal.mapper.AgentRunMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowAgentRunAdapterTest {

    private final AgentRunService agentRunService = mock(AgentRunService.class);
    private final AgentTaskService agentTaskService = mock(AgentTaskService.class);
    private final AgentRunExecutionService executionService = mock(AgentRunExecutionService.class);
    private final AgentRunMapper runMapper = mock(AgentRunMapper.class);
    private final WorkflowAgentRunAdapter adapter = new WorkflowAgentRunAdapter(
            agentRunService, agentTaskService, executionService, runMapper,
            new WorkflowVariableResolver(), new ObjectMapper());

    private final WorkflowAgentRunAdapter.WorkflowExecutionContext context =
            new WorkflowAgentRunAdapter.WorkflowExecutionContext() {
                @Override public String getExecutionId() { return "exec-1"; }
                @Override public String getProjectKey() { return "proj-1"; }
                @Override public Long getWorkflowId() { return 9L; }
                @Override public Long getVersionId() { return 3L; }
            };

    @BeforeEach
    void setUp() {
        when(agentRunService.createOrReuse(any())).thenAnswer(invocation -> {
            AgentRunService.CreateRunCommand command = invocation.getArgument(0);
            assertEquals("exec-1:node-a", command.businessId(), "idempotent businessId");
            assertEquals("WORKFLOW_NODE", command.businessType());
            return queuedRun();
        });
    }

    @Test
    void invokesRunAndReturnsValidatedOutputs() {
        AgentTask task = new AgentTask();
        task.setId(77L);
        task.setLeaseOwner("workflow-exec-1");
        when(agentTaskService.claimRootTask(eq(55L), anyString(), anyInt())).thenReturn(task);
        when(agentRunService.get(55L)).thenReturn(succeededRun());

        Map<String, Object> outputs = adapter.invoke(context, nodeWithSchema(), List.of(),
                Map.of("style", "brief"), Map.of());

        assertEquals("plan-text", outputs.get("plan"));
        verify(executionService).execute(task, "workflow-exec-1");
    }

    @Test
    void reusesTerminalRunWithoutReExecution() {
        doReturn(succeededRun()).when(agentRunService).createOrReuse(any());

        Map<String, Object> outputs = adapter.invoke(context, nodeNoSchema(), List.of(),
                Map.of(), Map.of());

        assertEquals("plan-text", outputs.get("plan"));
        verify(agentTaskService, never()).claimRootTask(any(), any(), anyInt());
        verify(executionService, never()).execute(any(), any());
    }

    @Test
    void failsWhenRequiredOutputFieldMissing() {
        AgentTask task = new AgentTask();
        task.setLeaseOwner("workflow-exec-1");
        when(agentTaskService.claimRootTask(eq(55L), anyString(), anyInt())).thenReturn(task);
        AgentRun empty = succeededRun();
        empty.setResultJson("{\"other\":1}");
        when(agentRunService.get(55L)).thenReturn(empty);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.invoke(context, nodeWithSchema(), List.of(), Map.of(), Map.of()));
        assertTrue(error.getMessage().contains("missing required field"));
    }

    @Test
    void failsWhenRunNotSucceeded() {
        AgentTask task = new AgentTask();
        task.setLeaseOwner("workflow-exec-1");
        when(agentTaskService.claimRootTask(eq(55L), anyString(), anyInt())).thenReturn(task);
        AgentRun failed = queuedRun();
        failed.setStatus("FAILED");
        when(agentRunService.get(55L)).thenReturn(failed);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.invoke(context, nodeNoSchema(), List.of(), Map.of(), Map.of()));
        assertTrue(error.getMessage().contains("FAILED"));
    }

    private AgentRun queuedRun() {
        AgentRun run = new AgentRun();
        run.setId(55L);
        run.setStatus("QUEUED");
        return run;
    }

    private AgentRun succeededRun() {
        AgentRun run = new AgentRun();
        run.setId(55L);
        run.setStatus("SUCCEEDED");
        run.setResultJson("{\"plan\":\"plan-text\"}");
        return run;
    }

    private WorkflowDefinition.Node nodeWithSchema() {
        WorkflowDefinition.Node node = nodeNoSchema();
        try {
            node.setOutputsSchema(new ObjectMapper().readTree("{\"required\":[\"plan\"]}"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return node;
    }

    private WorkflowDefinition.Node nodeNoSchema() {
        WorkflowDefinition.Node node = new WorkflowDefinition.Node();
        node.setId("node-a");
        node.setType("AGENT");
        node.setAgentId(3L);
        node.setInputs(Map.of("prompt", "use {{inputs.style}}"));
        return node;
    }
}
