package com.aipal.service;

import com.aipal.entity.Workflow;
import com.aipal.mapper.WorkflowMapper;
import com.aipal.security.TenantTaskRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowTriggerServiceTest {

    @Test
    void triggersMatchingEventWorkflowsOnly() {
        WorkflowMapper mapper = mock(WorkflowMapper.class);
        WorkflowExecutionService executionService = mock(WorkflowExecutionService.class);
        Workflow matching = workflow(1L, "{\"eventType\":\"AgentStatusChanged\"}");
        Workflow other = workflow(2L, "{\"eventType\":\"DatasetImported\"}");
        when(mapper.selectList(any())).thenReturn(List.of(matching, other));
        when(executionService.triggerWorkflow(eq(1L), eq("EVENT"), any())).thenReturn("exec-1");
        WorkflowTriggerService service = new WorkflowTriggerService(
                mapper, executionService, mock(TenantTaskRunner.class), new ObjectMapper());

        List<String> executions = service.triggerEvent("AgentStatusChanged", Map.of("agentId", 7));

        assertEquals(List.of("exec-1"), executions);
        verify(executionService).triggerWorkflow(1L, "EVENT", Map.of("agentId", 7));
    }

    @Test
    void triggersScheduleWhenCronOccurrenceIsDue() {
        WorkflowMapper mapper = mock(WorkflowMapper.class);
        WorkflowExecutionService executionService = mock(WorkflowExecutionService.class);
        Workflow workflow = workflow(3L, "{\"cron\":\"0 * * * * *\"}");
        workflow.setLastTriggerTime(LocalDateTime.of(2026, 6, 14, 10, 0, 0));
        when(mapper.selectList(any())).thenReturn(List.of(workflow));
        WorkflowTriggerService service = new WorkflowTriggerService(
                mapper, executionService, mock(TenantTaskRunner.class), new ObjectMapper());

        service.triggerDueSchedules(LocalDateTime.of(2026, 6, 14, 10, 1, 1));

        verify(executionService).triggerWorkflow(eq(3L), eq("SCHEDULE"), any());
    }

    private Workflow workflow(Long id, String triggerConfig) {
        Workflow workflow = new Workflow();
        workflow.setId(id);
        workflow.setStatus(1);
        workflow.setTriggerConfig(triggerConfig);
        return workflow;
    }
}
