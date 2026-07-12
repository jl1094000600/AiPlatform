package com.aipal.service;

import com.aipal.common.BizException;
import com.aipal.entity.AgentToolDefinition;
import com.aipal.mapper.AgentToolDefinitionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolRegistryServiceTest {

    @Test
    void listsOnlyEnabledToolsThroughRegistryBoundary() {
        AgentToolDefinitionMapper mapper = mock(AgentToolDefinitionMapper.class);
        AgentToolDefinition tool = tool("memory.search", "READ_ONLY");
        when(mapper.selectList(any())).thenReturn(List.of(tool));

        AgentToolRegistryService service = new AgentToolRegistryService(mapper, new ObjectMapper());

        List<AgentToolDefinition> tools = service.listEnabled();

        assertEquals(1, tools.size());
        assertEquals("memory.search", tools.get(0).getToolName());
    }

    @Test
    void requireEnabledRejectsBlankToolName() {
        AgentToolRegistryService service = new AgentToolRegistryService(mock(AgentToolDefinitionMapper.class), new ObjectMapper());

        BizException ex = assertThrows(BizException.class, () -> service.requireEnabled(" "));

        assertEquals(400, ex.getCode());
    }

    @Test
    void dryRunReturnsAcceptedMetadataWithoutExecutingTool() {
        AgentToolDefinitionMapper mapper = mock(AgentToolDefinitionMapper.class);
        when(mapper.selectOne(any())).thenReturn(tool("artifact.create", "MUTATING",
                "{\"type\":\"object\",\"required\":[\"title\",\"artifactType\"]}"));
        AgentToolRegistryService service = new AgentToolRegistryService(mapper, new ObjectMapper());

        AgentToolRegistryService.ToolDryRunResult result = service.dryRun("artifact.create",
                Map.of("title", "Draft", "artifactType", "doc"));

        assertEquals("artifact.create", result.toolName());
        assertEquals("MUTATING", result.riskLevel());
        assertTrue(result.accepted());
    }

    @Test
    void dryRunFailsWhenToolIsDisabledOrMissing() {
        AgentToolDefinitionMapper mapper = mock(AgentToolDefinitionMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);
        AgentToolRegistryService service = new AgentToolRegistryService(mapper, new ObjectMapper());

        BizException ex = assertThrows(BizException.class, () -> service.dryRun("agent.invoke", "{}"));

        assertEquals(404, ex.getCode());
    }

    @Test
    void dryRunValidatesRequiredInputFields() {
        AgentToolDefinitionMapper mapper = mock(AgentToolDefinitionMapper.class);
        when(mapper.selectOne(any())).thenReturn(tool("memory.search", "READ_ONLY",
                "{\"type\":\"object\",\"required\":[\"query\"]}"));
        AgentToolRegistryService service = new AgentToolRegistryService(mapper, new ObjectMapper());

        BizException ex = assertThrows(BizException.class, () -> service.dryRun("memory.search", Map.of()));

        assertEquals(400, ex.getCode());
    }

    @Test
    void dryRunRejectsSchemaTypeAndRangeViolations() {
        AgentToolDefinitionMapper mapper = mock(AgentToolDefinitionMapper.class);
        when(mapper.selectOne(any())).thenReturn(tool("memory.search", "READ_ONLY",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"topK\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":20}},\"required\":[\"query\"]}"));
        AgentToolRegistryService service = new AgentToolRegistryService(mapper, new ObjectMapper());

        BizException typeError = assertThrows(BizException.class,
                () -> service.dryRun("memory.search", Map.of("query", 42, "topK", 3)));
        BizException rangeError = assertThrows(BizException.class,
                () -> service.dryRun("memory.search", Map.of("query", "demo", "topK", 99)));

        assertEquals(400, typeError.getCode());
        assertEquals(400, rangeError.getCode());
    }

    private static AgentToolDefinition tool(String name, String riskLevel) {
        return tool(name, riskLevel, null);
    }

    private static AgentToolDefinition tool(String name, String riskLevel, String inputSchema) {
        AgentToolDefinition tool = new AgentToolDefinition();
        tool.setToolName(name);
        tool.setRiskLevel(riskLevel);
        tool.setStatus(1);
        tool.setInputSchema(inputSchema);
        return tool;
    }
}
