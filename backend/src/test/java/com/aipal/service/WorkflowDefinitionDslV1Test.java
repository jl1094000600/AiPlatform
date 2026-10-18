package com.aipal.service;

import com.aipal.dto.WorkflowDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowDefinitionDslV1Test {

    private final WorkflowDefinitionService service = new WorkflowDefinitionService(new ObjectMapper());

    @Test
    void acceptsSchemaVersionOneWithVariablesAndReferences() {
        WorkflowDefinition definition = service.parseAndValidate("""
                {
                  "schemaVersion": 1,
                  "variables": [{"name": "topic", "type": "string", "required": true}],
                  "nodes": [
                    {"id": "1", "type": "START"},
                    {"id": "2", "type": "AGENT", "agentId": 3,
                     "inputs": {"prompt": "write about {{variables.topic}} from {{nodes.1.outputs.start}}"}},
                    {"id": "3", "type": "END"}
                  ],
                  "edges": [{"source": "1", "target": "2"}, {"source": "2", "target": "3"}]
                }
                """);
        assertEquals(1, definition.getSchemaVersion());
        assertEquals(1, definition.getVariables().size());
    }

    @Test
    void rejectsUnknownSchemaVersion() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.parseAndValidate("""
                        {"schemaVersion": 2,
                         "nodes":[{"id":"1","type":"START"},{"id":"2","type":"END"}],
                         "edges":[{"source":"1","target":"2"}]}
                        """));
        assertTrue(error.getMessage().contains("schemaVersion"));
    }

    @Test
    void rejectsUnknownVariableReference() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.parseAndValidate("""
                        {"schemaVersion": 1,
                         "variables": [{"name": "topic"}],
                         "nodes": [
                           {"id":"1","type":"START"},
                           {"id":"2","type":"AGENT","agentId":3,"inputs":{"prompt":"{{variables.missing}}"}},
                           {"id":"3","type":"END"}
                         ],
                         "edges":[{"source":"1","target":"2"},{"source":"2","target":"3"}]}
                        """));
        assertTrue(error.getMessage().contains("Unknown variable reference"));
    }

    @Test
    void rejectsMalformedNodeReference() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.parseAndValidate("""
                        {"schemaVersion": 1,
                         "nodes": [
                           {"id":"1","type":"START"},
                           {"id":"2","type":"AGENT","agentId":3,"inputs":{"prompt":"{{nodes.9.outputs.x}}"}},
                           {"id":"3","type":"END"}
                         ],
                         "edges":[{"source":"1","target":"2"},{"source":"2","target":"3"}]}
                        """));
        assertTrue(error.getMessage().contains("Unknown node reference"));
    }

    @Test
    void acceptsRunInputReference() {
        assertDoesNotThrow(() -> service.parseAndValidate("""
                {"schemaVersion": 1,
                 "nodes": [
                   {"id":"1","type":"START"},
                   {"id":"2","type":"AGENT","agentId":3,"inputs":{"prompt":"{{inputs.userQuery}}"}},
                   {"id":"3","type":"END"}
                 ],
                 "edges":[{"source":"1","target":"2"},{"source":"2","target":"3"}]}
                """));
    }

    @Test
    void rejectsDuplicateVariableNames() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.parseAndValidate("""
                        {"schemaVersion": 1,
                         "variables": [{"name": "topic"}, {"name": "topic"}],
                         "nodes":[{"id":"1","type":"START"},{"id":"2","type":"END"}],
                         "edges":[{"source":"1","target":"2"}]}
                        """));
        assertTrue(error.getMessage().contains("Duplicate workflow variable"));
    }

    @Test
    void legacyDefinitionWithoutSchemaVersionDefaultsToV1() {
        WorkflowDefinition definition = service.parseAndValidate("""
                {"nodes":[{"id":"1","type":"START"},{"id":"2","type":"END"}],
                 "edges":[{"source":"1","target":"2"}]}
                """);
        assertEquals(1, definition.getSchemaVersion());
    }
}
