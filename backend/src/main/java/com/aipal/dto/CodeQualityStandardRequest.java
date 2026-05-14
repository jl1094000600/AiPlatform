package com.aipal.dto;

import lombok.Data;

import java.util.List;

@Data
public class CodeQualityStandardRequest {
    private String standardCode;
    private String standardName;
    private String description;
    private String language;
    private String framework;
    private Integer status;
    private String gateConfig;
    private List<CodeQualityRuleRequest> rules;
}
