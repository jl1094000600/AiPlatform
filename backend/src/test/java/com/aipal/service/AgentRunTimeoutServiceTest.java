package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.entity.AgentRun;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.security.TenantContext;
import com.aipal.security.TenantTaskRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunTimeoutServiceTest {
    @AfterEach void clearTenant() { TenantContext.clear(); }

    @Test
    void marksExpiredRunningRunAsTimeoutAndCancelsItsTasks() {
        TenantContext.set(new TenantContext.Context(null, "system:test", 1L, "tenant-1", false, Set.of(), Set.of("system-task")));
        AgentRun run = new AgentRun();
        run.setId(9L);
        run.setTenantId(1L);
        run.setStatus(AgentRunStatus.RUNNING.name());
        run.setStartTime(LocalDateTime.now().minusMinutes(20));
        run.setVersion(1);
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentTaskService taskService = mock(AgentTaskService.class);
        AgentRunEventService eventService = mock(AgentRunEventService.class);
        when(runMapper.selectList(any())).thenReturn(List.of(run));
        when(runMapper.update(any(), any())).thenReturn(1);
        AgentRunTimeoutService service = new AgentRunTimeoutService(runMapper, taskService, mock(TenantTaskRunner.class), eventService);

        assertEquals(1, service.timeoutExpiredRuns(60));
        verify(taskService).cancelTasksForRun(9L, "Execution timed out");
        verify(eventService).record(run, AgentRunStatus.RUNNING.name(), AgentRunStatus.TIMEOUT.name(), "Execution timed out");
    }
}
