package com.aipal.event;

import com.aipal.entity.AgentRegistration;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Agent注销事件
 */
@Getter
public class AgentUnregisteredEvent extends ApplicationEvent {
    private final AgentRegistration registration;

    public AgentUnregisteredEvent(Object source, AgentRegistration registration) {
        super(source);
        this.registration = registration;
    }
}