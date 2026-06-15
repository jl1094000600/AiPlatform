package com.aipal.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RequirementAttachmentResponse {
    private Long id;
    private String requestId;
    private String fileName;
    private String mediaType;
    private String mimeType;
    private Long fileSize;
    private LocalDateTime createTime;
    private ParseTaskView latestTask;

    @Data
    @Builder
    public static class ParseTaskView {
        private Long id;
        private String status;
        private String resultText;
        private String editedResult;
        private String errorMessage;
        private Integer retryCount;
    }
}
