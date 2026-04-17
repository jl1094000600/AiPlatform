package com.aipal.agent.image.dto;

import lombok.Data;
import java.util.Map;

@Data
public class A2AMessage {
    private String sourceAgent;
    private String targetAgent;
    private String sessionId;
    private Action action;
    private Map<String, Object> payload;

    public enum Action {
        request,
        respond,
        ack,
        nack
    }
}
