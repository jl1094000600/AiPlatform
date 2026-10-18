package com.aipal.service;

import com.aipal.agent.runtime.AgentTaskStatus;
import com.aipal.agent.runtime.AgentRunStreamEventType;
import com.aipal.agent.runtime.AgentToolPermissionDecision;
import com.aipal.agent.runtime.tool.AgentTool;
import com.aipal.agent.runtime.tool.ToolResult;
import com.aipal.agent.runtime.trace.RunTracer;
import com.aipal.agent.runtime.trace.Span;
import com.aipal.agent.runtime.trace.SpanType;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentStep;
import com.aipal.entity.AgentToolCall;
import com.aipal.entity.AgentToolDefinition;
import com.aipal.mapper.AgentStepMapper;
import com.aipal.mapper.AgentToolCallMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolExecutorTest {

    @Test
    void deniedToolCallIsAuditedAndNotExecuted() {
        Fixtures fixtures = fixtures(AgentToolPermissionDecision.DENY, false);

        AgentToolExecutor.ToolExecutionResult result = fixtures.executor.execute(fixtures.run, "call-1",
                "memory.search", Map.of("query", "demo"));

        assertFalse(result.executed());
        assertEquals("DENY", result.status());
        assertFalse(fixtures.toolExecuted.get());
        ArgumentCaptor<AgentStep> stepCaptor = ArgumentCaptor.forClass(AgentStep.class);
        verify(fixtures.stepMapper).insert(stepCaptor.capture());
        assertEquals("TOOL_CALL", stepCaptor.getValue().getStepType());
        assertEquals("DENY", stepCaptor.getValue().getPermissionDecision());
        ArgumentCaptor<AgentToolCall> callCaptor = ArgumentCaptor.forClass(AgentToolCall.class);
        verify(fixtures.toolCallMapper).insert(callCaptor.capture());
        assertEquals("memory.search", callCaptor.getValue().getToolName());
        assertEquals("DENY", callCaptor.getValue().getPermissionDecision());
        ArgumentCaptor<AgentRunStreamEventType> eventCaptor = ArgumentCaptor.forClass(AgentRunStreamEventType.class);
        verify(fixtures.streamEventService, times(3)).record(eq(1L), eq(7L), eventCaptor.capture(), any(), eq(true));
        assertEquals(List.of(AgentRunStreamEventType.TOOL_REQUESTED, AgentRunStreamEventType.PERMISSION_DECIDED,
                AgentRunStreamEventType.TOOL_FAILED), eventCaptor.getAllValues());
    }

    @Test
    void allowedToolCallExecutesAndMarksAuditSucceeded() {
        Fixtures fixtures = fixtures(AgentToolPermissionDecision.ALLOW, true);

        AgentToolExecutor.ToolExecutionResult result = fixtures.executor.execute(fixtures.run, "call-2",
                "memory.search", Map.of("query", "demo"));

        assertTrue(result.executed());
        assertEquals(AgentTaskStatus.SUCCEEDED.name(), result.status());
        assertTrue(fixtures.toolExecuted.get());
        ArgumentCaptor<AgentToolCall> updateCaptor = ArgumentCaptor.forClass(AgentToolCall.class);
        verify(fixtures.toolCallMapper).updateById(updateCaptor.capture());
        assertEquals(AgentTaskStatus.SUCCEEDED.name(), updateCaptor.getValue().getStatus());
        assertEquals("{\"items\":[]}", updateCaptor.getValue().getOutputJson());
        ArgumentCaptor<AgentRunStreamEventType> eventCaptor = ArgumentCaptor.forClass(AgentRunStreamEventType.class);
        verify(fixtures.streamEventService, times(4)).record(eq(1L), eq(7L), eventCaptor.capture(), any(), eq(true));
        assertEquals(List.of(AgentRunStreamEventType.TOOL_REQUESTED, AgentRunStreamEventType.PERMISSION_DECIDED,
                AgentRunStreamEventType.TOOL_STARTED, AgentRunStreamEventType.TOOL_COMPLETED), eventCaptor.getAllValues());
    }

    @Test
    void toolOutsideAgentVersionWhitelistIsAuditedAndNotExecuted() {
        Fixtures fixtures = fixtures(AgentToolPermissionDecision.ALLOW, true);
        fixtures.run.setDefinitionSnapshot("""
                {"toolRegistrySnapshot":{"whitelistMode":"AGENT_VERSION","allowedToolNames":["model.ask"]}}
                """);

        AgentToolExecutor.ToolExecutionResult result = fixtures.executor.execute(fixtures.run, "call-3",
                "memory.search", Map.of("query", "demo"));

        assertFalse(result.executed());
        assertEquals("DENY", result.status());
        assertEquals("Tool is not allowed by Agent Version whitelist", result.errorMessage());
        assertFalse(fixtures.toolExecuted.get());
        verify(fixtures.permissionEvaluator, never()).evaluate(any(), any());
    }

    @Test
    void malformedRunSnapshotFailsClosedBeforePermissionEvaluation() {
        Fixtures fixtures = fixtures(AgentToolPermissionDecision.ALLOW, true);
        fixtures.run.setDefinitionSnapshot("{");

        AgentToolExecutor.ToolExecutionResult result = fixtures.executor.execute(fixtures.run, "call-4",
                "memory.search", Map.of("query", "demo"));

        assertFalse(result.executed());
        assertEquals("DENY", result.status());
        assertFalse(fixtures.toolExecuted.get());
        verify(fixtures.permissionEvaluator, never()).evaluate(any(), any());
    }

    private Fixtures fixtures(AgentToolPermissionDecision decision, boolean includeTool) {
        AgentToolRegistryService registryService = mock(AgentToolRegistryService.class);
        AgentToolPermissionEvaluator evaluator = mock(AgentToolPermissionEvaluator.class);
        AgentToolCallMapper toolCallMapper = mock(AgentToolCallMapper.class);
        AgentStepMapper stepMapper = mock(AgentStepMapper.class);
        AgentRunStreamEventService streamEventService = mock(AgentRunStreamEventService.class);
        RunTracer runTracer = mock(RunTracer.class);
        AgentRun run = run();
        AgentToolDefinition definition = definition();
        when(runTracer.start(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new Span(1L, 1L, 7L, "span-1", SpanType.TOOL_CALL, "memory.search",
                        88L, 99L, "call-2", "trace-a", LocalDateTime.now()));
        when(registryService.requireEnabled("memory.search")).thenReturn(definition);
        when(evaluator.evaluate(run, definition)).thenReturn(new AgentToolPermissionEvaluator.ToolPermissionResult(
                decision, "READ_ONLY", false, decision == AgentToolPermissionDecision.ALLOW ? 9L : null));
        when(stepMapper.selectCount(any())).thenReturn(1L);
        when(stepMapper.insert(any())).thenAnswer(invocation -> {
            AgentStep step = invocation.getArgument(0);
            step.setId(88L);
            return 1;
        });
        when(toolCallMapper.insert(any())).thenAnswer(invocation -> {
            AgentToolCall call = invocation.getArgument(0);
            call.setId(99L);
            return 1;
        });
        AtomicBoolean toolExecuted = new AtomicBoolean(false);
        AgentTool tool = new AgentTool() {
            @Override public String name() { return "memory.search"; }
            @Override public ToolResult execute(com.aipal.agent.runtime.tool.ToolInvocationContext context, Object input) {
                toolExecuted.set(true);
                return new ToolResult(Map.of("items", List.of()), "empty", 1, 2);
            }
        };
        AgentToolExecutor executor = new AgentToolExecutor(registryService, evaluator, toolCallMapper, stepMapper,
                streamEventService, runTracer, new ObjectMapper(),
                includeTool ? List.of(tool) : List.of());
        return new Fixtures(executor, run, evaluator, stepMapper, toolCallMapper, streamEventService, toolExecuted);
    }

    private AgentRun run() {
        AgentRun run = new AgentRun();
        run.setId(7L);
        run.setTenantId(1L);
        run.setAgentId(11L);
        run.setAgentVersionId(22L);
        run.setOwnerUserId(33L);
        run.setProjectKey("project-a");
        run.setTraceId("trace-a");
        return run;
    }

    private AgentToolDefinition definition() {
        AgentToolDefinition definition = new AgentToolDefinition();
        definition.setToolName("memory.search");
        definition.setRiskLevel("READ_ONLY");
        definition.setRequiresApproval(0);
        return definition;
    }

    private record Fixtures(AgentToolExecutor executor, AgentRun run, AgentToolPermissionEvaluator permissionEvaluator,
                            AgentStepMapper stepMapper,
                            AgentToolCallMapper toolCallMapper, AgentRunStreamEventService streamEventService,
                            AtomicBoolean toolExecuted) {
    }
}
