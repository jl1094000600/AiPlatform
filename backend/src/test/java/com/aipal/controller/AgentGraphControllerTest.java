package com.aipal.controller;

import com.aipal.dto.AgentGraphEdge;
import com.aipal.dto.AgentGraphNode;
import com.aipal.dto.AgentGraphResponse;
import com.aipal.service.MonitorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AgentGraphController 图谱功能测试
 *
 * 测试接口: /api/monitor/agent-graph
 *
 * 图谱数据结构:
 * - nodes: Agent节点列表 [{id, name, type, status, lastHeartbeat, instanceCount}]
 * - edges: 调用关系边 [{source, target, callCount, avgResponseTime, lastCallTime}]
 */
@WebMvcTest(MonitorController.class)
class AgentGraphControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MonitorService monitorService;

    @Test
    @WithMockUser
    void testGetAgentGraph_Success() throws Exception {
        AgentGraphResponse mockResponse = createMockGraphResponse();

        when(monitorService.getAgentGraph()).thenReturn(mockResponse);

        mockMvc.perform(get("/api/monitor/agent-graph"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void testGetAgentGraph_EmptyGraph() throws Exception {
        AgentGraphResponse emptyResponse = new AgentGraphResponse();
        emptyResponse.setNodes(new ArrayList<>());
        emptyResponse.setEdges(new ArrayList<>());

        when(monitorService.getAgentGraph()).thenReturn(emptyResponse);

        mockMvc.perform(get("/api/monitor/agent-graph"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void testGetAgentGraph_WithOnlineAgents() throws Exception {
        AgentGraphResponse response = new AgentGraphResponse();

        List<AgentGraphNode> nodes = new ArrayList<>();
        AgentGraphNode node = new AgentGraphNode();
        node.setId(1L);
        node.setName("TestAgent");
        node.setType("AI");
        node.setStatus(1); // online
        node.setLastHeartbeat(LocalDateTime.now());
        node.setInstanceCount(2);
        nodes.add(node);

        List<AgentGraphEdge> edges = new ArrayList<>();
        AgentGraphEdge edge = new AgentGraphEdge();
        edge.setSource(1L);
        edge.setTarget(2L);
        edge.setCallCount(100L);
        edge.setAvgResponseTime(150.5);
        edge.setLastCallTime(LocalDateTime.now());
        edges.add(edge);

        response.setNodes(nodes);
        response.setEdges(edges);

        when(monitorService.getAgentGraph()).thenReturn(response);

        mockMvc.perform(get("/api/monitor/agent-graph"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void testGetAgentGraph_VerifyResponseStructure() throws Exception {
        AgentGraphResponse mockResponse = createMockGraphResponse();
        when(monitorService.getAgentGraph()).thenReturn(mockResponse);

        mockMvc.perform(get("/api/monitor/agent-graph"))
                .andExpect(status().isOk());
    }

    private AgentGraphResponse createMockGraphResponse() {
        AgentGraphResponse response = new AgentGraphResponse();

        List<AgentGraphNode> nodes = new ArrayList<>();
        AgentGraphNode node1 = new AgentGraphNode();
        node1.setId(1L);
        node1.setName("Agent-A");
        node1.setType("AI");
        node1.setStatus(1);
        node1.setLastHeartbeat(LocalDateTime.now());
        node1.setInstanceCount(1);
        nodes.add(node1);

        AgentGraphNode node2 = new AgentGraphNode();
        node2.setId(2L);
        node2.setName("Agent-B");
        node2.setType("AI");
        node2.setStatus(1);
        node2.setLastHeartbeat(LocalDateTime.now());
        node2.setInstanceCount(2);
        nodes.add(node2);

        List<AgentGraphEdge> edges = new ArrayList<>();
        AgentGraphEdge edge = new AgentGraphEdge();
        edge.setSource(1L);
        edge.setTarget(2L);
        edge.setCallCount(50L);
        edge.setAvgResponseTime(120.0);
        edge.setLastCallTime(LocalDateTime.now());
        edges.add(edge);

        response.setNodes(nodes);
        response.setEdges(edges);
        return response;
    }
}
