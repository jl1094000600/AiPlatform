package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.common.BizException;
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

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SC01 A04: run cancel/terminal reclaim and cross-tenant rejection.
 */
class AgentRunCancelTenantBoundaryTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void getRejectsCrossTenantRun() {
        Fixtures fixtures = fixtures();
        AgentRun foreign = runningRun();
        foreign.setTenantId(2L);
        when(fixtures.runMapper.selectById(99L)).thenReturn(foreign);
        TenantContext.set(context(1L, 7L));

        BizException error = assertThrows(BizException.class, () -> fixtures.service.get(99L),
                "cross-tenant run must not be readable");
        assertEquals(404, error.getCode());
    }

    @Test
    void cancelRejectsCrossTenantRunWithoutStateChange() {
        Fixtures fixtures = fixtures();
        AgentRun foreign = runningRun();
        foreign.setTenantId(2L);
        when(fixtures.runMapper.selectById(99L)).thenReturn(foreign);
        TenantContext.set(context(1L, 7L));

        BizException error = assertThrows(BizException.class,
                () -> fixtures.service.cancel(99L, "stop"),
                "cross-tenant run must not be cancellable");
        assertEquals(404, error.getCode());
        verify(fixtures.runMapper, never()).update(any(), any());
        verify(fixtures.toolResponseService, never()).abortPendingForRun(any(), any());
    }

    @Test
    void cancelOnTerminalRunIsNoOpReclaim() {
        Fixtures fixtures = fixtures();
        AgentRun terminal = runningRun();
        terminal.setStatus(AgentRunStatus.SUCCEEDED.name());
        when(fixtures.runMapper.selectById(99L)).thenReturn(terminal);
        TenantContext.set(context(1L, 7L, Set.of("agent:manage")));

        AgentRun result = fixtures.service.cancel(99L, "late cancel");

        assertEquals(AgentRunStatus.SUCCEEDED.name(), result.getStatus(),
                "terminal run must not be re-activated by a late cancel");
        verify(fixtures.runMapper, never()).update(any(), any());
        verify(fixtures.toolResponseService, never()).abortPendingForRun(any(), any());
        verify(fixtures.agentTaskService, never()).cancelTasksForRun(any(), any());
    }

    @Test
    void cancelRunningRunMarksCancelledAndAbortsPendingWork() {
        Fixtures fixtures = fixtures();
        AgentRun running = runningRun();
        when(fixtures.runMapper.selectById(99L)).thenReturn(running);
        when(fixtures.runMapper.update(any(), any())).thenReturn(1);
        TenantContext.set(context(1L, 7L, Set.of("agent:manage")));

        AgentRun result = fixtures.service.cancel(99L, "user aborted");

        assertEquals(AgentRunStatus.CANCELLED.name(), result.getStatus());
        assertEquals("user aborted", result.getErrorMessage());
        verify(fixtures.toolResponseService).abortPendingForRun(99L, "user aborted");
        verify(fixtures.agentTaskService).cancelTasksForRun(99L, "user aborted");
        verify(fixtures.eventService).record(any(AgentRun.class), any(), any(), any());
    }

    private static Fixtures fixtures() {
        return new Fixtures();
    }

    private static TenantContext.Context context(Long tenantId, Long userId) {
        return context(tenantId, userId, Set.of());
    }

    private static TenantContext.Context context(Long tenantId, Long userId, Set<String> permissions) {
        return new TenantContext.Context(userId, "tester", tenantId, "tenant-" + tenantId,
                false, Set.of(), permissions);
    }

    private static AgentRun runningRun() {
        AgentRun run = new AgentRun();
        run.setId(99L);
        run.setTenantId(1L);
        run.setOwnerUserId(7L);
        run.setProjectKey("demo");
        run.setStatus(AgentRunStatus.RUNNING.name());
        run.setVersion(1);
        run.setCreateTime(LocalDateTime.now());
        return run;
    }

    /** Minimal AgentRunService wiring with mocked collaborators. */
    private static final class Fixtures {
        final AgentRunMapper runMapper = mock(AgentRunMapper.class);
        final AgentTaskService agentTaskService = mock(AgentTaskService.class);
        final AgentToolResponseService toolResponseService = mock(AgentToolResponseService.class);
        final AgentRunEventService eventService = mock(AgentRunEventService.class);
        final AgentRunService service;

        Fixtures() {
            service = new AgentRunService(
                    runMapper,
                    mock(AgentStepMapper.class),
                    mock(AgentTaskMapper.class),
                    mock(AgentToolCallMapper.class),
                    agentTaskService,
                    mock(AgentMemorySnapshotMapper.class),
                    mock(AgentArtifactMapper.class),
                    mock(AgentRunEventMapper.class),
                    mock(AgentRunStreamEventMapper.class),
                    eventService,
                    mock(AiAgentMapper.class),
                    mock(AgentVersionService.class),
                    mock(AgentRuntimeConfigService.class),
                    mock(AgentRunExecutionSnapshotService.class),
                    mock(AgentToolSnapshotService.class),
                    toolResponseService,
                    mock(MemoryProjectService.class),
                    mock(MemoryOrchestrator.class),
                    new ObjectMapper());
        }
    }
}
