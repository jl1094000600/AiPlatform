package com.aipal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtsRequest {
    private String text;
    private String voiceId;
    private Float speed;
    private Integer volume;
    private String outputFormat;
    private String sessionId;
}
