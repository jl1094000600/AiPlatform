package com.aipal.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ModelTrainingRequest {
    private String modelPath;
    private String trainData;
    private String outputDir;
    private Integer epochs;
    private BigDecimal learningRate;
    private Integer queryMaxLen;
    private Integer passageMaxLen;
    private Integer trainGroupSize;
    private Boolean unifiedFinetuning;
    private Boolean dryRun;
    private String device;
}
