package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.config.JwtConfig;
import com.aipal.dto.AutomationApprovalRequest;
import com.aipal.dto.AutomationDeployProfileRequest;
import com.aipal.dto.AutomationPipelineRequest;
import com.aipal.security.RequirePermission;
import com.aipal.service.AutomationDeployProfileService;
import com.aipal.service.AutomationDeploymentExecutionService;
import com.aipal.service.AutomationPipelineService;
import com.aipal.service.CodeQualityService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/automation")
@RequiredArgsConstructor
public class AutomationPipelineController {

    private final AutomationPipelineService automationService;
    private final AutomationDeployProfileService deployProfileService;
    private final AutomationDeploymentExecutionService deploymentExecutionService;
    private final CodeQualityService codeQualityService;
    private final JwtConfig jwtConfig;

    @GetMapping("/pipelines")
    @RequirePermission("automation:list")
    public Result<?> listPipelines(@RequestParam(defaultValue = "1") int pageNum,
                                   @RequestParam(defaultValue = "20") int pageSize,
                                   @RequestParam(required = false) String status) {
        return Result.success(automationService.listPipelines(pageNum, pageSize, status));
    }

    @PostMapping("/pipelines")
    @RequirePermission("automation:create")
    public Result<?> createPipeline(@RequestBody AutomationPipelineRequest request, HttpServletRequest httpRequest) {
        applyCurrentUser(request, httpRequest);
        return Result.success(automationService.createPipeline(request));
    }

    @GetMapping("/pipelines/{id}")
    @RequirePermission("automation:list")
    public Result<?> getPipeline(@PathVariable Long id) {
        return Result.success(automationService.getDetail(id));
    }

    @GetMapping("/pipelines/{id}/deploy-runs")
    @RequirePermission("automation:list")
    public Result<?> getDeployRuns(@PathVariable Long id) {
        return Result.success(deploymentExecutionService.listRuns(id));
    }

    @GetMapping("/pipelines/{id}/code-quality-runs")
    @RequirePermission("code-quality:list")
    public Result<?> getCodeQualityRuns(@PathVariable Long id) {
        return Result.success(codeQualityService.listRuns(id));
    }

    @GetMapping("/code-quality-runs/{runId}/issues")
    @RequirePermission("code-quality:list")
    public Result<?> getCodeQualityIssues(@PathVariable Long runId) {
        return Result.success(codeQualityService.listIssues(runId));
    }

    @GetMapping("/code-quality-runs/{runId}/evidence")
    @RequirePermission("code-quality:list")
    public Result<?> getCodeQualityEvidence(@PathVariable Long runId) {
        return Result.success(codeQualityService.listEvidence(runId));
    }

    @GetMapping("/deploy-profiles")
    @RequirePermission("automation:list")
    public Result<?> listDeployProfiles(@RequestParam(defaultValue = "1") int pageNum,
                                        @RequestParam(defaultValue = "20") int pageSize,
                                        @RequestParam(required = false) Integer status) {
        return Result.success(deployProfileService.list(pageNum, pageSize, status));
    }

    @GetMapping("/deploy-profiles/enabled")
    @RequirePermission("automation:list")
    public Result<?> listEnabledDeployProfiles() {
        return Result.success(deployProfileService.listEnabled());
    }

    @GetMapping("/deploy-profiles/{id}")
    @RequirePermission("automation:list")
    public Result<?> getDeployProfile(@PathVariable Long id) {
        return Result.success(deployProfileService.get(id));
    }

    @PostMapping("/deploy-profiles")
    @RequirePermission("automation:create")
    public Result<?> createDeployProfile(@RequestBody AutomationDeployProfileRequest request) {
        return Result.success(deployProfileService.create(request));
    }

