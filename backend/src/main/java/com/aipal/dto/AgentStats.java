package com.aipal.dto;

import lombok.Data;

@Data
public class AgentStats {
    private Long totalCalls;
    private Long successCalls;
    private Long failedCalls;
    private Double avgDuration;
    private Double peakQps;
}
