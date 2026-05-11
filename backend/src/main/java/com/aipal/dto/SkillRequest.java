package com.aipal.dto;

import lombok.Data;

import java.util.List;

@Data
public class SkillRequest {
    private String skillCode;
    private String skillName;
    private String description;
    private Integer status;
    private String promptContent;
    private List<SkillFunctionDefinition> functionDefinitions;
}
