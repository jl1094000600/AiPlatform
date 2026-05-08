package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.AiAgentRuntimeConfig;
import com.aipal.service.AgentRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent-config")
@RequiredArgsConstructor
public class AgentConfigController {

    private final AgentRuntimeConfigService runtimeConfigService;

    @GetMapping("/{agentCode}")
    public Result<AiAgentRuntimeConfig> getEnabledConfig(@PathVariable String agentCode) {
        return Result.success(runtimeConfigService.getEnabledByAgentCode(agentCode));
    }
}
