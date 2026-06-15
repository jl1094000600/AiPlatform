package com.aipal.dto;

import lombok.Data;

@Data
public class DataGenerationRequest {
    private String template;
    private Integer templateId;
    private Integer count;
}
