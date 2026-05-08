package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("automation_stage_run")
public class AutomationStageRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pipelineId;
    private String stageKey;
    private String stageName;
    private Integer stageOrder;
    private String executorType;
    private String aiModelCode;
    private String status;
    private Integer requiresApproval;
    private String inputSummary;
    private String outputSummary;
    private String artifactPath;
    private String artifactContent;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationMs;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
