package com.aipal.agent.runtime.tool;

import com.aipal.service.AgentService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelAskToolTest {

    @Test
    void invokesFrozenAgentRuntimeForBoundedModelAsk() {
        AgentService agentService = mock(AgentService.class);
        when(agentService.callAgentFrozen(eq(11L), any(), eq(33L), isNull(), eq(55L), eq(66L), eq(77L)))
                .thenReturn(Map.of("message", "ok", "traceId", "trace-model", "agentName", "Agent A",
                        "modelCode", "gpt-x", "promptId", 66L, "promptVersionId", 77L));
        ModelAskTool tool = new ModelAskTool(agentService);

        ToolResult result = tool.execute(context(), Map.of("prompt", "summarize"));

        Map<?, ?> output = (Map<?, ?>) result.output();
        assertEquals("ok", output.get("answer"));
        assertEquals("trace-model", output.get("traceId"));
        assertEquals("model.ask completed", result.outputSummary());
        verify(agentService).callAgentFrozen(eq(11L), any(), eq(33L), isNull(), eq(55L), eq(66L), eq(77L));
    }

    @Test
    void rejectsMissingPrompt() {
        ModelAskTool tool = new ModelAskTool(mock(AgentService.class));

        assertThrows(IllegalArgumentException.class, () -> tool.execute(context(), Map.of()));
    }

    private ToolInvocationContext context() {
        return new ToolInvocationContext(1L, 7L, 11L, 22L, 88L, 33L, "project-a",
                "trace-a", "call-1", "model.ask", 55L, 66L, 77L);
    }
}
