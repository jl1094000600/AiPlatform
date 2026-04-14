package com.aipal.service;

import com.aipal.dto.ExecutionChainNode;
import com.aipal.dto.ExecutionChainResponse;
import com.aipal.entity.A2ATask;
import com.aipal.entity.AiAgent;
import com.aipal.mapper.A2ATaskMapper;
import com.aipal.mapper.AgentHeartbeatMapper;
import com.aipal.mapper.AiAgentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * MonitorService 执行链路服务测试
 *
 * 测试接口: GET /api/v1/monitor/execution-chain
 */
@SpringBootTest
@Transactional
class ExecutionChainServiceTest {

    @Autowired
    private MonitorService monitorService;

    @MockBean
    private AiAgentMapper agentMapper;

    @MockBean
    private AgentHeartbeatMapper heartbeatMapper;

    @MockBean
    private A2ATaskMapper a2aTaskMapper;

    @Test
    void testGetExecutionChain_ByTaskId() {
        // Mock tasks
        A2ATask task = createMockTask("task-1", 1L, 2L);
        when(a2aTaskMapper.selectList(any())).thenReturn(Collections.singletonList(task));
        when(agentMapper.selectList(any())).thenReturn(createMockAgents());

        ExecutionChainResponse response = monitorService.getExecutionChain("task-1", null);
        assertNotNull(response);
        assertNotNull(response.getChain());
    }

    @Test
    void testGetExecutionChain_BySessionId() {
        A2ATask task1 = createMockTask("task-1", 1L, 2L);
        task1.setSessionId("session-123");
        A2ATask task2 = createMockTask("task-2", 2L, 3L);
        task2.setSessionId("session-123");

        when(a2aTaskMapper.selectList(any())).thenReturn(List.of(task1, task2));
        when(agentMapper.selectList(any())).thenReturn(createMockAgents());

        ExecutionChainResponse response = monitorService.getExecutionChain(null, "session-123");
        assertNotNull(response);
        assertEquals("session-123", response.getSessionId());
    }

    @Test
    void testGetExecutionChain_EmptyChain() {
        when(a2aTaskMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(agentMapper.selectList(any())).thenReturn(new ArrayList<>());

        ExecutionChainResponse response = monitorService.getExecutionChain("non-existent-task", null);
        assertNotNull(response);
        assertTrue(response.getChain().isEmpty());
    }

    @Test
    void testGetExecutionChain_NodeStructure() {
        A2ATask task = createMockTask("task-1", 1L, 2L);
        when(a2aTaskMapper.selectList(any())).thenReturn(Collections.singletonList(task));
        when(agentMapper.selectList(any())).thenReturn(createMockAgents());

        ExecutionChainResponse response = monitorService.getExecutionChain("task-1", null);

        assertFalse(response.getChain().isEmpty());
        ExecutionChainNode node = response.getChain().get(0);

        assertNotNull(node.getTaskId());
        assertNotNull(node.getSourceAgentId());
        assertNotNull(node.getTargetAgentId());
        assertNotNull(node.getSourceAgentName());
        assertNotNull(node.getTargetAgentName());
        assertNotNull(node.getStatus());
    }

    @Test
    void testGetExecutionChain_DurationCalculation() {
        A2ATask task = createMockTask("task-1", 1L, 2L);
        task.setStartTime(LocalDateTime.now().minusSeconds(10));
        task.setEndTime(LocalDateTime.now());

        when(a2aTaskMapper.selectList(any())).thenReturn(Collections.singletonList(task));
        when(agentMapper.selectList(any())).thenReturn(createMockAgents());

        ExecutionChainResponse response = monitorService.getExecutionChain("task-1", null);

        assertFalse(response.getChain().isEmpty());
        ExecutionChainNode node = response.getChain().get(0);
        // Duration should be approximately 10000ms (10 seconds)
        assertTrue(node.getDurationMs() >= 9000 && node.getDurationMs() <= 11000);
    }

    @Test
    void testGetExecutionChain_MultipleTasks() {
        A2ATask task1 = createMockTask("task-1", 1L, 2L);
        task1.setSessionId("session-multi");
        A2ATask task2 = createMockTask("task-2", 2L, 3L);
        task2.setSessionId("session-multi");

        when(a2aTaskMapper.selectList(any())).thenReturn(List.of(task1, task2));
        when(agentMapper.selectList(any())).thenReturn(createMockAgents());

        ExecutionChainResponse response = monitorService.getExecutionChain(null, "session-multi");

        assertEquals(2, response.getChain().size());
    }

    @Test
    void testGetExecutionChain_RequiresTaskIdOrSessionId() {
        // When both taskId and sessionId are null, should throw exception
        assertThrows(RuntimeException.class, () -> {
            monitorService.getExecutionChain(null, null);
        });
    }

    @Test
    void testGetExecutionChain_RequiresNonEmptyTaskId() {
        // When taskId is empty string, should throw exception
        assertThrows(RuntimeException.class, () -> {
            monitorService.getExecutionChain("", null);
        });
    }

    // Helper methods

    private A2ATask createMockTask(String taskId, Long sourceAgentId, Long targetAgentId) {
        A2ATask task = new A2ATask();
        task.setTaskId(taskId);
        task.setSessionId("session-" + taskId);
        task.setSourceAgentId(sourceAgentId);
        task.setTargetAgentId(targetAgentId);
        task.setStatus("completed");
        task.setTaskType("sync");
        task.setStartTime(LocalDateTime.now().minusMinutes(1));
        task.setEndTime(LocalDateTime.now());
        task.setCreateTime(LocalDateTime.now().minusMinutes(1));
        return task;
    }

    private List<AiAgent> createMockAgents() {
        AiAgent agent1 = new AiAgent();
        agent1.setId(1L);
        agent1.setAgentName("Agent-A");

        AiAgent agent2 = new AiAgent();
        agent2.setId(2L);
        agent2.setAgentName("Agent-B");

        AiAgent agent3 = new AiAgent();
        agent3.setId(3L);
        agent3.setAgentName("Agent-C");

        return List.of(agent1, agent2, agent3);
    }
}
