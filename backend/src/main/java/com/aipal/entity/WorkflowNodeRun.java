package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Node-level run trace for a workflow execution.
 * input/output are persisted as SHA-256 digests only so sensitive values never land in the table.
 */
@Data
@TableName("ai_workflow_node_run")
public class WorkflowNodeRun {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String executionId;

    private String nodeId;

    /** START / AGENT / CONDITION / END */
    private String nodeType;

    /** RUNNING / COMPLETED / FAILED / CANCELLED */
    private String status;

    /** 1-based attempt ordinal for retry visibility. */
    private Integer attempt;

    /** SHA-256 hex digest of the node input snapshot. */
    private String inputDigest;

    /** SHA-256 hex digest of the node output snapshot. */
    private String outputDigest;

    private Long durationMs;

    private String errorMessage;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
