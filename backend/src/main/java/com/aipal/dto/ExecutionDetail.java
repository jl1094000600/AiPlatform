package com.aipal.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExecutionDetail {
    private String executionId;
    private String workflowId;
    private String workflowName;
    private String workflowCode;
    private String triggerType;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long totalDuration;
    private List<NodeExecution> nodes;
}
