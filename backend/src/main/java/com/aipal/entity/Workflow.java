package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 编排配置实体类
 * 定义工作流的配置、触发方式和执行定义
 */
@Data
@TableName("ai_workflow")
public class Workflow {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 编排编码 */
    private String workflowCode;

    /** 编排名称 */
    private String workflowName;

    /** 编排描述 */
    private String description;

    /** 触发类型: MANUAL / SCHEDULE / EVENT */
    private String triggerType;

    /** 触发配置（JSON） */
    private String triggerConfig;

    /** 编排定义（JSON） */
    private String workflowDefinition;

    /** 状态: 0-禁用, 1-启用 */
    private Integer status;

    private Long ownerId;

    /** 最后触发时间 */
    private LocalDateTime lastTriggerTime;

    /** 触发次数 */
    private Integer triggerCount;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
