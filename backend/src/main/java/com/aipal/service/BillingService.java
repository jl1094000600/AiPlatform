package com.aipal.service;

import com.aipal.entity.BillingBudget;
import com.aipal.entity.MonCallRecord;
import com.aipal.mapper.BillingBudgetMapper;
import com.aipal.mapper.MonCallRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BillingService {

    private static final BigDecimal TOKEN_UNIT_COST = new BigDecimal("0.00001");

    private final MonCallRecordMapper callRecordMapper;
    private final BillingBudgetMapper budgetMapper;

    public Map<String, Object> getUsage(LocalDate startDate, LocalDate endDate, Long agentId, Long bizModuleId) {
        List<MonCallRecord> records = queryRecords(startDate, endDate, agentId, bizModuleId);
        long calls = records.size();
        long tokens = records.stream().mapToLong(this::totalTokens).sum();
        Map<String, Object> usage = new HashMap<>();
        usage.put("totalCalls", calls);
        usage.put("totalTokens", tokens);
        usage.put("totalCost", calculateCost(tokens));
        usage.put("records", records);
        return usage;
    }

    public Map<String, Object> getCostTrends(LocalDate startDate, LocalDate endDate, Long agentId, Long bizModuleId) {
        List<MonCallRecord> records = queryRecords(startDate, endDate, agentId, bizModuleId);
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(6);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        List<Map<String, Object>> points = new ArrayList<>();

        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            LocalDate current = day;
            long tokens = records.stream()
                    .filter(r -> r.getCreateTime() != null && r.getCreateTime().toLocalDate().equals(current))
                    .mapToLong(this::totalTokens)
                    .sum();
            long calls = records.stream()
                    .filter(r -> r.getCreateTime() != null && r.getCreateTime().toLocalDate().equals(current))
                    .count();
            Map<String, Object> point = new HashMap<>();
            point.put("date", day.toString());
            point.put("calls", calls);
            point.put("tokens", tokens);
            point.put("cost", calculateCost(tokens));
            points.add(point);
        }
        return Map.of("records", points);
    }

    public Page<BillingBudget> listBudgets(int pageNum, int pageSize) {
        Page<BillingBudget> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<BillingBudget> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(BillingBudget::getCreateTime);
        return budgetMapper.selectPage(page, wrapper);
    }

    public boolean saveBudget(BillingBudget budget) {
        LocalDateTime now = LocalDateTime.now();
        budget.setCreateTime(now);
        budget.setUpdateTime(now);
        if (budget.getStatus() == null) budget.setStatus(1);
        if (budget.getAlertThreshold() == null) budget.setAlertThreshold(new BigDecimal("80"));
        return budgetMapper.insert(budget) > 0;
    }

    public boolean updateBudget(Long id, BillingBudget budget) {
        budget.setId(id);
        budget.setUpdateTime(LocalDateTime.now());
        return budgetMapper.updateById(budget) > 0;
    }

    public byte[] exportBill(LocalDate startDate, LocalDate endDate, Long agentId, Long bizModuleId) {
        List<MonCallRecord> records = queryRecords(startDate, endDate, agentId, bizModuleId);
        StringBuilder csv = new StringBuilder("traceId,agentId,bizModuleId,totalTokens,cost,success,createTime\n");
        for (MonCallRecord record : records) {
            long tokens = totalTokens(record);
            csv.append(nullToEmpty(record.getTraceId())).append(',')
                    .append(nullToEmpty(record.getAgentId())).append(',')
                    .append(nullToEmpty(record.getBizModuleId())).append(',')
                    .append(tokens).append(',')
                    .append(calculateCost(tokens)).append(',')
                    .append(nullToEmpty(record.getSuccess())).append(',')
                    .append(nullToEmpty(record.getCreateTime())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<MonCallRecord> queryRecords(LocalDate startDate, LocalDate endDate, Long agentId, Long bizModuleId) {
        LambdaQueryWrapper<MonCallRecord> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null) wrapper.ge(MonCallRecord::getCreateTime, startDate.atStartOfDay());
        if (endDate != null) wrapper.lt(MonCallRecord::getCreateTime, endDate.plusDays(1).atStartOfDay());
        if (agentId != null) wrapper.eq(MonCallRecord::getAgentId, agentId);
        if (bizModuleId != null) wrapper.eq(MonCallRecord::getBizModuleId, bizModuleId);
        wrapper.orderByDesc(MonCallRecord::getCreateTime);
        return callRecordMapper.selectList(wrapper);
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

    private Object nullToEmpty(Object value) {
        return value == null ? "" : value;
    }
}
