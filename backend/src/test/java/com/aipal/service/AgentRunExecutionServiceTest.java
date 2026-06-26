package com.aipal.service;

import com.aipal.agent.runtime.AgentRunStatus;
import com.aipal.agent.runtime.AgentTaskStatus;
import com.aipal.entity.AgentArtifact;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentStep;
import com.aipal.entity.AgentTask;
import com.aipal.mapper.AgentArtifactMapper;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.mapper.AgentStepMapper;
import com.aipal.mapper.AgentTaskMapper;
import com.aipal.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunExecutionServiceTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void executesWithFrozenSnapshotAndCreatesPendingApprovalArtifact() {
        Fixtures fixtures = fixtures(100);
        when(fixtures.agentService.callAgentFrozen(eq(11L), any(), eq(7L), eq(null),
                eq(1001L), eq(3003L), eq(4004L))).thenReturn(Map.of("answer", "ok", "totalTokens", 24));

        fixtures.service.execute(fixtures.task, "worker-a");

        ArgumentCaptor<AgentArtifact> artifact = ArgumentCaptor.forClass(AgentArtifact.class);
        verify(fixtures.artifactMapper).insert(artifact.capture());
        assertEquals("AGENT_RESULT", artifact.getValue().getArtifactType());
        assertEquals("PENDING_APPROVAL", artifact.getValue().getStatus());
        verify(fixtures.eventService).record(fixtures.run, AgentRunStatus.RUNNING.name(), AgentRunStatus.WAITING_APPROVAL.name(), "Awaiting artifact approval");
        fixtures.service.shutdownHeartbeatScheduler();
    }

    @Test
    void tokenBudgetExceededFailsWithoutArtifact() {
        Fixtures fixtures = fixtures(10);
        when(fixtures.agentService.callAgentFrozen(eq(11L), any(), eq(7L), eq(null),
                eq(1001L), eq(3003L), eq(4004L))).thenReturn(Map.of("answer", "too large", "totalTokens", 11));

        fixtures.service.execute(fixtures.task, "worker-a");

        verify(fixtures.artifactMapper, never()).insert(any());
        verify(fixtures.eventService).record(fixtures.run, AgentRunStatus.RUNNING.name(), AgentRunStatus.FAILED.name(), "Token budget exceeded");
        fixtures.service.shutdownHeartbeatScheduler();
    }

    private Fixtures fixtures(int maxTotalTokens) {
        TenantContext.set(new TenantContext.Context(7L, "tester", 1L, "tenant-1", false, Set.of(), Set.of()));
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        AgentTaskMapper taskMapper = mock(AgentTaskMapper.class);
        AgentStepMapper stepMapper = mock(AgentStepMapper.class);
        AgentArtifactMapper artifactMapper = mock(AgentArtifactMapper.class);
        AgentTaskService taskService = mock(AgentTaskService.class);
        AgentRunEventService eventService = mock(AgentRunEventService.class);
        AgentService agentService = mock(AgentService.class);
        AgentRunExecutionSnapshotService snapshotService = mock(AgentRunExecutionSnapshotService.class);
        AgentRun run = run(maxTotalTokens);
        AgentTask task = task();
        when(runMapper.selectById(99L)).thenReturn(run);
        when(stepMapper.insert(any())).thenAnswer(invocation -> {
            AgentStep step = invocation.getArgument(0);
            step.setId(88L);
            return 1;
        });
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(runMapper.update(any(), any())).thenReturn(1);
        when(snapshotService.decrypt(run)).thenReturn(Map.of("runtimeConfig", Map.of(
                "modelId", 1001,
                "promptId", 3003,
                "promptVersionId", 4004
        )));
        AgentRunExecutionService service = new AgentRunExecutionService(runMapper, taskMapper, stepMapper,
                artifactMapper, taskService, eventService, agentService, snapshotService, new ObjectMapper(), transactionTemplate());
        return new Fixtures(service, run, task, artifactMapper, eventService, agentService);
    }

    private AgentRun run(int maxTotalTokens) {
        AgentRun run = new AgentRun();
        run.setId(99L);
        run.setTenantId(1L);
        run.setAgentId(11L);
        run.setOwnerUserId(7L);
        run.setStatus(AgentRunStatus.RUNNING.name());
        run.setInputJson("{\"requirement\":\"demo\"}");
        run.setTraceId("trace-1");
        run.setVersion(1);
        run.setMaxTotalTokens(maxTotalTokens);
        return run;
    }

    private AgentTask task() {
        AgentTask task = new AgentTask();
        task.setId(77L);
        task.setTenantId(1L);
        task.setRunId(99L);
        task.setStatus(AgentTaskStatus.RUNNING.name());
        task.setLeaseOwner("worker-a");
        task.setLeaseUntil(LocalDateTime.now().plusMinutes(5));
        task.setAttemptCount(1);
        task.setMaxAttempts(3);
        return task;
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return new SimpleTransactionStatus(); }
            @Override public void commit(TransactionStatus status) {}
            @Override public void rollback(TransactionStatus status) {}
        });
    }

    private record Fixtures(AgentRunExecutionService service, AgentRun run, AgentTask task,
                            AgentArtifactMapper artifactMapper, AgentRunEventService eventService,
                            AgentService agentService) {}
}
