package com.aipal.dto;

import lombok.Data;

@Data
public class ModelTrainingDatasetMockRequest {
    private String fileName;
    private String topic;
    private Integer count;
    private String modelCode;
}
