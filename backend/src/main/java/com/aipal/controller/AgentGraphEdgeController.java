package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.AgentGraphEdgeEvaluation;
import com.aipal.dto.AgentGraphEdgeRequest;
import com.aipal.entity.AgentGraphEdgeConfig;
import com.aipal.security.RequirePermission;
import com.aipal.service.AgentGraphEdgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agent-graph/edges")
@RequiredArgsConstructor
public class AgentGraphEdgeController {
    private final AgentGraphEdgeService edgeService;

    @GetMapping
    @RequirePermission("graph:manage")
    public Result<List<AgentGraphEdgeConfig>> listEdges() {
        return Result.success(edgeService.listEdges());
    }

    @PostMapping("/evaluate")
    @RequirePermission("graph:manage")
    public Result<AgentGraphEdgeEvaluation> evaluate(@RequestBody AgentGraphEdgeRequest request) {
        return Result.success(edgeService.evaluate(request));
    }

    @PostMapping
    @RequirePermission("graph:manage")
    public Result<AgentGraphEdgeConfig> createEdge(@RequestBody AgentGraphEdgeRequest request) {
        return Result.success(edgeService.createEdge(request));
    }

    @DeleteMapping("/{edgeId}")
    @RequirePermission("graph:manage")
    public Result<Void> deleteEdge(@PathVariable Long edgeId) {
        edgeService.deleteEdge(edgeId);
        return Result.success();
    }
}
