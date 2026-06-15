package com.aipal.agent.marketing.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeartbeatService {

    @Value("${agent.code:marketing-agent}")
    private String agentCode;

    @Value("${agent.name:市场营销Agent}")
    private String agentName;

    @Value("${agent.instance-id:marketing-001}")
    private String instanceId;

    @Value("${agent.platform.registry-url:http://localhost:8080/api/v1/registry/agents}")
    private String registryUrl;

    @Value("${agent.platform.heartbeat-url:http://localhost:8080/api/v1/heartbeat/report}")
    private String heartbeatUrl;

    @Value("${agent.endpoint:http://localhost:8081}")
    private String agentEndpoint;

    @Value("${agent.platform.tenant-code:aiplatform}")
    private String tenantCode;

    @Value("${agent.platform.heartbeat-secret:}")
    private String heartbeatSecret;

    private final RestTemplate restTemplate = new RestTemplate();
    private int consecutiveFailures = 0;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    @PostConstruct
    public void init() {
        log.info("HeartbeatService initialized for agent: {}, instance: {}, registry: {}, heartbeat: {}",
                agentCode, instanceId, registryUrl, heartbeatUrl);
        registerWithPlatform();
        reportHeartbeat();
    }

    private void registerWithPlatform() {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("tenantCode", tenantCode);
            request.put("agentCode", agentCode);
            request.put("agentName", agentName);
            request.put("description", "提供销售数据分析、同比环比分析、统计汇总排名和图表生成功能");
            request.put("category", "市场营销");
            request.put("apiUrl", agentEndpoint);
            request.put("healthEndpoint", "/agent/marketing/health");
            request.put("instanceId", instanceId);
            request.put("heartbeatInterval", 30);
            request.put("heartbeatTimeout", 90);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Agent-Heartbeat-Token", heartbeatToken());
            restTemplate.postForEntity(registryUrl, new HttpEntity<>(request, headers), Map.class);
            log.info("Agent registered with platform: {} [{}]", agentCode, instanceId);
        } catch (Exception e) {
            log.warn("Failed to register agent with platform: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = 30000)
    public void reportHeartbeat() {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("tenantCode", tenantCode);
            request.put("agentCode", agentCode);
            request.put("instanceId", instanceId);
            request.put("healthScore", calculateHealthScore());
            request.put("endpoint", agentEndpoint);
            request.put("capabilities", List.of("sales_query", "trend_analysis", "statistics", "chart_generation"));

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("agentCode", agentCode);
            metadata.put("agentName", agentName);
            metadata.put("category", "市场营销");
            metadata.put("description", "提供销售数据分析、同比环比分析、统计汇总排名和图表生成功能");
            metadata.put("version", "1.0.0");
            request.put("metadata", metadata);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Agent-Heartbeat-Token", heartbeatToken());
            restTemplate.postForEntity(heartbeatUrl, new HttpEntity<>(request, headers), Void.class);

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

    private String heartbeatToken() {
        if (heartbeatSecret == null || heartbeatSecret.length() < 32) {
            throw new IllegalStateException("agent.platform.heartbeat-secret must contain at least 32 characters");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(heartbeatSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(tenantCode.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to calculate heartbeat token", exception);
        }
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
