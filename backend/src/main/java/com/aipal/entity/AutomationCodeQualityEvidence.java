package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("automation_code_quality_evidence")
public class AutomationCodeQualityEvidence {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long runId;
    private Long pipelineId;
    private Long stageRunId;
    private String evidenceType;
    private String toolName;
    private String commandText;
    private String status;
    private Integer score;
    private String summary;
    private String rawOutput;
    private String parsedResultJson;
    private Integer durationMs;
    private LocalDateTime createTime;
}
