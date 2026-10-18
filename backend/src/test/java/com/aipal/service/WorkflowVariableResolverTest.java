package com.aipal.service;

import com.aipal.dto.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowVariableResolverTest {

    private final WorkflowVariableResolver resolver = new WorkflowVariableResolver();

    @Test
    void resolvesWholeStringReferenceAsTypedValue() {
        Map<String, Object> resolved = resolver.resolveInputs(
                Map.of("count", "{{variables.limit}}"),
                List.of(variable("limit", 25)),
                Map.of(),
                Map.of());
        assertEquals(25, resolved.get("count"));
    }

    @Test
    void interpolatesEmbeddedReferencesIntoText() {
        Map<String, Object> resolved = resolver.resolveInputs(
                Map.of("prompt", "write about {{variables.topic}} using {{inputs.style}}"),
                List.of(variable("topic", "gravity")),
                Map.of("style", "brief"),
                Map.of());
        assertEquals("write about gravity using brief", resolved.get("prompt"));
    }

    @Test
    void resolvesUpstreamNodeOutputs() {
        Map<String, Object> resolved = resolver.resolveInputs(
                Map.of("plan", "{{nodes.n1.outputs.plan}}"),
                List.of(),
                Map.of(),
                Map.of("n1", Map.of("plan", "step-by-step")));
        assertEquals("step-by-step", resolved.get("plan"));
    }

    @Test
    void prefersRunInputOverVariableDefault() {
        Map<String, Object> resolved = resolver.resolveInputs(
                Map.of("topic", "{{variables.topic}}"),
                List.of(variable("topic", "default-topic")),
                Map.of("topic", "from-run"),
                Map.of());
        assertEquals("from-run", resolved.get("topic"));
    }

    @Test
    void usesVariableDefaultWhenRunInputMissing() {
        Map<String, Object> resolved = resolver.resolveInputs(
                Map.of("topic", "{{variables.topic}}"),
                List.of(variable("topic", "default-topic")),
                Map.of(),
                Map.of());
        assertEquals("default-topic", resolved.get("topic"));
    }

    @Test
    void failsClosedOnUnknownVariableReference() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveInputs(
                        Map.of("x", "{{variables.missing}}"),
                        List.of(),
                        Map.of(),
                        Map.of()));
        assertTrue(error.getMessage().contains("Unknown variable reference"));
    }

    @Test
    void failsClosedWhenNodeOutputsMissing() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveInputs(
                        Map.of("x", "{{nodes.n1.outputs.plan}}"),
                        List.of(),
                        Map.of(),
                        Map.of()));
        assertTrue(error.getMessage().contains("No outputs recorded"));
    }

    @Test
    void requiresDeclaredRequiredVariable() {
        WorkflowDefinition.Variable required = variable("topic", null);
        required.setRequired(true);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveInputs(
                        Map.of("x", "{{variables.topic}}"),
                        List.of(required),
                        Map.of(),
                        Map.of()));
        assertTrue(error.getMessage().contains("Missing required workflow variable"));
    }

    @Test
    void resolvesNestedStructures() {
        Map<String, Object> resolved = resolver.resolveInputs(
                Map.of("config", Map.of("query", "{{variables.topic}}", "tags", List.of("{{inputs.tag}}"))),
                List.of(variable("topic", "t1")),
                Map.of("tag", "urgent"),
                Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) resolved.get("config");
        assertEquals("t1", config.get("query"));
        assertEquals(List.of("urgent"), config.get("tags"));
    }

    private WorkflowDefinition.Variable variable(String name, Object defaultValue) {
        WorkflowDefinition.Variable variable = new WorkflowDefinition.Variable();
        variable.setName(name);
        variable.setDefaultValue(defaultValue);
        return variable;
    }
}
