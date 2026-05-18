package com.aipal.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AgentGraphEdge {
    private Long edgeId;
    private Long source;
    private Long target;
    private String sourceAgentCode;
    private String targetAgentCode;
    private String edgeSource;
    private String edgeType;
    private String triggerIntent;
    private Integer enabled;
    private String suitabilityLevel;
    private Integer suitabilityScore;
    private String suitabilityMessage;
    private Long callCount;
    private Double avgResponseTime;
    private LocalDateTime lastCallTime;
}
