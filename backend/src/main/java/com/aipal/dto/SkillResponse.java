package com.aipal.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SkillResponse {
    private Long id;
    private String skillCode;
    private String skillName;
    private String description;
    private Integer status;
    private String promptContent;
    private List<SkillFunctionDefinition> functionDefinitions;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
