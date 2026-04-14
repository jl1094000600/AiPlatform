package com.aipal.dto;

import lombok.Data;
import java.util.List;

@Data
public class AgentDetailResponse {
    private Long agentId;
    private String agentName;
    private String description;
    private String status;
    private String agentType;
    private String modelName;
    private String version;
    private List<String> capabilities;
    private List<AgentInstance> instances;
    private List<AgentRef> upstreamAgents;
    private List<AgentRef> downstreamAgents;
    private AgentStats todayStats;
}
