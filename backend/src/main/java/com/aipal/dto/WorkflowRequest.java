package com.aipal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Map;

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
    private String triggerType; // MANUAL / SCHEDULE / EVENT

    private String triggerConfig;

    @NotBlank(message = "workflowDefinition 不能为空")
    private String workflowDefinition; // JSON array of WorkflowStep

    private Integer status;

    private Long ownerId;
}