package com.aipal.controller;

import com.aipal.entity.Workflow;
import com.aipal.entity.WorkflowExecution;
import com.aipal.service.WorkflowExecutionService;
import com.aipal.mapper.WorkflowMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 编排Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowExecutionService executionService;
    private final WorkflowMapper workflowMapper;

    /**
     * 创建编排配置
     */
    @PostMapping
    public ResponseEntity<Workflow> createWorkflow(@Valid @RequestBody Workflow workflow) {
        Workflow saved = workflowMapper.insert(workflow) > 0 ? workflow : null;
        if (saved != null) {
            log.info("Created workflow: {}", workflow.getWorkflowCode());
        }
        return ResponseEntity.ok(saved);
    }

    /**
     * 获取编排列表
     */
    @GetMapping
    public ResponseEntity<List<Workflow>> listWorkflows() {
        List<Workflow> workflows = workflowMapper.selectList(null);
        return ResponseEntity.ok(workflows);
    }

    /**
     * 获取编排详情
     */
    @GetMapping("/{workflowId}")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable Long workflowId) {
        Workflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(workflow);
    }

    /**
     * 触发编排执行
     */
    @PostMapping("/{workflowId}/trigger")
    public ResponseEntity<String> triggerWorkflow(
            @PathVariable Long workflowId,
            @RequestParam(defaultValue = "MANUAL") String triggerType,
            @RequestBody(required = false) Map<String, Object> params) {
        executionService.triggerWorkflow(workflowId, triggerType, params);
        return ResponseEntity.ok("Workflow triggered");
    }

    /**
     * 获取编排执行状态
     */
    @GetMapping("/executions/{executionId}")
    public ResponseEntity<WorkflowExecution> getExecution(@PathVariable String executionId) {
        WorkflowExecution execution = executionService.getExecution(executionId);
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(execution);
    }

    /**
     * 取消执行
     */
    @PostMapping("/executions/{executionId}/cancel")
    public ResponseEntity<Void> cancelExecution(@PathVariable String executionId) {
        executionService.cancelExecution(executionId);
        return ResponseEntity.noContent().build();
    }
}
