package com.aipal.service;

import com.aipal.dto.WorkflowDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves Graph DSL v1 variable references:
 *   {{variables.<name>}}  workflow-level variables
 *   {{nodes.<nodeId>.outputs.<field>}}  upstream node outputs
 *   {{inputs.<field>}}  run/try-run inputs
 *
 * Unknown references fail closed; the resolver never invents values.
 */
@Component
public class WorkflowVariableResolver {

    private static final Pattern REFERENCE = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*}}");

    /**
     * Resolve every string (or nested string) in {@code inputs}.
     *
     * @param inputs        node inputs from the definition
     * @param variables     declared workflow variables with defaults
     * @param runInputs     trigger/try-run inputs
     * @param nodeOutputs   map of nodeId -> output map from completed upstream nodes
     */
    public Map<String, Object> resolveInputs(Map<String, Object> inputs,
                                             List<WorkflowDefinition.Variable> variables,
                                             Map<String, Object> runInputs,
                                             Map<String, Map<String, Object>> nodeOutputs) {
        Map<String, Object> resolvedVariables = collectVariables(variables, runInputs);
        if (inputs == null || inputs.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            resolved.put(entry.getKey(), resolveValue(entry.getValue(), resolvedVariables, runInputs, nodeOutputs));
        }
        return resolved;
    }

    private Map<String, Object> collectVariables(List<WorkflowDefinition.Variable> variables,
                                                 Map<String, Object> runInputs) {
        Map<String, Object> values = new HashMap<>();
        if (variables != null) {
            for (WorkflowDefinition.Variable variable : variables) {
                if (variable == null || variable.getName() == null) continue;
                Object value = runInputs != null ? runInputs.get(variable.getName()) : null;
                if (value == null) {
                    value = variable.getDefaultValue();
                }
                if (value == null && variable.isRequired()) {
                    throw new IllegalArgumentException("Missing required workflow variable: " + variable.getName());
                }
                values.put(variable.getName(), value);
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private Object resolveValue(Object value,
                                Map<String, Object> variables,
                                Map<String, Object> runInputs,
                                Map<String, Map<String, Object>> nodeOutputs) {
        if (value == null) return null;
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                resolved.put(String.valueOf(entry.getKey()),
                        resolveValue(entry.getValue(), variables, runInputs, nodeOutputs));
            }
            return resolved;
        }
        if (value instanceof List<?> items) {
            List<Object> resolved = new ArrayList<>(items.size());
            for (Object item : items) resolved.add(resolveValue(item, variables, runInputs, nodeOutputs));
            return resolved;
        }
        if (!(value instanceof String text)) return value;

        Matcher matcher = REFERENCE.matcher(text);
        if (!matcher.find()) return text;
        // Whole-string reference returns the raw typed value; embedded references stringify.
        matcher.reset();
        if (matcher.matches()) {
            return lookup(matcher.group(1).trim(), variables, runInputs, nodeOutputs);
        }
        StringBuffer buffer = new StringBuffer();
        matcher.reset();
        while (matcher.find()) {
            Object resolved = lookup(matcher.group(1).trim(), variables, runInputs, nodeOutputs);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(resolved == null ? "" : String.valueOf(resolved)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private Object lookup(String path,
                          Map<String, Object> variables,
                          Map<String, Object> runInputs,
                          Map<String, Map<String, Object>> nodeOutputs) {
        if (path.startsWith("variables.")) {
            String name = path.substring("variables.".length());
            if (!variables.containsKey(name)) {
                throw new IllegalArgumentException("Unknown variable reference: " + path);
            }
            return variables.get(name);
        }
        if (path.startsWith("inputs.")) {
            String name = path.substring("inputs.".length());
            return runInputs == null ? null : runInputs.get(name);
        }
        if (path.startsWith("nodes.")) {
            String[] parts = path.split("\\.");
            if (parts.length < 4 || !"outputs".equals(parts[2])) {
                throw new IllegalArgumentException("Node reference must be nodes.<nodeId>.outputs.<field>: " + path);
            }
            Map<String, Object> outputs = nodeOutputs == null ? null : nodeOutputs.get(parts[1]);
            if (outputs == null) {
                throw new IllegalArgumentException("No outputs recorded for node reference: " + path);
            }
            Object current = outputs;
            for (int i = 3; i < parts.length; i++) {
                if (!(current instanceof Map<?, ?> map)) {
                    throw new IllegalArgumentException("Cannot traverse node reference: " + path);
                }
                current = map.get(parts[i]);
            }
            return current;
        }
        throw new IllegalArgumentException("Unsupported reference: " + path);
    }
}
