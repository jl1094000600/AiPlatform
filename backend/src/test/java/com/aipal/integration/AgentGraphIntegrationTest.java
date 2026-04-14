package com.aipal.integration;

import com.aipal.dto.AgentGraphEdge;
import com.aipal.dto.AgentGraphNode;
import com.aipal.dto.AgentGraphResponse;
import com.aipal.entity.A2ATask;
import com.aipal.entity.AgentHeartbeat;
import com.aipal.entity.AiAgent;
import com.aipal.mapper.A2ATaskMapper;
import com.aipal.mapper.AgentHeartbeatMapper;
import com.aipal.mapper.AiAgentMapper;
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
 * 3. 创建A2A任务记录
 * 4. 调用 getAgentGraph API
 * 5. 验证返回数据结构
 *
 * 边数据来源: ai_a2a_task 表 (source_agent_id -> target_agent_id)
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
    private A2ATaskMapper a2aTaskMapper;

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

        // Step 3: Create A2A task records (边数据)
        A2ATask task1 = new A2ATask();
        task1.setSourceAgentId(agent1.getId());
        task1.setTargetAgentId(agent2.getId());
        task1.setStartTime(LocalDateTime.now().minusMinutes(1));
        task1.setEndTime(LocalDateTime.now());
        task1.setCreateTime(LocalDateTime.now());
        a2aTaskMapper.insert(task1);

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
            assertNotNull(edge.getSource(), "Edge source should not be null");
            assertNotNull(edge.getTarget(), "Edge target should not be null");
            assertNotNull(edge.getCallCount(), "Edge callCount should not be null");
        }
    }

    @Test
    void testEmptyDatabaseScenario() {
        // Clear all A2A task data
        a2aTaskMapper.delete(null);
        heartbeatMapper.delete(null);
        // Note: We don't delete agents as they might be needed for other tests

        AgentGraphResponse response = monitorService.getAgentGraph();

        assertNotNull(response);
        assertNotNull(response.getNodes());
        assertNotNull(response.getEdges());
    }

    @Test
    void testA2ATaskEdgeGeneration() {
        // Create agents first
        AiAgent agent1 = new AiAgent();
        agent1.setAgentName("SourceAgent");
        agent1.setCategory("AI");
        agentMapper.insert(agent1);

        AiAgent agent2 = new AiAgent();
        agent2.setAgentName("TargetAgent");
        agent2.setCategory("AI");
        agentMapper.insert(agent2);

        // Create A2A task
        A2ATask task = new A2ATask();
        task.setSourceAgentId(agent1.getId());
        task.setTargetAgentId(agent2.getId());
        task.setStartTime(LocalDateTime.now().minusSeconds(5));
        task.setEndTime(LocalDateTime.now());
        task.setCreateTime(LocalDateTime.now());
        a2aTaskMapper.insert(task);

        AgentGraphResponse response = monitorService.getAgentGraph();

        // Find the edge between our agents
        boolean foundEdge = response.getEdges().stream()
                .anyMatch(e -> e.getSource().equals(agent1.getId()) &&
                               e.getTarget().equals(agent2.getId()));
        assertTrue(foundEdge, "Should find edge from source to target agent");
    }
}
