package com.aipal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent Push注册请求DTO
 */
@Data
public class AgentRegisterRequest {
    @NotBlank(message = "agentCode 不能为空")
    @Size(max = 64, message = "agentCode 长度不能超过64")
    private String agentCode;

    @NotBlank(message = "agentName 不能为空")
    @Size(max = 128, message = "agentName 长度不能超过128")
    private String agentName;

    @Size(max = 512, message = "description 长度不能超过512")
    private String description;

    @Size(max = 64, message = "category 长度不能超过64")
    private String category;

    @Size(max = 256, message = "apiUrl 长度不能超过256")
    private String apiUrl;

    @Size(max = 128, message = "healthEndpoint 长度不能超过128")
    private String healthEndpoint;

    private String requestSchema;
    private String responseSchema;

    @NotBlank(message = "instanceId 不能为空")
    @Size(max = 64, message = "instanceId 长度不能超过64")
    private String instanceId;

    /** 心跳间隔（秒），默认30 */
    private Integer heartbeatInterval;