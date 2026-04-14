package com.aipal.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AgentInstance {
    private String instanceId;
    private String status;
    private Double load;
    private Integer queueSize;
    private LocalDateTime lastHeartbeat;
    private String version;
}
