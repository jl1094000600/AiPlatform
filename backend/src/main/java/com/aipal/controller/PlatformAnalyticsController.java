package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.security.RequirePermission;
import com.aipal.service.PlatformAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class PlatformAnalyticsController {

    private final PlatformAnalyticsService analyticsService;

    @GetMapping("/overview")
    @RequirePermission("dashboard:view")
    public Result<?> overview(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.success(analyticsService.getOverview(startDate, endDate));
    }

    @GetMapping("/badcases")
    @RequirePermission("dashboard:view")
    public Result<?> badcases(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.success(analyticsService.getBadcases(startDate, endDate));
    }

    @GetMapping("/token-cost")
    @RequirePermission("dashboard:view")
    public Result<?> tokenCost(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.success(analyticsService.getTokenCost(startDate, endDate));
    }

    @GetMapping("/pipelines")
    @RequirePermission("dashboard:view")
    public Result<?> pipelines(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.success(analyticsService.getPipelines(startDate, endDate));
    }

    @GetMapping("/models/value")
    @RequirePermission("dashboard:view")
    public Result<?> modelValue(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Result.success(analyticsService.getModelValue(startDate, endDate));
    }
}
