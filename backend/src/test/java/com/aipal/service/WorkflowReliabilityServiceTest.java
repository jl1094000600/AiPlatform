package com.aipal.service;

import com.aipal.entity.WorkflowExecution;
import com.aipal.entity.WorkflowExecutionEvent;
import com.aipal.mapper.WorkflowExecutionEventMapper;
import com.aipal.mapper.WorkflowExecutionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W4: CAS finish, idempotent cancel, lease recovery, event trail.
 */
class WorkflowReliabilityServiceTest {

    private WorkflowExecutionMapper executionMapper;
    private WorkflowExecutionEventMapper eventMapper;
    private WorkflowReliabilityService service;
    private final List<WorkflowExecutionEvent> events = new ArrayList<>();
    private final List<WorkflowExecution> rows = new ArrayList<>();
    private final AtomicInteger updateWins = new AtomicInteger(1);

    @BeforeEach
    void setUp() {
        // Initialize MyBatis-Plus lambda cache outside a Spring context.
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, WorkflowExecution.class);
        executionMapper = mock(WorkflowExecutionMapper.class);
        eventMapper = mock(WorkflowExecutionEventMapper.class);
        service = new WorkflowReliabilityService(executionMapper, eventMapper);

        when(eventMapper.insert(any())).thenAnswer(inv -> {
            events.add(inv.getArgument(0));
            return 1;
        });
        when(executionMapper.update(isNull(), any())).thenAnswer(inv -> {
            int v = updateWins.getAndDecrement();
            return v > 0 ? 1 : 0;
        });
    }

    private WorkflowExecution row(String status) {
        WorkflowExecution e = new WorkflowExecution();
        e.setExecutionId("exec-1");
        e.setStatus(status);
        e.setLeaseUntil(LocalDateTime.now().minusMinutes(5));
        return e;
    }

    @Test
    void finishCasOnlyWinnerWrites() {
        updateWins.set(1);
        assertTrue(service.finishCas("exec-1", "COMPLETED", "{}", null));
        assertFalse(service.finishCas("exec-1", "FAILED", null, "late"));
        assertTrue(events.stream().anyMatch(e -> "FINISHED".equals(e.getEventType())));
    }

    @Test
    void cancelIsIdempotentOnTerminal() {
        when(executionMapper.selectOne(any())).thenReturn(row("COMPLETED"));
        assertEquals("COMPLETED", service.cancelCas("exec-1", "already done"));
    }

    @Test
    void cancelOnRunningSetsRequestedAndCancels() {
        when(executionMapper.selectOne(any()))
                .thenReturn(row("RUNNING"))
                .thenReturn(row("CANCELLED"));
        updateWins.set(1);
        assertEquals("CANCELLED", service.cancelCas("exec-1", "user"));
    }

    @Test
    void recoverExpiredLeasesFailsRunningWithStaleLease() {
        when(executionMapper.selectList(any())).thenReturn(List.of(row("RUNNING")));
        updateWins.set(1);
        int n = service.recoverExpiredLeases("lease expired");
        assertEquals(1, n);
        assertTrue(events.stream().anyMatch(e -> "RECOVERED".equals(e.getEventType())));
    }

    @Test
    void recordEventPersistsTrail() {
        service.recordEvent("exec-1", "STARTED", null, "go");
        assertEquals(1, events.size());
        assertEquals("STARTED", events.get(0).getEventType());
    }

    @Test
    void markWaitingPausesNonTerminalExecution() {
        updateWins.set(1);
        assertTrue(service.markWaiting("exec-1", "WAITING_APPROVAL", "node-1", "tool approval"));
        assertTrue(events.stream().anyMatch(e -> "WAITING_APPROVAL".equals(e.getEventType())));
    }

    @Test
    void markWaitingIgnoresTerminalStatus() {
        updateWins.set(0);
        assertFalse(service.markWaiting("exec-1", "WAITING_APPROVAL", "node-1", "tool approval"));
    }

    @Test
    void resumeRunningLeavesWaitingState() {
        updateWins.set(1);
        assertTrue(service.resumeRunning("exec-1", "approved"));
        assertTrue(events.stream().anyMatch(e -> "RESUMED".equals(e.getEventType())));
    }

    @Test
    void finishCasInvokesTerminalCleanupAndDropsLease() {
        List<String> cleaned = new ArrayList<>();
        service.setTerminalCleanup((id, status) -> cleaned.add(id + ":" + status));
        updateWins.set(1);
        assertTrue(service.finishCas("exec-1", "COMPLETED", "{}", null));
        assertEquals(List.of("exec-1:COMPLETED"), cleaned);
    }

    @Test
    void finishCasSkipsCleanupWhenLosingRace() {
        List<String> cleaned = new ArrayList<>();
        service.setTerminalCleanup((id, status) -> cleaned.add(id));
        updateWins.set(0);
        assertFalse(service.finishCas("exec-1", "COMPLETED", "{}", null));
        assertTrue(cleaned.isEmpty());
    }

    @Test
    void recoverExpiredLeasesAlsoFailsWaitingRows() {
        WorkflowExecution waiting = row("WAITING_APPROVAL");
        when(executionMapper.selectList(any())).thenReturn(List.of(waiting));
        updateWins.set(1);
        int n = service.recoverExpiredLeases("lease expired");
        assertEquals(1, n);
    }
}
