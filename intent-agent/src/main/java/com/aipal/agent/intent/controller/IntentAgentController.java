package com.aipal.agent.intent.controller;

import com.aipal.agent.intent.config.AgentConfig;
import com.aipal.agent.intent.service.HeartbeatService;
import com.aipal.agent.intent.service.IntentRecognitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/agent/intent")
@RequiredArgsConstructor
public class IntentAgentController {

    private final IntentRecognitionService intentRecognitionService;
    private final HeartbeatService heartbeatService;
    private final AgentConfig agentConfig;

    @PostMapping("/invoke")
    public ResponseEntity<Map<String, Object>> invoke(@RequestBody Map<String, Object> request) {
        String intent = (String) request.getOrDefault("intent", "classify");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", request);

        log.info("Received invoke request: intent={}", intent);
        return ResponseEntity.ok(intentRecognitionService.processIntent(intent, params));
    }

    @PostMapping("/classify")
    public ResponseEntity<Map<String, Object>> classify(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(intentRecognitionService.classify(request));
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getInfo() {
        return ResponseEntity.ok(intentRecognitionService.getAgentInfo());
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "agent", agentConfig.getCode(),
                "instanceId", heartbeatService.getInstanceId(),
                "version", "1.0.0",
                "capabilities", intentRecognitionService.capabilities(),
                "consecutiveFailures", heartbeatService.getConsecutiveFailures()
        ));
    }

    @GetMapping("/intents")
    public ResponseEntity<Map<String, Object>> listIntents() {
        return ResponseEntity.ok(intentRecognitionService.listIntents());
    }
}
