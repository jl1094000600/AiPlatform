package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prompt_engineering_optimize_run")
public class PromptEngineeringOptimizeRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long promptId;
    private Long sourceVersionId;
    private Long targetVersionId;
    private Long modelId;
    private String modelCode;
    private String optimizeGoal;
    private String optimizationSummary;
    private String rawResult;
    private String status;
    private String errorMessage;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationMs;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
