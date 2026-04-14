package com.aipal.dto;

import lombok.Data;

@Data
public class AgentUpdateRequest {
    private String agentName;
    private String description;
    private String category;
    private String apiUrl;
    private String httpMethod;
    private String requestSchema;
    private String responseSchema;
    private Long ownerId;
}
