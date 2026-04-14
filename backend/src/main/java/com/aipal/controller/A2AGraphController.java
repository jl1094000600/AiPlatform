package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.AgentDetailResponse;
import com.aipal.dto.CallRecordItem;
import com.aipal.dto.PageResult;
import com.aipal.service.A2AGraphService;
import com.aipal.service.MonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/a2a/graph")
@RequiredArgsConstructor
public class A2AGraphController {
    private final A2AGraphService a2aGraphService;
    private final MonitorService monitorService;

    @GetMapping("/agents/{agentId}")
    public Result<AgentDetailResponse> getAgentDetail(@PathVariable Long agentId) {
        return Result.success(a2aGraphService.getAgentDetail(agentId));
    }

    @GetMapping("/agents/{agentId}/calls")
    public Result<PageResult<CallRecordItem>> getAgentCalls(
            @PathVariable Long agentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String callType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(a2aGraphService.getAgentCalls(agentId, page, pageSize, callType, status, startTime, endTime));
    }

    @PostMapping("/export")
    public Result<?> exportGraph(@RequestParam(required = false) String format) {
        return Result.success(a2aGraphService.exportGraph(format));
    }

    @GetMapping("/executions/{executionId}")
    public Result<?> getExecutionChain(@PathVariable String executionId) {
        return Result.success(monitorService.getExecutionChain(executionId, null));
    }
}
