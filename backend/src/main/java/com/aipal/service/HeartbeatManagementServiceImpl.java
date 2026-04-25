package com.aipal.service;

import com.aipal.dto.HeartbeatRequest;
import com.aipal.entity.AgentHeartbeat;
import com.aipal.entity.AgentRegistration;
import com.aipal.mapper.AgentHeartbeatMapper;
import com.aipal.mapper.AgentRegistrationMapper;
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
    private final AgentEventService agentEventService;

    @Override
    public void recordHeartbeat(HeartbeatRequest request) {
        String agentCode = request.getAgentCode();
        String instanceId = request.getInstanceId() != null ? request.getInstanceId() : "default";

        // 获取Agent的heartbeatTimeout配置
        AgentRegistration registration = getRegistration(agentCode, instanceId);
        int timeoutSeconds = DEFAULT_HEARTBEAT_TIMEOUT.getSeconds();
        if (registration != null && registration.getHeartbeatTimeout() != null) {
            timeoutSeconds = registration.getHeartbeatTimeout();
        }

        String key = HEARTBEAT_KEY_PREFIX + agentCode + ":" + instanceId;

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
            heartbeat.setAgentCode(agentCode);
            heartbeat.setInstanceId(instanceId);
            heartbeat.setCreateTime(LocalDateTime.now());
            heartbeatMapper.insert(heartbeat);
        }

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

    @Override
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
                try {
                    Object lastHeartbeatStr = redisTemplate.opsForHash().get(key, "lastHeartbeat");
                    if (lastHeartbeatStr == null) {
                        continue;
                    }

                    LocalDateTime lastHeartbeat = LocalDateTime.parse(lastHeartbeatStr.toString());

                    // 获取该Agent的超时配置
                    String keyWithoutPrefix = key.replace(HEARTBEAT_KEY_PREFIX, "");
                    String[] parts = keyWithoutPrefix.split(":", 2);
                    String agentCode = parts[0];
                    String instanceId = parts.length > 1 ? parts[1] : "default";

                    AgentRegistration registration = getRegistration(agentCode, instanceId);
                    int timeoutSeconds = DEFAULT_HEARTBEAT_TIMEOUT.getSeconds();
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
        } finally {
            detectLock.unlock();
        }
    }

    @Override
    public boolean isAgentOnline(String agentCode, String instanceId) {
        String key = HEARTBEAT_KEY_PREFIX + agentCode + ":" + instanceId;
        Object lastHeartbeatStr = redisTemplate.opsForHash().get(key, "lastHeartbeat");
        if (lastHeartbeatStr == null) {
            return false;
        }

        try {
            LocalDateTime lastHeartbeat = LocalDateTime.parse(lastHeartbeatStr.toString());

            // 获取该Agent的超时配置
            AgentRegistration registration = getRegistration(agentCode, instanceId);
            int timeoutSeconds = DEFAULT_HEARTBEAT_TIMEOUT.getSeconds();
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
}
