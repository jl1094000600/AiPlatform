package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Immutable published workflow snapshot. Draft edits go to ai_workflow.workflow_definition;
 * publish copies the validated definition here and runs bind to versionId + definitionHash.
 */
@Data
@TableName("ai_workflow_version")
public class WorkflowVersion {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long workflowId;

    /** Monotonic per workflow, starts at 1. */
    private Integer versionNo;

    /** Full Graph DSL without layout. */
    private String definitionJson;

    /** Optional canvas layout snapshot. */
    private String layoutJson;

    /** SHA-256 of canonicalized definitionJson. */
    private String definitionHash;

    /** ACTIVE / DEPRECATED */
    private String status;

    private Long publishedBy;

    private LocalDateTime publishTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
