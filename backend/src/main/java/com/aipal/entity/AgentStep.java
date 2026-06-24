package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_step")
public class AgentStep {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId; private Long runId; private Long parentStepId; private Integer stepNo;
    private String stepType; private String status; private String toolName; private String toolIdempotencyKey;
    private String inputJson; private String outputJson; private String traceId; private Integer inputTokens; private Integer outputTokens;
    private String errorMessage; private LocalDateTime startTime; private LocalDateTime endTime;
    private LocalDateTime createTime; private LocalDateTime updateTime; @TableLogic private Integer isDeleted;
}
