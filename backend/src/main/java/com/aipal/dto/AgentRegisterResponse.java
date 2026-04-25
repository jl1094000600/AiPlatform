package com.aipal.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Agent注册响应DTO
 */
@Data
public class AgentRegisterResponse {
    private Boolean success;
    private String agentCode;
    private String instanceId;
    private String message;
    private LocalDateTime registeredTime;
    private Integer heartbeatInterval;
    private Integer heartbeatTimeout;
}
