package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.service.CallRecordService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MonitorController 单元测试
 * 测试 /api/v1/monitor 下的接口
 */
@WebMvcTest(MonitorController.class)
class MonitorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CallRecordService callRecordService;

    @Test
    @WithMockUser
    void testListRecords() throws Exception {
        Page<Object> mockPage = new Page<>(1, 20);
        when(callRecordService.listCallRecords(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(mockPage);

        mockMvc.perform(get("/api/v1/monitor/records")
                        .param("pageNum", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void testGetStatistics() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCalls", 100);
        stats.put("successRate", 95.5);
        stats.put("avgDuration", 150);

        when(callRecordService.countByAgentAndTimeRange(any(), any(), any())).thenReturn(100L);
        when(callRecordService.getSuccessRateByAgentAndTimeRange(any(), any(), any())).thenReturn(95.5);
        when(callRecordService.getAvgDurationByAgentAndTimeRange(any(), any(), any())).thenReturn(150.0);

        mockMvc.perform(get("/api/v1/monitor/statistics"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void testGetRealtimeData() throws Exception {
        when(callRecordService.countOnlineAgents()).thenReturn(10);
        when(callRecordService.getCurrentQps()).thenReturn(50.0);
        when(callRecordService.getAvgResponseTime()).thenReturn(100.0);

        mockMvc.perform(get("/api/v1/monitor/realtime"))
                .andExpect(status().isOk());
    }
}
