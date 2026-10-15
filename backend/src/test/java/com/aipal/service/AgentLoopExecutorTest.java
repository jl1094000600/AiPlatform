package com.aipal.service;

import com.aipal.agent.runtime.budget.BudgetExceededException;
import com.aipal.agent.runtime.budget.RunBudgetGuard;
import com.aipal.entity.AgentRun;
import com.aipal.mapper.AgentTaskMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLoopExecutorTest {

    @Test
    void v1LoopDelegatesToFrozenAgentCall() {
        AgentService agentService = mock(AgentService.class);
        when(agentService.callAgentFrozen(eq(11L), any(), eq(7L), eq(null),
                eq(1001L), eq(3003L), eq(4004L))).thenReturn(Map.of("answer", "ok", "totalTokens", 24));
        AgentLoopExecutor executor = new AgentLoopExecutor(agentService, mock(AgentToolExecutor.class),
                new RunBudgetGuard(), mock(AgentTaskMapper.class));

        AgentLoopExecutor.LoopResult result = executor.execute(run("v1"), Map.of("requirement", "demo"),
                Map.of("runtimeConfig", Map.of("modelId", 1001, "promptId", 3003, "promptVersionId", 4004)));

        assertEquals(24, result.totalTokens());
        assertEquals("ok", result.output().get("answer"));
    }

    @Test
    void v2LoopExecutesToolThenReturnsFinalOutput() {
        AgentService agentService = mock(AgentService.class);
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        AgentTaskMapper taskMapper = mock(AgentTaskMapper.class);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        when(agentService.callAgentFrozen(eq(11L), any(), eq(7L), eq(null),
                eq(1001L), eq(3003L), eq(4004L)))
                .thenReturn(Map.of("action", Map.of("type", "TOOL_CALL", "toolName", "memory.search",
                        "callId", "call-1", "input", Map.of("query", "demo")), "totalTokens", 10))
                .thenReturn(Map.of("action", "FINAL", "output", Map.of("answer", "done"), "totalTokens", 15));
        when(toolExecutor.execute(eq(run("v2")), eq("call-1"), eq("memory.search"), eq(Map.of("query", "demo"))))
                .thenReturn(new AgentToolExecutor.ToolExecutionResult(true, "SUCCEEDED",
                        Map.of("items", java.util.List.of()), null, 9L, 8L));
        AgentLoopExecutor executor = new AgentLoopExecutor(agentService, toolExecutor,
                new RunBudgetGuard(), taskMapper);

        AgentLoopExecutor.LoopResult result = executor.execute(run("v2"), Map.of("requirement", "demo"),
                Map.of("runtimeConfig", Map.of("modelId", 1001, "promptId", 3003, "promptVersionId", 4004)));

        assertEquals(25, result.totalTokens());
        assertEquals("done", result.output().get("answer"));
    }

    @Test
    void v2LoopFailsWhenToolIsDenied() {
        AgentService agentService = mock(AgentService.class);
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        AgentTaskMapper taskMapper = mock(AgentTaskMapper.class);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        when(agentService.callAgentFrozen(eq(11L), any(), eq(7L), eq(null),
                eq(1001L), eq(3003L), eq(4004L)))
                .thenReturn(Map.of("action", "TOOL_CALL", "toolName", "memory.search",
                        "input", Map.of("query", "demo"), "totalTokens", 10));
        when(toolExecutor.execute(eq(run("v2")), any(), eq("memory.search"), eq(Map.of("query", "demo"))))
                .thenReturn(new AgentToolExecutor.ToolExecutionResult(false, "DENY", null,
                        "Tool permission DENY", 9L, 8L));
        AgentLoopExecutor executor = new AgentLoopExecutor(agentService, toolExecutor,
                new RunBudgetGuard(), taskMapper);

        assertThrows(IllegalStateException.class, () -> executor.execute(run("v2"), Map.of("requirement", "demo"),
                Map.of("runtimeConfig", Map.of("modelId", 1001, "promptId", 3003, "promptVersionId", 4004))));
    }

    @Test
    void unknownLoopVersionIsRejected() {
        AgentLoopExecutor executor = new AgentLoopExecutor(mock(AgentService.class), mock(AgentToolExecutor.class),
                new RunBudgetGuard(), mock(AgentTaskMapper.class));

        assertThrows(UnsupportedOperationException.class, () -> executor.execute(run("v3"), Map.of(),
                Map.of("runtimeConfig", Map.of("modelId", 1001))));
    }

    /** FI-10: a model call that already blew the token budget must not trigger the next tool side effect. */
    @Test
    void tokenBudgetExceededAfterModelCallBlocksToolSideEffect() {
        AgentService agentService = mock(AgentService.class);
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        AgentTaskMapper taskMapper = mock(AgentTaskMapper.class);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        when(agentService.callAgentFrozen(eq(11L), any(), eq(7L), eq(null),
                eq(1001L), eq(3003L), eq(4004L)))
                .thenReturn(Map.of("action", Map.of("type", "TOOL_CALL", "toolName", "memory.search",
                        "callId", "call-1", "input", Map.of("query", "demo")), "totalTokens", 11));
        AgentLoopExecutor executor = new AgentLoopExecutor(agentService, toolExecutor,
                new RunBudgetGuard(), taskMapper);

        AgentRun run = run("v2");
        run.setMaxTotalTokens(10);
        BudgetExceededException exception = assertThrows(BudgetExceededException.class,
                () -> executor.execute(run, Map.of("requirement", "demo"),
                        Map.of("runtimeConfig", Map.of("modelId", 1001, "promptId", 3003, "promptVersionId", 4004))));

        assertEquals("TOKENS", exception.code());
        verify(toolExecutor, never()).execute(any(), any(), any(), any());
        verify(agentService, times(1)).callAgentFrozen(any(), any(), any(), any(), any(), any(), any());
    }

    /** FI-10: once over budget the N+1 model call is never issued. */
    @Test
    void tokenBudgetExceededAfterFirstTurnBlocksNextModelCall() {
        AgentService agentService = mock(AgentService.class);
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        AgentTaskMapper taskMapper = mock(AgentTaskMapper.class);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        when(agentService.callAgentFrozen(eq(11L), any(), eq(7L), eq(null),
                eq(1001L), eq(3003L), eq(4004L)))
                .thenReturn(Map.of("action", Map.of("type", "TOOL_CALL", "toolName", "memory.search",
                        "callId", "call-1", "input", Map.of("query", "demo")), "totalTokens", 6))
                .thenReturn(Map.of("action", Map.of("type", "TOOL_CALL", "toolName", "memory.search",
                        "callId", "call-2", "input", Map.of("query", "again")), "totalTokens", 6));
        when(toolExecutor.execute(any(), any(), any(), any()))
                .thenReturn(new AgentToolExecutor.ToolExecutionResult(true, "SUCCEEDED",
                        Map.of("items", java.util.List.of()), null, 9L, 8L));
        AgentLoopExecutor executor = new AgentLoopExecutor(agentService, toolExecutor,
                new RunBudgetGuard(), taskMapper);

        AgentRun run = run("v2");
        run.setMaxTotalTokens(10);
        BudgetExceededException exception = assertThrows(BudgetExceededException.class,
                () -> executor.execute(run, Map.of("requirement", "demo"),
                        Map.of("runtimeConfig", Map.of("modelId", 1001, "promptId", 3003, "promptVersionId", 4004))));

        assertEquals("TOKENS", exception.code());
        // turn-1 model ran and its tool ran; turn-2 model ran; turn-2 tool must NOT run and no turn-3 model call.
        verify(agentService, times(2)).callAgentFrozen(any(), any(), any(), any(), any(), any(), any());
        verify(toolExecutor, times(1)).execute(any(), any(), any(), any());
    }

    /** Over-budget model output must not become a FINAL deliverable. */
    @Test
    void budgetExceededSuppressesFinalDeliverable() {
        AgentService agentService = mock(AgentService.class);
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        AgentTaskMapper taskMapper = mock(AgentTaskMapper.class);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        when(agentService.callAgentFrozen(eq(11L), any(), eq(7L), eq(null),
                eq(1001L), eq(3003L), eq(4004L)))
                .thenReturn(Map.of("action", "FINAL", "output", Map.of("answer", "too large"),
                        "totalTokens", 11));
        AgentLoopExecutor executor = new AgentLoopExecutor(agentService, toolExecutor,
                new RunBudgetGuard(), taskMapper);

        AgentRun run = run("v2");
        run.setMaxTotalTokens(10);
        assertThrows(BudgetExceededException.class,
                () -> executor.execute(run, Map.of("requirement", "demo"),
                        Map.of("runtimeConfig", Map.of("modelId", 1001, "promptId", 3003, "promptVersionId", 4004))));
        verify(toolExecutor, never()).execute(any(), any(), any(), any());
    }

    /** Time budget is checked before the model call, not only after the loop returns. */
    @Test
    void timeBudgetExceededBeforeModelCallBlocksInvocation() {
        AgentService agentService = mock(AgentService.class);
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        AgentTaskMapper taskMapper = mock(AgentTaskMapper.class);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        AgentLoopExecutor executor = new AgentLoopExecutor(agentService, toolExecutor,
                new RunBudgetGuard(), taskMapper);

        AgentRun run = run("v2");
        run.setMaxDurationSeconds(60);
        run.setStartTime(LocalDateTime.now().minusHours(1));
        BudgetExceededException exception = assertThrows(BudgetExceededException.class,
                () -> executor.execute(run, Map.of("requirement", "demo"),
                        Map.of("runtimeConfig", Map.of("modelId", 1001, "promptId", 3003, "promptVersionId", 4004))));

        assertEquals("TIME", exception.code());
        verify(agentService, never()).callAgentFrozen(any(), any(), any(), any(), any(), any(), any());
        verify(toolExecutor, never()).execute(any(), any(), any(), any());
    }

    /** Child-task budget is checked before tool side effects. */
    @Test
    void childTaskBudgetExceededBeforeToolCallBlocksTool() {
        AgentService agentService = mock(AgentService.class);
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        AgentTaskMapper taskMapper = mock(AgentTaskMapper.class);
        when(taskMapper.selectCount(any())).thenReturn(5L);
        when(agentService.callAgentFrozen(eq(11L), any(), eq(7L), eq(null),
                eq(1001L), eq(3003L), eq(4004L)))
                .thenReturn(Map.of("action", Map.of("type", "TOOL_CALL", "toolName", "task.create",
                        "callId", "call-1", "input", Map.of("taskType", "MODEL_TASK")), "totalTokens", 1));
        AgentLoopExecutor executor = new AgentLoopExecutor(agentService, toolExecutor,
                new RunBudgetGuard(), taskMapper);

        AgentRun run = run("v2");
        run.setId(99L);
        run.setTenantId(1L);
        run.setMaxChildTasks(5);
        BudgetExceededException exception = assertThrows(BudgetExceededException.class,
                () -> executor.execute(run, Map.of("requirement", "demo"),
                        Map.of("runtimeConfig", Map.of("modelId", 1001, "promptId", 3003, "promptVersionId", 4004))));

        assertEquals("CHILD_TASKS", exception.code());
        verify(toolExecutor, never()).execute(any(), any(), any(), any());
    }

    private AgentRun run(String loopVersion) {
        AgentRun run = new AgentRun();
        run.setAgentId(11L);
        run.setOwnerUserId(7L);
        run.setLoopVersion(loopVersion);
        return run;
    }
}
