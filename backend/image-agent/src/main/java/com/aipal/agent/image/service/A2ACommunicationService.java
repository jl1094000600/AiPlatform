package com.aipal.agent.image.service;

import cn.hutool.core.util.IdUtil;
import com.aipal.agent.image.config.AgentConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class A2ACommunicationService {

    private static final String STREAM_KEY_PREFIX = "a2a:stream:";
    private static final String CONSUMER_GROUP = "image-agent-group";
    private static final Duration MESSAGE_TIMEOUT = Duration.ofSeconds(60);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AgentConfig agentConfig;

    private final Map<String, Function<A2AMessage, A2AMessage>> handlers = new ConcurrentHashMap<>();
    private ExecutorService messageProcessor;

    @PostConstruct
    public void init() {
        messageProcessor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "image-agent-a2a-processor");
            t.setDaemon(true);
            return t;
        });
        messageProcessor.submit(this::processMessages);
        log.info("A2ACommunicationService initialized for agent: {}", agentConfig.getAgentCode());
    }

    public void registerHandler(Function<A2AMessage, A2AMessage> handler) {
        handlers.put(agentConfig.getAgentCode(), handler);
        log.info("Registered A2A handler for agent: {}", agentConfig.getAgentCode());
    }

    private void processMessages() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String sessionId = agentConfig.getAgentCode();
                String streamKey = STREAM_KEY_PREFIX + sessionId;

                ensureConsumerGroup(streamKey);

                List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                        Consumer.from(CONSUMER_GROUP, "image-agent-consumer"),
                        StreamReadOptions.empty().count(10).block(Duration.ofMillis(500)),
                        StreamOffset.create(streamKey, ReadOffset.last())
                );

                if (records == null || records.isEmpty()) {
                    continue;
                }

                for (MapRecord<String, Object, Object> record : records) {
                    Object msgObj = record.getValue().get("message");
                    if (msgObj == null) continue;

                    try {
                        A2AMessage message = objectMapper.readValue(msgObj.toString(), A2AMessage.class);
                        handleMessage(message);
                        redisTemplate.opsForStream().acknowledge(streamKey, CONSUMER_GROUP, record.getId());
                    } catch (Exception e) {
                        log.error("Failed to process A2A message", e);
                    }
                }
            } catch (Exception e) {
                log.warn("Message processing error, will retry: {}", e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void ensureConsumerGroup(String streamKey) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), CONSUMER_GROUP);
            log.info("Created consumer group {} for stream {}", CONSUMER_GROUP, streamKey);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.debug("Consumer group {} already exists", CONSUMER_GROUP);
            }
        }
    }

    private void handleMessage(A2AMessage message) {
        log.debug("Processing A2A message: {} -> {} [{}]",
                message.getSourceAgent(), message.getTargetAgent(), message.getAction());

        if (message.getTargetAgent() != null &&
                !message.getTargetAgent().equals(agentConfig.getAgentCode())) {
            return;
        }

        Function<A2AMessage, A2AMessage> handler = handlers.get(agentConfig.getAgentCode());
        if (handler != null) {
            A2AMessage response = handler.apply(message);
            if (response != null && message.getCorrelationId() != null) {
                response.setCorrelationId(message.getMessageId());
                sendMessage(response);
            }
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

            log.debug("A2A message sent: {} -> {} [{}]",
                    message.getSourceAgent(), message.getTargetAgent(), message.getAction());
            return message.getMessageId();
        } catch (Exception e) {
            log.error("Failed to send A2A message", e);
            throw new RuntimeException("Failed to send A2A message", e);
        }
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class A2AMessage {
        private String messageId;
        private String sourceAgent;
        private String targetAgent;
        private String sessionId;
        private Action action;
        private Map<String, Object> payload;
        private LocalDateTime timestamp;
        private String correlationId;

        public enum Action {
            invoke, respond, delegate, broadcast
        }
    }
}
