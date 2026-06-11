package com.aipal.dto;

import lombok.Data;

@Data
public class AutomationCodeFeedbackRequest {
    private Long batchId;
    private String alignmentStatus;
    private Integer alignmentScore;
    private String summary;
    private String failureReason;
    private String detailJson;
    private String reviewedBy;
}
