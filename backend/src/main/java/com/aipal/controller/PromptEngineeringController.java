package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.PromptEvaluateRequest;
import com.aipal.dto.PromptOptimizeRequest;
import com.aipal.entity.PromptEngineeringPrompt;
import com.aipal.entity.PromptEngineeringTestCase;
import com.aipal.entity.PromptEngineeringVersion;
import com.aipal.service.PromptEngineeringService;
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

@RestController
@RequestMapping("/api/v1/prompt-engineering")
@RequiredArgsConstructor
public class PromptEngineeringController {
    private final PromptEngineeringService promptService;

    @GetMapping("/prompts")
    public Result<?> listPrompts(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) Long agentId,
                                 @RequestParam(required = false) String projectKey,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(required = false) String keyword) {
        return Result.success(promptService.listPrompts(pageNum, pageSize, agentId, projectKey, status, keyword));
    }

    @GetMapping("/prompts/{id}")
    public Result<?> getPrompt(@PathVariable Long id) {
        return Result.success(promptService.getPrompt(id));
    }

    @PostMapping("/prompts")
    public Result<?> createPrompt(@RequestBody PromptEngineeringPrompt request) {
        return Result.success(promptService.createPrompt(request));
    }

    @PutMapping("/prompts/{id}")
    public Result<?> updatePrompt(@PathVariable Long id, @RequestBody PromptEngineeringPrompt request) {
        return Result.success(promptService.updatePrompt(id, request));
    }

    @DeleteMapping("/prompts/{id}")
    public Result<?> deletePrompt(@PathVariable Long id) {
        return Result.success(promptService.deletePrompt(id));
    }

    @GetMapping("/prompts/{id}/versions")
    public Result<?> listVersions(@PathVariable Long id) {
        return Result.success(promptService.listVersions(id));
    }

    @PostMapping("/prompts/{id}/versions")
    public Result<?> createVersion(@PathVariable Long id, @RequestBody PromptEngineeringVersion request) {
        return Result.success(promptService.createVersion(id, request));
    }

    @GetMapping("/prompts/{id}/test-cases")
    public Result<?> listTestCases(@PathVariable Long id) {
        return Result.success(promptService.listTestCases(id));
    }

    @PostMapping("/prompts/{id}/test-cases")
    public Result<?> createTestCase(@PathVariable Long id, @RequestBody PromptEngineeringTestCase request) {
        return Result.success(promptService.createTestCase(id, request));
    }

    @PutMapping("/prompts/{id}/test-cases/{caseId}")
    public Result<?> updateTestCase(@PathVariable Long id, @PathVariable Long caseId,
                                    @RequestBody PromptEngineeringTestCase request) {
        return Result.success(promptService.updateTestCase(id, caseId, request));
    }

    @DeleteMapping("/prompts/{id}/test-cases/{caseId}")
    public Result<?> deleteTestCase(@PathVariable Long id, @PathVariable Long caseId) {
        return Result.success(promptService.deleteTestCase(id, caseId));
    }

    @PostMapping("/versions/{versionId}/evaluate")
    public Result<?> evaluate(@PathVariable Long versionId, @RequestBody(required = false) PromptEvaluateRequest request) {
        return Result.success(promptService.evaluate(versionId, request));
    }

    @GetMapping("/versions/{versionId}/eval-runs")
    public Result<?> listEvalRuns(@PathVariable Long versionId) {
        return Result.success(promptService.listEvalRuns(versionId));
    }

    @GetMapping("/eval-runs/{runId}/results")
    public Result<?> listEvalResults(@PathVariable Long runId) {
        return Result.success(promptService.listEvalResults(runId));
    }

    @PostMapping("/versions/{versionId}/optimize")
    public Result<?> optimize(@PathVariable Long versionId, @RequestBody(required = false) PromptOptimizeRequest request) {
        return Result.success(promptService.optimize(versionId, request));
    }

    @GetMapping("/versions/{versionId}/optimize-runs")
    public Result<?> listOptimizeRuns(@PathVariable Long versionId) {
        return Result.success(promptService.listOptimizeRuns(versionId));
    }

    @PostMapping("/versions/{versionId}/publish")
    public Result<?> publish(@PathVariable Long versionId) {
        return Result.success(promptService.publish(versionId));
    }
}
