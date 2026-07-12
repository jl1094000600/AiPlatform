package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.AgentToolDefinition;
import com.aipal.security.RequirePermission;
import com.aipal.service.AgentToolRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agent-tools")
@RequiredArgsConstructor
public class AgentToolController {
    private final AgentToolRegistryService registryService;

    @GetMapping
    @RequirePermission("agent:invoke")
    public Result<List<AgentToolDefinition>> list() {
        return Result.success(registryService.listEnabled());
    }

    @GetMapping("/{toolName:.+}")
    @RequirePermission("agent:invoke")
    public Result<AgentToolDefinition> get(@PathVariable String toolName) {
        return Result.success(registryService.requireEnabled(toolName));
    }

    @PostMapping("/{toolName:.+}/test")
    @RequirePermission("agent:invoke")
    public Result<AgentToolRegistryService.ToolDryRunResult> dryRun(@PathVariable String toolName,
                                                                    @RequestBody(required = false) Object input) {
        return Result.success(registryService.dryRun(toolName, input));
    }
}
