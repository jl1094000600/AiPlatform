package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 编排执行记录实体类
 * 记录工作流每次执行的状态和结果
 */
@Data
@TableName("ai_workflow_execution")
public class WorkflowExecution {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 执行ID */
    private String executionId;

    /** 编排ID */
    private Long workflowId;

    /** 触发类型 */
    private String triggerType;

    /** 触发来源 */
    private String triggerSource;

    /** 状态: PENDING / RUNNING / COMPLETED / FAILED / CANCELLED */
    private String status;

    /** 启动参数（JSON） */
    private String startParams;

    /** 执行上下文（JSON） */
    private String executionContext;

    /** 执行结果 */
    private String result;

    /** 错误信息 */
    private String errorMessage;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    private LocalDateTime createTime;
}
