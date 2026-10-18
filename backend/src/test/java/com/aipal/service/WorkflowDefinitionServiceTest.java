package com.aipal.service;

import com.aipal.dto.WorkflowDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowDefinitionServiceTest {

    private final WorkflowDefinitionService service = new WorkflowDefinitionService(new ObjectMapper());

    @Test
    void acceptsReachableSupportedGraph() {
        WorkflowDefinition definition = service.parseAndValidate("""
                {"timeoutSeconds":60,"nodes":[
                  {"id":"start","type":"START"},
                  {"id":"end","type":"END"}
                ],"edges":[{"source":"start","target":"end"}]}
                """);

        assertEquals(2, definition.getNodes().size());
        assertEquals(60, definition.getTimeoutSeconds());
    }

    @Test
    void acceptsGraphContainingAllFourPocNodeTypes() {
        WorkflowDefinition definition = service.parseAndValidate("""
                {"nodes":[
                  {"id":"start","type":"START"},
                  {"id":"check","type":"CONDITION","conditionField":"approved","operator":"EQ","conditionValue":true},
                  {"id":"agent","type":"AGENT","targetAgent":"writer"},
                  {"id":"end","type":"END"}
                ],"edges":[
                  {"source":"start","target":"check"},
                  {"source":"check","target":"agent","condition":"true"},
                  {"source":"check","target":"end","condition":"false"},
                  {"source":"agent","target":"end"}
                ]}
                """);

        assertEquals(4, definition.getNodes().size());
        assertEquals(1800, definition.getTimeoutSeconds());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PARALLEL", "JOIN", " parallel ", " join "})
    void rejectsDeferredParallelTypesWithActionableMessage(String type) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.parseAndValidate("""
                        {"nodes":[
                          {"id":"start","type":"START"},
                          {"id":"blocked","type":"%s"},
                          {"id":"end","type":"END"}
                        ],"edges":[
                          {"source":"start","target":"blocked"},
                          {"source":"blocked","target":"end"}
                        ]}
                        """.formatted(type)));

        assertTrue(error.getMessage().contains(type.trim().toUpperCase()));
        assertTrue(error.getMessage().contains("JOIN barrier"));
        assertTrue(error.getMessage().contains("deduplication tests"));
        assertTrue(error.getMessage().contains("blocked"));
    }

    @Test
    void rejectsDuplicateNodeIds() {
        assertThrows(IllegalArgumentException.class, () -> service.parseAndValidate("""
                {"nodes":[
                  {"id":"same","type":"START"},
                  {"id":"same","type":"END"}
                ],"edges":[]}
                """));
    }

    @Test
    void rejectsMissingEdgeEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> service.parseAndValidate("""
                {"nodes":[
                  {"id":"start","type":"START"},
                  {"id":"end","type":"END"}
                ],"edges":[{"source":"start","target":"missing"}]}
                """));
    }

    @Test
    void rejectsUnreachableEndNode() {
        assertThrows(IllegalArgumentException.class, () -> service.parseAndValidate("""
                {"nodes":[
                  {"id":"start","type":"START"},
                  {"id":"agent","type":"AGENT","targetAgent":"writer"},
                  {"id":"end","type":"END"}
                ],"edges":[{"source":"start","target":"agent"}]}
                """));
    }

    @Test
    void rejectsUnsupportedNodeType() {
        assertThrows(IllegalArgumentException.class, () -> service.parseAndValidate("""
                {"nodes":[
                  {"id":"start","type":"START"},
                  {"id":"loop","type":"LOOP"},
                  {"id":"end","type":"END"}
                ],"edges":[
                  {"source":"start","target":"loop"},
                  {"source":"loop","target":"end"}
                ]}
                """));
    }

    @Test
    void rejectsDisconnectedNodes() {
        assertThrows(IllegalArgumentException.class, () -> service.parseAndValidate("""
                {"nodes":[
                  {"id":"start","type":"START"},
                  {"id":"end","type":"END"},
                  {"id":"orphan","type":"AGENT","targetAgent":"writer"},
                  {"id":"orphanEnd","type":"END"}
                ],"edges":[
                  {"source":"start","target":"end"},
                  {"source":"orphan","target":"orphanEnd"}
                ]}
                """));
    }
}
