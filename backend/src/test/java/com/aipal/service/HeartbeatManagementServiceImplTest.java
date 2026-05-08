package com.aipal.service;

import com.aipal.dto.HeartbeatRequest;
import com.aipal.entity.AgentHeartbeat;
import com.aipal.entity.AiAgent;
import com.aipal.mapper.AgentHeartbeatMapper;
import com.aipal.mapper.AgentRegistrationMapper;
import com.aipal.mapper.AiAgentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeartbeatManagementServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private AgentHeartbeatMapper heartbeatMapper;
    @Mock
    private AgentRegistrationMapper registrationMapper;
    @Mock
    private AiAgentMapper agentMapper;
    @Mock
    private AgentEventService agentEventService;

    @InjectMocks
    private HeartbeatManagementServiceImpl heartbeatManagementService;

    @Test
    void heartbeatUsesAgentCodeAndInstanceId() {
        AiAgent agent = new AiAgent();
        agent.setId(2L);
        agent.setAgentCode("marketing-agent");
        agent.setAgentName("市场营销Agent");

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(agentMapper.selectOne(any())).thenReturn(agent);
        when(registrationMapper.selectOne(any())).thenReturn(null);
        when(heartbeatMapper.selectOne(any())).thenReturn(null);

        HeartbeatRequest request = new HeartbeatRequest();
        request.setAgentCode("marketing-agent");
        request.setInstanceId("marketing-001");
        request.setEndpoint("http://localhost:8081");
        request.setHealthScore(100);
        request.setMetadata(Map.of("agentName", "市场营销Agent", "category", "市场营销"));

        heartbeatManagementService.recordHeartbeat(request);

        ArgumentCaptor<AgentHeartbeat> heartbeatCaptor = ArgumentCaptor.forClass(AgentHeartbeat.class);
        verify(heartbeatMapper).insert(heartbeatCaptor.capture());
        assertEquals(2L, heartbeatCaptor.getValue().getAgentId());
        assertEquals("marketing-agent", heartbeatCaptor.getValue().getAgentCode());
        assertEquals("marketing-001", heartbeatCaptor.getValue().getInstanceId());
    }

    @Test
    void detectOfflineAgentsMarksStaleDatabaseHeartbeatOfflineWhenRedisKeyIsMissing() {
        AgentHeartbeat heartbeat = new AgentHeartbeat();
        heartbeat.setId(10L);
        heartbeat.setAgentId(2L);
        heartbeat.setAgentCode("marketing-agent");
        heartbeat.setInstanceId("marketing-001");
        heartbeat.setStatus(1);
        heartbeat.setLastHeartbeat(LocalDateTime.now().minusMinutes(5));

        AiAgent agent = new AiAgent();
        agent.setId(2L);
        agent.setAgentCode("marketing-agent");
        agent.setStatus(1);

        Cursor<String> cursor = anyCursorWithoutKeys();
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(heartbeatMapper.selectList(any())).thenReturn(List.of(heartbeat));
        when(heartbeatMapper.selectOne(any())).thenReturn(heartbeat);
        when(registrationMapper.selectOne(any())).thenReturn(null);
        when(agentMapper.selectOne(any())).thenReturn(agent);

        heartbeatManagementService.detectOfflineAgents();

        verify(heartbeatMapper, atLeastOnce()).updateById(heartbeat);
        assertEquals(2, heartbeat.getStatus());
        assertEquals(2, agent.getStatus());
    }

    @SuppressWarnings("unchecked")
    private Cursor<String> anyCursorWithoutKeys() {
        Cursor<String> cursor = org.mockito.Mockito.mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(false);
        return cursor;
    }
}
