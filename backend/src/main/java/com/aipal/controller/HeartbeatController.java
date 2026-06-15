package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.HeartbeatRequest;
import com.aipal.entity.AgentHeartbeat;
import com.aipal.security.HeartbeatAuthenticator;
import com.aipal.security.RequirePermission;
import com.aipal.service.HeartbeatManagementService;
import com.aipal.service.HeartbeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/heartbeat")
@RequiredArgsConstructor
public class HeartbeatController {

    private final HeartbeatService heartbeatService;
    private final HeartbeatManagementService heartbeatManagementService;
    private final HeartbeatAuthenticator heartbeatAuthenticator;

    @PostMapping("/report")
    public Result<Void> reportHeartbeat(
            @RequestHeader("X-Agent-Heartbeat-Token") String token,
            @Valid @RequestBody HeartbeatRequest request) {
        heartbeatAuthenticator.authenticateAndRun(
                request.getTenantCode(), token, () -> heartbeatManagementService.recordHeartbeat(request));
        return Result.success(null);
    }

    @GetMapping("/status/{agentId}")
    @RequirePermission("agent:list")
    public Result<Boolean> getStatus(@PathVariable Long agentId) {
        return Result.success(heartbeatService.isAgentOnline(agentId));
    }

    @GetMapping("/detail/{agentId}")
    @RequirePermission("agent:list")
    public Result<AgentHeartbeat> getHeartbeat(@PathVariable Long agentId) {
        AgentHeartbeat heartbeat = heartbeatService.getHeartbeat(agentId);
        if (heartbeat == null) {
            return Result.error("Heartbeat not found");
        }
        return Result.success(heartbeat);
    }

    @PostMapping("/detect-offline")
    @RequirePermission("agent:update")
    public Result<Void> detectOfflineAgents() {
        heartbeatService.detectOfflineAgents();
        return Result.success(null);
    }
}
