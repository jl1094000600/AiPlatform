package com.aipal.dto;

import lombok.Data;

@Data
public class AgentGraphEdgeRequest {
    private Long sourceAgentId;
    private Long targetAgentId;
    private String edgeType;
    private String triggerIntent;
    private String conditionExpression;
    private String paramMapping;
    private Integer timeoutSeconds;
    private Integer retryCount;
    private Integer enabled;
}
