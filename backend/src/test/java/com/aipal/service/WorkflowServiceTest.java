package com.aipal.service;

import com.aipal.dto.WorkflowRequest;
import com.aipal.entity.Workflow;
import com.aipal.mapper.WorkflowMapper;
import com.aipal.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowServiceTest {
    private final WorkflowMapper mapper = mock(WorkflowMapper.class);
    private final WorkflowService service = new WorkflowService(mapper,
            new WorkflowDefinitionService(new ObjectMapper()));

    @BeforeEach
    void setTenantContext() {
        TenantContext.set(new TenantContext.Context(
                7L, "tester", 42L, "tenant-42", false, Set.of(), Set.of()));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PARALLEL", "JOIN"})
    void rejectsCreatingDeferredParallelDefinitionWithoutSaving(String type) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(request(deferredDefinition(type))));

        assertTrue(error.getMessage().contains("JOIN barrier"));
        verify(mapper, never()).insert(any(Workflow.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PARALLEL", "JOIN"})
    void rejectsUpdatingToDeferredParallelDefinitionWithoutChangingStoredWorkflow(String type) {
        Workflow existing = workflow(simpleDefinition(), 1);
        when(mapper.selectById(1L)).thenReturn(existing);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.update(1L, request(deferredDefinition(type))));

        assertTrue(error.getMessage().contains("JOIN barrier"));
        assertEquals(simpleDefinition(), existing.getWorkflowDefinition());
        assertEquals(1, existing.getStatus());
        verify(mapper, never()).updateById(any(Workflow.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PARALLEL", "JOIN"})
    void rejectsDeployingStoredDeferredParallelDefinition(String type) {
        Workflow existing = workflow(deferredDefinition(type), 0);
        when(mapper.selectById(1L)).thenReturn(existing);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.deploy(1L));

        assertTrue(error.getMessage().contains("JOIN barrier"));
        assertEquals(0, existing.getStatus());
        verify(mapper, never()).updateById(any(Workflow.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PARALLEL", "JOIN"})
    void retainsReadAccessToStoredDeferredParallelDefinitions(String type) {
        Workflow existing = workflow(deferredDefinition(type), 1);
        when(mapper.selectById(1L)).thenReturn(existing);
        when(mapper.selectList(any())).thenReturn(List.of(existing));

        assertSame(existing, service.get(1L));
        assertEquals(List.of(existing), service.list(null, null));
        assertEquals(deferredDefinition(type), existing.getWorkflowDefinition());
        verify(mapper, never()).updateById(any(Workflow.class));
    }

    @Test
    void stillAllowsSavingAndDeployingSupportedWorkflow() {
        Workflow created = service.create(request(simpleDefinition()));
        created.setId(1L);
        when(mapper.selectById(1L)).thenReturn(created);
        when(mapper.updateById(any(Workflow.class))).thenReturn(1);

        assertEquals(0, created.getStatus());
        assertEquals(42L, created.getTenantId());
        assertEquals(7L, created.getOwnerId());
        assertEquals(1, service.deploy(1L).getStatus());
        assertEquals(0, service.update(1L, request(simpleDefinition())).getStatus());
        verify(mapper).insert(created);
    }

    private WorkflowRequest request(String definition) {
        WorkflowRequest request = new WorkflowRequest();
        request.setWorkflowCode("poc");
        request.setWorkflowName("PoC");
        request.setTriggerType("MANUAL");
        request.setWorkflowDefinition(definition);
        return request;
    }

    private Workflow workflow(String definition, int status) {
        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setWorkflowDefinition(definition);
        workflow.setStatus(status);
        return workflow;
    }

    private String simpleDefinition() {
        return """
                {"nodes":[{"id":"start","type":"START"},{"id":"end","type":"END"}],
                 "edges":[{"source":"start","target":"end"}]}
                """;
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
}
