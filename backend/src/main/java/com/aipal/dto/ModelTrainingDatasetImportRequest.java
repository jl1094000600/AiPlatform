package com.aipal.dto;

import lombok.Data;

@Data
public class ModelTrainingDatasetImportRequest {
    private String fileName;
    private String content;
}
