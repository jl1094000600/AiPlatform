package com.aipal.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RealtimeAsrSessionResponse {
    private String sessionId;
    private String token;
    private String wsUrl;
    private Integer sampleRate;
    private String format;
    private Long expiresInSeconds;
}
