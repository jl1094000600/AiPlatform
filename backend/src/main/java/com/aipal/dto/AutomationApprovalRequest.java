package com.aipal.dto;

import lombok.Data;

@Data
public class AutomationApprovalRequest {
    private String status;
    private String comment;
    private String reviewedBy;
    private String artifactContent;
}
