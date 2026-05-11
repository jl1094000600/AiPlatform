package com.aipal.dto;

import lombok.Data;

@Data
public class AutomationDeployProfileRequest {
    private String profileName;
    private String deployType;
    private String environmentName;
    private Integer status;
    private String buildCommand;
    private String testCommand;
    private String healthCheckUrl;
    private Integer timeoutSeconds;
    private String dockerConfig;
    private String jenkinsConfig;
}
