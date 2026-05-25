package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prompt_engineering_eval_run")
public class PromptEngineeringEvalRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runCode;
    private Long promptId;
    private Long versionId;
    private Long modelId;
    private String modelCode;
    private String status;
    private Integer overallScore;
    private String metricsJson;
    private String summary;
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
