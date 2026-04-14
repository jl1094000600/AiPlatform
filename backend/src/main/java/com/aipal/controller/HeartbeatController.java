package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.HeartbeatRequest;
import com.aipal.entity.AgentHeartbeat;
import com.aipal.service.HeartbeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/heartbeat")
@RequiredArgsConstructor
public class HeartbeatController {

    private final HeartbeatService heartbeatService;

    @PostMapping("/report")
    public Result<Void> reportHeartbeat(@Valid @RequestBody HeartbeatRequest request) {
        heartbeatService.recordHeartbeat(request);
        return Result.success(null);
    }

    @GetMapping("/status/{agentId}")
    public Result<Boolean> getStatus(@PathVariable Long agentId) {
        return Result.success(heartbeatService.isAgentOnline(agentId));
    }

    @GetMapping("/detail/{agentId}")
    public Result<AgentHeartbeat> getHeartbeat(@PathVariable Long agentId) {
        AgentHeartbeat heartbeat = heartbeatService.getHeartbeat(agentId);
        if (heartbeat == null) {
            return Result.error("Heartbeat not found");
        }
        return Result.success(heartbeat);
    }

    @PostMapping("/detect-offline")
    public Result<Void> detectOfflineAgents() {
        heartbeatService.detectOfflineAgents();
        return Result.success(null);
    }
}
