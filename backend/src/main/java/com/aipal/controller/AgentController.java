package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.AiAgent;
import com.aipal.entity.AiAgentVersion;
import com.aipal.service.AgentService;
import com.aipal.service.AgentVersionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final AgentVersionService agentVersionService;

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

    @GetMapping("/{id}/versions")
    public Result<List<AiAgentVersion>> getVersions(@PathVariable Long id) {
        return Result.success(agentVersionService.getVersionsByAgentId(id));
    }

    @PostMapping("/{id}/call")
    public Result<?> callAgent(@PathVariable Long id, @RequestBody(required = false) Object params) {
        return Result.success(agentService.callAgent(id, params));
    }
}
