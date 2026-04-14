package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.MonCallRecord;
import com.aipal.service.CallRecordService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/monitor")
@RequiredArgsConstructor
public class MonitorController {
    private final CallRecordService callRecordService;

    @GetMapping("/records")
    public Result<Page<MonCallRecord>> listRecords(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(callRecordService.listCallRecords(pageNum, pageSize, agentId, startTime, endTime));
    }

    @GetMapping("/traces/{traceId}")
    public Result<MonCallRecord> getTrace(@PathVariable String traceId) {
        return Result.success(callRecordService.getByTraceId(traceId));
    }

    @GetMapping("/recent")
    public Result<?> getRecentRecords(@RequestParam(defaultValue = "100") int limit) {
        return Result.success(callRecordService.getRecentRecords(limit));
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics(
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCalls", callRecordService.countByAgentAndTimeRange(agentId, startTime, endTime));
        stats.put("successRate", callRecordService.getSuccessRateByAgentAndTimeRange(agentId, startTime, endTime));
        stats.put("avgDuration", callRecordService.getAvgDurationByAgentAndTimeRange(agentId, startTime, endTime));
        return Result.success(stats);
    }

    @GetMapping("/realtime")
    public Result<Map<String, Object>> getRealtimeData() {
        Map<String, Object> realtime = new HashMap<>();
        realtime.put("onlineAgents", callRecordService.countOnlineAgents());
        realtime.put("currentQps", callRecordService.getCurrentQps());
        realtime.put("avgResponseTime", callRecordService.getAvgResponseTime());
        return Result.success(realtime);
    }
}
