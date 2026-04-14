package com.aipal.integration;

import com.aipal.dto.AgentGraphEdge;
import com.aipal.dto.AgentGraphNode;
import com.aipal.dto.AgentGraphResponse;
import com.aipal.entity.AgentHeartbeat;
import com.aipal.entity.AiAgent;
import com.aipal.entity.MonCallRecord;
import com.aipal.mapper.AgentHeartbeatMapper;
import com.aipal.mapper.AiAgentMapper;
import com.aipal.mapper.MonCallRecordMapper;
import com.aipal.service.MonitorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentGraph 端到端集成测试
 *
 * 测试完整数据流:
 * 1. 创建测试Agent数据
 * 2. 创建心跳记录
 * 3. 创建调用记录
 * 4. 调用 getAgentGraph API
 * 5. 验证返回数据结构
 */
@SpringBootTest
@Transactional
class AgentGraphIntegrationTest {

    @Autowired
    private MonitorService monitorService;

    @Autowired
    private AiAgentMapper agentMapper;

    @Autowired
    private AgentHeartbeatMapper heartbeatMapper;

    @Autowired
    private MonCallRecordMapper callRecordMapper;

    @Test
    void testEndToEndGraphGeneration() {
        // Step 1: Create test agents
        AiAgent agent1 = new AiAgent();
        agent1.setAgentName("TestAgent-1");
        agent1.setCategory("AI");
        agent1.setStatus(1);
        agentMapper.insert(agent1);

        AiAgent agent2 = new AiAgent();
        agent2.setAgentName("TestAgent-2");
        agent2.setCategory("AI");
        agent2.setStatus(1);
        agentMapper.insert(agent2);

        // Step 2: Create heartbeat records
        AgentHeartbeat heartbeat1 = new AgentHeartbeat();
        heartbeat1.setAgentId(agent1.getId());
        heartbeat1.setStatus(1);
        heartbeat1.setLastHeartbeat(LocalDateTime.now());
        heartbeatMapper.insert(heartbeat1);

        AgentHeartbeat heartbeat2 = new AgentHeartbeat();
        heartbeat2.setAgentId(agent2.getId());
        heartbeat2.setStatus(1);
        heartbeat2.setLastHeartbeat(LocalDateTime.now());
        heartbeatMapper.insert(heartbeat2);

        // Step 3: Create call records
        MonCallRecord record1 = new MonCallRecord();
        record1.setAgentId(agent1.getId());
        record1.setDurationMs(100);
        record1.setSuccess((byte) 1);
        record1.setCreateTime(LocalDateTime.now());
        callRecordMapper.insert(record1);

        MonCallRecord record2 = new MonCallRecord();
        record2.setAgentId(agent2.getId());
        record2.setDurationMs(200);
        record2.setSuccess((byte) 1);
        record2.setCreateTime(LocalDateTime.now());
        callRecordMapper.insert(record2);

        // Step 4: Call getAgentGraph
        AgentGraphResponse response = monitorService.getAgentGraph();

        // Step 5: Verify response structure
        assertNotNull(response);
        assertNotNull(response.getNodes());
        assertNotNull(response.getEdges());

        // Verify nodes contain our test agents
        boolean foundAgent1 = response.getNodes().stream()
                .anyMatch(n -> n.getName().equals("TestAgent-1"));
        boolean foundAgent2 = response.getNodes().stream()
                .anyMatch(n -> n.getName().equals("TestAgent-2"));
        assertTrue(foundAgent1 || foundAgent2, "Should find at least one test agent");
    }

    @Test
    void testGraphResponseStructure() {
        AgentGraphResponse response = monitorService.getAgentGraph();

        assertNotNull(response.getNodes());
        assertTrue(response.getNodes() instanceof java.util.List);

        assertNotNull(response.getEdges());
        assertTrue(response.getEdges() instanceof java.util.List);
    }

    @Test
    void testNodeFields() {
        AgentGraphResponse response = monitorService.getAgentGraph();

        for (AgentGraphNode node : response.getNodes()) {
            assertNotNull(node.getId(), "Node ID should not be null");
            assertNotNull(node.getName(), "Node name should not be null");
            assertNotNull(node.getType(), "Node type should not be null");
            assertNotNull(node.getStatus(), "Node status should not be null");
        }
    }

    @Test
    void testEdgeFields() {
        AgentGraphResponse response = monitorService.getAgentGraph();

        for (AgentGraphEdge edge : response.getEdges()) {
            assertNotNull(edge.getTarget(), "Edge target should not be null");
            assertNotNull(edge.getCallCount(), "Edge callCount should not be null");
        }
    }

    @Test
    void testEmptyDatabaseScenario() {
        // Clear all data
        callRecordMapper.delete(null);
        heartbeatMapper.delete(null);
        // Note: We don't delete agents as they might be needed for other tests

        AgentGraphResponse response = monitorService.getAgentGraph();

        assertNotNull(response);
        assertNotNull(response.getNodes());
        assertNotNull(response.getEdges());
    }
}
