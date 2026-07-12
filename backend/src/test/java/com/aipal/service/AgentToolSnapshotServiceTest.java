package com.aipal.service;

import com.aipal.entity.AgentToolDefinition;
import com.aipal.entity.AiAgentVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolSnapshotServiceTest {

    @Test
    void freezesAgentVersionWhitelistAgainstEnabledRegistryTools() {
        AgentToolRegistryService registryService = mock(AgentToolRegistryService.class);
        when(registryService.listEnabled()).thenReturn(List.of(
                tool("memory.search", "READ_ONLY"),
                tool("model.ask", "READ_ONLY")));
        AiAgentVersion version = new AiAgentVersion();
        version.setConfig("{\"toolWhitelist\":[\"memory.search\",\"missing.tool\"]}");
        AgentToolSnapshotService service = new AgentToolSnapshotService(registryService, new ObjectMapper());

        Map<String, Object> snapshot = service.snapshotFor(version);

        assertEquals(AgentToolSnapshotService.MODE_AGENT_VERSION, snapshot.get("whitelistMode"));
        assertEquals(List.of("memory.search"), snapshot.get("allowedToolNames"));
        assertEquals(List.of("missing.tool"), snapshot.get("missingToolNames"));
        assertEquals(1, ((List<?>) snapshot.get("tools")).size());
    }

    @Test
    void defaultsToAllEnabledToolsWhenVersionHasNoWhitelist() {
        AgentToolRegistryService registryService = mock(AgentToolRegistryService.class);
        when(registryService.listEnabled()).thenReturn(List.of(
                tool("memory.search", "READ_ONLY"),
                tool("model.ask", "READ_ONLY")));
        AgentToolSnapshotService service = new AgentToolSnapshotService(registryService, new ObjectMapper());

        Map<String, Object> snapshot = service.snapshotFor(new AiAgentVersion());

        assertEquals(AgentToolSnapshotService.MODE_REGISTRY_ENABLED, snapshot.get("whitelistMode"));
        assertEquals(List.of("memory.search", "model.ask"), snapshot.get("allowedToolNames"));
        assertEquals(2, ((List<?>) snapshot.get("tools")).size());
    }

    @Test
    void malformedVersionConfigDoesNotBroadenToolAccess() {
        AgentToolRegistryService registryService = mock(AgentToolRegistryService.class);
        when(registryService.listEnabled()).thenReturn(List.of(
                tool("memory.search", "READ_ONLY"),
                tool("model.ask", "READ_ONLY")));
        AiAgentVersion version = new AiAgentVersion();
        version.setConfig("{");
        AgentToolSnapshotService service = new AgentToolSnapshotService(registryService, new ObjectMapper());

        Map<String, Object> snapshot = service.snapshotFor(version);

        assertEquals(AgentToolSnapshotService.MODE_AGENT_VERSION, snapshot.get("whitelistMode"));
        assertEquals(List.of(), snapshot.get("allowedToolNames"));
        assertEquals(0, ((List<?>) snapshot.get("tools")).size());
    }

    private static AgentToolDefinition tool(String name, String riskLevel) {
        AgentToolDefinition tool = new AgentToolDefinition();
        tool.setToolName(name);
        tool.setRiskLevel(riskLevel);
        tool.setInputSchema("{\"type\":\"object\"}");
        tool.setOutputSchema("{\"type\":\"object\"}");
        tool.setRequiresApproval(0);
        tool.setTimeoutSeconds(10);
        tool.setConcurrencySafe(1);
        tool.setIdempotent(1);
        return tool;
    }
}
