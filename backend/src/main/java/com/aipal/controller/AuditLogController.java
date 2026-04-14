package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.SysAuditLog;
import com.aipal.service.AuditLogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public Result<Page<SysAuditLog>> listAuditLogs(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(auditLogService.listAuditLogs(pageNum, pageSize, userId, operation, startTime, endTime));
    }

    @GetMapping("/{id}")
    public Result<SysAuditLog> getAuditLog(@PathVariable Long id) {
        return Result.success(auditLogService.getAuditLogById(id));
    }
}
