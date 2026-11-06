package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.agent.runtime.workspace.WorkspaceFactory;
import com.aipal.agent.runtime.workspace.WorkspaceManager;
import com.aipal.entity.AgentRun;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * G1: approve / reject / timeout / finalize must reclaim run workspace (not only cancel).
 */
class AgentRunTerminalWorkspaceReclaimTest {

    private WorkspaceManager injectWorkspaceManager(Object target, WorkspaceManager manager) throws Exception {
        Field field = target.getClass().getDeclaredField("workspaceManager");
        field.setAccessible(true);
        field.set(target, manager);
        return manager;
    }

    private AgentRun run() {
        AgentRun run = new AgentRun();
        run.setId(9L);
        run.setTenantId(42L);
        return run;
    }

    @Test
    void timeoutServiceReleasesWorkspace() throws Exception {
        AgentRunTimeoutService service = new AgentRunTimeoutService(
                mock(com.aipal.mapper.AgentRunMapper.class),
                mock(AgentTaskService.class),
                mock(com.aipal.security.TenantTaskRunner.class),
                mock(AgentRunEventService.class));
        WorkspaceManager manager = mock(WorkspaceManager.class);
        injectWorkspaceManager(service, manager);

        var method = AgentRunTimeoutService.class.getDeclaredMethod("releaseWorkspaceSafely", AgentRun.class);
        method.setAccessible(true);
        method.invoke(service, run());
        verify(manager, times(1)).release(any(WorkspaceFactory.WorkspaceIdentity.class));
    }

    @Test
    void timeoutServiceSkipsWhenManagerMissing() throws Exception {
        AgentRunTimeoutService service = new AgentRunTimeoutService(
                mock(com.aipal.mapper.AgentRunMapper.class),
                mock(AgentTaskService.class),
                mock(com.aipal.security.TenantTaskRunner.class),
                mock(AgentRunEventService.class));
        var method = AgentRunTimeoutService.class.getDeclaredMethod("releaseWorkspaceSafely", AgentRun.class);
        method.setAccessible(true);
        method.invoke(service, run());
    }

    @Test
    void executionServiceReleasesWorkspaceOnFinalizeHelper() throws Exception {
        AgentRunExecutionService service = new AgentRunExecutionService(
                mock(com.aipal.mapper.AgentRunMapper.class),
                mock(com.aipal.mapper.AgentTaskMapper.class),
                mock(com.aipal.mapper.AgentStepMapper.class),
                mock(com.aipal.mapper.AgentArtifactMapper.class),
                mock(AgentTaskService.class),
                mock(AgentRunEventService.class),
                mock(AgentRunStreamEventService.class),
                mock(AgentLoopExecutor.class),
                mock(AgentRunExecutionSnapshotService.class),
                mock(com.aipal.agent.runtime.budget.RunBudgetGuard.class),
                mock(com.aipal.agent.runtime.trace.RunTracer.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(org.springframework.transaction.support.TransactionTemplate.class));
        WorkspaceManager manager = mock(WorkspaceManager.class);
        injectWorkspaceManager(service, manager);

        var method = AgentRunExecutionService.class.getDeclaredMethod("releaseWorkspaceSafely", AgentRun.class);
        method.setAccessible(true);
        method.invoke(service, run());
        verify(manager, times(1)).release(any(WorkspaceFactory.WorkspaceIdentity.class));
    }
}
