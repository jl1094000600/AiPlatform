package com.aipal.dto;

import lombok.Data;

@Data
public class AutomationPipelineRequest {
    private String productLine;
    private String projectName;
    private String requirementTitle;
    private String requirementSummary;
    private String initiator;
    private Long initiatorUserId;
    private String initiatorUsername;
    private Long modelId;
    private String aiModelCode;
    private String templateFile;
    private String projectMode;
    private String codeLevel;
    private Boolean generateFrontend;
    private Boolean generateBackend;
    private String frontendOutputPath;
    private String backendOutputPath;
    private Long skillId;
    private String skillSnapshot;
}
