package com.aipal.agent.image.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileParseResponse {
    private String sessionId;
    private String status;
    private String content;
    private String error;
}
