package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.service.BusinessDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business-dashboard")
@RequiredArgsConstructor
public class BusinessDashboardController {

    private final BusinessDashboardService dashboardService;

    @GetMapping("/summary")
    public Result<?> summary() {
        return Result.success(dashboardService.getSummary());
    }

    @GetMapping("/trends")
    public Result<?> trends() {
        return Result.success(dashboardService.getTrends());
    }

    @GetMapping("/exceptions")
    public Result<?> exceptions() {
        return Result.success(dashboardService.getExceptions());
    }
}
