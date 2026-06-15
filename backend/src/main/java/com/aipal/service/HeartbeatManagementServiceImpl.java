package com.aipal.service;

import com.aipal.dto.HeartbeatRequest;
import com.aipal.entity.AgentHeartbeat;
import com.aipal.entity.AgentRegistration;
import com.aipal.entity.AiAgent;
import com.aipal.mapper.AgentHeartbeatMapper;
import com.aipal.mapper.AgentRegistrationMapper;
import com.aipal.mapper.AiAgentMapper;
import com.aipal.security.TenantContext;
import com.aipal.security.TenantTaskRunner;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 心跳管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HeartbeatManagementServiceImpl implements HeartbeatManagementService {

    private static final String HEARTBEAT_KEY_PREFIX = "agent:heartbeat:";
    private static final Duration DEFAULT_HEARTBEAT_TIMEOUT = Duration.ofSeconds(90);
    private static final String LOCK_KEY = "lock:heartbeat:detect";
    private static final Lock detectLock = new ReentrantLock();

    private final RedisTemplate<String, Object> redisTemplate;
    private final AgentHeartbeatMapper heartbeatMapper;
    private final AgentRegistrationMapper registrationMapper;
    private final AiAgentMapper agentMapper;
    private final AgentEventService agentEventService;

    @Autowired
    private TenantTaskRunner tenantTaskRunner;

    @Override
    public void recordHeartbeat(HeartbeatRequest request) {
        String agentCode = resolveAgentCode(request);
        String instanceId = request.getInstanceId() != null ? request.getInstanceId() : "default";
        AiAgent agent = upsertAgentMasterFromHeartbeat(agentCode, request);

        // 获取Agent的heartbeatTimeout配置
        AgentRegistration registration = getOrCreateRegistration(agentCode, instanceId, request, agent);
        int timeoutSeconds = (int) DEFAULT_HEARTBEAT_TIMEOUT.getSeconds();
        if (registration != null && registration.getHeartbeatTimeout() != null) {
            timeoutSeconds = registration.getHeartbeatTimeout();
        }

        String key = heartbeatKey(agentCode, instanceId);

        // 更新Redis心跳
        redisTemplate.opsForHash().put(key, "lastHeartbeat", LocalDateTime.now().toString());
        if (request.getHealthScore() != null) {
            redisTemplate.opsForHash().put(key, "healthScore", String.valueOf(request.getHealthScore()));
        }
        if (request.getEndpoint() != null) {
            redisTemplate.opsForHash().put(key, "endpoint", request.getEndpoint());
        }
        redisTemplate.expire(key, timeoutSeconds * 2L, TimeUnit.SECONDS);

        // 更新数据库
        AgentHeartbeat heartbeat = heartbeatMapper.selectOne(
                new LambdaQueryWrapper<AgentHeartbeat>()
                        .eq(AgentHeartbeat::getAgentCode, agentCode)
                        .eq(AgentHeartbeat::getInstanceId, instanceId)
        );

        if (heartbeat == null) {
            heartbeat = new AgentHeartbeat();
            heartbeat.setAgentId(agent.getId());
            heartbeat.setAgentCode(agentCode);
            heartbeat.setInstanceId(instanceId);
            heartbeat.setCreateTime(LocalDateTime.now());
            heartbeatMapper.insert(heartbeat);
        }

        heartbeat.setAgentId(agent.getId());
        heartbeat.setAgentCode(agentCode);
        heartbeat.setLastHeartbeat(LocalDateTime.now());
        if (request.getHealthScore() != null) {
            heartbeat.setHealthScore(request.getHealthScore());
        }
        if (request.getEndpoint() != null) {
            heartbeat.setEndpoint(request.getEndpoint());
        }
        heartbeat.setStatus(1);
        heartbeat.setUpdateTime(LocalDateTime.now());
        heartbeatMapper.updateById(heartbeat);

        // 同时更新Registration表的lastHeartbeat
        if (registration != null) {
            registration.setLastHeartbeat(LocalDateTime.now());
            registration.setStatus(1); // 在线
            registrationMapper.updateById(registration);
        }

        log.debug("Agent heartbeat recorded: {} [{}]", agentCode, instanceId);
    }

    private String resolveAgentCode(HeartbeatRequest request) {
        if (request.getAgentCode() != null && !request.getAgentCode().isBlank()) {
            return request.getAgentCode();
        }

        if (request.getMetadata() != null) {
            Object metadataAgentCode = request.getMetadata().get("agentCode");
            if (metadataAgentCode != null && !metadataAgentCode.toString().isBlank()) {
                return metadataAgentCode.toString();
            }
        }

        if (request.getAgentId() != null) {
            AiAgent agent = agentMapper.selectById(request.getAgentId());
            if (agent != null && agent.getAgentCode() != null && !agent.getAgentCode().isBlank()) {
                return agent.getAgentCode();
            }
        }

        throw new IllegalArgumentException("agentCode is required for heartbeat");
    }

    private AiAgent upsertAgentMasterFromHeartbeat(String agentCode, HeartbeatRequest request) {
        AiAgent agent = agentMapper.selectOne(
                new LambdaQueryWrapper<AiAgent>().eq(AiAgent::getAgentCode, agentCode)
        );

        boolean creating = agent == null;
        if (creating) {
            agent = new AiAgent();
            agent.setAgentCode(agentCode);
            agent.setAgentName(metadataValue(request, "agentName", agentCode));
            agent.setCategory(metadataValue(request, "category", "Agent"));
            agent.setDescription(metadataValue(request, "description", null));
            agent.setHttpMethod("POST");
            agent.setCreateTime(LocalDateTime.now());
        }

        if (request.getEndpoint() != null && !request.getEndpoint().isBlank()) {
            agent.setApiUrl(request.getEndpoint());
        }
        agent.setStatus(1);
        agent.setUpdateTime(LocalDateTime.now());

        if (creating) {
            agentMapper.insert(agent);
        } else {
            agentMapper.updateById(agent);
        }
        return agent;
    }

    private AgentRegistration getOrCreateRegistration(String agentCode, String instanceId,
                                                      HeartbeatRequest request, AiAgent agent) {
        AgentRegistration registration = getRegistration(agentCode, instanceId);
        if (registration != null) {
            return registration;
        }

        registration = new AgentRegistration();
        registration.setAgentCode(agentCode);
        registration.setAgentName(agent.getAgentName());
        registration.setDescription(agent.getDescription());
        registration.setCategory(agent.getCategory());
        registration.setRegistryType("HEARTBEAT");
        registration.setApiUrl(request.getEndpoint() != null ? request.getEndpoint() : agent.getApiUrl());
        registration.setHealthEndpoint("/health");
        registration.setInstanceId(instanceId);
        registration.setHeartbeatInterval(30);
        registration.setHeartbeatTimeout((int) DEFAULT_HEARTBEAT_TIMEOUT.getSeconds());
        registration.setStatus(1);
        registration.setLastHeartbeat(LocalDateTime.now());
        registration.setRegisteredTime(LocalDateTime.now());
        registration.setCreateTime(LocalDateTime.now());
        registration.setUpdateTime(LocalDateTime.now());
        registrationMapper.insert(registration);
        return registration;
    }

    private String metadataValue(HeartbeatRequest request, String key, String defaultValue) {
        if (request.getMetadata() == null) {
            return defaultValue;
        }
        Object value = request.getMetadata().get(key);
        if (value == null || value.toString().isBlank()) {
            return defaultValue;
        }
        return value.toString();
    }

    @Override
    @Scheduled(fixedRateString = "${aipal.scheduling.heartbeat-rate-ms:30000}",
            initialDelayString = "${aipal.scheduling.initial-delay-ms:5000}")
    public void detectOfflineAgents() {
        if (!detectLock.tryLock()) {
            log.debug("Another instance is detecting offline agents, skip");
            return;
        }

        try {
            tenantTaskRunner.forEachActiveTenant("heartbeat-management", tenant -> detectOfflineAgentsForCurrentTenant());
        } finally {
            detectLock.unlock();
        }
    }

    private void detectOfflineAgentsForCurrentTenant() {
        Set<String> keys = scanKeys(tenantHeartbeatPattern());
        LocalDateTime now = LocalDateTime.now();
        if (keys != null && !keys.isEmpty()) {
            for (String key : keys) {
                try {
                    Object lastHeartbeatStr = redisTemplate.opsForHash().get(key, "lastHeartbeat");
                    if (lastHeartbeatStr == null) {
                        continue;
                    }

                    LocalDateTime lastHeartbeat = LocalDateTime.parse(lastHeartbeatStr.toString());

                    String keyWithoutPrefix = key.replace(tenantHeartbeatPrefix(), "");
                    String[] parts = keyWithoutPrefix.split(":", 2);
                    String agentCode = parts[0];
                    String instanceId = parts.length > 1 ? parts[1] : "default";

                    AgentRegistration registration = getRegistration(agentCode, instanceId);
                    int timeoutSeconds = (int) DEFAULT_HEARTBEAT_TIMEOUT.getSeconds();
                    if (registration != null && registration.getHeartbeatTimeout() != null) {
                        timeoutSeconds = registration.getHeartbeatTimeout();
                    }

                    Duration timeout = Duration.ofSeconds(timeoutSeconds);
                    if (Duration.between(lastHeartbeat, now).compareTo(timeout) > 0) {
                        markAgentOffline(agentCode, instanceId);
                        log.warn("Agent {} [{}] marked offline due to heartbeat timeout",
                                agentCode, instanceId);
                    }
                } catch (Exception e) {
                    log.warn("Error processing heartbeat key {}: {}", key, e.getMessage());
                }
            }
        }
        markStaleDatabaseHeartbeatsOffline(now);
    }

    private void markStaleDatabaseHeartbeatsOffline(LocalDateTime now) {
        List<AgentHeartbeat> staleHeartbeats = heartbeatMapper.selectList(
                new LambdaQueryWrapper<AgentHeartbeat>()
                        .eq(AgentHeartbeat::getStatus, 1)
                        .lt(AgentHeartbeat::getLastHeartbeat, now.minus(DEFAULT_HEARTBEAT_TIMEOUT))
        );

        for (AgentHeartbeat heartbeat : staleHeartbeats) {
            String agentCode = heartbeat.getAgentCode();
            if (agentCode == null || agentCode.isBlank()) {
                log.debug("Skip stale heartbeat without agentCode, id={}", heartbeat.getId());
                continue;
            }
            String instanceId = heartbeat.getInstanceId() != null ? heartbeat.getInstanceId() : "default";
            try {
                markAgentOffline(agentCode, instanceId);
                log.warn("Agent {} [{}] marked offline due to stale database heartbeat",
                        agentCode, instanceId);
            } catch (Exception e) {
                log.warn("Error marking stale heartbeat offline for {} [{}]: {}",
                        agentCode, instanceId, e.getMessage());
            }
        }
    }

    @Override
    public boolean isAgentOnline(String agentCode, String instanceId) {
        String key = heartbeatKey(agentCode, instanceId);
        Object lastHeartbeatStr = redisTemplate.opsForHash().get(key, "lastHeartbeat");
        if (lastHeartbeatStr == null) {
            return false;
        }

        try {
            LocalDateTime lastHeartbeat = LocalDateTime.parse(lastHeartbeatStr.toString());

            // 获取该Agent的超时配置
            AgentRegistration registration = getRegistration(agentCode, instanceId);
            int timeoutSeconds = (int) DEFAULT_HEARTBEAT_TIMEOUT.getSeconds();
            if (registration != null && registration.getHeartbeatTimeout() != null) {
                timeoutSeconds = registration.getHeartbeatTimeout();
            }

            Duration timeout = Duration.ofSeconds(timeoutSeconds);
            return Duration.between(lastHeartbeat, LocalDateTime.now()).compareTo(timeout) <= 0;
        } catch (Exception e) {
            log.warn("Error checking agent online status: {} [{}]", agentCode, instanceId, e);
            return false;
        }
    }

    @Override
    public AgentHeartbeat getHeartbeat(String agentCode, String instanceId) {
        return heartbeatMapper.selectOne(
                new LambdaQueryWrapper<AgentHeartbeat>()
                        .eq(AgentHeartbeat::getAgentCode, agentCode)
                        .eq(AgentHeartbeat::getInstanceId, instanceId)
        );
    }

    @Override
    public List<AgentHeartbeat> getAgentInstances(String agentCode) {
        return heartbeatMapper.selectList(
                new LambdaQueryWrapper<AgentHeartbeat>()
                        .eq(AgentHeartbeat::getAgentCode, agentCode)
                        .orderByDesc(AgentHeartbeat::getLastHeartbeat)
        );
    }

    private AgentRegistration getRegistration(String agentCode, String instanceId) {
        return registrationMapper.selectOne(
                new LambdaQueryWrapper<AgentRegistration>()
                        .eq(AgentRegistration::getAgentCode, agentCode)
                        .eq(AgentRegistration::getInstanceId, instanceId != null ? instanceId : "default")
        );
    }

    private void markAgentOffline(String agentCode, String instanceId) {
        // 更新心跳表状态
        AgentHeartbeat heartbeat = heartbeatMapper.selectOne(
                new LambdaQueryWrapper<AgentHeartbeat>()
                        .eq(AgentHeartbeat::getAgentCode, agentCode)
                        .eq(AgentHeartbeat::getInstanceId, instanceId)
        );
        if (heartbeat != null && heartbeat.getStatus() != 2) {
            heartbeat.setStatus(2); // 离线
            heartbeat.setUpdateTime(LocalDateTime.now());
            heartbeatMapper.updateById(heartbeat);
        }

        // 更新注册表状态
        AgentRegistration registration = getRegistration(agentCode, instanceId);
        if (registration != null && registration.getStatus() != 2) {
            Integer previousStatus = registration.getStatus();
            registration.setStatus(2); // 离线
            registration.setUpdateTime(LocalDateTime.now());
            registrationMapper.updateById(registration);

            // 发布心跳超时事件
            agentEventService.publishHeartbeatTimeoutEvent(agentCode, instanceId);

            // 发布状态变更事件
            agentEventService.publishStatusChangeEvent(agentCode, instanceId, previousStatus, 2);
        }

        AiAgent agent = agentMapper.selectOne(
                new LambdaQueryWrapper<AiAgent>().eq(AiAgent::getAgentCode, agentCode)
        );
        if (agent != null && agent.getStatus() != null && agent.getStatus() == 1) {
            agent.setStatus(2);
            agent.setUpdateTime(LocalDateTime.now());
            agentMapper.updateById(agent);
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

    private String heartbeatKey(String agentCode, String instanceId) {
        return tenantHeartbeatPrefix() + agentCode + ":" + instanceId;
    }

    private String tenantHeartbeatPattern() {
        return tenantHeartbeatPrefix() + "*";
    }

    private String tenantHeartbeatPrefix() {
        return HEARTBEAT_KEY_PREFIX + TenantContext.tenantId() + ":";
    }
}
