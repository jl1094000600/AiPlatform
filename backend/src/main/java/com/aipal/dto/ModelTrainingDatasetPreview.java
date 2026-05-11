package com.aipal.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ModelTrainingDatasetPreview {
    private String fileName;
    private String topic;
    private Integer count;
    private String modelCode;
    private List<Map<String, Object>> records;
    private String content;
}
