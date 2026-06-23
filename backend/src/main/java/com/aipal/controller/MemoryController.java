package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.AiMemoryItem;
import com.aipal.entity.AiMemoryRecallTrace;
import com.aipal.entity.AiMemoryVersion;
import com.aipal.security.RequirePermission;
import com.aipal.service.memory.MemoryManagementService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/memories")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryManagementService memoryManagementService;

    @GetMapping
    @RequirePermission("memory:list")
    public Result<Page<AiMemoryItem>> list(@RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "20") int pageSize,
                                           @RequestParam(required = false) String memoryType,
                                           @RequestParam(required = false) String status) {
        return Result.success(memoryManagementService.list(pageNum, pageSize, memoryType, status));
    }

    @GetMapping("/{id}")
    @RequirePermission("memory:read")
    public Result<AiMemoryItem> get(@PathVariable Long id) {
        return Result.success(memoryManagementService.get(id));
    }

    @GetMapping("/{id}/versions")
    @RequirePermission("memory:read")
    public Result<List<AiMemoryVersion>> versions(@PathVariable Long id) {
        return Result.success(memoryManagementService.versions(id));
    }

    @PutMapping("/{id}")
    @RequirePermission("memory:write")
    public Result<AiMemoryItem> update(@PathVariable Long id, @Valid @RequestBody MemoryUpdateRequest request) {
        return Result.success(memoryManagementService.update(id, request.version(), request.title(), request.content(), request.reason()));
    }

    @PostMapping("/{id}/forget")
    @RequirePermission("memory:forget")
    public Result<AiMemoryItem> forget(@PathVariable Long id, @RequestBody(required = false) MemoryForgetRequest request) {
        return Result.success(memoryManagementService.forget(id, request == null ? null : request.reason()));
    }

    @PostMapping("/{id}/confirm")
    @RequirePermission("memory:write")
    public Result<AiMemoryItem> confirm(@PathVariable Long id) {
        return Result.success(memoryManagementService.confirm(id));
    }

    @GetMapping("/traces/{traceId}")
    @RequirePermission("memory:trace")
    public Result<AiMemoryRecallTrace> trace(@PathVariable String traceId) {
        return Result.success(memoryManagementService.trace(traceId));
    }

    public record MemoryUpdateRequest(@NotNull Integer version, @NotBlank String title,
                                      @NotBlank String content, String reason) {
    }

    public record MemoryForgetRequest(String reason) {
    }
}
