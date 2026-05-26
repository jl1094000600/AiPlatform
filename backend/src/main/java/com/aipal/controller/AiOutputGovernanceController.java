package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.AiOutputGovernancePolicyTemplate;
import com.aipal.entity.AiOutputGovernanceRecord;
import com.aipal.security.RequirePermission;
import com.aipal.service.AiOutputGovernanceService;
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
@RequestMapping("/api/v1/ai-output-governance")
@RequiredArgsConstructor
public class AiOutputGovernanceController {
    private final AiOutputGovernanceService governanceService;

    @GetMapping("/records")
    @RequirePermission("governance:list")
    public Result<Page<AiOutputGovernanceRecord>> listRecords(@RequestParam(defaultValue = "1") int pageNum,
                                                              @RequestParam(defaultValue = "20") int pageSize,
                                                              @RequestParam(required = false) Long pipelineId,
                                                              @RequestParam(required = false) String artifactType,
                                                              @RequestParam(required = false) String riskLevel,
                                                              @RequestParam(required = false) String governanceStatus) {
        return Result.success(governanceService.listRecords(
                pageNum, pageSize, pipelineId, artifactType, riskLevel, governanceStatus));
    }

    @GetMapping("/records/{id}")
    @RequirePermission("governance:list")
    public Result<AiOutputGovernanceRecord> getRecord(@PathVariable Long id) {
        return Result.success(governanceService.getRecord(id));
    }

    @GetMapping("/policy-templates")
    @RequirePermission("governance:list")
    public Result<Page<AiOutputGovernancePolicyTemplate>> listPolicyTemplates(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status) {
        return Result.success(governanceService.listPolicyTemplates(pageNum, pageSize, category, status));
    }

    @GetMapping("/policy-templates/enabled")
    @RequirePermission("governance:list")
    public Result<List<AiOutputGovernancePolicyTemplate>> listEnabledPolicyTemplates() {
        return Result.success(governanceService.listEnabledPolicyTemplates());
    }

    @PostMapping("/policy-templates")
    @RequirePermission("governance:manage")
    public Result<AiOutputGovernancePolicyTemplate> createPolicyTemplate(
            @RequestBody AiOutputGovernancePolicyTemplate request) {
        return Result.success(governanceService.createPolicyTemplate(request));
    }

    @PutMapping("/policy-templates/{id}")
    @RequirePermission("governance:manage")
    public Result<AiOutputGovernancePolicyTemplate> updatePolicyTemplate(
            @PathVariable Long id, @RequestBody AiOutputGovernancePolicyTemplate request) {
        return Result.success(governanceService.updatePolicyTemplate(id, request));
    }

    @DeleteMapping("/policy-templates/{id}")
    @RequirePermission("governance:manage")
    public Result<Boolean> deletePolicyTemplate(@PathVariable Long id) {
        return Result.success(governanceService.deletePolicyTemplate(id));
    }
}
