package com.aipal.service;

import com.aipal.entity.AgentHeartbeat;
import com.aipal.entity.MonCallRecord;
import com.aipal.mapper.AgentHeartbeatMapper;
import com.aipal.mapper.MonCallRecordMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessDashboardServiceTest {

    @Test
    void summaryAggregatesTodayCallsTokensAndCost() {
        MonCallRecord record = new MonCallRecord();
        record.setCreateTime(LocalDateTime.now());
        record.setSuccess(1);
        record.setDurationMs(120);
        record.setInputTokens(100);
        record.setOutputTokens(50);
        record.setTotalTokens(150);

        MonCallRecordMapper callRecordMapper = mock(MonCallRecordMapper.class);
        AgentHeartbeatMapper heartbeatMapper = mock(AgentHeartbeatMapper.class);
        when(callRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(heartbeatMapper.selectList(any())).thenReturn(List.of(
                heartbeat("marketing-agent", LocalDateTime.now()),
                heartbeat("image-recognition-agent", LocalDateTime.now())
        ));

        BusinessDashboardService service = new BusinessDashboardService(callRecordMapper, heartbeatMapper);
        Map<String, Object> summary = service.getSummary();

        assertEquals(1L, summary.get("todayCalls"));
        assertEquals(2L, summary.get("onlineAgents"));
        assertEquals(150L, summary.get("totalTokens"));
        assertNotNull(summary.get("todayCost"));
    }

    @Test
    void summaryDoesNotCountStaleOnlineHeartbeat() {
        MonCallRecordMapper callRecordMapper = mock(MonCallRecordMapper.class);
        AgentHeartbeatMapper heartbeatMapper = mock(AgentHeartbeatMapper.class);
        when(callRecordMapper.selectList(any())).thenReturn(List.of());
        when(heartbeatMapper.selectList(any())).thenReturn(List.of(
                heartbeat("marketing-agent", LocalDateTime.now().minusMinutes(5))
        ));

        BusinessDashboardService service = new BusinessDashboardService(callRecordMapper, heartbeatMapper);
        Map<String, Object> summary = service.getSummary();

        assertEquals(0L, summary.get("onlineAgents"));
    }

    private AgentHeartbeat heartbeat(String agentCode, LocalDateTime lastHeartbeat) {
        AgentHeartbeat heartbeat = new AgentHeartbeat();
        heartbeat.setAgentCode(agentCode);
        heartbeat.setInstanceId(agentCode + "-001");
        heartbeat.setStatus(1);
        heartbeat.setLastHeartbeat(lastHeartbeat);
        return heartbeat;
    }
}
