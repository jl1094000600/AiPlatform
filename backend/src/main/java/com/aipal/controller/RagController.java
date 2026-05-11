package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.RagIngestionRequest;
import com.aipal.dto.RagIngestionResponse;
import com.aipal.entity.RagIngestionRecord;
import com.aipal.service.RagService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagController {
    private final RagService ragService;

    @PostMapping("/ingestions")
    public Result<RagIngestionResponse> ingest(@RequestBody RagIngestionRequest request) {
        try {
            return Result.success(ragService.ingest(request));
        } catch (IllegalArgumentException ex) {
            return Result.badRequest(ex.getMessage());
        } catch (IllegalStateException ex) {
            return Result.serverError(ex.getMessage());
        }
    }

    @GetMapping("/ingestions")
    public Result<Page<RagIngestionRecord>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(ragService.list(pageNum, pageSize));
    }

    @GetMapping("/chroma/collections")
    public Result<?> listChromaCollections(@RequestParam(required = false) String chromaUrl) {
        try {
            return Result.success(ragService.listChromaCollections(chromaUrl));
        } catch (IllegalStateException ex) {
            return Result.serverError(ex.getMessage());
        }
    }

    @GetMapping("/chroma/collections/{collectionId}/documents")
    public Result<?> listChromaDocuments(@PathVariable String collectionId,
                                         @RequestParam(required = false) String chromaUrl,
                                         @RequestParam(defaultValue = "20") int limit,
                                         @RequestParam(defaultValue = "0") int offset) {
        try {
            return Result.success(ragService.listChromaDocuments(chromaUrl, collectionId, limit, offset));
        } catch (IllegalArgumentException ex) {
            return Result.badRequest(ex.getMessage());
        } catch (IllegalStateException ex) {
            return Result.serverError(ex.getMessage());
        }
    }
}
