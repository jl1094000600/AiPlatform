package com.aipal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class A2AMessage {
    private String messageId;
    private String sourceAgent;
    private String targetAgent;
    private String sessionId;
    private Action action;
    private Map<String, Object> payload;
    private LocalDateTime timestamp;
    private String correlationId;
    private String traceId;

    public enum Action {
        invoke,
        respond,
        delegate,
        broadcast
    }
}
