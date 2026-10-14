package com.aipal.agent.runtime.tool.builtin;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.agent.runtime.budget.RunBudgetGuard;
import com.aipal.agent.runtime.tool.ToolInvocationContext;
import com.aipal.agent.runtime.tool.ToolResult;
import com.aipal.agent.runtime.trace.RunTracer;
import com.aipal.agent.runtime.trace.Span;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentToolCall;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.mapper.AgentToolCallMapper;
import com.aipal.service.AgentRunEventService;
import com.aipal.service.AgentToolResponseService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AskUserToolTest {

    @Test
    void askUserReturnsAnswerAfterRespond() throws Exception {
        Fixtures fixtures = fixtures();
        AskUserTool tool = new AskUserTool(fixtures.responseService, fixtures.runMapper, new RunBudgetGuard());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ToolResult> pending = executor.submit(() -> tool.execute(fixtures.context,
                    Map.of("question", "Pick a branch?", "timeoutSeconds", 5)));
            waitUntilRegistered(fixtures.responseService);
            fixtures.responseService.respondToToolCall(99L, "call-1", "feature-x");
            ToolResult result = pending.get(3, TimeUnit.SECONDS);
            assertEquals("feature-x", ((Map<?, ?>) result.output()).get("answer"));
            assertEquals("ANSWERED", ((Map<?, ?>) result.output()).get("status"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void askUserTimeoutFailsTheToolCall() {
        Fixtures fixtures = fixtures();
        AskUserTool tool = new AskUserTool(fixtures.responseService, fixtures.runMapper, new RunBudgetGuard());

        AskUserTool.AskUserException exception = assertThrows(AskUserTool.AskUserException.class,
                () -> tool.execute(fixtures.context, Map.of("question", "Pick a branch?", "timeoutSeconds", 1)));
        assertTrue(exception.getMessage().contains("timed out"));
    }

    @Test
    void askUserRequiresQuestion() {
        Fixtures fixtures = fixtures();
        AskUserTool tool = new AskUserTool(fixtures.responseService, fixtures.runMapper, new RunBudgetGuard());

        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(fixtures.context, Map.of("timeoutSeconds", 5)));
    }

    private void waitUntilRegistered(AgentToolResponseService service) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (service.findPending(99L, "call-1").isPresent()) return;
            Thread.sleep(10);
        }
        throw new AssertionError("ask was not registered in time");
    }

    private Fixtures fixtures() {
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentToolCallMapper toolCallMapper = mock(AgentToolCallMapper.class);
        RunTracer tracer = mock(RunTracer.class);
        AgentRun run = new AgentRun();
        run.setId(99L);
        run.setTenantId(1L);
        run.setStatus(AgentRunStatus.RUNNING.name());
        run.setTraceId("trace-1");
        run.setVersion(1);
        when(runMapper.selectById(99L)).thenReturn(run);
        AgentToolCall call = new AgentToolCall();
        call.setId(55L);
        call.setTenantId(1L);
        call.setRunId(99L);
        call.setCallId("call-1");
        call.setToolName("ask_user");
        call.setStatus(AgentRunStatus.RUNNING.name());
        when(toolCallMapper.selectOne(any())).thenReturn(call);
        when(tracer.start(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new Span(1L, 1L, 99L, "span-1", null, "ask_user", null, null, "call-1", "trace-1",
                        LocalDateTime.now()));
        AgentToolResponseService responseService = new AgentToolResponseService(runMapper, toolCallMapper,
                mock(AgentRunEventService.class), tracer);
        ToolInvocationContext context = new ToolInvocationContext(1L, 99L, 11L, 22L, 5L, 7L, "project-a",
                "trace-1", "call-1", "ask_user", null, null, null);
        return new Fixtures(responseService, runMapper, context);
    }

    private record Fixtures(AgentToolResponseService responseService, AgentRunMapper runMapper,
                            ToolInvocationContext context) {
    }
}
