package com.aipal.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Agent状态变更事件
 */
@Getter
public class AgentStatusChangedEvent extends ApplicationEvent {
    private final String agentCode;
    private final String instanceId;
    private final Integer previousStatus;
    private final Integer currentStatus;

    public AgentStatusChangedEvent(Object source, String agentCode, String instanceId,
                                   Integer previousStatus, Integer currentStatus) {
        super(source);
        this.agentCode = agentCode;
        this.instanceId = instanceId;
        this.previousStatus = previousStatus;
        this.currentStatus = currentStatus;
    }
}
