package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_tool_call")
public class AgentToolCall {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long runId;
    private Long stepId;
    private String callId;
    private String toolName;
    private String riskLevel;
    private String permissionDecision;
    private String status;
    private String idempotencyKey;
    private String inputJson;
    private String outputJson;
    private String outputSummary;
    private String errorMessage;
    private Integer inputTokens;
    private Integer outputTokens;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
