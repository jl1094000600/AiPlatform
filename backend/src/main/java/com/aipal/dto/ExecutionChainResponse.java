package com.aipal.dto;

import lombok.Data;
import java.util.List;

@Data
public class ExecutionChainResponse {
    private String sessionId;
    private String workflowId;
    private List<ExecutionChainNode> chain;
}
