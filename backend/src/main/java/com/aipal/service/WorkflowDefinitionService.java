package com.aipal.service;

import com.aipal.dto.WorkflowDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowDefinitionService {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "START", "AGENT", "CONDITION", "PARALLEL", "END"
    );

    private final ObjectMapper objectMapper;

    public WorkflowDefinition parseAndValidate(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("workflowDefinition is required");
        }

        WorkflowDefinition definition;
        try {
            definition = objectMapper.readValue(json, WorkflowDefinition.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("workflowDefinition must be valid JSON", e);
        }
        validate(definition);
        return definition;
    }

    public void validate(WorkflowDefinition definition) {
        if (definition == null || definition.getNodes() == null || definition.getNodes().isEmpty()) {
            throw new IllegalArgumentException("workflowDefinition.nodes must not be empty");
        }
        if (definition.getEdges() == null) {
            definition.setEdges(new ArrayList<>());
        }
        if (definition.getTimeoutSeconds() == null) {
            definition.setTimeoutSeconds(1800);
        }
        if (definition.getTimeoutSeconds() < 1 || definition.getTimeoutSeconds() > 86400) {
            throw new IllegalArgumentException("timeoutSeconds must be between 1 and 86400");
        }

        Map<String, WorkflowDefinition.Node> nodes = new HashMap<>();
        List<String> starts = new ArrayList<>();
        Set<String> ends = new HashSet<>();
        for (WorkflowDefinition.Node node : definition.getNodes()) {
            if (node == null || node.getId() == null || node.getId().isBlank()) {
                throw new IllegalArgumentException("Every workflow node must have an id");
            }
            if (nodes.putIfAbsent(node.getId(), node) != null) {
                throw new IllegalArgumentException("Duplicate workflow node id: " + node.getId());
            }
            String type = normalizeType(node.getType());
            node.setType(type);
            if (!SUPPORTED_TYPES.contains(type)) {
                throw new IllegalArgumentException("Unsupported workflow node type: " + type);
            }
            if ("START".equals(type)) starts.add(node.getId());
            if ("END".equals(type)) ends.add(node.getId());
            if ("AGENT".equals(type)
                    && (node.getTargetAgent() == null || node.getTargetAgent().isBlank())
                    && node.getAgentId() == null) {
                throw new IllegalArgumentException("AGENT node requires targetAgent or agentId: " + node.getId());
            }
            if ("CONDITION".equals(type)) {
                validateCondition(node);
            }
        }
        if (starts.size() != 1) {
            throw new IllegalArgumentException("Workflow must contain exactly one START node");
        }
        if (ends.isEmpty()) {
            throw new IllegalArgumentException("Workflow must contain at least one END node");
        }

        Map<String, List<String>> outgoing = new HashMap<>();
        for (WorkflowDefinition.Edge edge : definition.getEdges()) {
            if (edge == null || edge.getSource() == null || edge.getTarget() == null) {
                throw new IllegalArgumentException("Every workflow edge must have source and target");
            }
            if (!nodes.containsKey(edge.getSource()) || !nodes.containsKey(edge.getTarget())) {
                throw new IllegalArgumentException("Workflow edge endpoint does not exist: "
                        + edge.getSource() + " -> " + edge.getTarget());
            }
            outgoing.computeIfAbsent(edge.getSource(), ignored -> new ArrayList<>()).add(edge.getTarget());
        }

        for (WorkflowDefinition.Node node : definition.getNodes()) {
            int count = outgoing.getOrDefault(node.getId(), List.of()).size();
            if (!"END".equals(node.getType()) && count == 0) {
                throw new IllegalArgumentException("Non-END node has no outgoing edge: " + node.getId());
            }
            if (("START".equals(node.getType()) || "AGENT".equals(node.getType())) && count != 1) {
                throw new IllegalArgumentException(node.getType() + " node must have exactly one outgoing edge: " + node.getId());
            }
            if (("CONDITION".equals(node.getType()) || "PARALLEL".equals(node.getType())) && count < 2) {
                throw new IllegalArgumentException(node.getType() + " node must have at least two outgoing edges: " + node.getId());
            }
        }

        rejectCycles(starts.get(0), outgoing);
        Set<String> reachable = reachableFrom(starts.get(0), outgoing);
        if (ends.stream().noneMatch(reachable::contains)) {
            throw new IllegalArgumentException("No END node is reachable from START");
        }
        if (reachable.size() != nodes.size()) {
            throw new IllegalArgumentException("Every workflow node must be reachable from START");
        }
    }

    private void validateCondition(WorkflowDefinition.Node node) {
        String field = node.getConditionField();
        String operator = node.getOperator();
        if (node.getCondition() != null) {
            if (!node.getCondition().isObject()) {
                throw new IllegalArgumentException("CONDITION node must use a structured condition: " + node.getId());
            }
            field = node.getCondition().path("field").asText(field);
            operator = node.getCondition().path("operator").asText(operator);
        }
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("CONDITION node requires condition field: " + node.getId());
        }
        if (operator == null || !Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "CONTAINS", "EXISTS")
                .contains(operator.toUpperCase())) {
            throw new IllegalArgumentException("Unsupported condition operator for node: " + node.getId());
        }
    }

    private void rejectCycles(String start, Map<String, List<String>> outgoing) {
        rejectCycles(start, outgoing, new HashSet<>(), new HashSet<>());
    }

    private void rejectCycles(String node, Map<String, List<String>> outgoing,
                              Set<String> visiting, Set<String> visited) {
        if (visiting.contains(node)) {
            throw new IllegalArgumentException("Workflow graph must not contain cycles");
        }
        if (!visited.add(node)) return;
        visiting.add(node);
        for (String target : outgoing.getOrDefault(node, List.of())) {
            rejectCycles(target, outgoing, visiting, visited);
        }
        visiting.remove(node);
    }

    private Set<String> reachableFrom(String start, Map<String, List<String>> outgoing) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) continue;
            queue.addAll(outgoing.getOrDefault(current, List.of()));
        }
        return visited;
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.trim().toUpperCase();
    }
}
