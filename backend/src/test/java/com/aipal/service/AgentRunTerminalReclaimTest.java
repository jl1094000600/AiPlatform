package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.agent.runtime.workspace.Workspace;
import com.aipal.agent.runtime.workspace.WorkspaceFactory;
import com.aipal.agent.runtime.workspace.WorkspaceManager;
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

import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W5: terminal-state resource recycling.
 * Cancellation must release the run workspace; WorkspaceManager release must close
 * exactly once and drop the active binding so later find/isActive see nothing.
 *
 * Known production gaps (not red tests): AgentRunService.approve / reject and
 * AgentRunTimeoutService do not call releaseWorkspaceSafely today — see W5-test-run.md.
 */
class AgentRunTerminalReclaimTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void cancelReleasesRunWorkspace() {
        AtomicInteger closed = new AtomicInteger();
        Workspace workspace = countingWorkspace(closed);
        WorkspaceManager manager = new WorkspaceManager(new WorkspaceFactory("local", "docker", "img") {
            @Override
            public Workspace openToolWorkspace(WorkspaceFactory.WorkspaceIdentity identity) {
                return workspace;
            }
        });
        WorkspaceFactory.WorkspaceIdentity identity =
                new WorkspaceFactory.WorkspaceIdentity(1L, null, 99L, null, null);
        manager.acquire(identity);
        assertTrue(manager.isActive(identity));

        Fixtures fixtures = fixtures();
        AgentRun running = runningRun();
        when(fixtures.runMapper.selectById(99L)).thenReturn(running);
        when(fixtures.runMapper.update(any(), any())).thenReturn(1);
        setWorkspaceManager(fixtures.service, manager);
        TenantContext.set(context(1L, 7L, Set.of("agent:manage")));

        AgentRun result = fixtures.service.cancel(99L, "stop now");

        assertEquals(AgentRunStatus.CANCELLED.name(), result.getStatus());
        assertEquals(1, closed.get(), "cancel must recycle the run workspace");
        assertFalse(manager.isActive(identity), "workspace binding must be dropped on cancel");
        assertNull(manager.find(identity));
    }

    @Test
    void cancelOnTerminalRunIsNoOpForWorkspace() {
        AtomicInteger closed = new AtomicInteger();
        Workspace workspace = countingWorkspace(closed);
        WorkspaceManager manager = new WorkspaceManager(new WorkspaceFactory("local", "docker", "img") {
            @Override
            public Workspace openToolWorkspace(WorkspaceFactory.WorkspaceIdentity identity) {
                return workspace;
            }
        });
        WorkspaceFactory.WorkspaceIdentity identity =
                new WorkspaceFactory.WorkspaceIdentity(1L, null, 99L, null, null);
        manager.acquire(identity);

        Fixtures fixtures = fixtures();
        AgentRun terminal = runningRun();
        terminal.setStatus(AgentRunStatus.SUCCEEDED.name());
        when(fixtures.runMapper.selectById(99L)).thenReturn(terminal);
        setWorkspaceManager(fixtures.service, manager);
        TenantContext.set(context(1L, 7L, Set.of("agent:manage")));

        AgentRun result = fixtures.service.cancel(99L, "late");

        assertEquals(AgentRunStatus.SUCCEEDED.name(), result.getStatus());
        // late cancel is a no-op for state; workspace may still be bound until an explicit release
        // (documented gap — cancel path only releases when it actually transitions state)
        assertEquals(0, closed.get());
    }

    @Test
    void workspaceReleaseByIdentityClosesOnceAndDropsBinding() {
        AtomicInteger closed = new AtomicInteger();
        Workspace workspace = countingWorkspace(closed);
        WorkspaceManager manager = new WorkspaceManager(new WorkspaceFactory("local", "docker", "img") {
            @Override
            public Workspace openToolWorkspace(WorkspaceFactory.WorkspaceIdentity identity) {
                return workspace;
            }
        });
        WorkspaceFactory.WorkspaceIdentity identity =
                new WorkspaceFactory.WorkspaceIdentity(10L, null, 101L, null, null);

        manager.acquire(identity);
        assertTrue(manager.isActive(identity));
        assertEquals(workspace, manager.find(identity));

        manager.release(identity);
        manager.release(identity);

        assertEquals(1, closed.get(), "release must close the workspace exactly once");
        assertNull(manager.find(identity));
        assertFalse(manager.isActive(identity));
    }

    @Test
    void workspaceReleaseByTenantAndRunIdRecycles() {
        AtomicInteger closed = new AtomicInteger();
        Workspace workspace = countingWorkspace(closed);
        WorkspaceManager manager = new WorkspaceManager(new WorkspaceFactory("local", "docker", "img") {
            @Override
            public Workspace openToolWorkspace(WorkspaceFactory.WorkspaceIdentity identity) {
                return workspace;
            }
        });

        manager.acquire(new WorkspaceFactory.WorkspaceIdentity(3L, null, 77L, null, null));
        assertTrue(manager.isActive(new WorkspaceFactory.WorkspaceIdentity(3L, null, 77L, null, null)));

        manager.release(3L, 77L);

        assertEquals(1, closed.get());
        assertNull(manager.find(3L, 77L));
        assertFalse(manager.isActive(new WorkspaceFactory.WorkspaceIdentity(3L, null, 77L, null, null)));
    }

    @Test
    void acquireReusesSameWorkspaceUntilRelease() {
        AtomicInteger opened = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        WorkspaceManager manager = new WorkspaceManager(new WorkspaceFactory("local", "docker", "img") {
            @Override
            public Workspace openToolWorkspace(WorkspaceFactory.WorkspaceIdentity identity) {
                opened.incrementAndGet();
                return countingWorkspace(closed);
            }
        });
        WorkspaceFactory.WorkspaceIdentity identity =
                new WorkspaceFactory.WorkspaceIdentity(5L, null, 55L, null, null);

        Workspace first = manager.acquire(identity);
        Workspace second = manager.acquire(identity);
        assertEquals(first, second, "same identity must reuse the bound workspace");
        assertEquals(1, opened.get());

        manager.release(identity);
        Workspace third = manager.acquire(identity);
        assertEquals(2, opened.get(), "release then acquire must open a fresh workspace");
        manager.release(identity);
        assertEquals(2, closed.get());
    }

    private static Workspace countingWorkspace(AtomicInteger closed) {
        return new Workspace() {
            @Override
            public java.nio.file.Path workDir() {
                return Paths.get("target", "ws-terminal-reclaim-test");
            }

            @Override
            public com.aipal.agent.runtime.workspace.CommandResult exec(List<String> command, Duration timeout) {
                return com.aipal.agent.runtime.workspace.CommandResult.success("cmd", "ok", Duration.ofMillis(1));
            }

            @Override
            public void putFile(String relPath, String content) {
            }

            @Override
            public String getFile(String relPath) {
                return "";
            }

            @Override
            public String health() {
                return "READY";
            }

            @Override
            public void close() {
                closed.incrementAndGet();
            }
        };
    }

    private static void setWorkspaceManager(AgentRunService service, WorkspaceManager manager) {
        try {
            Field field = AgentRunService.class.getDeclaredField("workspaceManager");
            field.setAccessible(true);
            field.set(service, manager);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("failed to inject workspaceManager", e);
        }
    }

    private static Fixtures fixtures() {
        return new Fixtures();
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

    private static final class Fixtures {
        final AgentRunMapper runMapper = mock(AgentRunMapper.class);
        final AgentToolResponseService toolResponseService = mock(AgentToolResponseService.class);
        final AgentTaskService agentTaskService = mock(AgentTaskService.class);
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
