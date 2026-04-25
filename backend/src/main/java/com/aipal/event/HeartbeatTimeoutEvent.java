package com.aipal.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 心跳超时事件
 */
@Getter
public class HeartbeatTimeoutEvent extends ApplicationEvent {
    private final String agentCode;
    private final String instanceId;
    private final LocalDateTime lastHeartbeat;

    public HeartbeatTimeoutEvent(Object source, String agentCode, String instanceId,
                                  LocalDateTime lastHeartbeat) {
        super(source);
        this.agentCode = agentCode;
        this.instanceId = instanceId;
        this.lastHeartbeat = lastHeartbeat;
    }
}
