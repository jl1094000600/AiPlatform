package com.aipal.dto;

import lombok.Data;

@Data
public class AgentQualityEvaluationRequest {
    private Long agentId;
    private Integer sampleCount;
}
