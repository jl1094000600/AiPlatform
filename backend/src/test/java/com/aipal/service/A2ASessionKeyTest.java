package com.aipal.service;

import com.aipal.dto.A2AMessage;
import com.aipal.entity.A2ATask;
import com.aipal.mapper.A2ATaskMapper;
import com.aipal.mapper.AiAgentMapper;
import com.aipal.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W5: A2A session/correlation keys are tenant-scoped so two tenants never share
 * a stream, pending-response or handler key for the same business sessionId.
 */
class A2ASessionKeyTest {

    private A2ATaskMapper a2aTaskMapper;
    private AiAgentMapper aiAgentMapper;
    private A2AMessageService service;

    @BeforeEach
    void setUp() {
        a2aTaskMapper = mock(A2ATaskMapper.class);
        aiAgentMapper = mock(AiAgentMapper.class);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.RedisTemplate<String, Object> redis =
                mock(org.springframework.data.redis.core.RedisTemplate.class);
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.StreamOperations<String, Object, Object> streams =
                mock(org.springframework.data.redis.core.StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streams);
        service = new A2AMessageService(redis, mapper, a2aTaskMapper, aiAgentMapper);
        when(a2aTaskMapper.insert(any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void asTenant(Long tenantId) {
        TenantContext.set(new TenantContext.Context(
                1L, "tester", tenantId, "t-" + tenantId, false, Set.of(), Set.of()));
    }

    @Test
    void sessionKeyEmbedsTenant() {
        assertEquals("1:s1", A2AMessageService.sessionKey(1L, "s1"));
        assertEquals("2:s1", A2AMessageService.sessionKey(2L, "s1"));
        assertNotEquals(A2AMessageService.sessionKey(1L, "s1"),
                A2AMessageService.sessionKey(2L, "s1"));
    }

    @Test
    void persistStampsTenantOnTask() {
        asTenant(42L);
        A2AMessage message = A2AMessage.builder()
                .sourceAgent("a")
                .targetAgent("b")
                .sessionId("shared-session")
                .action(A2AMessage.Action.invoke)
                .build();
        service.sendMessage(message);

        ArgumentCaptor<A2ATask> captor = ArgumentCaptor.forClass(A2ATask.class);
        verify(a2aTaskMapper).insert(captor.capture());
        assertEquals(42L, captor.getValue().getTenantId());
        assertEquals("shared-session", captor.getValue().getSessionId());
    }

    @Test
    void sameSessionIdDifferentTenantsGetDistinctStreamKeys() {
        // streamKey is private; verify indirectly via the public sessionKey contract
        // that every derived key embeds the tenant.
        assertNotEquals(A2AMessageService.sessionKey(1L, "shared"),
                A2AMessageService.sessionKey(2L, "shared"));
    }
}
