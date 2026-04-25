package com.aipal.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchEvaluationRequest {
    private List<Long> datasetIds;
    private List<Long> agentIds;
    private String criteriaCode;
    private Boolean parallel;
    private Integer sampleCount;
    private Integer timeout;
    private Integer retryCount;
    private Integer concurrency;
}