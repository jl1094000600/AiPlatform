package com.aipal.service;

import com.aipal.security.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeAsrWebSocketHandler extends TextWebSocketHandler {
    private static final String ATTR_SESSION_ID = "asrSessionId";
    private static final String ATTR_TICKET = "asrTicket";
    private static final String ATTR_STREAM = "asrStream";

    private final RealtimeAsrSessionService sessionService;
    private final DashScopeRealtimeAsrClient asrClient;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, String> params = queryParams(session);
        String sessionId = params.get("sessionId");
        String token = params.get("token");
        try {
            RealtimeAsrSessionService.SessionTicket ticket = sessionService.requireSession(sessionId, token);
            session.getAttributes().put(ATTR_SESSION_ID, sessionId);
            session.getAttributes().put(ATTR_TICKET, ticket);
            sendJson(session, Map.of("type", "connected"));
        } catch (RuntimeException e) {
            sendJson(session, Map.of("type", "error", "message", "Invalid realtime ASR session"));
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid session"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root;
        try {
            root = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            sendJson(session, Map.of("type", "error", "message", "Invalid realtime ASR message"));
            return;
        }
        String type = root.path("type").asText("");
        if ("start".equals(type)) {
            startStream(session, root);
            return;
        }
        if ("stop".equals(type)) {
            Object stream = session.getAttributes().get(ATTR_STREAM);
            if (stream instanceof DashScopeRealtimeAsrClient.StreamSession asrStream) {
                asrStream.finish();
            } else {
                sendJson(session, Map.of("type", "completed"));
            }
            return;
        }
        sendJson(session, Map.of("type", "error", "message", "Unsupported realtime ASR message: " + type));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        DashScopeRealtimeAsrClient.StreamSession stream = stream(session);
        ByteBuffer payload = message.getPayload();
        byte[] frame = new byte[payload.remaining()];
        payload.get(frame);
        try {
            stream.sendAudio(frame);
        } catch (RuntimeException e) {
            sendJson(session, Map.of("type", "error", "message", limitError(e.getMessage())));
            stream.close();
            session.close(CloseStatus.POLICY_VIOLATION.withReason("audio limit"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object stream = session.getAttributes().remove(ATTR_STREAM);
        if (stream instanceof DashScopeRealtimeAsrClient.StreamSession asrStream) {
            asrStream.close();
        }
        Object sessionId = session.getAttributes().remove(ATTR_SESSION_ID);
        if (sessionId instanceof String value) {
            sessionService.remove(value);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("Realtime ASR WebSocket transport error", exception);
        Object stream = session.getAttributes().remove(ATTR_STREAM);
        if (stream instanceof DashScopeRealtimeAsrClient.StreamSession asrStream) {
            asrStream.close();
        }
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR.withReason("transport error"));
        }
    }

    private void startStream(WebSocketSession session, JsonNode message) {
        if (session.getAttributes().containsKey(ATTR_STREAM)) {
            sendJson(session, Map.of("type", "error", "message", "Realtime ASR stream already started"));
            return;
        }
        RealtimeAsrSessionService.SessionTicket ticket = ticket(session);
        Integer sampleRate = message.path("sampleRate").isNumber() ? message.path("sampleRate").asInt() : null;
        try {
            DashScopeRealtimeAsrClient.StreamSession stream = asrClient.open(ticket.context(), sampleRate,
                    new DashScopeRealtimeAsrClient.StreamListener() {
                        private long sequence = 0;

                        @Override
                        public void onReady() {
                            sendJson(session, Map.of("type", "ready"));
                        }

                        @Override
                        public void onTranscript(String text, boolean sentenceEnd) {
                            sendJson(session, Map.of(
                                    "type", sentenceEnd ? "final" : "partial",
                                    "text", text,
                                    "seq", ++sequence
                            ));
                        }

                        @Override
                        public void onCompleted() {
                            sendJson(session, Map.of("type", "completed"));
                        }

                        @Override
                        public void onError(String message) {
                            sendJson(session, Map.of("type", "error", "message", limitError(message)));
                        }
                    });
            session.getAttributes().put(ATTR_STREAM, stream);
        } catch (RuntimeException e) {
            sendJson(session, Map.of("type", "error", "message", limitError(e.getMessage())));
        }
    }

    private RealtimeAsrSessionService.SessionTicket ticket(WebSocketSession session) {
        Object ticket = session.getAttributes().get(ATTR_TICKET);
        if (ticket instanceof RealtimeAsrSessionService.SessionTicket asrTicket) {
            return asrTicket;
        }
        throw new IllegalStateException("Realtime ASR session is missing");
    }

    private DashScopeRealtimeAsrClient.StreamSession stream(WebSocketSession session) {
        Object stream = session.getAttributes().get(ATTR_STREAM);
        if (stream instanceof DashScopeRealtimeAsrClient.StreamSession asrStream) {
            return asrStream;
        }
        throw new IllegalStateException("Realtime ASR stream has not started");
    }

    private Map<String, String> queryParams(WebSocketSession session) {
        Map<String, String> values = new HashMap<>();
        String query = session.getUri() == null ? null : session.getUri().getRawQuery();
        if (query == null || query.isBlank()) {
            return values;
        }
        for (String part : query.split("&")) {
            int index = part.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = URLDecoder.decode(part.substring(0, index), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(part.substring(index + 1), StandardCharsets.UTF_8);
            values.put(key, value);
        }
        return values;
    }

    private void sendJson(WebSocketSession session, Object payload) {
        if (!session.isOpen()) {
            return;
        }
        synchronized (session) {
            if (!session.isOpen()) {
                return;
            }
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            } catch (Exception e) {
                log.warn("Failed to send realtime ASR WebSocket message", e);
            }
        }
    }

    private String limitError(String message) {
        String value = message == null || message.isBlank() ? "Realtime ASR failed" : message;
        return value.substring(0, Math.min(500, value.length()));
    }
}
