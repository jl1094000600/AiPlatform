package com.aipal.service;

import com.aipal.entity.AiAgent;
import com.aipal.mapper.AiAgentMapper;
import com.aipal.mapper.AiAgentVersionMapper;
import com.aipal.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRegistryTenantIsolationTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void cachesSameAgentCodeSeparatelyForEachTenant() {
        AiAgentMapper agentMapper = mock(AiAgentMapper.class);
        AiAgentVersionMapper versionMapper = mock(AiAgentVersionMapper.class);
        when(agentMapper.selectOne(any())).thenAnswer(invocation -> agent(TenantContext.tenantId() * 10));
        when(versionMapper.selectOne(any())).thenReturn(null);
        AgentRegistry registry = new AgentRegistry(
                agentMapper,
                versionMapper,
                mock(ChatModelService.class),
                mock(A2AMessageService.class));

        TenantContext.set(context(1L));
        AgentRegistry.AgentContext first = registry.getAgent("writer");
        TenantContext.set(context(2L));
        AgentRegistry.AgentContext second = registry.getAgent("writer");
        TenantContext.set(context(1L));

        assertEquals(10L, first.getAgent().getId());
        assertEquals(20L, second.getAgent().getId());
        assertNotSame(first, second);
        assertEquals(first, registry.getAgent("writer"));
    }

    private AiAgent agent(Long id) {
        AiAgent agent = new AiAgent();
        agent.setId(id);
        agent.setAgentCode("writer");
        agent.setAgentName("Writer");
        agent.setStatus(1);
        return agent;
    }

    private TenantContext.Context context(Long tenantId) {
        return new TenantContext.Context(7L, "tester", tenantId, "tenant-" + tenantId,
                false, Set.of(), Set.of());
    }
}
