package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_agent_quality_run")
public class AiAgentQualityRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runCode;
    private Long agentId;
    private String agentCode;
    private Long datasetId;
    private Long modelId;
    private Integer topK;
    private Double temperature;
    private String inputField;
    private String expectedField;
    private Integer sampleCount;
    private Double accuracy;
    private Double precisionScore;
    private Double recallScore;
    private Double f1Score;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
