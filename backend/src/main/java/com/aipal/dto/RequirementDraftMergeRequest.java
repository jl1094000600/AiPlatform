package com.aipal.dto;

import lombok.Data;

import java.util.List;

@Data
public class RequirementDraftMergeRequest {
    private String originalText;
    private List<Long> attachmentIds;
}
