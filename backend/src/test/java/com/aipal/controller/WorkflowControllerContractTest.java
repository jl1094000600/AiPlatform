package com.aipal.controller;

import com.aipal.service.WorkflowExecutionService;
import com.aipal.service.WorkflowService;
import com.aipal.service.WorkflowTriggerService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowControllerContractTest {

    @Test
    void exposesCompleteWorkflowContract() {
        RequestMapping mapping = WorkflowController.class.getAnnotation(RequestMapping.class);
        assertEquals("/api/v1/workflows", mapping.value()[0]);

        Set<String> routes = Arrays.stream(WorkflowController.class.getDeclaredMethods())
                .map(this::route)
                .collect(Collectors.toSet());

        assertTrue(routes.contains("GET "));
        assertTrue(routes.contains("POST "));
        assertTrue(routes.contains("GET /{workflowId}"));
        assertTrue(routes.contains("PUT /{workflowId}"));
        assertTrue(routes.contains("DELETE /{workflowId}"));
        assertTrue(routes.contains("POST /{workflowId}/deploy"));
        assertTrue(routes.contains("POST /{workflowId}/trigger"));
        assertTrue(routes.contains("GET /{workflowId}/executions"));
        assertTrue(routes.contains("GET /executions"));
        assertTrue(routes.contains("GET /executions/{executionId}"));
        assertTrue(routes.contains("POST /executions/{executionId}/cancel"));
        assertTrue(routes.contains("POST /events/{eventType}"));
    }

    @Test
    void controllerDependsOnWorkflowServicesInsteadOfDirectMappers() {
        assertEquals(3, WorkflowController.class.getDeclaredFields().length);
        Set<Class<?>> fieldTypes = Arrays.stream(WorkflowController.class.getDeclaredFields())
                .map(field -> field.getType())
                .collect(Collectors.toSet());
        assertTrue(fieldTypes.contains(WorkflowService.class));
        assertTrue(fieldTypes.contains(WorkflowExecutionService.class));
        assertTrue(fieldTypes.contains(WorkflowTriggerService.class));
    }

    private String route(Method method) {
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) return "GET " + first(get.value());
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) return "POST " + first(post.value());
        PutMapping put = method.getAnnotation(PutMapping.class);
        if (put != null) return "PUT " + first(put.value());
        DeleteMapping delete = method.getAnnotation(DeleteMapping.class);
        if (delete != null) return "DELETE " + first(delete.value());
        return method.getName();
    }

    private String first(String[] values) {
        return values.length == 0 ? "" : values[0];
    }
}
