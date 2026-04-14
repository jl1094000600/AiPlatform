package com.aipal.dto;

import lombok.Data;

@Data
public class AgentRef {
    private Long agentId;
    private String agentName;
    private String callType;
    private Integer callCountPerHour;
}
