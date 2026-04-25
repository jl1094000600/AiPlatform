package com.aipal.service;

import cn.hutool.core.util.IdUtil;
import com.aipal.dto.A2AMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class A2AMessageService {

    private static final String STREAM_KEY_PREFIX = "a2a:stream:";
    private static final String CONSUMER_GROUP = "a2a-group";
    private static final Duration MESSAGE_TIMEOUT = Duration.ofSeconds(60);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, CompletableFuture<A2AMessage>> pendingResponses = new ConcurrentHashMap<>();
    private final Map<String, Function<A2AMessage, A2AMessage>> agentHandlers = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("A2AMessageService initialized");
    }

    private void ensureConsumerGroup(String streamKey) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), CONSUMER_GROUP);
            log.info("Created consumer group {} for stream {}", CONSUMER_GROUP, streamKey);
        } catch (RedisSystemException e) {
            if (e.getCause() instanceof DataAccessException dae) {
                if (dae.getMessage() != null && dae.getMessage().contains("BUSYGROUP")) {
                    log.debug("Consumer group {} already exists for stream {}", CONSUMER_GROUP, streamKey);
                } else {
                    log.warn("Failed to create consumer group: {}", dae.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Consumer group creation skipped: {}", e.getMessage());
        }
    }

    public String sendMessage(A2AMessage message) {
        if (message.getMessageId() == null) {
            message.setMessageId(IdUtil.fastSimpleUUID());
        }
        if (message.getTimestamp() == null) {
            message.setTimestamp(LocalDateTime.now());
        }

        String streamKey = STREAM_KEY_PREFIX + message.getSessionId();

        try {
            String json = objectMapper.writeValueAsString(message);
            HashMap<String, Object> map = new HashMap<>();
            map.put("message", json);
            redisTemplate.opsForStream().add(streamKey, map);

            if (message.getAction() == A2AMessage.Action.invoke
                && message.getCorrelationId() == null) {
                CompletableFuture<A2AMessage> future = new CompletableFuture<>();
                pendingResponses.put(message.getMessageId(), future);

                future.orTimeout(MESSAGE_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                    .whenComplete((resp, ex) -> pendingResponses.remove(message.getMessageId()));
            }

            log.debug("A2A message sent: {} -> {} [{}]",
                message.getSourceAgent(), message.getTargetAgent(), message.getAction());
            return message.getMessageId();
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize A2A message", e);
            throw new RuntimeException("Failed to send A2A message", e);
        }
    }

    public A2AMessage getResponse(String correlationId, long timeoutMs) {
        CompletableFuture<A2AMessage> future = pendingResponses.get(correlationId);
        if (future == null) {
            return null;
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Failed to get response for message {}", correlationId, e);
            return null;
        }
    }

    public void registerHandler(String agentCode, Function<A2AMessage, A2AMessage> handler) {
        agentHandlers.put(agentCode, handler);
        log.info("Registered A2A handler for agent: {}", agentCode);
    }

    public void processMessage(String sessionId) {
        String streamKey = STREAM_KEY_PREFIX + sessionId;

        try {
            ensureConsumerGroup(streamKey);
        } catch (Exception e) {
            log.warn("Could not ensure consumer group, stream may not exist yet");
        }

        List<MapRecord<String, Object, Object>> records;
        try {
            records = redisTemplate.opsForStream().read(
                Consumer.from(CONSUMER_GROUP, "consumer-" + Thread.currentThread().getId()),
                StreamReadOptions.empty().count(10).block(Duration.ofMillis(500)),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed())
            );
        } catch (RedisSystemException e) {
            log.debug("Stream {} does not exist yet", streamKey);
            return;
        }

        if (records == null || records.isEmpty()) {
            return;
        }

        for (MapRecord<String, Object, Object> record : records) {
            Object msgObj = record.getValue().get("message");
            if (msgObj == null) continue;

            try {
                A2AMessage message = objectMapper.readValue(msgObj.toString(), A2AMessage.class);
                handleMessage(message);
                redisTemplate.opsForStream().acknowledge(streamKey, CONSUMER_GROUP, record.getId());
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize A2A message", e);
            }
        }
    }

    private void handleMessage(A2AMessage message) {
        log.debug("Processing A2A message: {} -> {} [{}]",
            message.getSourceAgent(), message.getTargetAgent(), message.getAction());

        CompletableFuture<A2AMessage> waitingFuture = pendingResponses.get(message.getCorrelationId());
        if (waitingFuture != null) {
            waitingFuture.complete(message);
            return;
        }

        Function<A2AMessage, A2AMessage> handler = agentHandlers.get(message.getTargetAgent());
        if (handler != null) {
            A2AMessage response = handler.apply(message);
            if (response != null && message.getCorrelationId() != null) {
                response.setCorrelationId(message.getMessageId());
                sendMessage(response);
            }
        }
    }

    public List<A2AMessage> getSessionMessages(String sessionId, long limit) {
        String streamKey = STREAM_KEY_PREFIX + sessionId;
        List<A2AMessage> messages = new ArrayList<>();

        List<MapRecord<String, Object, Object>> records;
        try {
            records = redisTemplate.opsForStream().read(
                StreamReadOptions.empty().count(limit),
                StreamOffset.create(streamKey, ReadOffset.from("0"))
            );
        } catch (RedisSystemException e) {
            log.debug("Stream {} does not exist", streamKey);
            return messages;
        }

        if (records == null) return messages;

        for (MapRecord<String, Object, Object> record : records) {
            Object msgObj = record.getValue().get("message");
            if (msgObj != null) {
                try {
                    messages.add(objectMapper.readValue(msgObj.toString(), A2AMessage.class));
                } catch (JsonProcessingException e) {
                    log.error("Failed to deserialize message", e);
                }
            }
        }
        return messages;
    }
}
