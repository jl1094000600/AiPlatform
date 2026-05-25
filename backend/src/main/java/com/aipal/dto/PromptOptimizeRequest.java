package com.aipal.dto;

import lombok.Data;

@Data
public class PromptOptimizeRequest {
    private Long modelId;
    private String optimizeGoal;
}
