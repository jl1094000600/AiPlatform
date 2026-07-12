package com.aipal.agent.runtime.tool;

import com.aipal.entity.AiMemoryItem;
import com.aipal.service.memory.MemoryContext;
import com.aipal.service.memory.MemoryOrchestrator;
import com.aipal.service.memory.MemoryRecallCandidate;
import com.aipal.service.memory.MemoryRecallRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemorySearchToolTest {

    @Test
    void searchesAuthorizedMemoryAndReturnsStructuredItems() {
        MemoryOrchestrator orchestrator = mock(MemoryOrchestrator.class);
        AiMemoryItem item = new AiMemoryItem();
        item.setMemoryCode("mem-1");
        item.setVersion(2);
        item.setTitle("Project fact");
        item.setSourceType("MANUAL");
        item.setScopeType("PROJECT");
        item.setContent("The launch checklist is approved.");
        when(orchestrator.prepareContext(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new MemoryContext("mem_trace", "ENFORCED", 3, true, "", 9,
                        List.of(new MemoryRecallCandidate(item, 0.88, 9, "keyword"))));
        MemorySearchTool tool = new MemorySearchTool(orchestrator);

        ToolResult result = tool.execute(context(), Map.of("query", "launch", "topK", 3));

        Map<?, ?> output = (Map<?, ?>) result.output();
        assertEquals("mem_trace", output.get("traceId"));
        assertEquals(1, ((List<?>) output.get("items")).size());
        assertEquals("memory.search returned 1 item(s)", result.outputSummary());
        ArgumentCaptor<MemoryRecallRequest> requestCaptor = ArgumentCaptor.forClass(MemoryRecallRequest.class);
        verify(orchestrator).prepareContext(requestCaptor.capture());
        assertEquals(11L, requestCaptor.getValue().agentId());
        assertEquals("project-a", requestCaptor.getValue().projectKey());
        assertEquals("launch", requestCaptor.getValue().requestSummary());
    }

    @Test
    void rejectsMissingQuery() {
        MemorySearchTool tool = new MemorySearchTool(mock(MemoryOrchestrator.class));

        assertThrows(IllegalArgumentException.class, () -> tool.execute(context(), Map.of()));
    }

    private ToolInvocationContext context() {
        return new ToolInvocationContext(1L, 7L, 11L, 22L, 88L, 33L, "project-a",
                "trace-a", "call-1", "memory.search", 55L, 66L, 77L);
    }
}
