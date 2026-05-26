package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.CodeQualityStandardRequest;
import com.aipal.dto.CodeQualityStandardResponse;
import com.aipal.security.RequirePermission;
import com.aipal.service.CodeQualityService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/code-quality")
@RequiredArgsConstructor
public class CodeQualityController {
    private final CodeQualityService codeQualityService;

    @GetMapping("/standards")
    @RequirePermission("code-quality:list")
    public Result<Page<CodeQualityStandardResponse>> listStandards(@RequestParam(defaultValue = "1") int pageNum,
                                                                   @RequestParam(defaultValue = "20") int pageSize,
                                                                   @RequestParam(required = false) Integer status) {
        return Result.success(codeQualityService.listStandards(pageNum, pageSize, status));
    }

    @GetMapping("/standards/enabled")
    @RequirePermission("code-quality:list")
    public Result<List<CodeQualityStandardResponse>> listEnabledStandards() {
        return Result.success(codeQualityService.listEnabledStandards());
    }

    @GetMapping("/standards/{id}")
    @RequirePermission("code-quality:list")
    public Result<CodeQualityStandardResponse> getStandard(@PathVariable Long id) {
        return Result.success(codeQualityService.getStandard(id));
    }

    @PostMapping("/standards")
    @RequirePermission("code-quality:manage")
    public Result<CodeQualityStandardResponse> createStandard(@RequestBody CodeQualityStandardRequest request) {
        return Result.success(codeQualityService.createStandard(request));
    }

    @PutMapping("/standards/{id}")
    @RequirePermission("code-quality:manage")
    public Result<CodeQualityStandardResponse> updateStandard(@PathVariable Long id,
                                                              @RequestBody CodeQualityStandardRequest request) {
        return Result.success(codeQualityService.updateStandard(id, request));
    }

    @DeleteMapping("/standards/{id}")
    @RequirePermission("code-quality:manage")
    public Result<Boolean> deleteStandard(@PathVariable Long id) {
        return Result.success(codeQualityService.deleteStandard(id));
    }
}
