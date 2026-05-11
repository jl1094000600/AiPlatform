package com.aipal.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AutomationDeployProfileResponse {
    private Long id;
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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
