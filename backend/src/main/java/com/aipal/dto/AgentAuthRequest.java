package com.aipal.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AgentAuthRequest {
    @NotNull(message = "业务模块ID不能为空")
    private Long bizModuleId;

    @NotNull(message = "Agent ID不能为空")
    private Long agentId;

    private String agentVersion;
    private Integer qpsLimit;
    private Integer dailyLimit;
    private Integer status;
}
