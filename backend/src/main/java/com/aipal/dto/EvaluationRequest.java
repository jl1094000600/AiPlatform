package com.aipal.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class EvaluationRequest {
    private Long datasetId;
    private Long agentId;
    private List<Long> agentIds;
    private String criteriaCode;
    private Map<String, Object> params;
    private Integer sampleCount;
    private Integer timeout;
    private Integer retryCount;
    private Integer concurrency;
}