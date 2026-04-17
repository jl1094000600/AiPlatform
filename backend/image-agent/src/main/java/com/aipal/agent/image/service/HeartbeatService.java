package com.aipal.agent.image.service;

import cn.hutool.core.util.IdUtil;
import com.aipal.agent.image.config.AgentConfig;
import com.aipal.agent.image.dto.HeartbeatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeartbeatService {

    private static final String HEARTBEAT_KEY_PREFIX = "agent:heartbeat:";
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(90);

    private final RedisTemplate<String, Object> redisTemplate;
    private final AgentConfig agentConfig;

    @Value("${server.port:8081}")
    private int serverPort;

    private String instanceId = IdUtil.fastSimpleUUID();
    private Long agentId = 1L;

    @Scheduled(fixedRateString = "${image-agent.heartbeat-interval:30000}")
    public void sendHeartbeat() {
        try {
            HeartbeatRequest request = new HeartbeatRequest();
            request.setAgentId(agentId);
            request.setInstanceId(instanceId);
            request.setHealthScore(100);
            request.setEndpoint("http://localhost:" + serverPort);

            Map<String, Object> capabilities = new HashMap<>();
            capabilities.put("imageRecognition", true);
            capabilities.put("fileParsing", true);
            request.setCapabilities(new java.util.ArrayList<>(capabilities.keySet()));

            recordHeartbeatLocally(request);

            sendHeartbeatToPlatform(request);

            log.debug("Heartbeat sent for agent {} instance {}", agentId, instanceId);
        } catch (Exception e) {
            log.error("Failed to send heartbeat", e);
        }
    }

    private void recordHeartbeatLocally(HeartbeatRequest request) {
        String key = HEARTBEAT_KEY_PREFIX + request.getAgentId() + ":" + instanceId;
        redisTemplate.opsForHash().put(key, "lastHeartbeat", LocalDateTime.now().toString());
        redisTemplate.opsForHash().put(key, "healthScore", String.valueOf(request.getHealthScore()));
        redisTemplate.opsForHash().put(key, "endpoint", request.getEndpoint() != null ? request.getEndpoint() : "");
        redisTemplate.expire(key, HEARTBEAT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    private void sendHeartbeatToPlatform(HeartbeatRequest request) {
        try {
            String platformUrl = agentConfig.getPlatformUrl();
            String heartbeatUrl = platformUrl + "/api/agent/heartbeat";

            RestTemplate restTemplate = new RestTemplate();
            restTemplate.postForObject(heartbeatUrl, request, Void.class);
        } catch (Exception e) {
            log.warn("Failed to send heartbeat to platform: {}", e.getMessage());
        }
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getInstanceId() {
        return instanceId;
    }
}
