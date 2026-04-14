package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.MonApiMetrics;
import com.aipal.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        return Result.success(statisticsService.getOverview());
    }

    @GetMapping("/agent/{agentId}")
    public Result<List<MonApiMetrics>> getAgentStatistics(
            @PathVariable Long agentId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.success(statisticsService.getAgentStatistics(agentId, startDate, endDate));
    }

    @GetMapping("/model/{modelId}")
    public Result<Map<String, Object>> getModelStatistics(
            @PathVariable Long modelId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.success(statisticsService.getModelStatistics(modelId, startDate, endDate));
    }

    @GetMapping("/module/{moduleId}")
    public Result<List<MonApiMetrics>> getModuleStatistics(
            @PathVariable Long moduleId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.success(statisticsService.getModuleStatistics(moduleId, startDate, endDate));
    }
}
