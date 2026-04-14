package com.aipal.service;

import com.aipal.dto.AgentGraphEdge;
import com.aipal.dto.AgentGraphNode;
import com.aipal.dto.AgentGraphResponse;
import com.aipal.entity.A2ATask;
import com.aipal.entity.AgentHeartbeat;
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
 * MonitorService 图谱数据聚合测试
 *
 * 测试接口: GET /api/v1/monitor/agent-graph
 *
 * 图谱数据来源:
 * - 节点: ai_agent 表 + ai_agent_heartbeat 表
 * - 边: ai_a2a_task 表 (source_agent_id -> target_agent_id)
 */
@SpringBootTest
@Transactional
class AgentGraphServiceTest {

    @Autowired
    private MonitorService monitorService;

    @MockBean
    private AiAgentMapper agentMapper;

    @MockBean
    private AgentHeartbeatMapper heartbeatMapper;

    @MockBean
    private A2ATaskMapper a2aTaskMapper;

    @Test
    void testGetAgentGraph_Basic() {
        when(agentMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(heartbeatMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(a2aTaskMapper.selectList(any())).thenReturn(new ArrayList<>());

        AgentGraphResponse graph = monitorService.getAgentGraph();
        assertNotNull(graph);
        assertNotNull(graph.getNodes());
        assertNotNull(graph.getEdges());
    }

    @Test
    void testGetAgentGraph_WithAgents() {
        AiAgent agent = new AiAgent();
        agent.setId(1L);
        agent.setAgentName("TestAgent");
        agent.setCategory("AI");
        when(agentMapper.selectList(any())).thenReturn(Collections.singletonList(agent));
        when(heartbeatMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(a2aTaskMapper.selectList(any())).thenReturn(new ArrayList<>());

        AgentGraphResponse graph = monitorService.getAgentGraph();
        assertNotNull(graph);
        assertEquals(1, graph.getNodes().size());
        assertEquals("TestAgent", graph.getNodes().get(0).getName());
    }

    @Test
    void testGetAgentGraph_NodeStructure() {
        AiAgent agent = new AiAgent();
        agent.setId(1L);
        agent.setAgentName("TestAgent");
        agent.setCategory("AI");
        when(agentMapper.selectList(any())).thenReturn(Collections.singletonList(agent));
        when(heartbeatMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(a2aTaskMapper.selectList(any())).thenReturn(new ArrayList<>());

        AgentGraphResponse graph = monitorService.getAgentGraph();
        AgentGraphNode node = graph.getNodes().get(0);

        assertNotNull(node.getId());
        assertNotNull(node.getName());
        assertNotNull(node.getType());
        assertNotNull(node.getStatus());
    }

    @Test
    void testGetAgentGraph_EdgeStructure() {
        A2ATask task = new A2ATask();
        task.setSourceAgentId(1L);
        task.setTargetAgentId(2L);
        task.setStartTime(LocalDateTime.now().minusMinutes(1));
        task.setEndTime(LocalDateTime.now());
        task.setCreateTime(LocalDateTime.now());

        when(agentMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(heartbeatMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(a2aTaskMapper.selectList(any())).thenReturn(Collections.singletonList(task));

        AgentGraphResponse graph = monitorService.getAgentGraph();
        assertNotNull(graph.getEdges());
        assertEquals(1, graph.getEdges().size());
    }

    @Test
    void testGetAgentGraph_WithHeartbeat() {
        AiAgent agent = new AiAgent();
        agent.setId(1L);
        agent.setAgentName("OnlineAgent");
        agent.setCategory("AI");

        AgentHeartbeat heartbeat = new AgentHeartbeat();
        heartbeat.setAgentId(1L);
        heartbeat.setStatus(1);
        heartbeat.setLastHeartbeat(LocalDateTime.now());

        when(agentMapper.selectList(any())).thenReturn(Collections.singletonList(agent));
        when(heartbeatMapper.selectList(any())).thenReturn(Collections.singletonList(heartbeat));
        when(a2aTaskMapper.selectList(any())).thenReturn(new ArrayList<>());

        AgentGraphResponse graph = monitorService.getAgentGraph();
        AgentGraphNode node = graph.getNodes().get(0);

        assertEquals(1, node.getStatus());
        assertNotNull(node.getLastHeartbeat());
        assertEquals(1, node.getInstanceCount());
    }

    @Test
    void testGetAgentGraph_A2ATaskAggregation() {
        // Test that A2A tasks are properly aggregated by source->target pair
        A2ATask task1 = new A2ATask();
        task1.setSourceAgentId(1L);
        task1.setTargetAgentId(2L);
        task1.setStartTime(LocalDateTime.now().minusMinutes(2));
        task1.setEndTime(LocalDateTime.now().minusMinutes(1));
        task1.setCreateTime(LocalDateTime.now().minusMinutes(2));

        A2ATask task2 = new A2ATask();
        task2.setSourceAgentId(1L);
        task2.setTargetAgentId(2L);
        task2.setStartTime(LocalDateTime.now().minusMinutes(1));
        task2.setEndTime(LocalDateTime.now());
        task2.setCreateTime(LocalDateTime.now().minusMinutes(1));

        when(agentMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(heartbeatMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(a2aTaskMapper.selectList(any())).thenReturn(List.of(task1, task2));

        AgentGraphResponse graph = monitorService.getAgentGraph();

        // Both tasks should be aggregated into one edge
        assertEquals(1, graph.getEdges().size());
        assertEquals(2L, graph.getEdges().get(0).getCallCount());
        assertEquals(1L, graph.getEdges().get(0).getSource());
        assertEquals(2L, graph.getEdges().get(0).getTarget());
    }

    @Test
    void testGetAgentGraph_MultipleSourceTargetPairs() {
        // Two different source->target pairs should create two edges
        A2ATask task1 = new A2ATask();
        task1.setSourceAgentId(1L);
        task1.setTargetAgentId(2L);
        task1.setStartTime(LocalDateTime.now());
        task1.setEndTime(LocalDateTime.now());
        task1.setCreateTime(LocalDateTime.now());

        A2ATask task2 = new A2ATask();
        task2.setSourceAgentId(3L);
        task2.setTargetAgentId(4L);
        task2.setStartTime(LocalDateTime.now());
        task2.setEndTime(LocalDateTime.now());
        task2.setCreateTime(LocalDateTime.now());

        when(agentMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(heartbeatMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(a2aTaskMapper.selectList(any())).thenReturn(List.of(task1, task2));

        AgentGraphResponse graph = monitorService.getAgentGraph();
        assertEquals(2, graph.getEdges().size());
    }

    @Test
    void testGetAgentGraph_EdgeResponseTimeCalculation() {
        A2ATask task = new A2ATask();
        task.setSourceAgentId(1L);
        task.setTargetAgentId(2L);
        task.setStartTime(LocalDateTime.now().minusSeconds(10));
        task.setEndTime(LocalDateTime.now());
        task.setCreateTime(LocalDateTime.now());

        when(agentMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(heartbeatMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(a2aTaskMapper.selectList(any())).thenReturn(Collections.singletonList(task));

        AgentGraphResponse graph = monitorService.getAgentGraph();
        AgentGraphEdge edge = graph.getEdges().get(0);

        assertNotNull(edge.getAvgResponseTime());
        // Response time should be approximately 10000ms (10 seconds)
        assertTrue(edge.getAvgResponseTime() >= 9000 && edge.getAvgResponseTime() <= 11000);
    }
}
