package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_output_governance_record")
public class AiOutputGovernanceRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String recordCode;
    private String sourceType;
    private Long pipelineId;
    private Long stageRunId;
    private Long generationJobId;
    private String stageKey;
    private String artifactType;
    private String artifactPath;
    private String artifactSummary;
    private String modelCode;
    private String governanceStatus;
    private String riskLevel;
    private Integer riskScore;
    private String policySnapshot;
    private String metadataJson;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
