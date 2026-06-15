package com.aipal.agent.image.service;

import com.aipal.agent.image.config.AgentConfig;
import com.aipal.agent.image.dto.HeartbeatRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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

    private final AgentConfig agentConfig;
    private final RestTemplate restTemplate;

    private int consecutiveFailures = 0;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    @PostConstruct
    public void init() {
        log.info("HeartbeatService initialized for agent: {}, instance: {}, registry: {}, heartbeat: {}",
                agentConfig.getAgentCode(), agentConfig.getInstanceId(), agentConfig.getRegistryUrl(), agentConfig.getHeartbeatUrl());
        registerWithPlatform();
        sendHeartbeat();
    }

    private void registerWithPlatform() {
        try {
            restTemplate.postForEntity(agentConfig.getRegistryUrl(), registrationEntity(), Map.class);
            log.info("Agent registered with platform: {} [{}]", agentConfig.getAgentCode(), agentConfig.getInstanceId());
        } catch (Exception e) {
            log.warn("Failed to register agent with platform: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRateString = "${agent.heartbeat-interval:30000}")
    public void sendHeartbeat() {
        try {
            restTemplate.postForEntity(agentConfig.getHeartbeatUrl(), heartbeatEntity(), Void.class);
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

    Map<String, Object> buildRegistrationRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("tenantCode", agentConfig.getPlatform().getTenantCode());
        request.put("agentCode", agentConfig.getAgentCode());
        request.put("agentName", agentConfig.getAgentName());
        request.put("description", agentConfig.getDescription());
        request.put("category", agentConfig.getCategory());
        request.put("apiUrl", agentConfig.getEndpoint());
        request.put("healthEndpoint", "/api/image-agent/health");
        request.put("instanceId", agentConfig.getInstanceId());
        request.put("heartbeatInterval", Math.toIntExact(agentConfig.getHeartbeatInterval() / 1000));
        request.put("heartbeatTimeout", 90);
        return request;
    }

    private HttpEntity<Map<String, Object>> registrationEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Agent-Heartbeat-Token", heartbeatToken(
                agentConfig.getPlatform().getHeartbeatSecret(), agentConfig.getPlatform().getTenantCode()));
        return new HttpEntity<>(buildRegistrationRequest(), headers);
    }

    HeartbeatRequest buildHeartbeatRequest() {
        HeartbeatRequest request = new HeartbeatRequest();
        request.setTenantCode(agentConfig.getPlatform().getTenantCode());
        request.setAgentCode(agentConfig.getAgentCode());
        request.setInstanceId(agentConfig.getInstanceId());
        request.setHealthScore(calculateHealthScore());
        request.setEndpoint(agentConfig.getEndpoint());
        request.setCapabilities(List.of("image_recognition", "file_parsing", "document_parsing"));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agentCode", agentConfig.getAgentCode());
        metadata.put("agentName", agentConfig.getAgentName());
        metadata.put("category", agentConfig.getCategory());
        metadata.put("description", agentConfig.getDescription());
        metadata.put("version", "1.0.0");
        request.setMetadata(metadata);

        return request;
    }

    private HttpEntity<HeartbeatRequest> heartbeatEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Agent-Heartbeat-Token", heartbeatToken(
                agentConfig.getPlatform().getHeartbeatSecret(), agentConfig.getPlatform().getTenantCode()));
        return new HttpEntity<>(buildHeartbeatRequest(), headers);
    }

    private String heartbeatToken(String secret, String tenantCode) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("agent.platform.heartbeat-secret must contain at least 32 characters");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
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

    public String getInstanceId() {
        return agentConfig.getInstanceId();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }
}
