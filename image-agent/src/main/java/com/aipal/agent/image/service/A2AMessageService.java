package com.aipal.agent.image.service;

import com.aipal.agent.image.config.AgentConfig;
import com.aipal.agent.image.dto.A2AMessage;
import lombok.extern.slf4j.Slf4j;
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
    private final AgentConfig agentConfig;
    private final Map<String, Function<A2AMessage, A2AMessage>> handlers = new ConcurrentHashMap<>();

    public A2AMessageService(
            RedisTemplate<String, Object> redisTemplate,
            AgentConfig agentConfig) {
        this.redisTemplate = redisTemplate;
        this.agentConfig = agentConfig;
    }

    public void registerHandler(String agentCode, Function<A2AMessage, A2AMessage> handler) {
        handlers.put(agentCode, handler);
        log.info("Registered handler for agent: {}", agentCode);
    }

    public A2AMessage sendMessage(String targetAgent, Map<String, Object> payload) {
        A2AMessage message = new A2AMessage();
        message.setSourceAgent(agentConfig.getAgentCode());
        message.setTargetAgent(targetAgent);
        message.setSessionId(UUID.randomUUID().toString());
        message.setAction(A2AMessage.Action.request);
        message.setPayload(payload);

        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            org.springframework.http.HttpEntity<A2AMessage> request = new org.springframework.http.HttpEntity<>(message);
            return restTemplate.postForObject(agentConfig.getA2aUrl(), request, A2AMessage.class);
        } catch (Exception e) {
            log.error("Failed to send A2A message to {}", targetAgent, e);
            A2AMessage errorResponse = new A2AMessage();
            errorResponse.setSourceAgent(agentConfig.getAgentCode());
            errorResponse.setTargetAgent(targetAgent);
            errorResponse.setSessionId(message.getSessionId());
            errorResponse.setAction(A2AMessage.Action.nack);
            errorResponse.setPayload(Map.of("error", e.getMessage()));
            return errorResponse;
        }
    }

    public A2AMessage handleMessage(A2AMessage message) {
        log.info("Received A2A message from {} to {}", message.getSourceAgent(), message.getTargetAgent());

        Function<A2AMessage, A2AMessage> handler = handlers.get(message.getTargetAgent());
        if (handler != null) {
            return handler.apply(message);
        }

        A2AMessage response = new A2AMessage();
        response.setSourceAgent(agentConfig.getAgentCode());
        response.setTargetAgent(message.getSourceAgent());
        response.setSessionId(message.getSessionId());
        response.setAction(A2AMessage.Action.respond);
        response.setPayload(Map.of("status", "success", "message", "Message received by " + agentConfig.getAgentCode()));
        return response;
    }
}