    @PutMapping("/deploy-profiles/{id}")
    @RequirePermission("automation:create")
    public Result<?> updateDeployProfile(@PathVariable Long id, @RequestBody AutomationDeployProfileRequest request) {
        return Result.success(deployProfileService.update(id, request));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/deploy-profiles/{id}")
    @RequirePermission("automation:delete")
    public Result<?> deleteDeployProfile(@PathVariable Long id) {
        return Result.success(deployProfileService.delete(id));
    }

    @PostMapping("/stages/{stageId}/run")
    @RequirePermission("automation:run")
    public Result<?> runStage(@PathVariable Long stageId) {
        return Result.success(automationService.runStage(stageId));
    }

    @PostMapping("/pipelines/{id}/regenerate-prd")
    @RequirePermission("automation:run")
    public Result<?> regeneratePrd(@PathVariable Long id) {
        return Result.success(automationService.regeneratePrd(id));
    }

    @PostMapping("/pipelines/{id}/regenerate-code")
    @RequirePermission("automation:run")
    public Result<?> regenerateCode(@PathVariable Long id) {
        return Result.success(automationService.regenerateCode(id));
    }

    private void applyCurrentUser(AutomationPipelineRequest request, HttpServletRequest httpRequest) {
        String token = bearerToken(httpRequest);
        if (token == null) {
            return;
        }
        try {
            Long userId = jwtConfig.getUserIdFromToken(token);
            String username = jwtConfig.getUsernameFromToken(token);
            request.setInitiatorUserId(userId);
            request.setInitiatorUsername(username);
            request.setInitiator(username);
        } catch (Exception ignored) {
        }
    }

    private String bearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7);
        return jwtConfig.validateToken(token) ? token : null;
    }

    @GetMapping("/pipelines/{id}/code-tree")
    @RequirePermission("automation:list")
    public Result<?> getCodeTree(@PathVariable Long id) {
        return Result.success(automationService.getCodeTree(id));
    }

    @GetMapping("/pipelines/{id}/code-file")
    @RequirePermission("automation:list")
    public Result<?> getCodeFile(@PathVariable Long id, @RequestParam String path) {
        return Result.success(automationService.getCodeFile(id, path));
    }

    @GetMapping("/code-templates")
    @RequirePermission("automation:list")
    public Result<?> listCodeTemplates() {
        return Result.success(automationService.listCodeTemplates());
    }

    @GetMapping("/prd-templates")
    @RequirePermission("automation:list")
    public Result<?> listPrdTemplates() {
        return Result.success(automationService.listPrdTemplates());
    }

    @GetMapping("/code-template")
    @RequirePermission("automation:list")
    public Result<?> getCodeTemplate(@RequestParam String fileName) {
        return Result.success(automationService.getCodeTemplate(fileName));
    }

    @GetMapping("/prd-template")
    @RequirePermission("automation:list")
    public Result<?> getPrdTemplate(@RequestParam String fileName) {
        return Result.success(automationService.getPrdTemplate(fileName));
    }

    @PutMapping("/code-template")
    @RequirePermission("automation:create")
    public Result<?> saveCodeTemplate(@RequestParam String fileName, @RequestBody Map<String, String> body) {
        return Result.success(automationService.saveCodeTemplate(fileName, body == null ? "" : body.get("content")));
    }

    @PutMapping("/prd-template")
    @RequirePermission("automation:create")
    public Result<?> savePrdTemplate(@RequestParam String fileName, @RequestBody Map<String, String> body) {
        return Result.success(automationService.savePrdTemplate(fileName, body == null ? "" : body.get("content")));
    }

    @GetMapping("/project-directories")
    @RequirePermission("automation:list")
    public Result<?> getProjectDirectories() {
        return Result.success(automationService.getProjectDirectoryTree());
    }

    @GetMapping("/approvals")
    @RequirePermission("automation:approve")
    public Result<?> listApprovals(@RequestParam(defaultValue = "1") int pageNum,
                                   @RequestParam(defaultValue = "20") int pageSize,
                                   @RequestParam(required = false) String status) {
        return Result.success(automationService.listApprovals(pageNum, pageSize, status));
    }

    @GetMapping("/approvals/{approvalId}/document")
    @RequirePermission("automation:approve")
    public Result<?> getApprovalDocument(@PathVariable Long approvalId) {
        return Result.success(automationService.getApprovalDocument(approvalId));
    }

    @PostMapping("/approvals/{approvalId}/approve")
    @RequirePermission("automation:approve")
    public Result<?> approve(@PathVariable Long approvalId, @RequestBody AutomationApprovalRequest request) {
        return Result.success(automationService.approve(approvalId, request));
    }

    @GetMapping("/reports/summary")
    @RequirePermission("automation:list")
    public Result<?> reportSummary() {
        return Result.success(automationService.getReportSummary());
    }
}
