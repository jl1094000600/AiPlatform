package com.aipal.service;

import com.aipal.dto.RealtimeAsrSessionResponse;
import com.aipal.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RealtimeAsrSessionService {
    private final Map<String, SessionTicket> sessions = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${aipal.asr.realtime.session-ttl-seconds:120}")
    private long sessionTtlSeconds;
    @Value("${aipal.asr.realtime.sample-rate:16000}")
    private int sampleRate;
    @Value("${aipal.asr.realtime.format:pcm_s16le}")
    private String format;

    public RealtimeAsrSessionResponse createSession() {
        TenantContext.Context context = TenantContext.get();
        if (context == null || context.userId() == null || context.tenantId() == null) {
            throw new IllegalStateException("Tenant context is required for realtime ASR");
        }
        cleanupExpired();
        String sessionId = UUID.randomUUID().toString();
        String token = token();
        Instant expiresAt = Instant.now().plus(Duration.ofSeconds(Math.max(30, sessionTtlSeconds)));
        sessions.put(sessionId, new SessionTicket(sessionId, token, context, expiresAt));
        return RealtimeAsrSessionResponse.builder()
                .sessionId(sessionId)
                .token(token)
                .wsUrl("/api/asr/realtime?sessionId=" + sessionId + "&token=" + token)
                .sampleRate(sampleRate)
                .format(format)
                .expiresInSeconds(Math.max(30, sessionTtlSeconds))
                .build();
    }

    public SessionTicket requireSession(String sessionId, String token) {
        cleanupExpired();
        SessionTicket ticket = sessions.get(sessionId);
        if (ticket == null || token == null || !token.equals(ticket.token()) || ticket.expired()) {
            throw new IllegalArgumentException("Invalid realtime ASR session");
        }
        return ticket;
    }

    public void remove(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    private void cleanupExpired() {
        sessions.entrySet().removeIf(entry -> entry.getValue().expired());
    }

    private String token() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record SessionTicket(String sessionId, String token, TenantContext.Context context, Instant expiresAt) {
        private boolean expired() {
            return expiresAt == null || Instant.now().isAfter(expiresAt);
        }
    }
}
