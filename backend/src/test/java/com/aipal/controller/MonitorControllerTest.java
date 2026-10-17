package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.MonCallRecord;
import com.aipal.service.CallRecordService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MonitorController 单元测试
 * 测试 /api/v1/monitor 下的接口
 */
class MonitorControllerTest extends MonitorControllerTestSupport {

    @Test
    void requestWithoutBearerTokenIsRejected() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/monitor/realtime"))
                .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(callRecordService, monitorService);
    }

    @Test
    void signedUserWithoutRequiredPermissionIsRejected() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/monitor/realtime")
                        .header("Authorization", "Bearer " + token(java.util.List.of())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        org.mockito.Mockito.verifyNoInteractions(callRecordService, monitorService);
    }

    @Test
    void testListRecords() throws Exception {
        Page<MonCallRecord> mockPage = new Page<>(1, 20);
        when(callRecordService.listCallRecords(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(mockPage);

        mockMvc.perform(authorizedGet("/api/v1/monitor/records")
                        .param("pageNum", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void testGetStatistics() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCalls", 100);
        stats.put("successRate", 95.5);
        stats.put("avgDuration", 150);

        when(callRecordService.countByAgentAndTimeRange(any(), any(), any())).thenReturn(100L);
        when(callRecordService.getSuccessRateByAgentAndTimeRange(any(), any(), any())).thenReturn(95.5);
        when(callRecordService.getAvgDurationByAgentAndTimeRange(any(), any(), any())).thenReturn(150.0);

        mockMvc.perform(authorizedGet("/api/v1/monitor/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCalls").value(100))
                .andExpect(jsonPath("$.data.successRate").value(95.5));
    }

    @Test
    void testGetRealtimeData() throws Exception {
        when(callRecordService.countOnlineAgents()).thenReturn(10L);
        when(callRecordService.getCurrentQps()).thenReturn(50.0);
        when(callRecordService.getAvgResponseTime()).thenReturn(100.0);

        mockMvc.perform(authorizedGet("/api/v1/monitor/realtime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onlineAgents").value(10))
                .andExpect(jsonPath("$.data.currentQps").value(50.0));
    }
}
