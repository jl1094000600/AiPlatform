package com.aipal.service;

import com.aipal.dto.HeartbeatRequest;
import com.aipal.entity.AgentHeartbeat;
import com.aipal.mapper.AgentHeartbeatMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeartbeatService {

    private static final String HEARTBEAT_KEY_PREFIX = "agent:heartbeat:";
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(90);
    private static final String LOCK_KEY = "lock:heartbeat:detect";
    private static final Lock detectLock = new ReentrantLock();

    private final RedisTemplate<String, Object> redisTemplate;
    private final AgentHeartbeatMapper heartbeatMapper;

    public void recordHeartbeat(HeartbeatRequest request) {
        String instanceId = request.getInstanceId() != null ? request.getInstanceId() : "default";
        String key = HEARTBEAT_KEY_PREFIX + request.getAgentId() + ":" + instanceId;

        redisTemplate.opsForHash().put(key, "lastHeartbeat", LocalDateTime.now().toString());
        redisTemplate.opsForHash().put(key, "healthScore", String.valueOf(request.getHealthScore()));
        redisTemplate.opsForHash().put(key, "endpoint", request.getEndpoint() != null ? request.getEndpoint() : "");
        redisTemplate.expire(key, HEARTBEAT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        AgentHeartbeat heartbeat = heartbeatMapper.selectOne(
            new LambdaQueryWrapper<AgentHeartbeat>()
                .eq(AgentHeartbeat::getAgentId, request.getAgentId())
                .eq(AgentHeartbeat::getInstanceId, instanceId)
        );

        if (heartbeat == null) {
            heartbeat = new AgentHeartbeat();
            heartbeat.setAgentId(request.getAgentId());
            heartbeat.setInstanceId(instanceId);
            heartbeat.setCreateTime(LocalDateTime.now());
            heartbeatMapper.insert(heartbeat);
        }

        heartbeat.setLastHeartbeat(LocalDateTime.now());
        heartbeat.setHealthScore(request.getHealthScore());
        heartbeat.setEndpoint(request.getEndpoint());
        heartbeat.setStatus(1);
        heartbeat.setUpdateTime(LocalDateTime.now());
        heartbeatMapper.updateById(heartbeat);

        log.debug("Agent {} instance {} heartbeat recorded", request.getAgentId(), instanceId);
    }

    @Scheduled(fixedRate = 30000)
    public void detectOfflineAgents() {
        if (!detectLock.tryLock()) {
            log.debug("Another instance is detecting offline agents, skip");
            return;
        }

        try {
            Set<String> keys = scanKeys(HEARTBEAT_KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            for (String key : keys) {
                Object lastHeartbeatStr = redisTemplate.opsForHash().get(key, "lastHeartbeat");
                if (lastHeartbeatStr == null) {
                    continue;
                }

                LocalDateTime lastHeartbeat = LocalDateTime.parse(lastHeartbeatStr.toString());
                if (Duration.between(lastHeartbeat, now).compareTo(HEARTBEAT_TIMEOUT) > 0) {
                    String keyWithoutPrefix = key.replace(HEARTBEAT_KEY_PREFIX, "");
                    String[] parts = keyWithoutPrefix.split(":", 2);
                    Long agentId = Long.parseLong(parts[0]);
                    String instanceId = parts.length > 1 ? parts[1] : "default";
                    markAgentOffline(agentId, instanceId);
                    log.warn("Agent {} instance {} marked offline due to heartbeat timeout", agentId, instanceId);
                }
            }
        } finally {
            detectLock.unlock();
        }
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions()
                .match(pattern)
                .count(100)
                .build())) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            log.warn("SCAN failed, falling back to keys()", e);
            return redisTemplate.keys(pattern);
        }
        return keys;
    }

    private void markAgentOffline(Long agentId, String instanceId) {
        AgentHeartbeat heartbeat = heartbeatMapper.selectOne(
            new LambdaQueryWrapper<AgentHeartbeat>()
                .eq(AgentHeartbeat::getAgentId, agentId)
                .eq(AgentHeartbeat::getInstanceId, instanceId)
        );
        if (heartbeat != null && heartbeat.getStatus() != 2) {
            heartbeat.setStatus(2);
            heartbeat.setUpdateTime(LocalDateTime.now());
            heartbeatMapper.updateById(heartbeat);
        }
    }

    public boolean isAgentOnline(Long agentId) {
        return isAgentOnline(agentId, "default");
    }

    public boolean isAgentOnline(Long agentId, String instanceId) {
        String key = HEARTBEAT_KEY_PREFIX + agentId + ":" + instanceId;
        Object lastHeartbeatStr = redisTemplate.opsForHash().get(key, "lastHeartbeat");
        if (lastHeartbeatStr == null) {
            return false;
        }
        LocalDateTime lastHeartbeat = LocalDateTime.parse(lastHeartbeatStr.toString());
        return Duration.between(lastHeartbeat, LocalDateTime.now()).compareTo(HEARTBEAT_TIMEOUT) <= 0;
    }

    public AgentHeartbeat getHeartbeat(Long agentId) {
        return getHeartbeat(agentId, "default");
    }

    public AgentHeartbeat getHeartbeat(Long agentId, String instanceId) {
        return heartbeatMapper.selectOne(
            new LambdaQueryWrapper<AgentHeartbeat>()
                .eq(AgentHeartbeat::getAgentId, agentId)
                .eq(AgentHeartbeat::getInstanceId, instanceId)
        );
    }
}
