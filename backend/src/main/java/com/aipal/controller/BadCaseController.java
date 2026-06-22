package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.security.RequirePermission;
import com.aipal.service.BadCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/badcases")
@RequiredArgsConstructor
public class BadCaseController {
    private final BadCaseService badCaseService;

    @GetMapping
    @RequirePermission("automation:list")
    public Result<?> list(@RequestParam(defaultValue = "1") int pageNum,
                          @RequestParam(defaultValue = "20") int pageSize,
                          @RequestParam(required = false) String stage,
                          @RequestParam(required = false) String badcaseType,
                          @RequestParam(required = false) String severity,
                          @RequestParam(required = false) String sourceType,
                          @RequestParam(required = false) String keyword) {
        return Result.success(badCaseService.list(pageNum, pageSize, stage, badcaseType, severity, sourceType, keyword));
    }

    @GetMapping("/statistics")
    @RequirePermission("automation:list")
    public Result<?> statistics() {
        return Result.success(badCaseService.statistics());
    }
}
