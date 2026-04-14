package com.aipal.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class NodeExecution {
    private String nodeId;
    private String nodeName;
    private String nodeType;
    private Long agentId;
    private String agentName;
    private String status;
    private Long duration;
    private Map<String, Object> input;
    private Map<String, Object> output;
    private String errorMessage;
    private Integer parallelIndex;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
