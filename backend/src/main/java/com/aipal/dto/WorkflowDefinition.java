package com.aipal.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Graph DSL v1 execution contract.
 * Layout (canvas positions) is stored separately and never feeds the executor.
 */
@Data
public class WorkflowDefinition {
    /** Contract version. Unknown major versions are rejected. */
    private Integer schemaVersion = 1;

    private Integer timeoutSeconds = 1800;
    private List<Node> nodes = new ArrayList<>();
    private List<Edge> edges = new ArrayList<>();
    /** Workflow-level variable declarations referenced as {@code {{variables.<name>}}}. */
    private List<Variable> variables = new ArrayList<>();

    @Data
    public static class Variable {
        private String name;
        private String type = "string";
        private boolean required;
        private Object defaultValue;
        private String description;
    }

    @Data
    public static class Node {
        private String id;
        private String type;
        private String label;
        private String targetAgent;
        private Long agentId;
        private Map<String, Object> params;
        /**
         * Structured inputs. String values may contain {@code {{...}}} references.
         * Kept separate from {@code params} so validation can walk references.
         */
        private Map<String, Object> inputs;
        private Integer timeout;
        private Integer retryCount;
        private JsonNode condition;
        private String conditionField;
        private String operator;
        private Object conditionValue;
        /** Optional JSON Schema describing this node's outputs for downstream field checks. */
        private JsonNode outputsSchema;
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
