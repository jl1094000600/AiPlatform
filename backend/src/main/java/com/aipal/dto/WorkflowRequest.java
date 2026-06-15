package com.aipal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 创建编排请求
 */
@Data
public class WorkflowRequest {
    @NotBlank(message = "workflowCode 不能为空")
    private String workflowCode;

    @NotBlank(message = "workflowName 不能为空")
    private String workflowName;

    private String description;

    @NotBlank(message = "triggerType 不能为空")
    @Pattern(regexp = "(?i)MANUAL|SCHEDULE|EVENT", message = "triggerType must be MANUAL, SCHEDULE or EVENT")
    private String triggerType; // MANUAL / SCHEDULE / EVENT

    private String triggerConfig;

    @NotBlank(message = "workflowDefinition 不能为空")
    private String workflowDefinition; // JSON array of WorkflowStep

    private Integer status;

    private Long ownerId;
}
