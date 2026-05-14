package com.aipal.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CodeQualityStandardResponse {
    private Long id;
    private String standardCode;
    private String standardName;
    private String description;
    private String language;
    private String framework;
    private Integer status;
    private String gateConfig;
    private List<CodeQualityRuleResponse> rules;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
