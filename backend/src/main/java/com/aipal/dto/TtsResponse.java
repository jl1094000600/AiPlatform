package com.aipal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtsResponse {
    private String taskId;
    private String audioUrl;
    private String audioData;
    private Float duration;
    private String format;
    private String voiceId;
    private String status;
    private String errorMessage;
}
