package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.AgentRun;
import com.aipal.security.TenantContext;
import com.aipal.service.AgentRunService;
import com.aipal.service.AgentRunStreamEventService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunControllerTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void streamReusesRunAccessAndReturnsBusinessVisibleEvents() {
        TenantContext.set(new TenantContext.Context(7L, "tester", 1L, "tenant-1", false, Set.of(), Set.of()));
        AgentRunService runService = mock(AgentRunService.class);
        AgentRunStreamEventService streamEventService = mock(AgentRunStreamEventService.class);
        AgentRun run = new AgentRun();
        run.setId(99L);
        run.setTenantId(1L);
        when(runService.get(99L)).thenReturn(run);
        AgentRunStreamEventService.RunStreamEventView event =
                new AgentRunStreamEventService.RunStreamEventView(1L, 99L, "TOOL_STARTED", 4L,
                        Map.of("toolName", "memory.search"), true, LocalDateTime.now());
        when(streamEventService.listForRun(1L, 99L, 3L, 50, false)).thenReturn(List.of(event));
        AgentRunController controller = new AgentRunController(runService, streamEventService,
                mock(com.aipal.service.AgentToolResponseService.class),
                mock(com.aipal.agent.runtime.trace.RunTracer.class));

        Result<List<AgentRunStreamEventService.RunStreamEventView>> result = controller.stream(99L, 3L, 50);

        assertEquals(1, result.getData().size());
        assertEquals("TOOL_STARTED", result.getData().get(0).eventType());
        verify(runService).get(99L);
        verify(streamEventService).listForRun(1L, 99L, 3L, 50, false);
    }

    @Test
    void toolCallsReusesRunServiceProjection() {
        AgentRunService runService = mock(AgentRunService.class);
        AgentRunStreamEventService streamEventService = mock(AgentRunStreamEventService.class);
        AgentRunService.ToolCallView call = new AgentRunService.ToolCallView(1L, 2L, "call-1",
                "memory.search", "READ_ONLY", "ALLOW", "SUCCEEDED", "ok", null,
                1, 2, LocalDateTime.now(), LocalDateTime.now());
        when(runService.listToolCalls(99L)).thenReturn(List.of(call));
        AgentRunController controller = new AgentRunController(runService, streamEventService,
                mock(com.aipal.service.AgentToolResponseService.class),
                mock(com.aipal.agent.runtime.trace.RunTracer.class));

        Result<List<AgentRunService.ToolCallView>> result = controller.toolCalls(99L);

        assertEquals(1, result.getData().size());
        assertEquals("memory.search", result.getData().get(0).toolName());
        verify(runService).listToolCalls(99L);
    }
}
