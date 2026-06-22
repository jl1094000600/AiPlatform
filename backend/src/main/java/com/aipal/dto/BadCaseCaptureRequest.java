package com.aipal.dto;

import lombok.Data;

@Data
public class BadCaseCaptureRequest {
    private String sourceType;
    private String stage;
    private String badcaseType;
    private String severity;
    private String projectName;
    private String requirementTitle;
    private String inputPrompt;
    private String generatedPrd;
    private String generatedCode;
    private String expectedBehavior;
    private String failureReason;
    private String reviewedBy;
    private Long pipelineId;
    private Long stageRunId;
    private Long batchId;
    private Long feedbackId;
    private Long approvalId;
    private String tags;
}
