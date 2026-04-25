package com.aipal.dto;

import lombok.Data;

@Data
public class CriteriaConfigRequest {
    private String criteriaCode;
    private String criteriaName;
    private String description;
    private String formula;
    private Double weight;
    private String thresholds;
}