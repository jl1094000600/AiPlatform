package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_evaluation_sample")
public class AiEvaluationSample {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long evaluationId;
    private Integer sampleIndex;
    private String inputData;
    private String expectedOutput;
    private String actualOutput;
    private Double score;
    private Long durationMs;
    private Integer status;
    private String errorMessage;
    private String metricsData;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
