package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Append-only workflow execution events for audit and recovery visibility (W4).
 */
@Data
@TableName("ai_workflow_execution_event")
public class WorkflowExecutionEvent {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String executionId;

    /** CREATED / STARTED / NODE_STARTED / NODE_COMPLETED / NODE_FAILED / CANCELLED / TIMEOUT / RECOVERED / FINISHED */
    private String eventType;

    private String nodeId;

    private String message;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createTime;
}
