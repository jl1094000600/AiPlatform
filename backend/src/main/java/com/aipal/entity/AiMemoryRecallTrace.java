package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_memory_recall_trace")
public class AiMemoryRecallTrace {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private String traceId;
    private Long userId;
    private Long agentId;
    private String projectKey;
    private String recallMode;
    private Integer policyVersion;
    private String requestSummary;
    private String candidatesJson;
    private String injectedJson;
    private Integer tokenCount;
    private Long durationMs;
    private LocalDateTime createTime;
}
