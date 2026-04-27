package com.aipal.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.aipal.dto.A2AMessage;
import com.aipal.dto.WorkflowStep;
import com.aipal.entity.Workflow;
import com.aipal.entity.WorkflowExecution;
import com.aipal.mapper.WorkflowExecutionMapper;
import com.aipal.mapper.WorkflowMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
    private final A2AMessageService a2aMessageService;
    private final AgentRegistry agentRegistry;

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
        WorkflowExecution execution = getExecutionEntity(executionId);
        if (execution == null) {
            log.warn("Execution not found: {}", executionId);
            return;
        }

        WorkflowStep workflowStep = null;
        if (step instanceof WorkflowStep) {
            workflowStep = (WorkflowStep) step;
        } else if (step instanceof Map) {
            try {
                workflowStep = objectMapper.convertValue(step, WorkflowStep.class);
            } catch (Exception e) {
                log.warn("Failed to convert step to WorkflowStep", e);
                return;
            }
        }

        if (workflowStep == null) {
            log.warn("Step is not a valid WorkflowStep");
            return;
        }

        log.debug("Executing step {} for execution: {}, type: {}",
            workflowStep.getStepOrder(), executionId, workflowStep.getStepType());

        // Update execution context
        try {
            Map<String, Object> context = getExecutionContext(execution);
            context.put("currentStep", workflowStep.getStepOrder());
            context.put("lastStep", workflowStep);
            context.put("lastStepTime", LocalDateTime.now().toString());
            execution.setExecutionContext(objectMapper.writeValueAsString(context));
        } catch (Exception e) {
            log.warn("Failed to update execution context", e);
        }

        // Execute based on step type
        executeStepByType(execution, workflowStep);
    }

    private void executeStepByType(WorkflowExecution execution, WorkflowStep step) {
        try {
            switch (step.getStepType()) {
                case "AGENT_INVOKE":
                case "A2A_CALL":
                    executeA2ACall(execution, step);
                    break;
                case "CONDITION":
                    evaluateCondition(execution, step);
                    break;
                case "TRANSFORM":
                    executeTransform(execution, step);
                    break;
                case "END":
                    completeExecution(execution, null);
                    break;
                default:
                    log.warn("Unknown step type: {}", step.getStepType());
            }
        } catch (Exception e) {
            log.error("Failed to execute step {}: {}", step.getStepOrder(), e.getMessage(), e);
            failExecution(execution, e.getMessage());
        }
    }

    private void executeA2ACall(WorkflowExecution execution, WorkflowStep step) {
        String targetAgent = step.getTargetAgent();
        if (StrUtil.isBlank(targetAgent)) {
            log.warn("No target agent specified for step {}", step.getStepOrder());
            return;
        }

        // Build A2A message
        A2AMessage message = A2AMessage.builder()
            .sourceAgent("workflow-engine")
            .targetAgent(targetAgent)
            .sessionId(execution.getExecutionId())
            .action(A2AMessage.Action.invoke)
            .payload(step.getParams())
            .build();

        // Send message (this will persist to database and send via Redis stream)
        String messageId = a2aMessageService.sendMessage(message);

        // Get response with timeout
        int timeout = step.getTimeout() != null ? step.getTimeout() : 30;
        A2AMessage response = a2aMessageService.getResponse(messageId, timeout * 1000L);

        // Update context with result
        try {
            Map<String, Object> context = getExecutionContext(execution);
            context.put("lastResponse", response);
            context.put("lastMessageId", messageId);
            execution.setExecutionContext(objectMapper.writeValueAsString(context));
        } catch (Exception e) {
            log.warn("Failed to update context with response", e);
        }

        log.debug("A2A call completed for step {}, messageId: {}", step.getStepOrder(), messageId);
    }

    private void evaluateCondition(WorkflowExecution execution, WorkflowStep step) {
        String expression = step.getConditionExpression();
        boolean result = evaluateExpression(execution, expression);

        try {
            Map<String, Object> context = getExecutionContext(execution);
            context.put("conditionResult", result);
            execution.setExecutionContext(objectMapper.writeValueAsString(context));
        } catch (Exception e) {
            log.warn("Failed to update condition result", e);
        }

        log.debug("Condition {} evaluated to: {}", step.getStepOrder(), result);
    }

    private boolean evaluateExpression(WorkflowExecution execution, String expression) {
        // Simple expression evaluation - in production, use a proper expression language
        if (StrUtil.isBlank(expression)) {
            return true;
        }
        // For now, just return true for non-empty expressions
        // A real implementation would parse and evaluate the expression
        return true;
    }

    private void executeTransform(WorkflowExecution execution, WorkflowStep step) {
        // Transform step - apply transformation to execution context
        String transformExpr = step.getOutputTransform();
        if (StrUtil.isNotBlank(transformExpr)) {
            log.debug("Applying transform: {}", transformExpr);
            // In production, apply transformation expression to context
        }
    }

    private void completeExecution(WorkflowExecution execution, String result) {
        execution.setStatus("COMPLETED");
        execution.setResult(result);
        execution.setEndTime(LocalDateTime.now());
        executionMapper.updateById(execution);
        log.info("Workflow execution completed: {}", execution.getExecutionId());
    }

    private void failExecution(WorkflowExecution execution, String errorMessage) {
        execution.setStatus("FAILED");
        execution.setErrorMessage(errorMessage);
        execution.setEndTime(LocalDateTime.now());
        executionMapper.updateById(execution);
        log.error("Workflow execution failed: {}, error: {}", execution.getExecutionId(), errorMessage);
    }

    private Map<String, Object> getExecutionContext(WorkflowExecution execution) {
        if (StrUtil.isBlank(execution.getExecutionContext())) {
            return new java.util.HashMap<>();
        }
        try {
            return objectMapper.readValue(execution.getExecutionContext(),
                new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new java.util.HashMap<>();
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
