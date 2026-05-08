package com.aipal.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentQualitySummary {
    private Long agentId;
    private String agentCode;
    private String agentName;
    private Long datasetId;
    private Long modelId;
    private Integer topK;
    private Double temperature;
    private Double accuracy;
    private Double precisionScore;
    private Double recallScore;
    private Double f1Score;
    private Integer sampleCount;
    private Integer status;
    private LocalDateTime lastRunTime;
}
