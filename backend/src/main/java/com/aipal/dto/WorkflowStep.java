package com.aipal.dto;

import lombok.Data;
import java.util.Map;

/**
 * 编排步骤定义
 */
@Data
public class WorkflowStep {
    /**
     * 步骤编号
     */
    private Integer stepOrder;

    /**
     * 步骤类型: AGENT_INVOKE / A2A_CALL / TRANSFORM / CONDITION / END
     */
    private String stepType;

    /**
     * 步骤名称
     */
    private String stepName;

    /**
     * 目标Agent编码（用于AGENT_INVOKE和A2A_CALL）
     */
    private String targetAgent;

    /**
     * 调用参数
     */
    private Map<String, Object> params;

    /**
     * 超时时间（秒）
     */
    private Integer timeout;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 条件表达式（用于CONDITION类型）
     */
    private String conditionExpression;

    /**
     * 条件分支yes的下一步（用于CONDITION类型）
     */
    private Integer nextStepYes;

    /**
     * 条件分支no的下一步（用于CONDITION类型）
     */
    private Integer nextStepNo;

    /**
     * 输出转换表达式
     */
    private String outputTransform;
}