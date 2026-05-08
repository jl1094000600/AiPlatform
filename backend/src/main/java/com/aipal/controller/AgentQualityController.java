package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.AgentQualityEvaluationRequest;
import com.aipal.dto.AgentQualitySummary;
import com.aipal.entity.AiAgentQualityResult;
import com.aipal.entity.AiAgentQualityRun;
import com.aipal.service.AgentQualityService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agent-quality")
@RequiredArgsConstructor
public class AgentQualityController {

    private final AgentQualityService qualityService;

    @GetMapping("/summary")
    public Result<List<AgentQualitySummary>> getSummary() {
        return Result.success(qualityService.getSummary());
    }

    @GetMapping("/trends")
    public Result<List<AiAgentQualityRun>> getTrends(@RequestParam(required = false) Long agentId) {
        return Result.success(qualityService.getTrends(agentId));
    }

    @PostMapping("/evaluations")
    public Result<AiAgentQualityRun> runEvaluation(@RequestBody AgentQualityEvaluationRequest request) {
        return Result.success(qualityService.runEvaluation(request));
    }

    @GetMapping("/evaluations")
    public Result<Page<AiAgentQualityRun>> listEvaluations(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long agentId) {
        return Result.success(qualityService.listRuns(pageNum, pageSize, agentId));
    }

    @GetMapping("/evaluations/{runId}/results")
    public Result<List<AiAgentQualityResult>> listResults(@PathVariable Long runId) {
        return Result.success(qualityService.listResults(runId));
    }
}
