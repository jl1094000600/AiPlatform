package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.security.RequirePermission;
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
    @RequirePermission("dashboard:view")
    public Result<?> summary() {
        return Result.success(dashboardService.getSummary());
    }

    @GetMapping("/trends")
    @RequirePermission("dashboard:view")
    public Result<?> trends() {
        return Result.success(dashboardService.getTrends());
    }

    @GetMapping("/exceptions")
    @RequirePermission("dashboard:view")
    public Result<?> exceptions() {
        return Result.success(dashboardService.getExceptions());
    }
}
