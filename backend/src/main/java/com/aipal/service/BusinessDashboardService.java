package com.aipal.service;

import com.aipal.entity.AgentHeartbeat;
import com.aipal.mapper.AgentHeartbeatMapper;
import com.aipal.entity.MonCallRecord;
import com.aipal.mapper.MonCallRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BusinessDashboardService {

    private static final BigDecimal TOKEN_UNIT_COST = new BigDecimal("0.00001");

    private final MonCallRecordMapper callRecordMapper;
    private final AgentHeartbeatMapper heartbeatMapper;

    public Map<String, Object> getSummary() {
        List<MonCallRecord> todayRecords = getTodayRecords();
        long todayCalls = todayRecords.size();
        long successCalls = todayRecords.stream().filter(this::isSuccess).count();
        long totalTokens = todayRecords.stream().mapToLong(this::totalTokens).sum();
        double avgResponseTime = todayRecords.stream()
                .filter(r -> r.getDurationMs() != null)
                .mapToInt(MonCallRecord::getDurationMs)
                .average()
                .orElse(0);

        Map<String, Object> summary = new HashMap<>();
        summary.put("todayCalls", todayCalls);
        summary.put("onlineAgents", countOnlineAgents());
        summary.put("avgResponseTime", round(avgResponseTime));
        summary.put("todayCost", calculateCost(totalTokens));
        summary.put("successRate", todayCalls == 0 ? 100 : round((double) successCalls * 100 / todayCalls));
        summary.put("errorRate", todayCalls == 0 ? 0 : round((double) (todayCalls - successCalls) * 100 / todayCalls));
        summary.put("totalTokens", totalTokens);
        return summary;
    }

    private long countOnlineAgents() {
        List<AgentHeartbeat> heartbeats = heartbeatMapper.selectList(null);
        return heartbeats.stream()
                .filter(AgentRuntimeStatusSupport::isOnline)
                .map(AgentHeartbeat::getAgentCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .count();
    }

    public Map<String, Object> getTrends() {
        LocalDateTime start = LocalDateTime.now().minusHours(23).withMinute(0).withSecond(0).withNano(0);
        LambdaQueryWrapper<MonCallRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(MonCallRecord::getCreateTime, start);
        List<MonCallRecord> records = callRecordMapper.selectList(wrapper);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:00");
        List<Map<String, Object>> hourly = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            LocalDateTime bucket = start.plusHours(i);
            LocalDateTime next = bucket.plusHours(1);
            List<MonCallRecord> bucketRecords = records.stream()
                    .filter(r -> r.getCreateTime() != null)
                    .filter(r -> !r.getCreateTime().isBefore(bucket) && r.getCreateTime().isBefore(next))
                    .toList();
            long tokens = bucketRecords.stream().mapToLong(this::totalTokens).sum();
            double avgDuration = bucketRecords.stream()
                    .filter(r -> r.getDurationMs() != null)
                    .mapToInt(MonCallRecord::getDurationMs)
                    .average()
                    .orElse(0);

            Map<String, Object> point = new HashMap<>();
            point.put("time", bucket.format(formatter));
            point.put("calls", bucketRecords.size());
            point.put("avgResponseTime", round(avgDuration));
            point.put("cost", calculateCost(tokens));
            hourly.add(point);
        }

        return Map.of("records", hourly);
    }

    public List<Map<String, Object>> getExceptions() {
        Map<String, Object> summary = getSummary();
        List<Map<String, Object>> exceptions = new ArrayList<>();
        double errorRate = ((Number) summary.get("errorRate")).doubleValue();
        double avgResponseTime = ((Number) summary.get("avgResponseTime")).doubleValue();

        if (errorRate > 5) {
            exceptions.add(exception("错误率过高", "error_rate", errorRate, 5, "P1"));
        }
        if (avgResponseTime > 3000) {
            exceptions.add(exception("平均响应时间过高", "response_time", avgResponseTime, 3000, "P1"));
        }
        if (((Number) summary.get("onlineAgents")).longValue() == 0) {
            exceptions.add(exception("暂无在线 Agent", "offline_agents", 0, 1, "P0"));
        }
        return exceptions;
    }

    private List<MonCallRecord> getTodayRecords() {
        LambdaQueryWrapper<MonCallRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(MonCallRecord::getCreateTime, LocalDate.now().atStartOfDay());
        return callRecordMapper.selectList(wrapper);
    }

    private Map<String, Object> exception(String title, String metric, double value, double threshold, String level) {
        Map<String, Object> item = new HashMap<>();
        item.put("title", title);
        item.put("metric", metric);
        item.put("value", round(value));
        item.put("threshold", threshold);
        item.put("level", level);
        return item;
    }

    private boolean isSuccess(MonCallRecord record) {
        return record.getSuccess() != null && record.getSuccess() == 1;
    }

    private long totalTokens(MonCallRecord record) {
        if (record.getTotalTokens() != null) return record.getTotalTokens();
        long input = record.getInputTokens() == null ? 0 : record.getInputTokens();
        long output = record.getOutputTokens() == null ? 0 : record.getOutputTokens();
        return input + output;
    }

    private BigDecimal calculateCost(long tokens) {
        return TOKEN_UNIT_COST.multiply(BigDecimal.valueOf(tokens)).setScale(4, RoundingMode.HALF_UP);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
