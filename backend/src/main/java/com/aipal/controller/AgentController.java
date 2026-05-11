package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.config.JwtConfig;
import com.aipal.entity.AiAgent;
import com.aipal.entity.AiAgentRuntimeConfig;
import com.aipal.entity.AiAgentVersion;
import com.aipal.service.AgentRuntimeConfigService;
import com.aipal.service.AgentService;
import com.aipal.service.AgentVersionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final AgentVersionService agentVersionService;
    private final AgentRuntimeConfigService runtimeConfigService;
    private final JwtConfig jwtConfig;

    @GetMapping
    public Result<Page<AiAgent>> listAgents(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String category) {
        return Result.success(agentService.listAgents(pageNum, pageSize, name, status, category));
    }

    @GetMapping("/{id}")
    public Result<AiAgent> getAgent(@PathVariable Long id) {
        return Result.success(agentService.getAgentById(id));
    }

    @PostMapping
    public Result<Boolean> createAgent(@RequestBody AiAgent agent) {
        return Result.success(agentService.saveAgent(agent));
    }

    @PutMapping("/{id}")
    public Result<Boolean> updateAgent(@PathVariable Long id, @RequestBody AiAgent agent) {
        agent.setId(id);
        return Result.success(agentService.updateAgent(agent));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteAgent(@PathVariable Long id) {
        return Result.success(agentService.deleteAgent(id));
    }

    @PostMapping("/{id}/publish")
    public Result<Boolean> publishAgent(@PathVariable Long id) {
        return Result.success(agentService.publish(id));
    }

    @PostMapping("/{id}/offline")
    public Result<Boolean> offlineAgent(@PathVariable Long id) {
        return Result.success(agentService.offline(id));
    }

    @PostMapping("/{id}/rollback")
    public Result<Boolean> rollbackAgent(@PathVariable Long id) {
        return Result.success(agentVersionService.rollbackToPreviousVersion(id));
    }

    @GetMapping("/{id}/versions")
    public Result<List<AiAgentVersion>> getVersions(@PathVariable Long id) {
        return Result.success(agentVersionService.getVersionsByAgentId(id));
    }

    @PostMapping("/{id}/call")
    public Result<?> callAgent(@PathVariable Long id,
                               @RequestBody(required = false) Object params,
                               HttpServletRequest request) {
        return Result.success(agentService.callAgent(id, params, currentUserId(request), currentUsername(request)));
    }

    private Long currentUserId(HttpServletRequest request) {
        String token = bearerToken(request);
        if (token == null) return null;
        try {
            return jwtConfig.getUserIdFromToken(token);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String currentUsername(HttpServletRequest request) {
        String token = bearerToken(request);
        if (token == null) return null;
        try {
            return jwtConfig.getUsernameFromToken(token);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String bearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7);
        return jwtConfig.validateToken(token) ? token : null;
    }

    @GetMapping("/{id}/runtime-config")
    public Result<AiAgentRuntimeConfig> getRuntimeConfig(@PathVariable Long id) {
        return Result.success(runtimeConfigService.getOrDefaultByAgentId(id));
    }

    @PutMapping("/{id}/runtime-config")
    public Result<AiAgentRuntimeConfig> updateRuntimeConfig(
            @PathVariable Long id,
            @RequestBody AiAgentRuntimeConfig config) {
        return Result.success(runtimeConfigService.saveForAgent(id, config));
    }
}
