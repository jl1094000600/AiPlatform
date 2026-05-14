package com.aipal.dto;

import lombok.Data;

@Data
public class CodeQualityRuleRequest {
    private Long id;
    private String ruleCode;
    private String category;
    private String severity;
    private String title;
    private String description;
    private String checkPrompt;
    private Boolean enabled;
}
