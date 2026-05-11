package com.aipal.dto;

import lombok.Data;

@Data
public class SkillFunctionDefinition {
    private String name;
    private String description;
    private String parametersJson;
    private String returnSchema;
    private String javaSnippet;
    private Boolean enabled;
}
