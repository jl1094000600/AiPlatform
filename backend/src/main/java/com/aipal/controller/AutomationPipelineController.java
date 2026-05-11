package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.config.JwtConfig;
import com.aipal.dto.AutomationApprovalRequest;
import com.aipal.dto.AutomationPipelineRequest;
import com.aipal.service.AutomationPipelineService;
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
    private final JwtConfig jwtConfig;

    @GetMapping("/pipelines")
    public Result<?> listPipelines(@RequestParam(defaultValue = "1") int pageNum,
                                   @RequestParam(defaultValue = "20") int pageSize,
                                   @RequestParam(required = false) String status) {
        return Result.success(automationService.listPipelines(pageNum, pageSize, status));
    }

    @PostMapping("/pipelines")
    public Result<?> createPipeline(@RequestBody AutomationPipelineRequest request, HttpServletRequest httpRequest) {
        applyCurrentUser(request, httpRequest);
        return Result.success(automationService.createPipeline(request));
    }

    @GetMapping("/pipelines/{id}")
    public Result<?> getPipeline(@PathVariable Long id) {
        return Result.success(automationService.getDetail(id));
    }

    @PostMapping("/stages/{stageId}/run")
    public Result<?> runStage(@PathVariable Long stageId) {
        return Result.success(automationService.runStage(stageId));
    }

    @PostMapping("/pipelines/{id}/regenerate-prd")
    public Result<?> regeneratePrd(@PathVariable Long id) {
        return Result.success(automationService.regeneratePrd(id));
    }

    @PostMapping("/pipelines/{id}/regenerate-code")
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
    public Result<?> getCodeTree(@PathVariable Long id) {
        return Result.success(automationService.getCodeTree(id));
    }

    @GetMapping("/pipelines/{id}/code-file")
    public Result<?> getCodeFile(@PathVariable Long id, @RequestParam String path) {
        return Result.success(automationService.getCodeFile(id, path));
    }

    @GetMapping("/code-templates")
    public Result<?> listCodeTemplates() {
        return Result.success(automationService.listCodeTemplates());
    }

    @GetMapping("/prd-templates")
    public Result<?> listPrdTemplates() {
        return Result.success(automationService.listPrdTemplates());
    }

    @GetMapping("/code-template")
    public Result<?> getCodeTemplate(@RequestParam String fileName) {
        return Result.success(automationService.getCodeTemplate(fileName));
    }

    @GetMapping("/prd-template")
    public Result<?> getPrdTemplate(@RequestParam String fileName) {
        return Result.success(automationService.getPrdTemplate(fileName));
    }

    @PutMapping("/code-template")
    public Result<?> saveCodeTemplate(@RequestParam String fileName, @RequestBody Map<String, String> body) {
        return Result.success(automationService.saveCodeTemplate(fileName, body == null ? "" : body.get("content")));
    }

    @PutMapping("/prd-template")
    public Result<?> savePrdTemplate(@RequestParam String fileName, @RequestBody Map<String, String> body) {
        return Result.success(automationService.savePrdTemplate(fileName, body == null ? "" : body.get("content")));
    }

    @GetMapping("/project-directories")
    public Result<?> getProjectDirectories() {
        return Result.success(automationService.getProjectDirectoryTree());
    }

    @GetMapping("/approvals")
    public Result<?> listApprovals(@RequestParam(defaultValue = "1") int pageNum,
                                   @RequestParam(defaultValue = "20") int pageSize,
                                   @RequestParam(required = false) String status) {
        return Result.success(automationService.listApprovals(pageNum, pageSize, status));
    }

    @GetMapping("/approvals/{approvalId}/document")
    public Result<?> getApprovalDocument(@PathVariable Long approvalId) {
        return Result.success(automationService.getApprovalDocument(approvalId));
    }

    @PostMapping("/approvals/{approvalId}/approve")
    public Result<?> approve(@PathVariable Long approvalId, @RequestBody AutomationApprovalRequest request) {
        return Result.success(automationService.approve(approvalId, request));
    }

    @GetMapping("/reports/summary")
    public Result<?> reportSummary() {
        return Result.success(automationService.getReportSummary());
    }
}
