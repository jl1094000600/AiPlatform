package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.WorkflowRequest;
import com.aipal.entity.Workflow;
import com.aipal.entity.WorkflowExecution;
import com.aipal.security.RequirePermission;
import com.aipal.service.WorkflowExecutionService;
import com.aipal.service.WorkflowService;
import com.aipal.service.WorkflowTriggerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {
    private final WorkflowService workflowService;
    private final WorkflowExecutionService executionService;
    private final WorkflowTriggerService triggerService;

    @GetMapping
    @RequirePermission("workflow:manage")
    public Result<List<Workflow>> listWorkflows(
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) Integer status) {
        return Result.success(workflowService.list(triggerType, status));
    }

    @PostMapping
    @RequirePermission("workflow:manage")
    public Result<Workflow> createWorkflow(@Valid @RequestBody WorkflowRequest request) {
        return Result.success(workflowService.create(request));
    }

    @GetMapping("/{workflowId}")
    @RequirePermission("workflow:manage")
    public Result<Workflow> getWorkflow(@PathVariable Long workflowId) {
        return Result.success(workflowService.get(workflowId));
    }

    @PutMapping("/{workflowId}")
    @RequirePermission("workflow:manage")
    public Result<Workflow> updateWorkflow(@PathVariable Long workflowId,
                                           @Valid @RequestBody WorkflowRequest request) {
        return Result.success(workflowService.update(workflowId, request));
    }

    @DeleteMapping("/{workflowId}")
    @RequirePermission("workflow:manage")
    public Result<Boolean> deleteWorkflow(@PathVariable Long workflowId) {
        return Result.success(workflowService.delete(workflowId));
    }

    @PostMapping("/{workflowId}/deploy")
    @RequirePermission("workflow:manage")
    public Result<Workflow> deployWorkflow(@PathVariable Long workflowId) {
        return Result.success(workflowService.deploy(workflowId));
    }

    @PostMapping("/{workflowId}/trigger")
    @RequirePermission("workflow:manage")
    public Result<String> triggerWorkflow(
            @PathVariable Long workflowId,
            @RequestParam(defaultValue = "MANUAL") String triggerType,
            @RequestBody(required = false) Map<String, Object> params) {
        return Result.success(executionService.triggerWorkflow(workflowId, triggerType, params));
    }

    @GetMapping("/{workflowId}/executions")
    @RequirePermission("workflow:manage")
    public Result<List<WorkflowExecution>> listWorkflowExecutions(@PathVariable Long workflowId) {
        workflowService.get(workflowId);
        return Result.success(executionService.listExecutions(workflowId));
    }

    @GetMapping("/executions")
    @RequirePermission("workflow:manage")
    public Result<List<WorkflowExecution>> listExecutions() {
        return Result.success(executionService.listExecutions(null));
    }

    @GetMapping("/executions/{executionId}")
    @RequirePermission("workflow:manage")
    public Result<WorkflowExecution> getExecution(@PathVariable String executionId) {
        WorkflowExecution execution = executionService.getExecution(executionId);
        if (execution == null) {
            throw new IllegalArgumentException("Workflow execution not found: " + executionId);
        }
        return Result.success(execution);
    }

    @PostMapping("/executions/{executionId}/cancel")
    @RequirePermission("workflow:manage")
    public Result<Boolean> cancelExecution(@PathVariable String executionId) {
        executionService.cancelExecution(executionId);
        return Result.success(true);
    }

    @PostMapping("/events/{eventType}")
    @RequirePermission("workflow:manage")
    public Result<List<String>> triggerEvent(@PathVariable String eventType,
                                             @RequestBody(required = false) Map<String, Object> params) {
        return Result.success(triggerService.triggerEvent(eventType, params));
    }
}
