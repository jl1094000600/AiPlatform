package com.aipal.agent.marketing.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeartbeatService {

    @Value("${agent.code:marketing-agent}")
    private String agentCode;

    @Value("${agent.instance-id:marketing-001}")
    private String instanceId;

    @Value("${agent.platform.heartbeat-url:http://localhost:8080/api/v1/heartbeat/report}")
    private String heartbeatUrl;

    @Value("${agent.endpoint:http://localhost:8081}")
    private String agentEndpoint;

    private final RestTemplate restTemplate = new RestTemplate();
    private int consecutiveFailures = 0;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    @PostConstruct
    public void init() {
        log.info("HeartbeatService initialized for agent: {}, instance: {}, url: {}",
                agentCode, instanceId, heartbeatUrl);
    }

    @Scheduled(fixedRate = 30000)
    public void reportHeartbeat() {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("agentId", getAgentIdFromCode());
            request.put("instanceId", instanceId);
            request.put("healthScore", calculateHealthScore());
            request.put("endpoint", agentEndpoint);
            request.put("capabilities", List.of("sales_query", "trend_analysis", "statistics", "chart_generation"));

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("agentCode", agentCode);
            metadata.put("version", "1.0.0");
            request.put("metadata", metadata);

            restTemplate.postForEntity(heartbeatUrl, request, Void.class);

            consecutiveFailures = 0;
            log.debug("Heartbeat reported successfully");
        } catch (Exception e) {
            consecutiveFailures++;
            log.warn("Failed to report heartbeat: {}. Consecutive failures: {}",
                    e.getMessage(), consecutiveFailures);

            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                log.error("Max consecutive heartbeat failures reached. Agent may be offline from platform perspective.");
            }
        }
    }

    private Long getAgentIdFromCode() {
        return 2L;
    }

    private int calculateHealthScore() {
        if (consecutiveFailures > 0) {
            return Math.max(50, 100 - (consecutiveFailures * 15));
        }
        return 100;
    }

    public boolean isHealthy() {
        return consecutiveFailures < MAX_CONSECUTIVE_FAILURES;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }
}
