package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_run")
public class AgentRun {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId; private Long projectId; private String projectKey; private Long ownerUserId;
    private String businessType; private String businessId; private Long agentId; private Long agentVersionId;
    private String definitionSnapshot; private String definitionHash; private String idempotencyKey; private String status;
    private String inputJson; private String resultJson; private String traceId; private String memoryTraceId;
    private Integer maxSteps; private Integer maxChildTasks; private Integer maxTotalTokens; private Integer totalTokens;
    private String errorMessage; private LocalDateTime startTime; private LocalDateTime endTime; private Integer version;
    private LocalDateTime createTime; private LocalDateTime updateTime; @TableLogic private Integer isDeleted;
}
