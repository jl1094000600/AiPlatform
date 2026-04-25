package com.aipal.service;

import com.aipal.dto.AgentRegisterRequest;
import com.aipal.dto.AgentRegisterResponse;
import com.aipal.entity.AgentRegistration;
import com.aipal.exception.AgentRegistrationException;
import com.aipal.mapper.AgentRegistrationMapper;
import com.aipal.service.A2AMessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Agent注册服务实现
 * 支持Push（推送注册）和Pull（平台探测）两种模式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRegistryServiceImpl implements AgentRegistryService {

    private static final String HEARTBEAT_KEY_PREFIX = "agent:heartbeat:";

    @Value("${agent.registry.default-heartbeat-interval:30}")
    private int defaultHeartbeatInterval;

    @Value("${agent.registry.default-heartbeat-timeout:90}")
    private int defaultHeartbeatTimeout;

    private final AgentRegistrationMapper registrationMapper;
    private final AgentEventService agentEventService;
    private final A2AMessageService a2aMessageService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final WebClient webClient;

    /** 本地缓存注册信息 */
    private final Map<String, AgentRegistration> localCache = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public AgentRegisterResponse register(AgentRegisterRequest request) {
        log.info("Registering agent: {} [{}]", request.getAgentCode(), request.getInstanceId());

        try {
            String instanceId = request.getInstanceId() != null ? request.getInstanceId() : "default";

            // 检查是否已存在
            AgentRegistration existing = getRegistrationEntity(request.getAgentCode(), instanceId);
            if (existing != null && existing.getStatus() != 3) {
                // 已存在且未注销，更新之
                existing.setAgentName(request.getAgentName());
                existing.setDescription(request.getDescription());
                existing.setCategory(request.getCategory());
                existing.setApiUrl(request.getApiUrl());
                existing.setHealthEndpoint(request.getHealthEndpoint() != null ?
                        request.getHealthEndpoint() : "/health");
                existing.setRequestSchema(request.getRequestSchema());
                existing.setResponseSchema(request.getResponseSchema());
                existing.setHeartbeatInterval(request.getHeartbeatInterval() != null ?
                        request.getHeartbeatInterval() : defaultHeartbeatInterval);
                existing.setHeartbeatTimeout(request.getHeartbeatTimeout() != null ?
                        request.getHeartbeatTimeout() : defaultHeartbeatTimeout);
                existing.setStatus(1); // 在线
                existing.setRegisteredTime(LocalDateTime.now());
                existing.setUpdateTime(LocalDateTime.now());

                registrationMapper.updateById(existing);
                updateLocalCache(existing);

                // 发布状态变更事件
                agentEventService.publishStatusChangeEvent(
                        request.getAgentCode(), instanceId, existing.getStatus(), 1);

                log.info("Agent updated: {} [{}]", request.getAgentCode(), instanceId);
            } else {
                // 新建注册
                AgentRegistration registration = new AgentRegistration();
                registration.setAgentCode(request.getAgentCode());
                registration.setAgentName(request.getAgentName());
                registration.setDescription(request.getDescription());
                registration.setCategory(request.getCategory());
                registration.setRegistryType("PUSH");
                registration.setApiUrl(request.getApiUrl());
                registration.setHealthEndpoint(request.getHealthEndpoint() != null ?
                        request.getHealthEndpoint() : "/health");
                registration.setRequestSchema(request.getRequestSchema());
                registration.setResponseSchema(request.getResponseSchema());
                registration.setInstanceId(instanceId);
                registration.setHeartbeatInterval(request.getHeartbeatInterval() != null ?
                        request.getHeartbeatInterval() : defaultHeartbeatInterval);
                registration.setHeartbeatTimeout(request.getHeartbeatTimeout() != null ?
                        request.getHeartbeatTimeout() : defaultHeartbeatTimeout);
                registration.setStatus(1); // 在线
                registration.setRegisteredTime(LocalDateTime.now());
                registration.setLastHeartbeat(LocalDateTime.now());
                registration.setCreateTime(LocalDateTime.now());
                registration.setUpdateTime(LocalDateTime.now());

                registrationMapper.insert(registration);
                updateLocalCache(registration);

                // 发布注册事件
                agentEventService.publishRegisterEvent(registration);

                log.info("Agent registered successfully: {} [{}]",
                        request.getAgentCode(), instanceId);
            }

            // 注册A2A Handler
            registerA2AHandler(request.getAgentCode());

            // 初始化Redis心跳
            initializeHeartbeat(request.getAgentCode(), instanceId,
                    request.getHeartbeatInterval() != null ?
                            request.getHeartbeatInterval() : defaultHeartbeatInterval);

            AgentRegisterResponse response = new AgentRegisterResponse();
            response.setSuccess(true);
            response.setAgentCode(request.getAgentCode());
            response.setInstanceId(instanceId);
            response.setMessage("Agent registered successfully");
            response.setRegisteredTime(LocalDateTime.now());
            response.setHeartbeatInterval(request.getHeartbeatInterval() != null ?
                    request.getHeartbeatInterval() : defaultHeartbeatInterval);
            response.setHeartbeatTimeout(request.getHeartbeatTimeout() != null ?
                    request.getHeartbeatTimeout() : defaultHeartbeatTimeout);

            return response;

        } catch (Exception e) {
            log.error("Failed to register agent: {} [{}]",
                    request.getAgentCode(), request.getInstanceId(), e);
            throw new AgentRegistrationException(
                    request.getAgentCode(), "REGISTRATION_FAILED", e.getMessage());
        }
    }

    @Override
    @Transactional
    public void unregister(String agentCode, String instanceId) {
        log.info("Unregistering agent: {} [{}]", agentCode, instanceId);

        AgentRegistration registration = getRegistrationEntity(agentCode, instanceId);
        if (registration == null) {
            log.warn("Agent not found: {} [{}]", agentCode, instanceId);
            return;
        }

        registration.setStatus(3); // 已注销
        registration.setUnregisteredTime(LocalDateTime.now());
        registration.setUpdateTime(LocalDateTime.now());

        registrationMapper.updateById(registration);

        // 移除本地缓存
        String cacheKey = agentCode + ":" + instanceId;
        localCache.remove(cacheKey);

        // 删除Redis心跳key
        String heartbeatKey = HEARTBEAT_KEY_PREFIX + agentCode + ":" + instanceId;
        redisTemplate.delete(heartbeatKey);

        // 发布注销事件
        agentEventService.publishUnregisterEvent(registration);

        log.info("Agent unregistered: {} [{}]", agentCode, instanceId);
    }

    @Override
    public boolean probeAgent(String agentCode, String instanceId) {
        log.debug("Probing agent: {} [{}]", agentCode, instanceId);

        AgentRegistration registration = getRegistrationEntity(agentCode, instanceId);
        if (registration == null || registration.getStatus() == 3) {
            return false;
        }

        if (registration.getApiUrl() == null || registration.getApiUrl().isEmpty()) {
            log.warn("Agent {} has no apiUrl configured, skipping probe", agentCode);
            return false;
        }

        // 通过HTTP探测/health端点
        String healthEndpoint = registration.getHealthEndpoint() != null ?
                registration.getHealthEndpoint() : "/health";
        String healthUrl = registration.getApiUrl() + healthEndpoint;

        try {
            HttpStatusCode statusCode = webClient.get()
                    .uri(healthUrl)
                    .retrieve()
                    .toBodilessEntity()
                    .map(response -> response.getStatusCode())
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (statusCode != null && statusCode.is2xxSuccessful()) {
                // 更新心跳时间
                registration.setLastHeartbeat(LocalDateTime.now());
                registration.setStatus(1); // 在线
                registrationMapper.updateById(registration);

                // 更新Redis
                updateRedisHeartbeat(agentCode, instanceId);

                log.debug("Agent {} probe successful", agentCode);
                return true;
            }
        } catch (Exception e) {
            log.warn("Agent probe failed: {} [{}], error: {}",
                    agentCode, instanceId, e.getMessage());
        }

        return false;
    }

    @Override
    public AgentRegistration getRegistration(String agentCode, String instanceId) {
        String cacheKey = agentCode + ":" + instanceId;
        AgentRegistration cached = localCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        AgentRegistration registration = getRegistrationEntity(agentCode, instanceId);
        if (registration != null) {
            updateLocalCache(registration);
        }
        return registration;
    }

    @Override
    public List<AgentRegistration> getAllRegistrations() {
        return registrationMapper.selectList(
                new LambdaQueryWrapper<AgentRegistration>()
                        .ne(AgentRegistration::getStatus, 3)
                        .orderByDesc(AgentRegistration::getCreateTime)
        );
    }

    @Override
    public void refreshRegistrations() {
        log.info("Refreshing agent registrations from database");
        List<AgentRegistration> registrations = getAllRegistrations();

        localCache.clear();
        for (AgentRegistration registration : registrations) {
            String cacheKey = registration.getAgentCode() + ":" + registration.getInstanceId();
            localCache.put(cacheKey, registration);

            // 重新注册A2A Handler
            if (registration.getStatus() == 1) {
                registerA2AHandler(registration.getAgentCode());
            }
        }

        log.info("Loaded {} agent registrations", registrations.size());
    }

    @Override
    @Transactional
    public void updateStatus(String agentCode, String instanceId, Integer status) {
        AgentRegistration registration = getRegistrationEntity(agentCode, instanceId);
        if (registration != null) {
            Integer previousStatus = registration.getStatus();
            registration.setStatus(status);
            registration.setUpdateTime(LocalDateTime.now());

            if (status == 1) {
                registration.setLastHeartbeat(LocalDateTime.now());
            }

            registrationMapper.updateById(registration);
            updateLocalCache(registration);

            // 发布状态变更事件
            agentEventService.publishStatusChangeEvent(
                    agentCode, instanceId, previousStatus, status);
        }
    }

    private AgentRegistration getRegistrationEntity(String agentCode, String instanceId) {
        return registrationMapper.selectOne(
                new LambdaQueryWrapper<AgentRegistration>()
                        .eq(AgentRegistration::getAgentCode, agentCode)
                        .eq(AgentRegistration::getInstanceId,
                                instanceId != null ? instanceId : "default")
        );
    }

    private void updateLocalCache(AgentRegistration registration) {
        String cacheKey = registration.getAgentCode() + ":" + registration.getInstanceId();
        localCache.put(cacheKey, registration);
    }

    private void registerA2AHandler(String agentCode) {
        // 注册一个默认的handler，Agent可以通过实现特定接口来注册真实handler
        a2aMessageService.registerHandler(agentCode, message -> {
            log.debug("Default handler for agent: {}, message: {}",
                    agentCode, message.getMessageId());
            return null;
        });
    }

    private void initializeHeartbeat(String agentCode, String instanceId, int intervalSeconds) {
        String key = HEARTBEAT_KEY_PREFIX + agentCode + ":" + instanceId;
        redisTemplate.opsForHash().put(key, "lastHeartbeat", LocalDateTime.now().toString());
        redisTemplate.opsForHash().put(key, "healthScore", "100");
        redisTemplate.expire(key, intervalSeconds * 4L, TimeUnit.SECONDS);
    }

    private void updateRedisHeartbeat(String agentCode, String instanceId) {
        String key = HEARTBEAT_KEY_PREFIX + agentCode + ":" + instanceId;
        redisTemplate.opsForHash().put(key, "lastHeartbeat", LocalDateTime.now().toString());
    }
}
