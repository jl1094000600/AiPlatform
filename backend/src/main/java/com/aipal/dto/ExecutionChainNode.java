package com.aipal.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ExecutionChainNode {
    private String taskId;
    private Long sourceAgentId;
    private String sourceAgentName;
    private Long targetAgentId;
    private String targetAgentName;
    private String status;
    private String taskType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
}
