package com.aipal.event;

import com.aipal.entity.AgentRegistration;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Agent注册事件
 */
@Getter
public class AgentRegisteredEvent extends ApplicationEvent {
    private final AgentRegistration registration;

    public AgentRegisteredEvent(Object source, AgentRegistration registration) {
        super(source);
        this.registration = registration;
    }
}
