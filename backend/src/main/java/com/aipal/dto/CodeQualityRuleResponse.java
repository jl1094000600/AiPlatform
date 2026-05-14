package com.aipal.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CodeQualityRuleResponse {
    private Long id;
    private Long standardId;
    private String ruleCode;
    private String category;
    private String severity;
    private String title;
    private String description;
    private String checkPrompt;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
