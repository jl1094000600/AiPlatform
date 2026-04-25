package com.aipal.service;

import com.aipal.entity.AgentRegistration;
import com.aipal.entity.AgentRegistrationEvent;
import com.aipal.event.AgentRegisteredEvent;
import com.aipal.event.AgentStatusChangedEvent;
import com.aipal.event.AgentUnregisteredEvent;
import com.aipal.event.HeartbeatTimeoutEvent;
import com.aipal.mapper.AgentRegistrationEventMapper;
import com.aipal.mapper.AgentRegistrationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent事件通知服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentEventServiceImpl implements AgentEventService {

    private final ApplicationEventPublisher eventPublisher;
    private final AgentRegistrationEventMapper eventMapper;
    private final AgentRegistrationMapper registrationMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void publishRegisterEvent(AgentRegistration registration) {
        log.info("Publishing register event for agent: {} [{}]",
                 registration.getAgentCode(), registration.getInstanceId());

        // 保存事件到数据库
        saveEvent(registration, "REGISTER", null, registration.getStatus(), "PUSH_API");

        // 发布Spring事件
        eventPublisher.publishEvent(new AgentRegisteredEvent(this, registration));
    }

    @Override
    public void publishUnregisterEvent(AgentRegistration registration) {
        log.info("Publishing unregister event for agent: {} [{}]",
                 registration.getAgentCode(), registration.getInstanceId());

        saveEvent(registration, "UNREGISTER", registration.getStatus(), 3, "PUSH_API");

        eventPublisher.publishEvent(new AgentUnregisteredEvent(this, registration));
    }

    @Override
    public void publishHeartbeatTimeoutEvent(String agentCode, String instanceId) {
        log.warn("Publishing heartbeat timeout event for agent: {} [{}]", agentCode, instanceId);

        AgentRegistration registration = getRegistrationEntity(agentCode, instanceId);
        if (registration != null) {
            saveEvent(registration, "HEARTBEAT_TIMEOUT",
                     registration.getStatus(), 2, "HEARTBEAT_TIMEOUT");

            eventPublisher.publishEvent(new HeartbeatTimeoutEvent(
                    this, agentCode, instanceId, registration.getLastHeartbeat()));
        }
    }

    @Override
    public void publishStatusChangeEvent(String agentCode, String instanceId,
                                          Integer previousStatus, Integer currentStatus) {
        log.info("Publishing status change event for agent: {} [{}] {} -> {}",
                 agentCode, instanceId, previousStatus, currentStatus);

        AgentRegistration registration = getRegistrationEntity(agentCode, instanceId);
        if (registration != null) {
            saveEvent(registration, "STATUS_CHANGE", previousStatus, currentStatus, "SYSTEM");

            eventPublisher.publishEvent(new AgentStatusChangedEvent(
                    this, agentCode, instanceId, previousStatus, currentStatus));
        }
    }

    @Override
    public List<AgentRegistrationEvent> getEventHistory(String agentCode, String instanceId,
                                                        LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<AgentRegistrationEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentRegistrationEvent::getAgentCode, agentCode)
               .eq(AgentRegistrationEvent::getInstanceId, instanceId)
               .between(AgentRegistrationEvent::getCreateTime, startTime, endTime)
               .orderByDesc(AgentRegistrationEvent::getCreateTime);

        return eventMapper.selectList(wrapper);
    }

    private void saveEvent(AgentRegistration registration, String eventType,
                           Integer previousStatus, Integer currentStatus, String source) {
        try {
            AgentRegistrationEvent event = new AgentRegistrationEvent();
            event.setAgentCode(registration.getAgentCode());
            event.setInstanceId(registration.getInstanceId());
            event.setEventType(eventType);
            event.setPreviousStatus(previousStatus);
            event.setCurrentStatus(currentStatus);
            event.setSource(source);
            event.setEventData(objectMapper.writeValueAsString(registration));
            event.setCreateTime(LocalDateTime.now());

            eventMapper.insert(event);
        } catch (Exception e) {
            log.error("Failed to save event for agent: {} [{}]",
                      registration.getAgentCode(), registration.getInstanceId(), e);
        }
    }

    private AgentRegistration getRegistrationEntity(String agentCode, String instanceId) {
        return registrationMapper.selectOne(
            new LambdaQueryWrapper<AgentRegistration>()
                .eq(AgentRegistration::getAgentCode, agentCode)
                .eq(AgentRegistration::getInstanceId, instanceId)
        );
    }
}
