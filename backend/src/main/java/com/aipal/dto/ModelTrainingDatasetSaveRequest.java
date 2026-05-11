package com.aipal.dto;

import lombok.Data;

@Data
public class ModelTrainingDatasetSaveRequest {
    private String fileName;
    private String datasetName;
    private String description;
    private String source;
    private String content;
}
