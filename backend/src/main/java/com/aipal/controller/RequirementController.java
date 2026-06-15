package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.RequirementAttachmentResultRequest;
import com.aipal.dto.RequirementDraftMergeRequest;
import com.aipal.dto.RequirementPrdRequest;
import com.aipal.service.RequirementAttachmentService;
import com.aipal.service.RequirementDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/requirements")
@RequiredArgsConstructor
public class RequirementController {
    private final RequirementAttachmentService attachmentService;
    private final RequirementDraftService draftService;

    @PostMapping("/attachments")
    public Result<?> upload(@RequestParam String requestId, @RequestPart("file") MultipartFile file) {
        return Result.success(attachmentService.upload(requestId, file));
    }

    @GetMapping("/attachments")
    public Result<?> list(@RequestParam String requestId) {
        return Result.success(attachmentService.list(requestId));
    }

    @PutMapping("/attachments/{id}/result")
    public Result<?> updateResult(@PathVariable Long id, @RequestBody RequirementAttachmentResultRequest request) {
        return Result.success(attachmentService.updateResult(id, request == null ? null : request.getResultText()));
    }

    @PostMapping("/attachments/{id}/retry")
    public Result<?> retry(@PathVariable Long id) {
        return Result.success(attachmentService.retry(id));
    }

    @DeleteMapping("/attachments/{id}")
    public Result<?> delete(@PathVariable Long id) {
        return Result.success(attachmentService.delete(id));
    }

    @PostMapping("/drafts/merge")
    public Result<?> merge(@RequestBody RequirementDraftMergeRequest request) {
        return Result.success(draftService.merge(request));
    }

    @PostMapping("/prd")
    public Result<?> createPrd(@RequestBody RequirementPrdRequest request) {
        return Result.success(draftService.createPrd(request));
    }

    @GetMapping("/prd/{pipelineId}")
    public Result<?> getPrd(@PathVariable Long pipelineId) {
        return Result.success(draftService.getPrd(pipelineId));
    }
}
