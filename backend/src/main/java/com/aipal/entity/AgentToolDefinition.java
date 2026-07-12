package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_tool_definition")
public class AgentToolDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String toolName;
    private String description;
    private String inputSchema;
    private String outputSchema;
    private String riskLevel;
    private Integer concurrencySafe;
    private Integer idempotent;
    private Integer timeoutSeconds;
    private Integer requiresApproval;
    private String tenantScopes;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
