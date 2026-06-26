package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.agent.runtime.AgentTaskStatus;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentTask;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.mapper.AgentTaskMapper;
import com.aipal.security.TenantContext;
import com.aipal.security.TenantTaskRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTaskServiceTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void claimsOneQueuedTaskAndStartsItsRun() {
        useTenant();
        AgentTaskMapper taskMapper = mock(AgentTaskMapper.class);
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentTask task = queuedTask();
        AgentRun run = queuedRun();
        when(taskMapper.selectNextClaimableForUpdate(1L)).thenReturn(task);
        when(runMapper.selectById(9L)).thenReturn(run);
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(runMapper.update(any(), any())).thenReturn(1);

        AgentRunEventService eventService = mock(AgentRunEventService.class);
        AgentTaskService service = new AgentTaskService(taskMapper, runMapper, mock(TenantTaskRunner.class), eventService);
        AgentTask claimed = service.claimNext("worker-a", 30);

        assertNotNull(claimed);
        assertEquals(AgentTaskStatus.RUNNING.name(), claimed.getStatus());
        assertEquals("worker-a", claimed.getLeaseOwner());
        assertEquals(1, claimed.getAttemptCount());
        verify(taskMapper).selectNextClaimableForUpdate(1L);
        verify(runMapper).update(any(), any());
        verify(eventService).record(run, AgentRunStatus.QUEUED.name(), AgentRunStatus.RUNNING.name(), "Worker claimed root task");
    }

    @Test
    void refusesToClaimTaskForCancelledRun() {
        useTenant();
        AgentTaskMapper taskMapper = mock(AgentTaskMapper.class);
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentTask task = queuedTask();
        AgentRun run = queuedRun();
        run.setStatus(AgentRunStatus.CANCELLED.name());
        when(taskMapper.selectNextClaimableForUpdate(1L)).thenReturn(task);
        when(runMapper.selectById(9L)).thenReturn(run);
        when(taskMapper.update(any(), any())).thenReturn(1);

        AgentTaskService service = new AgentTaskService(taskMapper, runMapper, mock(TenantTaskRunner.class), mock(AgentRunEventService.class));

        assertNull(service.claimNext("worker-a", 30));
        verify(taskMapper).update(any(), any());
    }

    private void useTenant() {
        TenantContext.set(new TenantContext.Context(7L, "tester", 1L, "tenant-1", false, Set.of(), Set.of()));
    }

    private AgentTask queuedTask() {
        AgentTask task = new AgentTask();
        task.setId(8L);
        task.setTenantId(1L);
        task.setRunId(9L);
        task.setStatus(AgentTaskStatus.QUEUED.name());
        task.setAttemptCount(0);
        task.setMaxAttempts(3);
        task.setAvailableAt(LocalDateTime.now());
        return task;
    }

    private AgentRun queuedRun() {
        AgentRun run = new AgentRun();
        run.setId(9L);
        run.setTenantId(1L);
        run.setStatus(AgentRunStatus.QUEUED.name());
        return run;
    }
}
