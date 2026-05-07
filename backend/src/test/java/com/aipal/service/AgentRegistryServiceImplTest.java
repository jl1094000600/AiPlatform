package com.aipal.service;

import com.aipal.dto.AgentRegisterRequest;
import com.aipal.entity.AgentRegistration;
import com.aipal.entity.AiAgent;
import com.aipal.mapper.AgentRegistrationMapper;
import com.aipal.mapper.AiAgentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRegistryServiceImplTest {

    @Mock
    private AgentRegistrationMapper registrationMapper;
    @Mock
    private AiAgentMapper agentMapper;
    @Mock
    private AgentEventService agentEventService;
    @Mock
    private A2AMessageService a2aMessageService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private WebClient webClient;

    @InjectMocks
    private AgentRegistryServiceImpl agentRegistryService;

    @Test
    void registerCreatesVisibleAgentAndRegistration() {
        when(registrationMapper.selectOne(any())).thenReturn(null);
        when(agentMapper.selectOne(any())).thenReturn(null);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        AgentRegisterRequest request = new AgentRegisterRequest();
        request.setAgentCode("marketing-agent");
        request.setAgentName("市场营销Agent");
        request.setDescription("销售分析");
        request.setCategory("市场营销");
        request.setApiUrl("http://localhost:8081");
        request.setHealthEndpoint("/agent/marketing/health");
        request.setInstanceId("marketing-001");
        request.setHeartbeatInterval(30);
        request.setHeartbeatTimeout(90);

        agentRegistryService.register(request);

        ArgumentCaptor<AiAgent> agentCaptor = ArgumentCaptor.forClass(AiAgent.class);
        verify(agentMapper).insert(agentCaptor.capture());
        assertEquals("marketing-agent", agentCaptor.getValue().getAgentCode());
        assertEquals("市场营销Agent", agentCaptor.getValue().getAgentName());
        assertEquals(1, agentCaptor.getValue().getStatus());

        ArgumentCaptor<AgentRegistration> registrationCaptor = ArgumentCaptor.forClass(AgentRegistration.class);
        verify(registrationMapper).insert(registrationCaptor.capture());
        assertEquals("marketing-agent", registrationCaptor.getValue().getAgentCode());
        assertEquals("marketing-001", registrationCaptor.getValue().getInstanceId());
        assertEquals(1, registrationCaptor.getValue().getStatus());
    }
}
