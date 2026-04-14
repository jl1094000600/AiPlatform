package com.aipal.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AgentGraphNode {
    private Long id;
    private String name;
    private String type;
    private Integer status;
    private LocalDateTime lastHeartbeat;
    private Integer instanceCount;
}
