package com.aipal.controller;

import com.aipal.dto.ExecutionChainNode;
import com.aipal.dto.ExecutionChainResponse;
import com.aipal.entity.MonCallRecord;
import com.aipal.service.CallRecordService;
import com.aipal.service.MonitorService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MonitorController 新API测试
 *
 * 测试接口:
 * 1. GET /api/v1/monitor/call-records - 调用记录列表
 * 2. GET /api/v1/monitor/execution-chain - 执行链路
 * 3. GET /api/v1/monitor/graph/export - 图谱导出
 */
@WebMvcTest(MonitorController.class)
class MonitorControllerNewApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CallRecordService callRecordService;

    @MockBean
    private MonitorService monitorService;

    // ========== 调用记录测试 ==========

    @Test
    @WithMockUser
    void testListCallRecords_Success() throws Exception {
        Page<MonCallRecord> mockPage = new Page<>(1, 20);
        when(callRecordService.listCallRecords(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(mockPage);

        mockMvc.perform(get("/api/v1/monitor/call-records")
                        .param("pageNum", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void testListCallRecords_WithAgentIdFilter() throws Exception {
        Page<MonCallRecord> mockPage = new Page<>(1, 20);
        when(callRecordService.listCallRecords(eq(1), eq(20), eq(1L), any(), any()))
                .thenReturn(mockPage);

        mockMvc.perform(get("/api/v1/monitor/call-records")
                        .param("pageNum", "1")
                        .param("pageSize", "20")
                        .param("agentId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void testListCallRecords_WithTimeRange() throws Exception {
        Page<MonCallRecord> mockPage = new Page<>(1, 20);
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now();

        when(callRecordService.listCallRecords(anyInt(), anyInt(), any(), eq(startTime), eq(endTime)))
                .thenReturn(mockPage);

        mockMvc.perform(get("/api/v1/monitor/call-records")
                        .param("pageNum", "1")
                        .param("pageSize", "20")
                        .param("startTime", "2024-01-01 00:00:00")
                        .param("endTime", "2024-01-02 00:00:00"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void testListCallRecords_Pagination() throws Exception {
        Page<MonCallRecord> mockPage = new Page<>(2, 10);
        when(callRecordService.listCallRecords(eq(2), eq(10), any(), any(), any()))
                .thenReturn(mockPage);

        mockMvc.perform(get("/api/v1/monitor/call-records")
                        .param("pageNum", "2")
                        .param("pageSize", "10"))
                .andExpect(status().isOk());
    }

    // ========== 执行链路测试 ==========

    @Test
    @WithMockUser
    void testGetExecutionChain_ByTaskId() throws Exception {
        ExecutionChainResponse mockResponse = createMockExecutionChain();
        when(monitorService.getExecutionChain(eq("task-123"), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/monitor/execution-chain")
                        .param("taskId", "task-123"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void testGetExecutionChain_BySessionId() throws Exception {
        ExecutionChainResponse mockResponse = createMockExecutionChain();
        when(monitorService.getExecutionChain(any(), eq("session-456")))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/monitor/execution-chain")
                        .param("sessionId", "session-456"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void testGetExecutionChain_EmptyChain() throws Exception {
        ExecutionChainResponse emptyResponse = new ExecutionChainResponse();
        emptyResponse.setSessionId("session-empty");
        emptyResponse.setChain(new ArrayList<>());

        when(monitorService.getExecutionChain(any(), any()))
                .thenReturn(emptyResponse);

        mockMvc.perform(get("/api/v1/monitor/execution-chain")
                        .param("sessionId", "session-empty"))
                .andExpect(status().isOk());
    }

    // ========== 图谱导出测试 ==========

    @Test
    @WithMockUser
    void testExportGraph_Success() throws Exception {
        when(monitorService.getAgentGraph()).thenReturn(new com.aipal.dto.AgentGraphResponse());

        mockMvc.perform(get("/api/v1/monitor/graph/export"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void testExportGraph_ReturnsCorrectFormat() throws Exception {
        com.aipal.dto.AgentGraphResponse mockGraph = new com.aipal.dto.AgentGraphResponse();
        mockGraph.setNodes(new ArrayList<>());
        mockGraph.setEdges(new ArrayList<>());

        when(monitorService.getAgentGraph()).thenReturn(mockGraph);

        mockMvc.perform(get("/api/v1/monitor/graph/export"))
                .andExpect(status().isOk());
    }

    // ========== Helper Methods ==========

    private ExecutionChainResponse createMockExecutionChain() {
        ExecutionChainResponse response = new ExecutionChainResponse();
        response.setSessionId("session-123");
        response.setWorkflowId("workflow-1");

        List<ExecutionChainNode> chain = new ArrayList<>();

        ExecutionChainNode node1 = new ExecutionChainNode();
        node1.setTaskId("task-1");
        node1.setSourceAgentId(1L);
        node1.setSourceAgentName("Agent-A");
        node1.setTargetAgentId(2L);
        node1.setTargetAgentName("Agent-B");
        node1.setStatus("completed");
        node1.setTaskType("sync");
        node1.setStartTime(LocalDateTime.now().minusMinutes(5));
        node1.setEndTime(LocalDateTime.now().minusMinutes(4));
        node1.setDurationMs(60000L);
        chain.add(node1);

        ExecutionChainNode node2 = new ExecutionChainNode();
        node2.setTaskId("task-2");
        node2.setSourceAgentId(2L);
        node2.setSourceAgentName("Agent-B");
        node2.setTargetAgentId(3L);
        node2.setTargetAgentName("Agent-C");
        node2.setStatus("completed");
        node2.setTaskType("async");
        node2.setStartTime(LocalDateTime.now().minusMinutes(4));
        node2.setEndTime(LocalDateTime.now().minusMinutes(2));
        node2.setDurationMs(120000L);
        chain.add(node2);

        response.setChain(chain);
        return response;
    }
}
