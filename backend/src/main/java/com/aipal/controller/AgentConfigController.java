package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.AiAgentRuntimeConfig;
import com.aipal.security.RequirePermission;
import com.aipal.service.AgentRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agent-config")
@RequiredArgsConstructor
public class AgentConfigController {

    private final AgentRuntimeConfigService runtimeConfigService;

    @GetMapping("/{agentCode}")
    @RequirePermission("agent:list")
    public Result<Map<String, Object>> getEnabledConfig(@PathVariable String agentCode) {
        return Result.success(publicRuntimeConfig(runtimeConfigService.getEnabledByAgentCode(agentCode)));
    }

    private Map<String, Object> publicRuntimeConfig(AiAgentRuntimeConfig config) {
        Map<String, Object> view = new LinkedHashMap<>();
        if (config == null) {
            return view;
        }
        view.put("agentId", config.getAgentId());
        view.put("agentCode", config.getAgentCode());
        view.put("modelCode", config.getModelCode());
        view.put("topK", config.getTopK());
        view.put("temperature", config.getTemperature());
        view.put("inputField", config.getInputField());
        view.put("expectedField", config.getExpectedField());
        view.put("enabled", config.getEnabled());
        view.put("promptAttached", config.getPromptId() != null || config.getPromptVersionId() != null);
        return view;
    }
}
