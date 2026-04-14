package com.aipal.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AgentResponse {
    private Long id;
    private String agentCode;
    private String agentName;
    private String description;
    private String category;
    private String apiUrl;
    private String httpMethod;
    private String requestSchema;
    private String responseSchema;
    private Integer status;
    private String statusName;
    private Long ownerId;
    private String ownerName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
