package com.aipal.dto;

import lombok.Data;
import java.util.List;

@Data
public class AgentGraphResponse {
    private List<AgentGraphNode> nodes;
    private List<AgentGraphEdge> edges;
}
