package com.aipal.agent.intent.controller;

import cn.hutool.core.util.IdUtil;
import com.aipal.agent.intent.config.AgentConfig;
import com.aipal.agent.intent.service.IntentRecognitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/a2a")
@RequiredArgsConstructor
public class A2AController {

    private final IntentRecognitionService intentRecognitionService;
    private final AgentConfig agentConfig;

    @PostMapping("/message")
    public Map<String, Object> handleMessage(@RequestBody Map<String, Object> message) {
        log.info("Received A2A message: source={}, action={}",
                message.get("sourceAgent"), message.get("action"));

        String action = (String) message.getOrDefault("action", "invoke");
        if (!"invoke".equals(action) && !"delegate".equals(action)) {
            return Map.of(
                    "status", "error",
                    "message", "Unknown action: " + action
            );
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.getOrDefault("payload", Map.of());
        String intent = (String) message.getOrDefault("intent", payload.getOrDefault("intent", "classify"));
        Map<String, Object> result = intentRecognitionService.processIntent(intent, payload);

        return Map.of(
                "messageId", IdUtil.fastSimpleUUID(),
                "sourceAgent", agentConfig.getCode(),
                "targetAgent", message.getOrDefault("sourceAgent", ""),
                "sessionId", message.getOrDefault("sessionId", ""),
                "action", "respond",
                "correlationId", message.getOrDefault("messageId", ""),
                "payload", result,
                "timestamp", LocalDateTime.now().toString()
        );
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return Map.of(
                "agent", agentConfig.getCode(),
                "status", "online",
                "capabilities", intentRecognitionService.capabilities(),
                "routes", List.of("image-recognition-agent", "marketing-agent", "tts-agent")
        );
    }
}
