package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.AlertRule;
import com.aipal.security.RequirePermission;
import com.aipal.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping("/rules")
    @RequirePermission("alert:view")
    public Result<?> rules(@RequestParam(defaultValue = "1") int pageNum,
                           @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(alertService.listRules(pageNum, pageSize));
    }

    @PostMapping("/rules")
    @RequirePermission("alert:view")
    public Result<Boolean> createRule(@RequestBody AlertRule rule) {
        return Result.success(alertService.createRule(rule));
    }

    @PutMapping("/rules/{id}")
    @RequirePermission("alert:view")
    public Result<Boolean> updateRule(@PathVariable Long id, @RequestBody AlertRule rule) {
        return Result.success(alertService.updateRule(id, rule));
    }

    @DeleteMapping("/rules/{id}")
    @RequirePermission("alert:view")
    public Result<Boolean> deleteRule(@PathVariable Long id) {
        return Result.success(alertService.deleteRule(id));
    }

    @GetMapping("/events")
    @RequirePermission("alert:view")
    public Result<?> events(@RequestParam(defaultValue = "1") int pageNum,
                            @RequestParam(defaultValue = "20") int pageSize,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String level) {
        return Result.success(alertService.listEvents(pageNum, pageSize, status, level));
    }

    @PostMapping("/events/{id}/ack")
    @RequirePermission("alert:view")
    public Result<Boolean> acknowledge(@PathVariable Long id, @RequestParam(required = false) String user) {
        return Result.success(alertService.acknowledge(id, user));
    }

    @PostMapping("/evaluate")
    @RequirePermission("alert:view")
    public Result<Integer> evaluate() {
        return Result.success(alertService.evaluateRules());
    }
}
