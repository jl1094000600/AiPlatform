package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.BillingBudget;
import com.aipal.security.RequirePermission;
import com.aipal.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/usage")
    @RequirePermission("billing:view")
    public Result<?> usage(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) Long bizModuleId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String source) {
        return Result.success(billingService.getUsage(startDate, endDate, agentId, bizModuleId, userId, username, source));
    }

    @GetMapping("/cost-trends")
    @RequirePermission("billing:view")
    public Result<?> costTrends(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) Long bizModuleId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String source) {
        return Result.success(billingService.getCostTrends(startDate, endDate, agentId, bizModuleId, userId, username, source));
    }

    @GetMapping("/budgets")
    @RequirePermission("billing:view")
    public Result<?> budgets(@RequestParam(defaultValue = "1") int pageNum,
                             @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(billingService.listBudgets(pageNum, pageSize));
    }

    @PostMapping("/budgets")
    @RequirePermission("billing:view")
    public Result<Boolean> createBudget(@RequestBody BillingBudget budget) {
        return Result.success(billingService.saveBudget(budget));
    }

    @PutMapping("/budgets/{id}")
    @RequirePermission("billing:view")
    public Result<Boolean> updateBudget(@PathVariable Long id, @RequestBody BillingBudget budget) {
        return Result.success(billingService.updateBudget(id, budget));
    }

    @GetMapping("/bills/export")
    @RequirePermission("billing:view")
    public ResponseEntity<byte[]> exportBill(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) Long bizModuleId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String source) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=billing.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(billingService.exportBill(startDate, endDate, agentId, bizModuleId, userId, username, source));
    }
}
