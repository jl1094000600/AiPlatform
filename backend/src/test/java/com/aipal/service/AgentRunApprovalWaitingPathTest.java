package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.common.BizException;
import com.aipal.entity.AgentArtifact;
import com.aipal.entity.AgentRun;
import com.aipal.mapper.AgentArtifactMapper;
import com.aipal.mapper.AgentMemorySnapshotMapper;
import com.aipal.mapper.AgentRunEventMapper;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.mapper.AgentRunStreamEventMapper;
import com.aipal.mapper.AgentStepMapper;
import com.aipal.mapper.AgentTaskMapper;
import com.aipal.mapper.AgentToolCallMapper;
import com.aipal.mapper.AiAgentMapper;
import com.aipal.service.memory.MemoryOrchestrator;
import com.aipal.service.memory.MemoryProjectService;
import com.aipal.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W5: tool/artifact approval WAITING approve and reject paths.
 * Covers AgentRunService.approve / reject status transitions, artifact finalization,
 * permission and cross-tenant rejection, and CAS lost-race behaviour.
 */
class AgentRunApprovalWaitingPathTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void approveMovesWaitingRunToSucceededAndFinalizesPendingArtifact() {
        Fixtures fixtures = fixtures();
        AgentRun waiting = waitingRun();
        when(fixtures.runMapper.selectById(99L)).thenReturn(waiting);
        when(fixtures.runMapper.update(any(), any())).thenReturn(1);
        TenantContext.set(context(1L, 7L, Set.of("agent:manage")));

        AgentRun result = fixtures.service.approve(99L, "looks good");

