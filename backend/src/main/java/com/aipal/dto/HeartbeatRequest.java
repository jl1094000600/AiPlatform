package com.aipal.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class HeartbeatRequest {
    private Long agentId;
    private String instanceId;
    private Integer healthScore;
    private String endpoint;
    private List<String> capabilities;
    private Map<String, Object> metadata;
}
