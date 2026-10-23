package com.aipal.service;

import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentToolCall;
import com.aipal.mapper.AgentRunMapper;
import com.aipal.mapper.AgentToolCallMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W5: tool-approval waits reuse AgentTool permission semantics (REQUIRE_APPROVAL +
 * AgentToolCall rows). Approve continues; reject fails the call.
 */
class AgentToolApprovalServiceTest {

    private AgentToolCallMapper toolCallMapper;
    private AgentRunMapper runMapper;
    private AgentToolApprovalService service;
    private final List<AgentToolCall> calls = new ArrayList<>();
    private final AtomicReference<String> callStatus = new AtomicReference<>("WAITING_TOOL_APPROVAL");

    @BeforeEach
    void setUp() {
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, AgentToolCall.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, AgentRun.class);

        toolCallMapper = mock(AgentToolCallMapper.class);
        runMapper = mock(AgentRunMapper.class);
        service = new AgentToolApprovalService(toolCallMapper, runMapper);

        when(toolCallMapper.insert(any())).thenAnswer(inv -> {
            AgentToolCall call = inv.getArgument(0);
            if (call.getId() == null) {
                call.setId(99L);
            }
            calls.add(call);
            return 1;
        });
        when(toolCallMapper.updateById(any())).thenAnswer(inv -> {
            AgentToolCall update = inv.getArgument(0);
            if (update.getId() != null && update.getStatus() != null) {
                callStatus.set(update.getStatus());
            }
            return 1;
        });
        when(toolCallMapper.selectOne(any())).thenAnswer(inv -> {
            AgentToolCall call = waitingCall();
            call.setStatus(callStatus.get());
            return call;
        });
        when(toolCallMapper.update(any(), any())).thenAnswer(inv -> {
            AgentToolCall update = inv.getArgument(0);
            if (update.getStatus() != null) {
                callStatus.set(update.getStatus());
                return 1;
            }
            return 0;
        });
    }

    private AgentToolCall waitingCall() {
        AgentToolCall call = new AgentToolCall();
        call.setId(99L);
        call.setTenantId(1L);
        call.setRunId(7L);
        call.setCallId("call-1");
        call.setToolName("shell.exec");
        call.setStatus("WAITING_TOOL_APPROVAL");
        return call;
    }

    private AgentRun workflowRun() {
        AgentRun run = new AgentRun();
        run.setId(7L);
        run.setTenantId(1L);
        run.setBusinessType("WORKFLOW_NODE");
        run.setBusinessId("exec-1:node-a");
        return run;
    }

    @Test
    void approveResumesWaitingCall() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<AgentToolApprovalService.ApprovalOutcome> outcome = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            outcome.set(service.awaitApproval(workflowRun(), waitingCall(), Duration.ofSeconds(5)));
            done.countDown();
        });
        waiter.start();
        Thread.sleep(100);

        assertTrue(service.approve(1L, 99L, "ok"));
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertTrue(outcome.get().approved());
    }

    @Test
    void rejectFailsWaitingCall() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<AgentToolApprovalService.ApprovalOutcome> outcome = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            outcome.set(service.awaitApproval(workflowRun(), waitingCall(), Duration.ofSeconds(5)));
            done.countDown();
        });
        waiter.start();
        Thread.sleep(100);

        assertTrue(service.reject(1L, 99L, "risky"));
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertFalse(outcome.get().approved());
        assertEquals("risky", outcome.get().reason());
    }

    @Test
    void timeoutFailsWaitingCall() {
        AgentToolApprovalService.ApprovalOutcome outcome =
                service.awaitApproval(workflowRun(), waitingCall(), Duration.ofMillis(200));
        assertFalse(outcome.approved());
        assertTrue(outcome.reason().contains("timed out"));
    }

    @Test
    void workflowExecutionIdParsesBusinessId() {
        assertEquals("exec-1", AgentToolApprovalService.workflowExecutionIdOf(workflowRun()));
        AgentRun plain = new AgentRun();
        plain.setBusinessType("CHAT");
        plain.setBusinessId("x");
        assertEquals(null, AgentToolApprovalService.workflowExecutionIdOf(plain));
    }

    @Test
    void approveForExecutionRejectsForeignCall() {
        when(runMapper.selectById(7L)).thenReturn(workflowRun());
        // tool call belongs to exec-1, asking for exec-2 must fail
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.approveForExecution("exec-2", 99L, "x"));
    }

    @Test
    void listWaitingReturnsCallsInWaitingStatus() {
        when(runMapper.selectList(any())).thenReturn(List.of(workflowRun()));
        when(runMapper.selectById(7L)).thenReturn(workflowRun());
        when(toolCallMapper.selectList(any())).thenAnswer(inv -> List.of(waitingCall()));
        List<AgentToolCall> waiting = service.listWaitingForExecution("exec-1");
        assertEquals(1, waiting.size());
    }
}
