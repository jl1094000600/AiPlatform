package com.aipal.controller;

import com.aipal.security.RequirePermission;
import com.aipal.service.ToolCallContractView;
import com.aipal.agent.runtime.trace.SpanContractView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R03 contract tests: path aliases and DTO field names must match
 * {@code front/src/api/agentRuntime.js} + the agent-runtime panels.
 */
class AgentRuntimeContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Test
    void agentRunControllerExposesBothToolListPaths() {
        RequestMapping mapping = AgentRunController.class.getAnnotation(RequestMapping.class);
        assertEquals("/api/v1/agent-runs", mapping.value()[0]);

        Method toolCalls = Arrays.stream(AgentRunController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("toolCalls"))
                .findFirst()
                .orElseThrow();
        GetMapping get = toolCalls.getAnnotation(GetMapping.class);
        Set<String> paths = Set.of(get.value());
        assertTrue(paths.contains("/{id}/tool-calls"), "legacy path must remain");
        assertTrue(paths.contains("/{id}/tools"), "frontend path /agent-runs/{id}/tools must exist");
    }

    @Test
    void agentRunControllerExposesTraceAndRespondPaths() {
        Set<String> routes = Arrays.stream(AgentRunController.class.getDeclaredMethods())
                .map(this::route)
                .collect(Collectors.toSet());
        assertTrue(routes.contains("GET /{id}/trace"), "trace path required");
        assertTrue(routes.contains("POST /{id}/tools/{callId}/respond"), "respond path required");
    }

    @Test
    void workspaceControllerExposesRunScopedQuery() {
        RequestMapping mapping = WorkspaceController.class.getAnnotation(RequestMapping.class);
        assertEquals("/api/v1/workspaces", mapping.value()[0]);

        Method getWorkspace = Arrays.stream(WorkspaceController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("getWorkspace"))
                .findFirst()
                .orElseThrow();
        GetMapping get = getWorkspace.getAnnotation(GetMapping.class);
        assertArrayEquals(new String[]{"/{runId}"}, get.value());
        RequirePermission permission = getWorkspace.getAnnotation(RequirePermission.class);
        assertEquals("agent:invoke", permission.value(), "workspace query must require permission");
    }

    @Test
    void toolCallContractViewCarriesFrontendFieldNames() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 10, 15, 8, 30, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 15, 8, 30, 2);
        ToolCallContractView view = new ToolCallContractView(
                "call-9", 91L, 5L, "call-9",
                "fs.read", "READ", "ALLOW",
                "SUCCESS", "SUCCEEDED",
                "path=src/App.java", "read 128 lines", "read 128 lines", null,
                1, 2, start, start, end, 2000L);

        JsonNode json = MAPPER.valueToTree(view);
        // Frontend ToolCallsPanel field names
        assertTrue(json.has("id"));
        assertTrue(json.has("toolName"));
        assertTrue(json.has("riskLevel"));
        assertTrue(json.has("status"));
        assertTrue(json.has("startedAt"));
        assertTrue(json.has("durationMs"));
        assertTrue(json.has("argsSummary"));
        assertTrue(json.has("resultSummary"));
        // Backend aliases retained
        assertTrue(json.has("callId"));
        assertTrue(json.has("startTime"));
        assertTrue(json.has("outputSummary"));
        assertTrue(json.has("statusRaw"));
        assertEquals("call-9", json.get("id").asText());
        assertEquals("SUCCESS", json.get("status").asText());
        assertEquals("SUCCEEDED", json.get("statusRaw").asText());
    }

    @Test
    void spanContractViewCarriesDualFieldNames() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 10, 15, 8, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 15, 8, 0, 3);
        SpanContractView view = new SpanContractView(
                "sp-1", "sp-1",
                "tool", "TOOL_CALL",
                "fs.read",
                "SUCCESS", "OK",
                1L, 2L, "call-1",
                3, 4,
                "fs.read src/App.java", "fs.read src/App.java",
                start, start, end, 3000L);

        JsonNode json = MAPPER.valueToTree(view);
        // Frontend TraceTimelinePanel field names
        assertEquals("sp-1", json.get("id").asText());
        assertEquals("tool", json.get("type").asText());
        assertEquals("SUCCESS", json.get("status").asText());
        assertTrue(json.has("startedAt"));
        assertEquals("fs.read src/App.java", json.get("detail").asText());
        assertEquals(3000L, json.get("durationMs").asLong());
        // Backend aliases retained
        assertEquals("sp-1", json.get("spanId").asText());
        assertEquals("TOOL_CALL", json.get("spanType").asText());
        assertEquals("OK", json.get("spanStatus").asText());
        assertTrue(json.has("startTime"));
        assertEquals("fs.read src/App.java", json.get("summary").asText());
    }

    @Test
    void workspaceViewCarriesContractFieldNames() throws Exception {
        WorkspaceController.WorkspaceView view = new WorkspaceController.WorkspaceView(
                "READY",
                "/workspace/run-1",
                java.util.List.of(new WorkspaceController.FileView("pom.xml", 2048L)),
                "[INFO] ok");

        JsonNode json = MAPPER.valueToTree(view);
        assertEquals("READY", json.get("status").asText());
        assertEquals("/workspace/run-1", json.get("workDir").asText());
        assertTrue(json.get("files").isArray());
        assertEquals("pom.xml", json.get("files").get(0).get("path").asText());
        assertEquals(2048L, json.get("files").get(0).get("size").asLong());
        assertEquals("[INFO] ok", json.get("recentLogs").asText());
    }

    private String route(Method method) {
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) return "GET " + (get.value().length == 0 ? "" : get.value()[0]);
        org.springframework.web.bind.annotation.PostMapping post =
                method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
        if (post != null) return "POST " + (post.value().length == 0 ? "" : post.value()[0]);
        return method.getName();
    }
}
