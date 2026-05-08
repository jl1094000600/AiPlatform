package com.aipal.service;

import com.aipal.entity.AgentHeartbeat;
import com.aipal.mapper.AgentHeartbeatMapper;
import com.aipal.mapper.MonCallRecordMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CallRecordServiceRuntimeStatusTest {

    @Test
    void realtimeOnlineCountIgnoresStaleHeartbeat() {
        AgentHeartbeat fresh = heartbeat("image-recognition-agent", LocalDateTime.now());
        AgentHeartbeat stale = heartbeat("marketing-agent", LocalDateTime.now().minusMinutes(5));

        MonCallRecordMapper callRecordMapper = mock(MonCallRecordMapper.class);
        AgentHeartbeatMapper heartbeatMapper = mock(AgentHeartbeatMapper.class);
        when(heartbeatMapper.selectList(any())).thenReturn(List.of(fresh, stale));

        CallRecordService service = new CallRecordService(callRecordMapper, heartbeatMapper);

        assertEquals(1L, service.countOnlineAgents());
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
