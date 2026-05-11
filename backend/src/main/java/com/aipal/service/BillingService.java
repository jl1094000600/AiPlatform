package com.aipal.service;

import com.aipal.entity.AutomationGenerationJob;
import com.aipal.entity.BillingBudget;
import com.aipal.entity.MonCallRecord;
import com.aipal.mapper.AutomationGenerationJobMapper;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillingService {

    private static final BigDecimal TOKEN_UNIT_COST = new BigDecimal("0.00001");
    private static final String SOURCE_AGENT = "AGENT";
    private static final String SOURCE_PIPELINE = "PIPELINE";

    private final MonCallRecordMapper callRecordMapper;
    private final AutomationGenerationJobMapper generationJobMapper;
    private final BillingBudgetMapper budgetMapper;

    public Map<String, Object> getUsage(LocalDate startDate, LocalDate endDate, Long agentId, Long bizModuleId,
                                        Long userId, String username, String source) {
        List<BillingEntry> entries = queryEntries(startDate, endDate, agentId, bizModuleId, userId, username, source);
        long calls = entries.size();
        long tokens = entries.stream().mapToLong(BillingEntry::tokens).sum();
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("totalCalls", calls);
        usage.put("totalTokens", tokens);
        usage.put("totalCost", calculateCost(tokens));
        usage.put("agentTokens", sumTokensBySource(entries, SOURCE_AGENT));
        usage.put("pipelineTokens", sumTokensBySource(entries, SOURCE_PIPELINE));
        usage.put("agentCost", calculateCost(sumTokensBySource(entries, SOURCE_AGENT)));
        usage.put("pipelineCost", calculateCost(sumTokensBySource(entries, SOURCE_PIPELINE)));
        usage.put("userSummaries", buildUserSummaries(entries));
        usage.put("records", entries.stream().map(BillingEntry::toMap).toList());
        return usage;
    }

    public Map<String, Object> getCostTrends(LocalDate startDate, LocalDate endDate, Long agentId, Long bizModuleId,
                                            Long userId, String username, String source) {
        List<BillingEntry> entries = queryEntries(startDate, endDate, agentId, bizModuleId, userId, username, source);
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(6);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        List<Map<String, Object>> points = new ArrayList<>();

        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            LocalDate current = day;
            List<BillingEntry> dayEntries = entries.stream()
                    .filter(entry -> entry.createTime() != null && entry.createTime().toLocalDate().equals(current))
                    .toList();
            long tokens = dayEntries.stream().mapToLong(BillingEntry::tokens).sum();
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", day.toString());
            point.put("calls", dayEntries.size());
            point.put("tokens", tokens);
            point.put("cost", calculateCost(tokens));
            point.put("agentTokens", sumTokensBySource(dayEntries, SOURCE_AGENT));
            point.put("pipelineTokens", sumTokensBySource(dayEntries, SOURCE_PIPELINE));
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

    public byte[] exportBill(LocalDate startDate, LocalDate endDate, Long agentId, Long bizModuleId,
                             Long userId, String username, String source) {
        List<BillingEntry> entries = queryEntries(startDate, endDate, agentId, bizModuleId, userId, username, source);
        StringBuilder csv = new StringBuilder("source,userId,username,traceId,agentId,bizModuleId,pipelineId,jobType,totalTokens,cost,status,createTime\n");
        for (BillingEntry entry : entries) {
            csv.append(nullToEmpty(entry.source())).append(',')
                    .append(nullToEmpty(entry.userId())).append(',')
                    .append(nullToEmpty(entry.username())).append(',')
                    .append(nullToEmpty(entry.traceId())).append(',')
                    .append(nullToEmpty(entry.agentId())).append(',')
                    .append(nullToEmpty(entry.bizModuleId())).append(',')
                    .append(nullToEmpty(entry.pipelineId())).append(',')
                    .append(nullToEmpty(entry.jobType())).append(',')
                    .append(entry.tokens()).append(',')
                    .append(calculateCost(entry.tokens())).append(',')
                    .append(nullToEmpty(entry.status())).append(',')
                    .append(nullToEmpty(entry.createTime())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<BillingEntry> queryEntries(LocalDate startDate, LocalDate endDate, Long agentId, Long bizModuleId,
                                            Long userId, String username, String source) {
        String normalizedSource = source == null ? "" : source.toUpperCase(Locale.ROOT);
        List<BillingEntry> entries = new ArrayList<>();
        if (!SOURCE_PIPELINE.equals(normalizedSource)) {
            entries.addAll(queryRecords(startDate, endDate, agentId, bizModuleId, userId, username)
                    .stream()
                    .map(this::fromCallRecord)
                    .toList());
        }
        if (agentId == null && bizModuleId == null && !SOURCE_AGENT.equals(normalizedSource)) {
            entries.addAll(queryGenerationJobs(startDate, endDate, userId, username)
                    .stream()
                    .filter(job -> totalTokens(job) > 0)
                    .map(this::fromGenerationJob)
                    .toList());
        }
        entries.sort(Comparator.comparing(BillingEntry::createTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return entries;
    }

    private List<MonCallRecord> queryRecords(LocalDate startDate, LocalDate endDate, Long agentId, Long bizModuleId,
                                             Long userId, String username) {
        LambdaQueryWrapper<MonCallRecord> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null) wrapper.ge(MonCallRecord::getCreateTime, startDate.atStartOfDay());
        if (endDate != null) wrapper.lt(MonCallRecord::getCreateTime, endDate.plusDays(1).atStartOfDay());
        if (agentId != null) wrapper.eq(MonCallRecord::getAgentId, agentId);
        if (bizModuleId != null) wrapper.eq(MonCallRecord::getBizModuleId, bizModuleId);
        if (userId != null) wrapper.eq(MonCallRecord::getUserId, userId);
        if (username != null && !username.isBlank()) wrapper.eq(MonCallRecord::getUsername, username);
        wrapper.orderByDesc(MonCallRecord::getCreateTime);
        return callRecordMapper.selectList(wrapper);
    }

    private List<AutomationGenerationJob> queryGenerationJobs(LocalDate startDate, LocalDate endDate,
                                                              Long userId, String username) {
        LambdaQueryWrapper<AutomationGenerationJob> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null) wrapper.ge(AutomationGenerationJob::getCreateTime, startDate.atStartOfDay());
        if (endDate != null) wrapper.lt(AutomationGenerationJob::getCreateTime, endDate.plusDays(1).atStartOfDay());
        if (userId != null) wrapper.eq(AutomationGenerationJob::getRequestUserId, String.valueOf(userId));
        if (username != null && !username.isBlank()) wrapper.eq(AutomationGenerationJob::getRequestUserId, username);
        wrapper.orderByDesc(AutomationGenerationJob::getCreateTime);
        return generationJobMapper.selectList(wrapper);
    }

    private BillingEntry fromCallRecord(MonCallRecord record) {
        return new BillingEntry(
                SOURCE_AGENT,
                record.getUserId(),
                record.getUsername(),
                record.getTraceId(),
                record.getAgentId(),
                record.getBizModuleId(),
                null,
                null,
                totalTokens(record),
                record.getSuccess() != null && record.getSuccess() == 1 ? "SUCCESS" : "FAILED",
                record.getCreateTime()
        );
    }

    private BillingEntry fromGenerationJob(AutomationGenerationJob job) {
        Long userId = parseLong(job.getRequestUserId());
        String username = userId == null ? job.getRequestUserId() : null;
        return new BillingEntry(
                SOURCE_PIPELINE,
                userId,
                username,
                job.getTraceId(),
                null,
                null,
                job.getPipelineId(),
                job.getJobType(),
                totalTokens(job),
                job.getStatus(),
                job.getCreateTime()
        );
    }

    private List<Map<String, Object>> buildUserSummaries(List<BillingEntry> entries) {
        return entries.stream()
                .collect(Collectors.groupingBy(this::userGroupKey, LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> {
                    List<BillingEntry> group = entry.getValue();
                    long tokens = group.stream().mapToLong(BillingEntry::tokens).sum();
                    Map<String, Object> item = new LinkedHashMap<>();
                    BillingEntry first = group.get(0);
                    item.put("userKey", entry.getKey());
                    item.put("userId", first.userId());
                    item.put("username", first.username() == null ? "Unassigned" : first.username());
                    item.put("calls", group.size());
                    item.put("tokens", tokens);
                    item.put("cost", calculateCost(tokens));
                    item.put("agentTokens", sumTokensBySource(group, SOURCE_AGENT));
                    item.put("pipelineTokens", sumTokensBySource(group, SOURCE_PIPELINE));
                    return item;
                })
                .sorted((left, right) -> Long.compare(
                        ((Number) right.get("tokens")).longValue(),
                        ((Number) left.get("tokens")).longValue()))
                .toList();
    }

    private String userGroupKey(BillingEntry entry) {
        if (entry.userId() != null) return "id:" + entry.userId();
        if (entry.username() != null && !entry.username().isBlank()) return "name:" + entry.username();
        return "unknown";
    }

    private long sumTokensBySource(List<BillingEntry> entries, String source) {
        return entries.stream()
                .filter(entry -> Objects.equals(entry.source(), source))
                .mapToLong(BillingEntry::tokens)
                .sum();
    }

    private long totalTokens(MonCallRecord record) {
        if (record.getTotalTokens() != null) return record.getTotalTokens();
        long input = record.getInputTokens() == null ? 0 : record.getInputTokens();
        long output = record.getOutputTokens() == null ? 0 : record.getOutputTokens();
        return input + output;
    }

    private long totalTokens(AutomationGenerationJob job) {
        if (job.getTotalTokens() != null) return job.getTotalTokens();
        long input = job.getInputTokens() == null ? 0 : job.getInputTokens();
        long output = job.getOutputTokens() == null ? 0 : job.getOutputTokens();
        return input + output;
    }

    private BigDecimal calculateCost(long tokens) {
        return TOKEN_UNIT_COST.multiply(BigDecimal.valueOf(tokens)).setScale(4, RoundingMode.HALF_UP);
    }

    private Object nullToEmpty(Object value) {
        return value == null ? "" : value;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record BillingEntry(
            String source,
            Long userId,
            String username,
            String traceId,
            Long agentId,
            Long bizModuleId,
            Long pipelineId,
            String jobType,
            long tokens,
            String status,
            LocalDateTime createTime
    ) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("source", source);
            map.put("userId", userId);
            map.put("username", username);
            map.put("traceId", traceId);
            map.put("agentId", agentId);
            map.put("bizModuleId", bizModuleId);
            map.put("pipelineId", pipelineId);
            map.put("jobType", jobType);
            map.put("tokens", tokens);
            map.put("status", status);
            map.put("createTime", createTime);
            return map;
        }
    }
}
