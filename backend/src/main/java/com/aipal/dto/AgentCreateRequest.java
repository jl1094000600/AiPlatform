package com.aipal.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class AgentCreateRequest {
    @NotBlank(message = "Agent编码不能为空")
    private String agentCode;
    @NotBlank(message = "Agent名称不能为空")
    private String agentName;
    @NotBlank(message = "描述不能为空")
    private String description;
    @NotBlank(message = "分类不能为空")
    private String category;
    @NotBlank(message = "接口地址不能为空")
    private String apiUrl;
    private String httpMethod = "POST";
    private String requestSchema;
    private String responseSchema;
    private Long ownerId;
}
