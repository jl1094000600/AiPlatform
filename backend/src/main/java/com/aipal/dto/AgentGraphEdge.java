package com.aipal.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AgentGraphEdge {
    private Long source;
    private Long target;
    private Long callCount;
    private Double avgResponseTime;
    private LocalDateTime lastCallTime;
}
