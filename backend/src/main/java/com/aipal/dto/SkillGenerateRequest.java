package com.aipal.dto;

import lombok.Data;

@Data
public class SkillGenerateRequest {
    private String requirement;
    private String scenario;
    private String modelCode;
    private Boolean includeFunction;
}
