package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.InvocationRecord;
import com.aipal.security.RequirePermission;
import com.aipal.service.InvocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/invocations")
@RequiredArgsConstructor
public class InvocationController {

    private final InvocationService invocationService;

    @PostMapping
    @RequirePermission("agent:invoke")
    public Result<InvocationRecord> invoke(@RequestBody Map<String, Object> request) {
        return Result.success(invocationService.invoke(request));
    }

    @GetMapping
    @RequirePermission("agent:invoke")
    public Result<?> list(@RequestParam(defaultValue = "1") int pageNum,
                          @RequestParam(defaultValue = "20") int pageSize,
                          @RequestParam(required = false) Long agentId,
                          @RequestParam(required = false) String status) {
        return Result.success(invocationService.list(pageNum, pageSize, agentId, status));
    }

    @PostMapping("/{id}/retry")
    @RequirePermission("agent:invoke")
    public Result<InvocationRecord> retry(@PathVariable Long id) {
        return Result.success(invocationService.retry(id));
    }

    @GetMapping("/{id}/download")
    @RequirePermission("agent:invoke")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        InvocationRecord record = invocationService.get(id);
        String content = record == null ? "" : record.getOutputResult();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invocation-result.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(content.getBytes(StandardCharsets.UTF_8));
    }
}
