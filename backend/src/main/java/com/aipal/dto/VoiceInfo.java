package com.aipal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceInfo {
    private String voiceId;
    private String voiceName;
    private String locale;
    private String gender;
    private String description;
}
