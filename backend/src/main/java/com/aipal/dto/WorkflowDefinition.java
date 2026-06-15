package com.aipal.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class WorkflowDefinition {
    private Integer timeoutSeconds = 1800;
    private List<Node> nodes = new ArrayList<>();
    private List<Edge> edges = new ArrayList<>();

    @Data
    public static class Node {
        private String id;
        private String type;
        private String label;
        private String targetAgent;
        private Long agentId;
        private Map<String, Object> params;
        private Integer timeout;
        private Integer retryCount;
        private JsonNode condition;
        private String conditionField;
        private String operator;
        private Object conditionValue;
    }

    @Data
    public static class Edge {
        @JsonAlias({"sourceId", "from"})
        private String source;

        @JsonAlias({"targetId", "to"})
        private String target;

        private String condition;
        private String label;
    }
}
