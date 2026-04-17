package com.aipal.agent.image.service;

import com.aipal.agent.image.dto.A2AMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Slf4j
@Service
public class A2AMessageService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String agentCode;
    private final String platformUrl;
    private final Map<String, Function<A2AMessage, A2AMessage>> handlers = new ConcurrentHashMap<>();

    public A2AMessageService(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${image-agent.agent-code:image-recognition-agent}") String agentCode,
            @Value("${image-agent.platform-url:http://localhost:8080}") String platformUrl) {
        this.redisTemplate = redisTemplate;
        this.agentCode = agentCode;
        this.platformUrl = platformUrl;
    }

    public void registerHandler(String agentCode, Function<A2AMessage, A2AMessage> handler) {
        handlers.put(agentCode, handler);
        log.info("Registered handler for agent: {}", agentCode);
    }

    public A2AMessage sendMessage(String targetAgent, Map<String, Object> payload) {
        A2AMessage message = new A2AMessage();
        message.setSourceAgent(agentCode);
        message.setTargetAgent(targetAgent);
        message.setSessionId(UUID.randomUUID().toString());
        message.setAction(A2AMessage.Action.request);
        message.setPayload(payload);

        String url = platformUrl + "/api/a2a/send";
        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            org.springframework.http.HttpEntity<A2AMessage> request = new org.springframework.http.HttpEntity<>(message);
            return restTemplate.postForObject(url, request, A2AMessage.class);
        } catch (Exception e) {
            log.error("Failed to send A2A message to {}", targetAgent, e);
            A2AMessage errorResponse = new A2AMessage();
            errorResponse.setSourceAgent(agentCode);
            errorResponse.setTargetAgent(targetAgent);
            errorResponse.setSessionId(message.getSessionId());
            errorResponse.setAction(A2AMessage.Action.nack);
            errorResponse.setPayload(Map.of("error", e.getMessage()));
            return errorResponse;
        }
    }

    public A2AMessage handleMessage(A2AMessage message) {
        log.info("Received A2A message from {} to {}", message.getSourceAgent(), message.getTargetAgent());

        Function<A2AMessage, A2AMessage> handler = handlers.get(message.getSourceAgent());
        if (handler != null) {
            return handler.apply(message);
        }

        A2AMessage response = new A2AMessage();
        response.setSourceAgent(agentCode);
        response.setTargetAgent(message.getSourceAgent());
        response.setSessionId(message.getSessionId());
        response.setAction(A2AMessage.Action.respond);
        response.setPayload(Map.of("status", "success", "message", "Message received by " + agentCode));
        return response;
    }
}
