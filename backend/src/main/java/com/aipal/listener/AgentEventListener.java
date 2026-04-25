package com.aipal.listener;

import com.aipal.entity.AgentRegistration;
import com.aipal.event.AgentRegisteredEvent;
import com.aipal.event.AgentStatusChangedEvent;
import com.aipal.event.AgentUnregisteredEvent;
import com.aipal.event.HeartbeatTimeoutEvent;
import com.aipal.service.A2AMessageService;
import com.aipal.service.AgentRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent事件监听器
 * 处理注册、注销、状态变更等事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentEventListener {

    private final AgentRegistryService agentRegistryService;
    private final A2AMessageService a2aMessageService;

    @EventListener
    public void onAgentRegistered(AgentRegisteredEvent event) {
        AgentRegistration registration = event.getRegistration();
        log.info("Agent registered: {} [{}]", registration.getAgentCode(),
                 registration.getInstanceId());

        // 1. 注册A2A Handler
        registerA2AHandler(registration);

        // 2. 通知图谱更新
        notifyGraphUpdate(registration, "REGISTER");
    }

    @EventListener
    public void onAgentUnregistered(AgentUnregisteredEvent event) {
        AgentRegistration registration = event.getRegistration();
        log.info("Agent unregistered: {} [{}]", registration.getAgentCode(),
                 registration.getInstanceId());

        // 1. 注销A2A Handler - 从handlerMap中移除
        // A2AMessageService没有提供unregisterHandler方法，需要在A2AMessageService中添加

        // 2. 通知图谱更新
        notifyGraphUpdate(registration, "UNREGISTER");
    }

    @EventListener
    public void onHeartbeatTimeout(HeartbeatTimeoutEvent event) {
        log.warn("Heartbeat timeout for agent: {} [{}], lastHeartbeat={}",
                 event.getAgentCode(), event.getInstanceId(), event.getLastHeartbeat());

        // 自动标记为离线状态
        agentRegistryService.updateStatus(event.getAgentCode(), event.getInstanceId(), 2);
    }

    @EventListener
    public void onAgentStatusChanged(AgentStatusChangedEvent event) {
        log.info("Agent status changed: {} [{}] {} -> {}",
                 event.getAgentCode(), event.getInstanceId(),
                 event.getPreviousStatus(), event.getCurrentStatus());

        // 通知图谱更新
        AgentRegistration registration = agentRegistryService.getRegistration(
                event.getAgentCode(), event.getInstanceId());
        if (registration != null) {
            notifyGraphUpdate(registration, "STATUS_CHANGE");
        }
    }

    private void registerA2AHandler(AgentRegistration registration) {
        a2aMessageService.registerHandler(registration.getAgentCode(), message -> {
            log.debug("Default A2A handler for agent: {}, message: {}",
                    registration.getAgentCode(), message.getMessageId());

            // 返回默认响应
            com.aipal.dto.A2AMessage response = new com.aipal.dto.A2AMessage();
            response.setSourceAgent(registration.getAgentCode());
            response.setTargetAgent(message.getSourceAgent());
            response.setSessionId(message.getSessionId());
            response.setAction(com.aipal.dto.A2AMessage.Action.respond);
            response.setPayload(Map.of("status", "acknowledged"));
            return response;
        });
    }

    private void notifyGraphUpdate(AgentRegistration registration, String eventType) {
        // 这里可以通知图谱服务更新
        // 例如通过Redis发布消息，或者直接调用图谱更新接口
        log.debug("Notifying graph update for agent: {}, event: {}",
                registration.getAgentCode(), eventType);
    }
}
