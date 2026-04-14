package com.aipal.service;

import com.aipal.entity.MonApiMetrics;
import com.aipal.entity.MonCallRecord;
import com.aipal.mapper.MonApiMetricsMapper;
import com.aipal.mapper.MonCallRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final MonCallRecordMapper callRecordMapper;
    private final MonApiMetricsMapper metricsMapper;

    public Map<String, Object> getOverview() {
        Map<String, Object> overview = new HashMap<>();

        LambdaQueryWrapper<MonCallRecord> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.ge(MonCallRecord::getRequestTime, LocalDate.now().atStartOfDay());
        long todayCalls = callRecordMapper.selectCount(todayWrapper);
        overview.put("todayCalls", todayCalls);

        LambdaQueryWrapper<MonCallRecord> successWrapper = new LambdaQueryWrapper<>();
        successWrapper.ge(MonCallRecord::getRequestTime, LocalDate.now().atStartOfDay())
                      .eq(MonCallRecord::getSuccess, 1);
        long todaySuccess = callRecordMapper.selectCount(successWrapper);
        overview.put("todaySuccessRate", todayCalls > 0 ? (double) todaySuccess / todayCalls * 100 : 0);

        LambdaQueryWrapper<MonCallRecord> avgWrapper = new LambdaQueryWrapper<>();
        avgWrapper.ge(MonCallRecord::getRequestTime, LocalDate.now().atStartOfDay())
                 .select(MonCallRecord::getDurationMs);
        List<MonCallRecord> todayRecords = callRecordMapper.selectList(avgWrapper);
        double avgDuration = todayRecords.stream()
                .filter(r -> r.getDurationMs() != null)
                .mapToInt(MonCallRecord::getDurationMs)
                .average()
                .orElse(0);
        overview.put("todayAvgDuration", avgDuration);

        LambdaQueryWrapper<MonCallRecord> tokenWrapper = new LambdaQueryWrapper<>();
        tokenWrapper.ge(MonCallRecord::getRequestTime, LocalDate.now().atStartOfDay())
                   .select(MonCallRecord::getInputTokens, MonCallRecord::getOutputTokens);
        List<MonCallRecord> tokenRecords = callRecordMapper.selectList(tokenWrapper);
        long totalInputTokens = tokenRecords.stream()
                .filter(r -> r.getInputTokens() != null)
                .mapToLong(MonCallRecord::getInputTokens)
                .sum();
        long totalOutputTokens = tokenRecords.stream()
                .filter(r -> r.getOutputTokens() != null)
                .mapToLong(MonCallRecord::getOutputTokens)
                .sum();
        overview.put("todayInputTokens", totalInputTokens);
        overview.put("todayOutputTokens", totalOutputTokens);
        overview.put("todayTotalTokens", totalInputTokens + totalOutputTokens);

        return overview;
    }

    public List<MonApiMetrics> getAgentStatistics(Long agentId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<MonApiMetrics> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MonApiMetrics::getAgentId, agentId)
               .between(MonApiMetrics::getStatDate, startDate, endDate)
               .orderByAsc(MonApiMetrics::getStatDate);
        return metricsMapper.selectList(wrapper);
    }

    public Map<String, Object> getModelStatistics(Long modelId, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> stats = new HashMap<>();

        LambdaQueryWrapper<MonApiMetrics> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MonApiMetrics::getModelId, modelId)
               .between(MonApiMetrics::getStatDate, startDate, endDate);
        List<MonApiMetrics> metrics = metricsMapper.selectList(wrapper);

        long totalCalls = metrics.stream().mapToLong(MonApiMetrics::getTotalCalls).sum();
        long totalInputTokens = metrics.stream().mapToLong(MonApiMetrics::getTotalInputTokens).sum();
        long totalOutputTokens = metrics.stream().mapToLong(MonApiMetrics::getTotalOutputTokens).sum();

        stats.put("totalCalls", totalCalls);
        stats.put("totalInputTokens", totalInputTokens);
        stats.put("totalOutputTokens", totalOutputTokens);
        stats.put("totalTokens", totalInputTokens + totalOutputTokens);

        return stats;
    }

    public List<MonApiMetrics> getModuleStatistics(Long moduleId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<MonApiMetrics> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MonApiMetrics::getBizModuleId, moduleId)
               .between(MonApiMetrics::getStatDate, startDate, endDate)
               .orderByAsc(MonApiMetrics::getStatDate);
        return metricsMapper.selectList(wrapper);
    }
}
