package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prompt_engineering_eval_result")
public class PromptEngineeringEvalResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Long promptId;
    private Long versionId;
    private Long testCaseId;
    private String caseName;
    private String inputJson;
    private String expectedOutput;
    private String predictedOutput;
    private Integer score;
    private Integer passed;
    private String dimensionScores;
    private String feedback;
    private String rawResult;
    private Integer durationMs;
    private String errorMessage;
    private LocalDateTime createTime;
}
