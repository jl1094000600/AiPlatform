package com.aipal.service;

import com.aipal.entity.AgentRun;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLoopExecutorTest {

    @Test
    void v1LoopDelegatesToFrozenAgentCall() {
        AgentService agentService = mock(AgentService.class);
        when(agentService.callAgentFrozen(eq(11L), any(), eq(7L), eq(null),
                eq(1001L), eq(3003L), eq(4004L))).thenReturn(Map.of("answer", "ok", "totalTokens", 24));
        AgentLoopExecutor executor = new AgentLoopExecutor(agentService, mock(AgentToolExecutor.class),
                new com.aipal.agent.runtime.budget.RunBudgetGuard());

        AgentLoopExecutor.LoopResult result = executor.execute(run("v1"), Map.of("requirement", "demo"),
                Map.of("runtimeConfig", Map.of("modelId", 1001, "promptId", 3003, "promptVersionId", 4004)));

        assertEquals(24, result.totalTokens());
        assertEquals("ok", result.output().get("answer"));
    }

    @Test
    void v2LoopExecutesToolThenReturnsFinalOutput() {
        AgentService agentService = mock(AgentService.class);
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        when(agentService.callAgentFrozen(eq(11L), any(), eq(7L), eq(null),
                eq(1001L), eq(3003L), eq(4004L)))
                .thenReturn(Map.of("action", Map.of("type", "TOOL_CALL", "toolName", "memory.search",
                        "callId", "call-1", "input", Map.of("query", "demo")), "totalTokens", 10))
                .thenReturn(Map.of("action", "FINAL", "output", Map.of("answer", "done"), "totalTokens", 15));
        when(toolExecutor.execute(eq(run("v2")), eq("call-1"), eq("memory.search"), eq(Map.of("query", "demo"))))
                .thenReturn(new AgentToolExecutor.ToolExecutionResult(true, "SUCCEEDED",
                        Map.of("items", java.util.List.of()), null, 9L, 8L));
        AgentLoopExecutor executor = new AgentLoopExecutor(agentService, toolExecutor,
                new com.aipal.agent.runtime.budget.RunBudgetGuard());

        AgentLoopExecutor.LoopResult result = executor.execute(run("v2"), Map.of("requirement", "demo"),
                Map.of("runtimeConfig", Map.of("modelId", 1001, "promptId", 3003, "promptVersionId", 4004)));

        assertEquals(25, result.totalTokens());
        assertEquals("done", result.output().get("answer"));
    }

    @Test
    void v2LoopFailsWhenToolIsDenied() {
        AgentService agentService = mock(AgentService.class);
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        when(agentService.callAgentFrozen(eq(11L), any(), eq(7L), eq(null),
                eq(1001L), eq(3003L), eq(4004L)))
                .thenReturn(Map.of("action", "TOOL_CALL", "toolName", "memory.search",
                        "input", Map.of("query", "demo"), "totalTokens", 10));
        when(toolExecutor.execute(eq(run("v2")), any(), eq("memory.search"), eq(Map.of("query", "demo"))))
                .thenReturn(new AgentToolExecutor.ToolExecutionResult(false, "DENY", null,
                        "Tool permission DENY", 9L, 8L));
        AgentLoopExecutor executor = new AgentLoopExecutor(agentService, toolExecutor,
                new com.aipal.agent.runtime.budget.RunBudgetGuard());

        assertThrows(IllegalStateException.class, () -> executor.execute(run("v2"), Map.of("requirement", "demo"),
                Map.of("runtimeConfig", Map.of("modelId", 1001, "promptId", 3003, "promptVersionId", 4004))));
    }

    @Test
    void unknownLoopVersionIsRejected() {
        AgentLoopExecutor executor = new AgentLoopExecutor(mock(AgentService.class), mock(AgentToolExecutor.class),
                new com.aipal.agent.runtime.budget.RunBudgetGuard());

        assertThrows(UnsupportedOperationException.class, () -> executor.execute(run("v3"), Map.of(),
                Map.of("runtimeConfig", Map.of("modelId", 1001))));
    }

    private AgentRun run(String loopVersion) {
        AgentRun run = new AgentRun();
        run.setAgentId(11L);
        run.setOwnerUserId(7L);
        run.setLoopVersion(loopVersion);
        return run;
    }
}
