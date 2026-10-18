package com.aipal.controller;

import com.aipal.common.ErrorCode;
import com.aipal.common.Result;
import com.aipal.dto.WorkflowRequest;
import com.aipal.dto.WorkflowTryRunDtos;
import com.aipal.dto.WorkflowVersionDtos;
import com.aipal.entity.Workflow;
import com.aipal.entity.WorkflowExecution;
import com.aipal.security.RequirePermission;
import com.aipal.service.WorkflowExecutionService;
import com.aipal.service.WorkflowService;
import com.aipal.service.WorkflowVersionService;
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
    private final WorkflowVersionService versionService;
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

    @PostMapping("/{workflowId}/versions")
    @RequirePermission("workflow:publish")
    public Result<WorkflowVersionDtos.VersionView> publishVersion(
            @PathVariable Long workflowId,
            @RequestBody WorkflowVersionDtos.PublishRequest request) {
        return Result.success(versionService.publish(workflowId,
                request.definitionJson(), request.layoutJson()));
    }

    @GetMapping("/{workflowId}/versions")
    @RequirePermission("workflow:manage")
    public Result<List<WorkflowVersionDtos.VersionView>> listVersions(@PathVariable Long workflowId) {
        return Result.success(versionService.list(workflowId));
    }

    @GetMapping("/{workflowId}/versions/{versionId}")
    @RequirePermission("workflow:manage")
    public Result<WorkflowVersionDtos.VersionDetail> getVersion(
            @PathVariable Long workflowId, @PathVariable Long versionId) {
        return Result.success(versionService.detail(workflowId, versionId));
    }

    @PutMapping("/{workflowId}/versions/{versionId}")
    @RequirePermission("workflow:publish")
    public Result<WorkflowVersionDtos.VersionView> overwriteVersion(
            @PathVariable Long workflowId, @PathVariable Long versionId,
            @RequestBody WorkflowVersionDtos.PublishRequest request) {
        return Result.success(versionService.overwrite(workflowId, versionId,
                request.definitionJson(), request.layoutJson()));
    }

    @PostMapping("/{workflowId}/versions/{versionId}/deprecate")
    @RequirePermission("workflow:publish")
    public Result<WorkflowVersionDtos.VersionView> deprecateVersion(
            @PathVariable Long workflowId, @PathVariable Long versionId) {
        return Result.success(versionService.deprecate(workflowId, versionId));
    }

    @PostMapping("/{workflowId}/trigger")
    @RequirePermission("workflow:manage")
    public Result<String> triggerWorkflow(
            @PathVariable Long workflowId,
            @RequestParam(defaultValue = "MANUAL") String triggerType,
            @RequestBody(required = false) Map<String, Object> params) {
        return Result.success(executionService.triggerWorkflow(workflowId, triggerType, params));
    }

    /** T20 dry-run against a published snapshot or an in-memory draft definition. */
    @PostMapping("/{workflowId}/try-run")
    @RequirePermission("workflow:manage")
    public Result<WorkflowTryRunDtos.TryRunResult> tryRun(
            @PathVariable Long workflowId,
            @RequestBody(required = false) WorkflowTryRunDtos.TryRunRequest request) {
        return Result.success(executionService.tryRun(workflowId, request));
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
            throw ErrorCode.WORKFLOW_EXECUTION_NOT_FOUND.ex();
        }
        return Result.success(execution);
    }

    /** T21 node-level traces (input/output stored as digests only). */
    @GetMapping("/executions/{executionId}/node-runs")
    @RequirePermission("workflow:manage")
    public Result<List<WorkflowTryRunDtos.NodeRunView>> listNodeRuns(@PathVariable String executionId) {
        return Result.success(executionService.listNodeRuns(executionId));
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
