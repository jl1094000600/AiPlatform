package com.aipal.dto;

import lombok.Data;
import java.util.Map;

@Data
public class AgentCallRequest {
    private Long agentId;
    private String agentVersion;
    private Long bizModuleId;
    private Map<String, Object> params;
}
