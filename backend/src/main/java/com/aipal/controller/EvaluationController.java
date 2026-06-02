package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.BatchEvaluationRequest;
import com.aipal.dto.CriteriaConfigRequest;
import com.aipal.dto.EvaluationRequest;
import com.aipal.entity.AiEvaluation;
import com.aipal.entity.AiEvaluationCriteria;
import com.aipal.security.RequirePermission;
import com.aipal.service.CriteriaEngineService;
import com.aipal.service.EvaluationService;
import com.aipal.service.EvaluationStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final EvaluationStatisticsService statisticsService;
    private final CriteriaEngineService criteriaEngineService;

    @GetMapping
    @RequirePermission("benchmark:view")
    public Result<?> listEvaluations(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long datasetId,
            @RequestParam(required = false) Long agentId) {
        return Result.success(evaluationService.listEvaluations(pageNum, pageSize, name, datasetId, agentId));
    }

    @GetMapping("/{id}")
    @RequirePermission("benchmark:view")
    public Result<AiEvaluation> getEvaluation(@PathVariable Long id) {
        return Result.success(evaluationService.getEvaluationById(id));
    }

    @PostMapping
    @RequirePermission("benchmark:run")
    public Result<String> startEvaluation(@RequestBody EvaluationRequest request) {
        return Result.success(evaluationService.startEvaluation(request));
    }

    @PostMapping("/batch")
    @RequirePermission("benchmark:run")
    public Result<String> startBatchEvaluation(@RequestBody BatchEvaluationRequest request) {
        return Result.success(evaluationService.startBatchEvaluation(request));
    }

    @GetMapping("/{evaluationCode}/status")
    @RequirePermission("benchmark:view")
    public Result<String> getStatus(@PathVariable String evaluationCode) {
        return Result.success(evaluationService.getEvaluationStatus(evaluationCode));
    }

    @GetMapping("/batch/{batchCode}")
    @RequirePermission("benchmark:view")
    public Result<List<AiEvaluation>> getBatchEvaluations(@PathVariable String batchCode) {
        return Result.success(evaluationService.getBatchEvaluations(batchCode));
    }

    @GetMapping("/statistics")
    @RequirePermission("benchmark:view")
    public Result<Map<String, Object>> getStatistics(
            @RequestParam(required = false) Long datasetId,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) String timeRange) {
        return Result.success(statisticsService.getEvaluationStatistics(datasetId, agentId, timeRange));
    }

    @GetMapping("/leaderboard")
    @RequirePermission("benchmark:view")
    public Result<Map<String, Object>> getLeaderboard(@RequestParam(defaultValue = "10") int topN) {
        return Result.success(statisticsService.getAgentLeaderboard(topN));
    }

    @GetMapping("/ranking")
    @RequirePermission("benchmark:view")
    public Result<Map<String, Object>> getDatasetRanking(@RequestParam(defaultValue = "10") int topN) {
        return Result.success(statisticsService.getDatasetRanking(topN));
    }

    @GetMapping("/{id}/report")
    @RequirePermission("benchmark:view")
    public Result<String> generateReport(@PathVariable Long id) {
        return Result.success(statisticsService.generateEvaluationReport(id));
    }

    @PostMapping("/criteria")
    @RequirePermission("benchmark:manage")
    public Result<AiEvaluationCriteria> createCriteria(@RequestBody CriteriaConfigRequest request) {
        return Result.success(criteriaEngineService.createCriteria(request));
    }

    @GetMapping("/criteria")
    @RequirePermission("benchmark:view")
    public Result<List<AiEvaluationCriteria>> getAllCriteria() {
        return Result.success(criteriaEngineService.getAllCriteria());
    }

    @PutMapping("/criteria/{id}")
    @RequirePermission("benchmark:manage")
    public Result<Boolean> updateCriteria(@PathVariable Long id, @RequestBody CriteriaConfigRequest request) {
        return Result.success(criteriaEngineService.updateCriteria(request, id));
    }

    @DeleteMapping("/criteria/{id}")
    @RequirePermission("benchmark:manage")
    public Result<Boolean> deleteCriteria(@PathVariable Long id) {
        return Result.success(criteriaEngineService.deleteCriteria(id));
    }
}
