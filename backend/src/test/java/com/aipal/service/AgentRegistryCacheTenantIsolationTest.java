package com.aipal.service;

import com.aipal.entity.AgentRegistration;
import com.aipal.mapper.AiAgentMapper;
import com.aipal.mapper.AgentRegistrationMapper;
import com.aipal.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Local registry cache must not leak registrations across tenants.
 * The DB interceptor cannot correct a cache hit keyed only by agentCode:instanceId.
 */
@ExtendWith(MockitoExtension.class)
class AgentRegistryCacheTenantIsolationTest {

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

    @InjectMocks
    private AgentRegistryServiceImpl agentRegistryService;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void cachesSameAgentCodeSeparatelyForEachTenant() {
        when(registrationMapper.selectOne(any())).thenAnswer(invocation ->
                registration(TenantContext.tenantId() * 100));

        TenantContext.set(context(1L));
        AgentRegistration first = agentRegistryService.getRegistration("writer", "default");

        TenantContext.set(context(2L));
        AgentRegistration second = agentRegistryService.getRegistration("writer", "default");

        TenantContext.set(context(1L));
        AgentRegistration cached = agentRegistryService.getRegistration("writer", "default");

        assertNotSame(first, second);
        assertSame(first, cached);
        org.junit.jupiter.api.Assertions.assertEquals(100L, first.getId());
        org.junit.jupiter.api.Assertions.assertEquals(200L, second.getId());
    }

    private AgentRegistration registration(Long id) {
        AgentRegistration registration = new AgentRegistration();
        registration.setId(id);
        registration.setAgentCode("writer");
        registration.setInstanceId("default");
        registration.setStatus(1);
        return registration;
    }

    private TenantContext.Context context(Long tenantId) {
        return new TenantContext.Context(7L, "tester", tenantId, "tenant-" + tenantId,
                false, Set.of(), Set.of());
    }
}
