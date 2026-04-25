package com.aipal.service;

import com.aipal.entity.WorkflowExecution;

import java.util.Map;

/**
 * 编排执行服务接口
 */
public interface WorkflowExecutionService {

    /**
     * 创建编排执行
     * @param workflowId 编排ID
     * @param triggerType 触发类型
     * @param triggerSource 触发来源
     * @param startParams 启动参数
     * @return 执行ID
     */
    String createExecution(Long workflowId, String triggerType, String triggerSource,
                          String startParams);

    /**
     * 启动编排执行
     * @param executionId 执行ID
     */
    void startExecution(String executionId);

    /**
     * 执行编排步骤
     * @param executionId 执行ID
     * @param step 步骤定义
     */
    void executeStep(String executionId, Object step);

    /**
     * 获取执行状态
     * @param executionId 执行ID
     * @return 执行记录
     */
    WorkflowExecution getExecution(String executionId);

    /**
     * 取消执行
     * @param executionId 执行ID
     */
    void cancelExecution(String executionId);

    /**
     * 触发编排（支持手动/定时/事件触发）
     * @param workflowId 编排ID
     * @param triggerType 触发类型
     * @param params 触发参数
     */
    void triggerWorkflow(Long workflowId, String triggerType, Map<String, Object> params);
}
