package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.AgentToolDefinition;
import com.aipal.service.AgentToolRegistryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolControllerTest {

    @Test
    void listsToolMetadataThroughRegistryBoundary() {
        AgentToolRegistryService registryService = mock(AgentToolRegistryService.class);
        AgentToolDefinition tool = new AgentToolDefinition();
        tool.setToolName("memory.search");
        tool.setRiskLevel("READ_ONLY");
        when(registryService.listEnabled()).thenReturn(List.of(tool));
        AgentToolController controller = new AgentToolController(registryService);

        Result<List<AgentToolDefinition>> result = controller.list();

        assertEquals(1, result.getData().size());
        assertEquals("memory.search", result.getData().get(0).getToolName());
        verify(registryService).listEnabled();
    }

    @Test
    void dryRunDelegatesToRegistryValidation() {
        AgentToolRegistryService registryService = mock(AgentToolRegistryService.class);
        AgentToolRegistryService.ToolDryRunResult dryRun =
                new AgentToolRegistryService.ToolDryRunResult("model.ask", "READ_ONLY", true, "accepted");
        when(registryService.dryRun("model.ask", Map.of("prompt", "hi"))).thenReturn(dryRun);
        AgentToolController controller = new AgentToolController(registryService);

        Result<AgentToolRegistryService.ToolDryRunResult> result =
                controller.dryRun("model.ask", Map.of("prompt", "hi"));

        assertEquals("model.ask", result.getData().toolName());
        assertEquals("READ_ONLY", result.getData().riskLevel());
        verify(registryService).dryRun("model.ask", Map.of("prompt", "hi"));
    }
}
