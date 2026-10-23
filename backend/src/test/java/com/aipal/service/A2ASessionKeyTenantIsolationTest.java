package com.aipal.service;

import com.aipal.dto.A2AMessage;
import com.aipal.entity.A2ATask;
import com.aipal.mapper.A2ATaskMapper;
import com.aipal.mapper.AiAgentMapper;
import com.aipal.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W5: A2A session keys and handler keys must be tenant-scoped.
 * Negative cases prove the same sessionId / agentCode under two tenants never collide.
 */
class A2ASessionKeyTenantIsolationTest {

    private RedisTemplate<String, Object> redisTemplate;
    private StreamOperations<String, Object, Object> streamOps;
    private A2ATaskMapper a2aTaskMapper;
    private AiAgentMapper aiAgentMapper;
    private A2AMessageService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        redisTemplate = mock(RedisTemplate.class);
        streamOps = mock(StreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        a2aTaskMapper = mock(A2ATaskMapper.class);
        aiAgentMapper = mock(AiAgentMapper.class);
        service = new A2AMessageService(redisTemplate, objectMapper, a2aTaskMapper, aiAgentMapper);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void sameSessionIdUnderDifferentTenantsUsesDistinctStreamKeys() {
        TenantContext.set(context(1L));
        service.sendMessage(invokeMessage("session-shared"));

        TenantContext.set(context(2L));
        service.sendMessage(invokeMessage("session-shared"));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(streamOps, org.mockito.Mockito.times(2)).add(keys.capture(), any());
        List<String> used = keys.getAllValues();
        assertEquals(2, used.size());
        assertEquals("a2a:stream:1:session-shared", used.get(0));
        assertEquals("a2a:stream:2:session-shared", used.get(1));
        assertNotEquals(used.get(0), used.get(1),
                "cross-tenant session isolation must not share a Redis stream key");
    }

    @Test
    void sessionMessagesAreReadFromOwnTenantStreamOnly() {
        TenantContext.set(context(1L));
        service.getSessionMessages("session-shared", 10);

        TenantContext.set(context(2L));
        service.getSessionMessages("session-shared", 10);

        ArgumentCaptor<StreamOffset<String>> offsets = ArgumentCaptor.forClass(StreamOffset.class);
        verify(streamOps, org.mockito.Mockito.times(2))
                .read(any(org.springframework.data.redis.connection.stream.StreamReadOptions.class),
                        offsets.capture());
        List<StreamOffset<String>> used = offsets.getAllValues();
        assertEquals("a2a:stream:1:session-shared", used.get(0).getKey());
        assertEquals("a2a:stream:2:session-shared", used.get(1).getKey());
    }

    @Test
    void handlerRegisteredInOneTenantIsNotVisibleFromAnotherTenant() {
        AtomicReference<A2AMessage> tenant1Calls = new AtomicReference<>();
        AtomicReference<A2AMessage> tenant2Calls = new AtomicReference<>();

        TenantContext.set(context(1L));
        service.registerHandler("writer", message -> {
            tenant1Calls.set(message);
            return A2AMessage.builder().payload(Map.of("ok", true)).build();
        });

        TenantContext.set(context(2L));
        service.registerHandler("writer", message -> {
            tenant2Calls.set(message);
            return A2AMessage.builder().payload(Map.of("ok", true)).build();
        });

        // Tenant 2 processes a message targeting the same agentCode: only tenant 2's handler may run.
        TenantContext.set(context(2L));
        processOneMessage(invokeMessage("session-x"));

        assertNull(tenant1Calls.get(), "tenant 1 handler must not see tenant 2 traffic");
        assertNotNull(tenant2Calls.get(), "tenant 2 handler must handle its own message");
    }

    @Test
    void handlerFromOtherTenantDoesNotFireWhenMessageIsProcessed() {
        AtomicReference<A2AMessage> foreignCalls = new AtomicReference<>();

        TenantContext.set(context(1L));
        service.registerHandler("writer", message -> {
            foreignCalls.set(message);
            return A2AMessage.builder().payload(Map.of("ok", true)).build();
        });

        TenantContext.set(context(2L));
        processOneMessage(invokeMessage("session-x"));

        assertNull(foreignCalls.get(),
                "handler registered under another tenant must not be invoked (handlerKey is tenant-scoped)");
    }

    @Test
    void sendMessagePersistsTaskWithTenantScopedStream() {
        TenantContext.set(context(9L));
        String messageId = service.sendMessage(invokeMessage("iso-session"));

        assertNotNull(messageId);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(streamOps).add(keys.capture(), any());
        assertTrue(keys.getValue().startsWith("a2a:stream:9:"),
                "stream key must carry the active tenant: " + keys.getValue());
        verify(a2aTaskMapper).insert(any(A2ATask.class));
    }

    @Test
    void sessionKeyEmbedsTenantId() {
        assertEquals("1:s1", A2AMessageService.sessionKey(1L, "s1"));
        assertEquals("2:s1", A2AMessageService.sessionKey(2L, "s1"));
        assertNotEquals(A2AMessageService.sessionKey(1L, "s1"), A2AMessageService.sessionKey(2L, "s1"));
    }

    @Test
    void pendingResponseIsNotVisibleAcrossTenants() {
        // Tenant 1 sends invoke and registers a pending response under its own key.
        TenantContext.set(context(1L));
        A2AMessage message = invokeMessage("session-pending");
        message.setMessageId("corr-1");
        service.sendMessage(message);

        // Tenant 2 asking for the same correlation id must not see tenant 1's future.
        TenantContext.set(context(2L));
        assertNull(service.getResponse("corr-1", 50),
                "pending A2A responses must be tenant-scoped so one tenant cannot collect another's reply");
    }

    @Test
    void processMessageDoesNotLeakAcrossTenantStream() {
        // Tenant 1 has a pending message in its stream; tenant 2 must not consume it.
        TenantContext.set(context(1L));
        A2AMessage message = invokeMessage("session-shared");
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        when(streamOps.read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                any(org.springframework.data.redis.connection.stream.StreamReadOptions.class),
                any(StreamOffset.class))).thenReturn(
                List.of(MapRecord.create("1-0", Map.of("message", json))));

        TenantContext.set(context(2L));
        service.processMessage("session-shared");

        // Tenant 2 reads from a2a:stream:2:session-shared — assert the key used is tenant 2's.
        ArgumentCaptor<StreamOffset<String>> offsets = ArgumentCaptor.forClass(StreamOffset.class);
        verify(streamOps).read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                any(org.springframework.data.redis.connection.stream.StreamReadOptions.class),
                offsets.capture());
        assertEquals("a2a:stream:2:session-shared", offsets.getValue().getKey());
    }

    private void processOneMessage(A2AMessage message) {
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        when(streamOps.read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                any(org.springframework.data.redis.connection.stream.StreamReadOptions.class),
                any(StreamOffset.class))).thenReturn(
                List.of(MapRecord.create("1-0", Map.of("message", json))));
        service.processMessage(message.getSessionId());
    }

    private static A2AMessage invokeMessage(String sessionId) {
        return A2AMessage.builder()
                .messageId("msg-" + sessionId + "-" + System.nanoTime())
                .sourceAgent("caller")
                .targetAgent("writer")
                .sessionId(sessionId)
                .action(A2AMessage.Action.invoke)
                .payload(Map.of("q", "hi"))
                .build();
    }

    private static TenantContext.Context context(Long tenantId) {
        return new TenantContext.Context(1L, "tester", tenantId, "tenant-" + tenantId,
                false, Set.of(), Set.of());
    }
}
