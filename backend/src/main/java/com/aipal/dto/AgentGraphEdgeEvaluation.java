package com.aipal.dto;

import lombok.Data;

import java.util.List;

@Data
public class AgentGraphEdgeEvaluation {
    private Boolean suitable;
    private String level;
    private Integer score;
    private String message;
    private List<String> reasons;
    private String recommendedEdgeType;
    private String recommendedTriggerIntent;
}
