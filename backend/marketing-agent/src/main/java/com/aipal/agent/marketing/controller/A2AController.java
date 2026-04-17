package com.aipal.agent.marketing.controller;

import cn.hutool.core.util.IdUtil;
import com.aipal.agent.marketing.service.MarketingAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/v1/a2a")
@RequiredArgsConstructor
public class A2AController {

    private final MarketingAgentService marketingAgentService;

    @Value("${agent.platform.a2a-url:http://localhost:8080/api/v1/a2a/message}")
    private String platformA2AUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, CompletableFuture<Map>> pendingRequests = new ConcurrentHashMap<>();

    @PostMapping("/message")
    public Map<String, Object> handleMessage(@RequestBody Map<String, Object> message) {
        log.info("Received A2A message: source={}, action={}", 
                message.get("sourceAgent"), message.get("action"));

        String action = (String) message.getOrDefault("action", "invoke");
        
        if ("invoke".equals(action)) {
            return handleInvoke(message);
        } else if ("delegate".equals(action)) {
            return handleDelegate(message);
        }
        
        return Map.of(
                "status", "error",
                "message", "Unknown action: " + action
        );
    }

    private Map<String, Object> handleInvoke(Map<String, Object> message) {
        String intent = (String) message.getOrDefault("intent", 
                ((Map<String, Object>) message.getOrDefault("payload", Map.of())).get("intent"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.getOrDefault("payload", Map.of());
        
        Map<String, Object> result = marketingAgentService.processIntent(intent, payload);
        
        return Map.of(
                "messageId", IdUtil.fastSimpleUUID(),
                "sourceAgent", "marketing-agent",
                "targetAgent", message.get("sourceAgent"),
                "sessionId", message.get("sessionId"),
                "action", "respond",
                "correlationId", message.get("messageId"),
                "payload", result,
                "timestamp", LocalDateTime.now().toString()
        );
    }

    private Map<String, Object> handleDelegate(Map<String, Object> message) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.getOrDefault("payload", Map.of());
        
        String targetIntent = (String) payload.getOrDefault("intent", "unknown");
        Map<String, Object> result = marketingAgentService.processIntent(targetIntent, payload);
        
        return Map.of(
                "messageId", IdUtil.fastSimpleUUID(),
                "sourceAgent", "marketing-agent",
                "targetAgent", message.get("sourceAgent"),
                "sessionId", message.get("sessionId"),
                "action", "respond",
                "correlationId", message.get("messageId"),
                "payload", result,
                "timestamp", LocalDateTime.now().toString()
        );
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return Map.of(
                "agent", "marketing-agent",
                "status", "online",
                "capabilities", List.of("sales_query", "trend_analysis", "statistics", "chart_generation")
        );
    }
}
