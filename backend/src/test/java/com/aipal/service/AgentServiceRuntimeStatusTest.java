package com.aipal.service;

import com.aipal.entity.AgentHeartbeat;
import com.aipal.entity.AiAgent;
import com.aipal.mapper.AgentHeartbeatMapper;
import com.aipal.mapper.AiAgentMapper;
import com.aipal.mapper.AiModelMapper;
import com.aipal.mapper.MonCallRecordMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentServiceRuntimeStatusTest {

    @Test
    void staleHeartbeatDoesNotMakeAgentOnline() {
        AiAgent agent = new AiAgent();
        agent.setId(1L);
        agent.setAgentCode("marketing-agent");
        agent.setAgentName("市场营销Agent");
        agent.setStatus(2);

        AgentHeartbeat heartbeat = new AgentHeartbeat();
        heartbeat.setAgentId(1L);
        heartbeat.setAgentCode("marketing-agent");
        heartbeat.setInstanceId("marketing-001");
        heartbeat.setStatus(1);
        heartbeat.setLastHeartbeat(LocalDateTime.now().minusMinutes(5));

        Page<AiAgent> page = new Page<>(1, 20);
        page.setRecords(List.of(agent));
        page.setTotal(1);

        AiAgentMapper agentMapper = mock(AiAgentMapper.class);
        AgentVersionService agentVersionService = mock(AgentVersionService.class);
        MonCallRecordMapper callRecordMapper = mock(MonCallRecordMapper.class);
        AgentHeartbeatMapper heartbeatMapper = mock(AgentHeartbeatMapper.class);
        AiModelMapper modelMapper = mock(AiModelMapper.class);
        AgentRuntimeConfigService runtimeConfigService = mock(AgentRuntimeConfigService.class);
        when(agentMapper.selectPage(any(), any())).thenReturn(page);
        when(heartbeatMapper.selectList(any())).thenReturn(List.of(heartbeat));

        AgentService service = new AgentService(agentMapper, agentVersionService, callRecordMapper, heartbeatMapper,
                modelMapper, runtimeConfigService);
        Page<AiAgent> result = service.listAgents(1, 20, null, null, null);

        AiAgent enriched = result.getRecords().get(0);
        assertEquals("offline", enriched.getRuntimeStatus());
        assertEquals(2, enriched.getStatus());
        assertEquals(0, enriched.getInstanceCount());
    }
}
