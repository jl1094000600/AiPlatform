package com.aipal.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ModelTrainingJob {
    private String id;
    private String status;
    private String modelPath;
    private String trainData;
    private String outputDir;
    private Boolean unifiedFinetuning;
    private Boolean dryRun;
    private String logPath;
    private String metricsPath;
    private Map<String, Object> metrics;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
