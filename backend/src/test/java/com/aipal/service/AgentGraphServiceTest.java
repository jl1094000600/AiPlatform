package com.aipal.service;

import com.aipal.dto.AgentGraphEdge;
import com.aipal.dto.AgentGraphNode;
import com.aipal.dto.AgentGraphResponse;
import com.aipal.entity.AgentHeartbeat;
import com.aipal.entity.AiAgent;
import com.aipal.entity.MonCallRecord;
import com.aipal.mapper.AgentHeartbeatMapper;
import com.aipal.mapper.AiAgentMapper;
import com.aipal.mapper.MonCallRecordMapper;
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
    private MonCallRecordMapper callRecordMapper;

    @Test
    void testGetAgentGraph_Basic() {
        // Mock empty data
        when(agentMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(heartbeatMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(callRecordMapper.selectList(any())).thenReturn(new ArrayList<>());

        AgentGraphResponse graph = monitorService.getAgentGraph();
        assertNotNull(graph);
        assertNotNull(graph.getNodes());
        assertNotNull(graph.getEdges());
    }

    @Test
    void testGetAgentGraph_WithAgents() {
        // Mock agent data
        AiAgent agent = new AiAgent();
        agent.setId(1L);
        agent.setAgentName("TestAgent");
        agent.setCategory("AI");
        when(agentMapper.selectList(any())).thenReturn(Collections.singletonList(agent));
        when(heartbeatMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(callRecordMapper.selectList(any())).thenReturn(new ArrayList<>());

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
        when(callRecordMapper.selectList(any())).thenReturn(new ArrayList<>());

        AgentGraphResponse graph = monitorService.getAgentGraph();
        AgentGraphNode node = graph.getNodes().get(0);

        assertNotNull(node.getId());
        assertNotNull(node.getName());
        assertNotNull(node.getType());
        assertNotNull(node.getStatus());
    }

    @Test
    void testGetAgentGraph_EdgeStructure() {
        // Mock call record data
        MonCallRecord record = new MonCallRecord();
        record.setAgentId(1L);
        record.setDurationMs(100);
        record.setCreateTime(LocalDateTime.now());

        when(agentMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(heartbeatMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(callRecordMapper.selectList(any())).thenReturn(Collections.singletonList(record));

        AgentGraphResponse graph = monitorService.getAgentGraph();
        assertNotNull(graph.getEdges());
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
        when(callRecordMapper.selectList(any())).thenReturn(new ArrayList<>());

        AgentGraphResponse graph = monitorService.getAgentGraph();
        AgentGraphNode node = graph.getNodes().get(0);

        assertEquals(1, node.getStatus());
        assertNotNull(node.getLastHeartbeat());
    }

    @Test
    void testGetAgentGraph_Aggregation() {
        // Test that call records are properly aggregated
        MonCallRecord record1 = new MonCallRecord();
        record1.setAgentId(1L);
        record1.setDurationMs(100);
        record1.setCreateTime(LocalDateTime.now());

        MonCallRecord record2 = new MonCallRecord();
        record2.setAgentId(1L);
        record2.setDurationMs(200);
        record2.setCreateTime(LocalDateTime.now());

        when(agentMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(heartbeatMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(callRecordMapper.selectList(any())).thenReturn(List.of(record1, record2));

        AgentGraphResponse graph = monitorService.getAgentGraph();
        assertNotNull(graph.getEdges());

        // Both records should be aggregated into one edge
        assertEquals(1, graph.getEdges().size());
        assertEquals(2L, graph.getEdges().get(0).getCallCount());
    }
}