        assertEquals(AgentRunStatus.SUCCEEDED.name(), result.getStatus());
        ArgumentCaptor<AgentArtifact> artifact = ArgumentCaptor.forClass(AgentArtifact.class);
        verify(fixtures.artifactMapper).update(artifact.capture(), any());
        assertEquals("FINAL", artifact.getValue().getStatus());
        verify(fixtures.eventService).record(any(AgentRun.class),
                org.mockito.ArgumentMatchers.eq(AgentRunStatus.WAITING_APPROVAL.name()),
                org.mockito.ArgumentMatchers.eq(AgentRunStatus.SUCCEEDED.name()),
                org.mockito.ArgumentMatchers.eq("looks good"));
    }

    @Test
    void rejectMovesWaitingRunToFailedAndMarksArtifactRejected() {
        Fixtures fixtures = fixtures();
        AgentRun waiting = waitingRun();
        when(fixtures.runMapper.selectById(99L)).thenReturn(waiting);
        when(fixtures.runMapper.update(any(), any())).thenReturn(1);
        TenantContext.set(context(1L, 7L, Set.of("agent:manage")));

        AgentRun result = fixtures.service.reject(99L, "needs rework");

        assertEquals(AgentRunStatus.FAILED.name(), result.getStatus());
        assertEquals("needs rework", result.getErrorMessage());
        ArgumentCaptor<AgentArtifact> artifact = ArgumentCaptor.forClass(AgentArtifact.class);
        verify(fixtures.artifactMapper).update(artifact.capture(), any());
        assertEquals("REJECTED", artifact.getValue().getStatus());
        verify(fixtures.eventService).record(any(AgentRun.class),
                org.mockito.ArgumentMatchers.eq(AgentRunStatus.WAITING_APPROVAL.name()),
                org.mockito.ArgumentMatchers.eq(AgentRunStatus.FAILED.name()),
                org.mockito.ArgumentMatchers.eq("needs rework"));
    }

    @Test
    void approveOnNonWaitingRunIsRejectedWithoutStateChange() {
        Fixtures fixtures = fixtures();
        AgentRun running = waitingRun();
        running.setStatus(AgentRunStatus.RUNNING.name());
        when(fixtures.runMapper.selectById(99L)).thenReturn(running);
        TenantContext.set(context(1L, 7L, Set.of("agent:manage")));

        BizException error = assertThrows(BizException.class, () -> fixtures.service.approve(99L, "ok"));
        assertEquals(409, error.getCode());
        verify(fixtures.runMapper, never()).update(any(), any());
        verify(fixtures.artifactMapper, never()).update(any(), any());
        verify(fixtures.eventService, never()).record(any(), any(), any(), any());
    }

    @Test
    void rejectOnNonWaitingRunIsRejectedWithoutStateChange() {
        Fixtures fixtures = fixtures();
        AgentRun succeeded = waitingRun();
        succeeded.setStatus(AgentRunStatus.SUCCEEDED.name());
        when(fixtures.runMapper.selectById(99L)).thenReturn(succeeded);
        TenantContext.set(context(1L, 7L, Set.of("agent:manage")));

        BizException error = assertThrows(BizException.class, () -> fixtures.service.reject(99L, "late"));
        assertEquals(409, error.getCode());
        verify(fixtures.runMapper, never()).update(any(), any());
        verify(fixtures.artifactMapper, never()).update(any(), any());
    }

    @Test
    void approveIsDeniedForUnauthorizedUser() {
        Fixtures fixtures = fixtures();
        AgentRun waiting = waitingRun();
        waiting.setOwnerUserId(7L);
        when(fixtures.runMapper.selectById(99L)).thenReturn(waiting);
        TenantContext.set(context(1L, 42L, Set.of()));

        BizException error = assertThrows(BizException.class, () -> fixtures.service.approve(99L, "ok"));
        assertEquals(403, error.getCode());
        verify(fixtures.runMapper, never()).update(any(), any());
    }

    @Test
    void rejectIsDeniedForUnauthorizedUser() {
        Fixtures fixtures = fixtures();
        AgentRun waiting = waitingRun();
        waiting.setOwnerUserId(7L);
        when(fixtures.runMapper.selectById(99L)).thenReturn(waiting);
        TenantContext.set(context(1L, 42L, Set.of()));

        BizException error = assertThrows(BizException.class, () -> fixtures.service.reject(99L, "no"));
        assertEquals(403, error.getCode());
        verify(fixtures.runMapper, never()).update(any(), any());
    }

    @Test
    void approveIsDeniedAcrossTenantBoundary() {
        Fixtures fixtures = fixtures();
        AgentRun foreign = waitingRun();
        foreign.setTenantId(2L);
        when(fixtures.runMapper.selectById(99L)).thenReturn(foreign);
        TenantContext.set(context(1L, 7L, Set.of("agent:manage")));

        BizException error = assertThrows(BizException.class, () -> fixtures.service.approve(99L, "ok"));
        assertEquals(404, error.getCode());
        verify(fixtures.runMapper, never()).update(any(), any());
        verify(fixtures.eventService, never()).record(any(), any(), any(), any());
    }

    @Test
    void toolRequiringApprovalRejectPathDoesNotExecute() {
        AgentToolRegistryService registryService = mock(AgentToolRegistryService.class);
        AgentToolPermissionEvaluator evaluator = mock(AgentToolPermissionEvaluator.class);
        AgentToolCallMapper toolCallMapper = mock(AgentToolCallMapper.class);
        AgentStepMapper stepMapper = mock(AgentStepMapper.class);
        AgentToolApprovalService approvalService = mock(AgentToolApprovalService.class);
        com.aipal.agent.runtime.trace.RunTracer tracer = mock(com.aipal.agent.runtime.trace.RunTracer.class);
        AgentRun run = waitingRun();
        run.setStatus(AgentRunStatus.RUNNING.name());
        com.aipal.entity.AgentToolDefinition definition = new com.aipal.entity.AgentToolDefinition();
        definition.setToolName("artifact.create");
        definition.setRiskLevel("MUTATING");
        definition.setRequiresApproval(1);
        when(registryService.requireEnabled("artifact.create")).thenReturn(definition);
        when(evaluator.evaluate(run, definition)).thenReturn(
                new AgentToolPermissionEvaluator.ToolPermissionResult(
                        com.aipal.agent.runtime.AgentToolPermissionDecision.REQUIRE_APPROVAL,
                        "MUTATING", true, 3L));
        when(stepMapper.selectCount(any())).thenReturn(0L);
        when(stepMapper.insert(any())).thenAnswer(invocation -> {
            com.aipal.entity.AgentStep step = invocation.getArgument(0);
            step.setId(1L);
            return 1;
        });
        when(toolCallMapper.insert(any())).thenAnswer(invocation -> {
            com.aipal.entity.AgentToolCall call = invocation.getArgument(0);
            call.setId(2L);
            return 1;
        });
        when(approvalService.awaitApproval(any(), any(), any()))
                .thenReturn(new AgentToolApprovalService.ApprovalOutcome(false, "Tool call rejected"));
        java.util.concurrent.atomic.AtomicBoolean executed = new java.util.concurrent.atomic.AtomicBoolean();
        com.aipal.agent.runtime.tool.AgentTool tool = new com.aipal.agent.runtime.tool.AgentTool() {
            @Override public String name() { return "artifact.create"; }
            @Override public com.aipal.agent.runtime.tool.ToolResult execute(
                    com.aipal.agent.runtime.tool.ToolInvocationContext context, Object input) {
                executed.set(true);
                return new com.aipal.agent.runtime.tool.ToolResult(Map.of(), "created", 0, 0);
            }
        };
        AgentToolExecutor executor = new AgentToolExecutor(registryService, evaluator, toolCallMapper, stepMapper,
                mock(AgentRunStreamEventService.class), tracer, new ObjectMapper(), approvalService, List.of(tool));

        AgentToolExecutor.ToolExecutionResult result =
                executor.execute(run, "call-approval", "artifact.create", Map.of("title", "doc"));

        assertFalse(executed.get(), "rejected tool approval must not execute the side effect");
        assertFalse(result.executed());
        assertEquals("FAILED", result.status());
    }

    @Test
    void toolRequiringApprovalApprovePathExecutes() {
        AgentToolRegistryService registryService = mock(AgentToolRegistryService.class);
        AgentToolPermissionEvaluator evaluator = mock(AgentToolPermissionEvaluator.class);
        AgentToolCallMapper toolCallMapper = mock(AgentToolCallMapper.class);
        AgentStepMapper stepMapper = mock(AgentStepMapper.class);
        AgentToolApprovalService approvalService = mock(AgentToolApprovalService.class);
        com.aipal.agent.runtime.trace.RunTracer tracer = mock(com.aipal.agent.runtime.trace.RunTracer.class);
        when(tracer.start(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new com.aipal.agent.runtime.trace.Span(1L, 1L, 7L, "span-1",
                        com.aipal.agent.runtime.trace.SpanType.TOOL_CALL, "artifact.create",
                        1L, 2L, "call-approval", "trace-a", LocalDateTime.now()));
        AgentRun run = waitingRun();
        run.setStatus(AgentRunStatus.RUNNING.name());
        com.aipal.entity.AgentToolDefinition definition = new com.aipal.entity.AgentToolDefinition();
        definition.setToolName("artifact.create");
        definition.setRiskLevel("MUTATING");
        definition.setRequiresApproval(1);
        when(registryService.requireEnabled("artifact.create")).thenReturn(definition);
        when(evaluator.evaluate(run, definition)).thenReturn(
                new AgentToolPermissionEvaluator.ToolPermissionResult(
                        com.aipal.agent.runtime.AgentToolPermissionDecision.REQUIRE_APPROVAL,
                        "MUTATING", true, 3L));
        when(stepMapper.selectCount(any())).thenReturn(0L);
        when(stepMapper.insert(any())).thenAnswer(invocation -> {
            com.aipal.entity.AgentStep step = invocation.getArgument(0);
            step.setId(1L);
            return 1;
        });
        when(toolCallMapper.insert(any())).thenAnswer(invocation -> {
            com.aipal.entity.AgentToolCall call = invocation.getArgument(0);
            call.setId(2L);
            return 1;
        });
        when(approvalService.awaitApproval(any(), any(), any()))
                .thenReturn(new AgentToolApprovalService.ApprovalOutcome(true, "Tool call approved"));
        java.util.concurrent.atomic.AtomicBoolean executed = new java.util.concurrent.atomic.AtomicBoolean();
        com.aipal.agent.runtime.tool.AgentTool tool = new com.aipal.agent.runtime.tool.AgentTool() {
            @Override public String name() { return "artifact.create"; }
            @Override public com.aipal.agent.runtime.tool.ToolResult execute(
                    com.aipal.agent.runtime.tool.ToolInvocationContext context, Object input) {
                executed.set(true);
                return new com.aipal.agent.runtime.tool.ToolResult(Map.of("id", 1), "created", 1, 1);
            }
        };
        AgentToolExecutor executor = new AgentToolExecutor(registryService, evaluator, toolCallMapper, stepMapper,
                mock(AgentRunStreamEventService.class), tracer, new ObjectMapper(), approvalService, List.of(tool));

        AgentToolExecutor.ToolExecutionResult result =
                executor.execute(run, "call-approval", "artifact.create", Map.of("title", "doc"));

        assertTrue(executed.get(), "approved tool approval must execute the side effect");
        assertTrue(result.executed());
        assertEquals("SUCCEEDED", result.status());
    }

    @Test
    void approveCasLostRaceDoesNotFinalizeArtifactOrRecordEvent() {
        Fixtures fixtures = fixtures();
        AgentRun waiting = waitingRun();
        when(fixtures.runMapper.selectById(99L)).thenReturn(waiting);
        when(fixtures.runMapper.update(any(), any())).thenReturn(0);
        TenantContext.set(context(1L, 7L, Set.of("agent:manage")));

        AgentRun result = fixtures.service.approve(99L, "ok");

        assertEquals(AgentRunStatus.WAITING_APPROVAL.name(), result.getStatus(),
                "losing the CAS race must leave the run unchanged for the caller");
        verify(fixtures.artifactMapper, never()).update(any(), any());
        verify(fixtures.eventService, never()).record(any(), any(), any(), any());
    }

    @Test
    void rejectCasLostRaceDoesNotFinalizeArtifactOrRecordEvent() {
        Fixtures fixtures = fixtures();
        AgentRun waiting = waitingRun();
        when(fixtures.runMapper.selectById(99L)).thenReturn(waiting);
        when(fixtures.runMapper.update(any(), any())).thenReturn(0);
        TenantContext.set(context(1L, 7L, Set.of("agent:manage")));

        AgentRun result = fixtures.service.reject(99L, "no");

        assertEquals(AgentRunStatus.WAITING_APPROVAL.name(), result.getStatus());
        verify(fixtures.artifactMapper, never()).update(any(), any());
        verify(fixtures.eventService, never()).record(any(), any(), any(), any());
    }

    private static Fixtures fixtures() {
        return new Fixtures();
    }

    private static TenantContext.Context context(Long tenantId, Long userId, Set<String> permissions) {
        return new TenantContext.Context(userId, "tester", tenantId, "tenant-" + tenantId,
                false, Set.of(), permissions);
    }

    private static AgentRun waitingRun() {
        AgentRun run = new AgentRun();
        run.setId(99L);
        run.setTenantId(1L);
        run.setOwnerUserId(7L);
        run.setProjectKey("demo");
        run.setStatus(AgentRunStatus.WAITING_APPROVAL.name());
        run.setVersion(1);
        run.setCreateTime(LocalDateTime.now());
        return run;
    }

    private static final class Fixtures {
        final AgentRunMapper runMapper = mock(AgentRunMapper.class);
        final AgentArtifactMapper artifactMapper = mock(AgentArtifactMapper.class);
        final AgentRunEventService eventService = mock(AgentRunEventService.class);
        final AgentRunService service;

        Fixtures() {
            service = new AgentRunService(
                    runMapper,
                    mock(AgentStepMapper.class),
                    mock(AgentTaskMapper.class),
                    mock(AgentToolCallMapper.class),
                    mock(AgentTaskService.class),
                    mock(AgentMemorySnapshotMapper.class),
                    artifactMapper,
                    mock(AgentRunEventMapper.class),
                    mock(AgentRunStreamEventMapper.class),
                    eventService,
                    mock(AiAgentMapper.class),
                    mock(AgentVersionService.class),
                    mock(AgentRuntimeConfigService.class),
                    mock(AgentRunExecutionSnapshotService.class),
                    mock(AgentToolSnapshotService.class),
                    mock(AgentToolResponseService.class),
                    mock(MemoryProjectService.class),
                    mock(MemoryOrchestrator.class),
                    new ObjectMapper());
        }
    }
}
