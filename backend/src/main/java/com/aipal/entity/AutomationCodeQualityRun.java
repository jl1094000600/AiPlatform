package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("automation_code_quality_run")
public class AutomationCodeQualityRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pipelineId;
    private Long stageRunId;
    private Long codeStageRunId;
    private Long standardId;
    private String standardSnapshot;
    private String gateSnapshot;
    private String modelCode;
    private String status;
    private Integer overallScore;
    private Integer passed;
    private String summary;
    private String metricsJson;
    private String rawResult;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationMs;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
