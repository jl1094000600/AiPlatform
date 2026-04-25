package com.aipal.service;

import cn.hutool.core.util.IdUtil;
import com.aipal.entity.Workflow;
import com.aipal.entity.WorkflowExecution;
import com.aipal.mapper.WorkflowExecutionMapper;
import com.aipal.mapper.WorkflowMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 编排执行服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {

    private final WorkflowExecutionMapper executionMapper;
    private final WorkflowMapper workflowMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public String createExecution(Long workflowId, String triggerType, String triggerSource,
                                  String startParams) {
        Workflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }

        String executionId = IdUtil.fastSimpleUUID();

        WorkflowExecution execution = new WorkflowExecution();
        execution.setExecutionId(executionId);
        execution.setWorkflowId(workflowId);
        execution.setTriggerType(triggerType);
        execution.setTriggerSource(triggerSource);
        execution.setStatus("PENDING");
        execution.setStartParams(startParams);
        execution.setStartTime(LocalDateTime.now());
        execution.setCreateTime(LocalDateTime.now());

        executionMapper.insert(execution);

        log.info("Created workflow execution: {} for workflow: {}", executionId, workflowId);
        return executionId;
    }

    @Override
    public void startExecution(String executionId) {
        WorkflowExecution execution = getExecutionEntity(executionId);
        if (execution == null) {
            log.warn("Execution not found: {}", executionId);
            return;
        }

        execution.setStatus("RUNNING");
        executionMapper.updateById(execution);

        log.info("Started workflow execution: {}", executionId);

        // 实际执行应该在异步线程中进行，这里只是更新状态
        // 真正的执行逻辑应该在 executeStep 中
    }

    @Override
    public void executeStep(String executionId, Object step) {
        log.debug("Executing step for execution: {}, step: {}", executionId, step);

        WorkflowExecution execution = getExecutionEntity(executionId);
        if (execution == null) {
            log.warn("Execution not found: {}", executionId);
            return;
        }

        // 这里应该解析step并执行实际的编排逻辑
        // 由于step是Object类型，实际实现时需要根据workflow定义来解析

        // 更新执行上下文
        try {
            Map<String, Object> context = objectMapper.readValue(
                    execution.getExecutionContext(), Map.class);
            context.put("lastStep", step);
            context.put("lastStepTime", LocalDateTime.now().toString());
            execution.setExecutionContext(objectMapper.writeValueAsString(context));
        } catch (Exception e) {
            log.warn("Failed to update execution context", e);
        }
    }

    @Override
    public WorkflowExecution getExecution(String executionId) {
        return getExecutionEntity(executionId);
    }

    @Override
    @Transactional
    public void cancelExecution(String executionId) {
        WorkflowExecution execution = getExecutionEntity(executionId);
        if (execution == null) {
            log.warn("Execution not found: {}", executionId);
            return;
        }

        if ("RUNNING".equals(execution.getStatus()) || "PENDING".equals(execution.getStatus())) {
            execution.setStatus("CANCELLED");
            execution.setEndTime(LocalDateTime.now());
            executionMapper.updateById(execution);
            log.info("Cancelled workflow execution: {}", executionId);
        }
    }

    @Override
    @Transactional
    public void triggerWorkflow(Long workflowId, String triggerType, Map<String, Object> params) {
        Workflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }

        if (workflow.getStatus() != 1) {
            throw new IllegalStateException("Workflow is not enabled: " + workflowId);
        }

        try {
            // 创建执行记录
            String executionId = createExecution(
                    workflowId,
                    triggerType,
                    params != null ? params.toString() : null,
                    objectMapper.writeValueAsString(params)
            );

            // 启动执行
            startExecution(executionId);

            // 更新workflow触发统计
            workflow.setLastTriggerTime(LocalDateTime.now());
            workflow.setTriggerCount(workflow.getTriggerCount() != null ?
                    workflow.getTriggerCount() + 1 : 1);
            workflowMapper.updateById(workflow);

            log.info("Triggered workflow: {} with execution: {}", workflowId, executionId);
        } catch (Exception e) {
            log.error("Failed to trigger workflow: {}", workflowId, e);
            throw new RuntimeException("Failed to trigger workflow", e);
        }
    }

    /**
     * 定时检测超时执行并标记为失败
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void detectTimeoutExecutions() {
        List<WorkflowExecution> runningExecutions = executionMapper.selectList(
                new LambdaQueryWrapper<WorkflowExecution>()
                        .eq(WorkflowExecution::getStatus, "RUNNING")
        );

        for (WorkflowExecution execution : runningExecutions) {
            // 检查是否超时（假设默认超时30分钟）
            if (execution.getStartTime() != null) {
                long minutes = java.time.Duration.between(
                        execution.getStartTime(), LocalDateTime.now()).toMinutes();
                if (minutes > 30) {
                    execution.setStatus("FAILED");
                    execution.setErrorMessage("Execution timeout");
                    execution.setEndTime(LocalDateTime.now());
                    executionMapper.updateById(execution);
                    log.warn("Execution timeout, marked as failed: {}", execution.getExecutionId());
                }
            }
        }
    }

    private WorkflowExecution getExecutionEntity(String executionId) {
        return executionMapper.selectOne(
                new LambdaQueryWrapper<WorkflowExecution>()
                        .eq(WorkflowExecution::getExecutionId, executionId)
        );
    }
}
