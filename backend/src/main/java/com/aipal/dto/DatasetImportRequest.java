package com.aipal.dto;

import lombok.Data;
import java.util.List;

@Data
public class DatasetImportRequest {
    private String datasetName;
    private String description;
    private String category;
    private String format;
    private List<FieldSchema> fields;

    @Data
    public static class FieldSchema {
        private String fieldName;
        private String fieldType;
        private String ruleType;
        private String ruleConfig;
        private Boolean nullable;
    }
}