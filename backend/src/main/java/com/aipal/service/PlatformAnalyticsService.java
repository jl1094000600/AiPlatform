package com.aipal.service;

import com.aipal.entity.AiModel;
import com.aipal.entity.AiOutputGovernanceRecord;
import com.aipal.entity.AutomationApproval;
import com.aipal.entity.AutomationCodeQualityIssue;
import com.aipal.entity.AutomationCodeQualityRun;
import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.AutomationStageRun;
import com.aipal.entity.BadCaseRecord;
import com.aipal.entity.BillingUsageDaily;
import com.aipal.entity.MonCallRecord;
import com.aipal.mapper.AiModelMapper;
import com.aipal.mapper.AiOutputGovernanceRecordMapper;
import com.aipal.mapper.AutomationApprovalMapper;
import com.aipal.mapper.AutomationCodeQualityIssueMapper;
import com.aipal.mapper.AutomationCodeQualityRunMapper;
import com.aipal.mapper.AutomationPipelineMapper;
import com.aipal.mapper.AutomationStageRunMapper;
import com.aipal.mapper.BadCaseRecordMapper;
import com.aipal.mapper.BillingUsageDailyMapper;
import com.aipal.mapper.MonCallRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformAnalyticsService {
    private static final int TOP_LIMIT = 10;

    private final BadCaseRecordMapper badCaseMapper;
    private final BillingUsageDailyMapper billingUsageMapper;
    private final MonCallRecordMapper callRecordMapper;
    private final AutomationPipelineMapper pipelineMapper;
    private final AutomationStageRunMapper stageRunMapper;
    private final AutomationApprovalMapper approvalMapper;
    private final AutomationCodeQualityRunMapper qualityRunMapper;
    private final AutomationCodeQualityIssueMapper qualityIssueMapper;
    private final AiOutputGovernanceRecordMapper governanceRecordMapper;
    private final AiModelMapper modelMapper;

    @Value("${aipal.analytics.default-token-unit-cost:0.00001}")
    private BigDecimal defaultTokenUnitCost;

    public Map<String, Object> getOverview(LocalDate startDate, LocalDate endDate) {
        DateRange range = range(startDate, endDate);
        List<MonCallRecord> calls = calls(range);
        List<BadCaseRecord> badcases = badcases(range);
        List<AutomationPipeline> pipelines = pipelines(range);
        List<AutomationStageRun> stages = stages(range);
        List<BillingUsageDaily> billing = billing(range);
        List<AiOutputGovernanceRecord> governance = governance(range);

        Map<Long, AiModel> models = modelsById();
        long successPipelines = pipelines.stream().filter(this::isPipelineSuccess).count();
        long failedPipelines = pipelines.stream().filter(this::isPipelineFailed).count();
        long totalTokens = calls.stream().mapToLong(this::totalTokens).sum();
        BigDecimal estimatedCost = costFromBillingOrTokens(billing, calls, models);
        double successRate = pipelines.isEmpty() ? 100 : percent(successPipelines, pipelines.size());
        double avgStageMs = stages.stream()
                .filter(stage -> stage.getDurationMs() != null)
                .mapToInt(AutomationStageRun::getDurationMs)
                .average()
                .orElse(0);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCalls", calls.size());
        summary.put("totalTokens", totalTokens);
        summary.put("estimatedCost", estimatedCost);
        summary.put("badcaseCount", badcases.size());
        summary.put("pipelineCount", pipelines.size());
        summary.put("pipelineSuccessRate", successRate);
        summary.put("pipelineFailedCount", failedPipelines);
        summary.put("avgStageDurationMs", round(avgStageMs));
        summary.put("highRiskOutputs", governance.stream().filter(this::isHighRisk).count());
        summary.put("dateRange", range.toMap());

        Map<String, Object> trends = new LinkedHashMap<>();
        trends.put("daily", dailyOverviewTrend(range, calls, badcases, pipelines, billing));

        Map<String, Object> breakdowns = new LinkedHashMap<>();
        breakdowns.put("pipelineStatus", mapEntries(countBy(pipelines, AutomationPipeline::getStatus)));
        breakdowns.put("badcaseSeverity", mapEntries(countBy(badcases, BadCaseRecord::getSeverity)));
        breakdowns.put("governanceRiskLevel", mapEntries(countBy(governance, AiOutputGovernanceRecord::getRiskLevel)));

        Map<String, Object> topItems = new LinkedHashMap<>();
        topItems.put("highTokenCalls", topCalls(calls, models));
        topItems.put("topBadcaseProjects", mapEntries(countBy(badcases, BadCaseRecord::getProjectName)));
        topItems.put("topFailedStages", topStagesByFailure(stages));

        return analyticsResponse(summary, trends, breakdowns, topItems, overviewRecommendations(summary));
    }

    public Map<String, Object> getBadcases(LocalDate startDate, LocalDate endDate) {
        DateRange range = range(startDate, endDate);
        List<BadCaseRecord> records = badcases(range);
        Map<String, Long> byStage = countBy(records, BadCaseRecord::getStage);
        Map<String, Long> byType = countBy(records, BadCaseRecord::getBadcaseType);
        Map<String, Long> bySeverity = countBy(records, BadCaseRecord::getSeverity);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", range.toMap());
        summary.put("total", records.size());
        summary.put("p0", bySeverity.getOrDefault("P0", 0L));
        summary.put("p1", bySeverity.getOrDefault("P1", 0L));
        summary.put("manual", countBy(records, BadCaseRecord::getSourceType).getOrDefault("MANUAL", 0L));

        Map<String, Object> trends = new LinkedHashMap<>();
        trends.put("daily", dailyCountTrend(range, records, BadCaseRecord::getCreateTime, "badcases"));

        Map<String, Object> breakdowns = new LinkedHashMap<>();
        breakdowns.put("byStage", mapEntries(byStage));
        breakdowns.put("byType", mapEntries(byType));
        breakdowns.put("bySeverity", mapEntries(bySeverity));
        breakdowns.put("bySource", mapEntries(countBy(records, BadCaseRecord::getSourceType)));

        Map<String, Object> topItems = new LinkedHashMap<>();
        topItems.put("topReasons", topText(records, BadCaseRecord::getFailureReason));
        topItems.put("topProjects", mapEntries(countBy(records, BadCaseRecord::getProjectName)));
        topItems.put("recent", records.stream()
                .sorted(Comparator.comparing(BadCaseRecord::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(TOP_LIMIT)
                .map(this::badcaseView)
                .toList());

        return analyticsResponse(summary, trends, breakdowns, topItems, badcaseRecommendations(byStage, byType, bySeverity));
    }

    public Map<String, Object> getTokenCost(LocalDate startDate, LocalDate endDate) {
        DateRange range = range(startDate, endDate);
        List<MonCallRecord> calls = calls(range);
        List<BillingUsageDaily> billing = billing(range);
        Map<Long, AiModel> models = modelsById();
        long totalTokens = calls.stream().mapToLong(this::totalTokens).sum();
        BigDecimal tokenEstimatedCost = calls.stream()
                .map(call -> estimateCallCost(call, models))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal billingCost = billing.stream()
                .map(item -> item.getTotalCost() == null ? BigDecimal.ZERO : item.getTotalCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", range.toMap());
        summary.put("totalCalls", calls.size());
        summary.put("totalTokens", totalTokens);
        summary.put("inputTokens", calls.stream().mapToLong(call -> safe(call.getInputTokens())).sum());
        summary.put("outputTokens", calls.stream().mapToLong(call -> safe(call.getOutputTokens())).sum());
        summary.put("estimatedCost", billingCost.compareTo(BigDecimal.ZERO) > 0 ? billingCost : tokenEstimatedCost);
        summary.put("tokenEstimatedCost", tokenEstimatedCost);
        summary.put("billingCost", billingCost);

        Map<String, Object> trends = new LinkedHashMap<>();
        trends.put("daily", dailyTokenTrend(range, calls, billing, models));

        Map<String, Object> breakdowns = new LinkedHashMap<>();
        breakdowns.put("byModel", tokenByModel(calls, models));
        breakdowns.put("byUser", topTokenUsers(calls));

        Map<String, Object> topItems = new LinkedHashMap<>();
        topItems.put("topCalls", topCalls(calls, models));

        return analyticsResponse(summary, trends, breakdowns, topItems, tokenRecommendations(calls, totalTokens, tokenEstimatedCost));
    }

    public Map<String, Object> getPipelines(LocalDate startDate, LocalDate endDate) {
        DateRange range = range(startDate, endDate);
        List<AutomationPipeline> pipelines = pipelines(range);
        List<AutomationStageRun> stages = stages(range);
        List<AutomationApproval> approvals = approvals(range);

        Map<String, List<AutomationStageRun>> stagesByKey = stages.stream()
                .collect(Collectors.groupingBy(stage -> value(stage.getStageKey(), "UNKNOWN")));
        List<Map<String, Object>> stageStats = stagesByKey.entrySet().stream()
                .map(entry -> stageStat(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("stage"))))
                .toList();

        long success = pipelines.stream().filter(this::isPipelineSuccess).count();
        long failed = pipelines.stream().filter(this::isPipelineFailed).count();
        long blocked = pipelines.stream().filter(this::isPipelineBlocked).count();
        long rejectedApprovals = approvals.stream().filter(item -> "REJECTED".equalsIgnoreCase(item.getStatus())).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", range.toMap());
        summary.put("total", pipelines.size());
        summary.put("success", success);
        summary.put("failed", failed);
        summary.put("blocked", blocked);
        summary.put("successRate", pipelines.isEmpty() ? 100 : percent(success, pipelines.size()));
        summary.put("approvalRejectionRate", approvals.isEmpty() ? 0 : percent(rejectedApprovals, approvals.size()));

        Map<String, Object> trends = new LinkedHashMap<>();
        trends.put("daily", dailyPipelineTrend(range, pipelines));

        Map<String, Object> breakdowns = new LinkedHashMap<>();
        breakdowns.put("byStatus", mapEntries(countBy(pipelines, AutomationPipeline::getStatus)));
        breakdowns.put("stageStats", stageStats);

        Map<String, Object> topItems = new LinkedHashMap<>();
        topItems.put("topBlockedStages", topStagesByFailure(stages));
        topItems.put("recentFailed", pipelines.stream()
                .filter(item -> isPipelineFailed(item) || isPipelineBlocked(item))
                .sorted(Comparator.comparing(AutomationPipeline::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(TOP_LIMIT)
                .map(this::pipelineView)
                .toList());

        return analyticsResponse(summary, trends, breakdowns, topItems, pipelineRecommendations(stageStats, blocked, failed));
    }

    public Map<String, Object> getModelValue(LocalDate startDate, LocalDate endDate) {
        DateRange range = range(startDate, endDate);
        List<MonCallRecord> calls = calls(range);
        List<AutomationCodeQualityRun> qualityRuns = qualityRuns(range);
        List<AutomationCodeQualityIssue> issues = qualityIssues(range);
        List<AiOutputGovernanceRecord> governance = governance(range);
        Map<Long, AiModel> models = modelsById();
        Map<String, AiModel> modelsByCode = models.values().stream()
                .filter(model -> model.getModelCode() != null)
                .collect(Collectors.toMap(AiModel::getModelCode, Function.identity(), (left, right) -> left));

        Map<String, ModelValueAccumulator> acc = new HashMap<>();
        for (MonCallRecord call : calls) {
            AiModel model = models.get(call.getModelId());
            String code = model == null ? "UNKNOWN" : value(model.getModelCode(), "UNKNOWN");
            acc.computeIfAbsent(code, ModelValueAccumulator::new).addCall(call, estimateCallCost(call, models));
        }
        for (AutomationCodeQualityRun run : qualityRuns) {
            String code = value(run.getModelCode(), "UNKNOWN");
            acc.computeIfAbsent(code, ModelValueAccumulator::new).addQualityRun(run);
        }
        for (AiOutputGovernanceRecord record : governance) {
            String code = value(record.getModelCode(), "UNKNOWN");
            acc.computeIfAbsent(code, ModelValueAccumulator::new).addGovernance(record);
        }
        for (AutomationCodeQualityIssue issue : issues) {
            qualityRuns.stream()
                    .filter(run -> run.getId() != null && run.getId().equals(issue.getRunId()))
                    .findFirst()
                    .ifPresent(run -> acc.computeIfAbsent(value(run.getModelCode(), "UNKNOWN"), ModelValueAccumulator::new).qualityIssues++);
        }

        List<Map<String, Object>> modelsView = acc.values().stream()
                .map(item -> item.toMap(modelsByCode.get(item.modelCode)))
                .sorted(Comparator.comparing(item -> ((Number) item.get("riskScore")).doubleValue(), Comparator.reverseOrder()))
                .toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", range.toMap());
        summary.put("modelCount", modelsView.size());
        summary.put("totalCalls", calls.size());
        summary.put("totalCost", modelsView.stream()
                .map(item -> (BigDecimal) item.get("cost"))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP));
        summary.put("qualityIssueCount", issues.size());

        Map<String, Object> trends = new LinkedHashMap<>();
        trends.put("daily", dailyModelTrend(range, calls, models));

        Map<String, Object> breakdowns = new LinkedHashMap<>();
        breakdowns.put("models", modelsView);

        Map<String, Object> topItems = new LinkedHashMap<>();
        topItems.put("highRiskModels", modelsView.stream()
                .sorted(Comparator.comparing(item -> ((Number) item.get("riskScore")).doubleValue(), Comparator.reverseOrder()))
                .limit(TOP_LIMIT)
                .toList());

        return analyticsResponse(summary, trends, breakdowns, topItems, modelRecommendations(modelsView));
    }

    private List<MonCallRecord> calls(DateRange range) {
        return callRecordMapper.selectList(new LambdaQueryWrapper<MonCallRecord>()
                .ge(MonCallRecord::getCreateTime, range.startDateTime())
                .lt(MonCallRecord::getCreateTime, range.endExclusive()));
    }

    private List<BadCaseRecord> badcases(DateRange range) {
        return badCaseMapper.selectList(new LambdaQueryWrapper<BadCaseRecord>()
                .ge(BadCaseRecord::getCreateTime, range.startDateTime())
                .lt(BadCaseRecord::getCreateTime, range.endExclusive()));
    }

    private List<BillingUsageDaily> billing(DateRange range) {
        return billingUsageMapper.selectList(new LambdaQueryWrapper<BillingUsageDaily>()
                .ge(BillingUsageDaily::getUsageDate, range.start())
                .le(BillingUsageDaily::getUsageDate, range.end()));
    }

    private List<AutomationPipeline> pipelines(DateRange range) {
        return pipelineMapper.selectList(new LambdaQueryWrapper<AutomationPipeline>()
                .ge(AutomationPipeline::getCreateTime, range.startDateTime())
                .lt(AutomationPipeline::getCreateTime, range.endExclusive()));
    }

    private List<AutomationStageRun> stages(DateRange range) {
        return stageRunMapper.selectList(new LambdaQueryWrapper<AutomationStageRun>()
                .ge(AutomationStageRun::getCreateTime, range.startDateTime())
                .lt(AutomationStageRun::getCreateTime, range.endExclusive()));
    }

    private List<AutomationApproval> approvals(DateRange range) {
        return approvalMapper.selectList(new LambdaQueryWrapper<AutomationApproval>()
                .ge(AutomationApproval::getCreateTime, range.startDateTime())
                .lt(AutomationApproval::getCreateTime, range.endExclusive()));
    }

    private List<AutomationCodeQualityRun> qualityRuns(DateRange range) {
        return qualityRunMapper.selectList(new LambdaQueryWrapper<AutomationCodeQualityRun>()
                .ge(AutomationCodeQualityRun::getCreateTime, range.startDateTime())
                .lt(AutomationCodeQualityRun::getCreateTime, range.endExclusive()));
    }

    private List<AutomationCodeQualityIssue> qualityIssues(DateRange range) {
        return qualityIssueMapper.selectList(new LambdaQueryWrapper<AutomationCodeQualityIssue>()
                .ge(AutomationCodeQualityIssue::getCreateTime, range.startDateTime())
                .lt(AutomationCodeQualityIssue::getCreateTime, range.endExclusive()));
    }

    private List<AiOutputGovernanceRecord> governance(DateRange range) {
        return governanceRecordMapper.selectList(new LambdaQueryWrapper<AiOutputGovernanceRecord>()
                .ge(AiOutputGovernanceRecord::getCreateTime, range.startDateTime())
                .lt(AiOutputGovernanceRecord::getCreateTime, range.endExclusive()));
    }

    private Map<Long, AiModel> modelsById() {
        return modelMapper.selectList(null).stream()
                .filter(model -> model.getId() != null)
                .collect(Collectors.toMap(AiModel::getId, Function.identity(), (left, right) -> left));
    }

    private DateRange range(LocalDate startDate, LocalDate endDate) {
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        LocalDate start = startDate == null ? end.minusDays(6) : startDate;
        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        return new DateRange(start, end);
    }

    private List<Map<String, Object>> dailyOverviewTrend(DateRange range, List<MonCallRecord> calls,
                                                         List<BadCaseRecord> badcases,
                                                         List<AutomationPipeline> pipelines,
                                                         List<BillingUsageDaily> billing) {
        Map<LocalDate, List<MonCallRecord>> callsByDate = calls.stream()
                .collect(Collectors.groupingBy(item -> dateOf(item.getCreateTime())));
        Map<LocalDate, Long> badcasesByDate = badcases.stream()
                .collect(Collectors.groupingBy(item -> dateOf(item.getCreateTime()), Collectors.counting()));
        Map<LocalDate, Long> pipelinesByDate = pipelines.stream()
                .collect(Collectors.groupingBy(item -> dateOf(item.getCreateTime()), Collectors.counting()));
        Map<LocalDate, BigDecimal> costByDate = billing.stream()
                .collect(Collectors.groupingBy(BillingUsageDaily::getUsageDate,
                        Collectors.mapping(item -> item.getTotalCost() == null ? BigDecimal.ZERO : item.getTotalCost(),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate day = range.start(); !day.isAfter(range.end()); day = day.plusDays(1)) {
            List<MonCallRecord> dayCalls = callsByDate.getOrDefault(day, List.of());
            result.add(Map.of(
                    "date", day.toString(),
                    "calls", dayCalls.size(),
                    "tokens", dayCalls.stream().mapToLong(this::totalTokens).sum(),
                    "cost", costByDate.getOrDefault(day, BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP),
                    "badcases", badcasesByDate.getOrDefault(day, 0L),
                    "pipelines", pipelinesByDate.getOrDefault(day, 0L)
            ));
        }
        return result;
    }

    private List<Map<String, Object>> dailyTokenTrend(DateRange range, List<MonCallRecord> calls,
                                                      List<BillingUsageDaily> billing, Map<Long, AiModel> models) {
        Map<LocalDate, List<MonCallRecord>> callsByDate = calls.stream()
                .collect(Collectors.groupingBy(item -> dateOf(item.getCreateTime())));
        Map<LocalDate, BigDecimal> billingByDate = billing.stream()
                .collect(Collectors.groupingBy(BillingUsageDaily::getUsageDate,
                        Collectors.mapping(item -> item.getTotalCost() == null ? BigDecimal.ZERO : item.getTotalCost(),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate day = range.start(); !day.isAfter(range.end()); day = day.plusDays(1)) {
            List<MonCallRecord> dayCalls = callsByDate.getOrDefault(day, List.of());
            BigDecimal estimated = dayCalls.stream()
                    .map(call -> estimateCallCost(call, models))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(4, RoundingMode.HALF_UP);
            result.add(Map.of(
                    "date", day.toString(),
                    "calls", dayCalls.size(),
                    "tokens", dayCalls.stream().mapToLong(this::totalTokens).sum(),
                    "inputTokens", dayCalls.stream().mapToLong(call -> safe(call.getInputTokens())).sum(),
                    "outputTokens", dayCalls.stream().mapToLong(call -> safe(call.getOutputTokens())).sum(),
                    "cost", billingByDate.getOrDefault(day, estimated).setScale(4, RoundingMode.HALF_UP)
            ));
        }
        return result;
    }

    private <T> List<Map<String, Object>> dailyCountTrend(DateRange range, List<T> records,
                                                         Function<T, LocalDateTime> timeGetter,
                                                         String countKey) {
        Map<LocalDate, Long> countByDate = records.stream()
                .collect(Collectors.groupingBy(item -> dateOf(timeGetter.apply(item)), Collectors.counting()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate day = range.start(); !day.isAfter(range.end()); day = day.plusDays(1)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", day.toString());
            item.put(countKey, countByDate.getOrDefault(day, 0L));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> dailyPipelineTrend(DateRange range, List<AutomationPipeline> pipelines) {
        Map<LocalDate, List<AutomationPipeline>> pipelinesByDate = pipelines.stream()
                .collect(Collectors.groupingBy(item -> dateOf(item.getCreateTime())));
        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate day = range.start(); !day.isAfter(range.end()); day = day.plusDays(1)) {
            List<AutomationPipeline> dayPipelines = pipelinesByDate.getOrDefault(day, List.of());
            long success = dayPipelines.stream().filter(this::isPipelineSuccess).count();
            long failed = dayPipelines.stream().filter(this::isPipelineFailed).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", day.toString());
            item.put("pipelines", dayPipelines.size());
            item.put("success", success);
            item.put("failed", failed);
            item.put("successRate", dayPipelines.isEmpty() ? 100 : percent(success, dayPipelines.size()));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> dailyModelTrend(DateRange range, List<MonCallRecord> calls,
                                                     Map<Long, AiModel> models) {
        Map<LocalDate, List<MonCallRecord>> callsByDate = calls.stream()
                .collect(Collectors.groupingBy(item -> dateOf(item.getCreateTime())));
        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate day = range.start(); !day.isAfter(range.end()); day = day.plusDays(1)) {
            List<MonCallRecord> dayCalls = callsByDate.getOrDefault(day, List.of());
            BigDecimal cost = dayCalls.stream()
                    .map(call -> estimateCallCost(call, models))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(4, RoundingMode.HALF_UP);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", day.toString());
            item.put("calls", dayCalls.size());
            item.put("tokens", dayCalls.stream().mapToLong(this::totalTokens).sum());
            item.put("cost", cost);
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> tokenByModel(List<MonCallRecord> calls, Map<Long, AiModel> models) {
        Map<String, List<MonCallRecord>> grouped = calls.stream()
                .collect(Collectors.groupingBy(call -> {
                    AiModel model = models.get(call.getModelId());
                    return model == null ? "UNKNOWN" : value(model.getModelCode(), "UNKNOWN");
                }));
        return grouped.entrySet().stream()
                .map(entry -> {
                    String modelCode = entry.getKey();
                    List<MonCallRecord> items = entry.getValue();
                    BigDecimal cost = items.stream()
                            .map(call -> estimateCallCost(call, models))
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(4, RoundingMode.HALF_UP);
                    return Map.<String, Object>of(
                            "modelCode", modelCode,
                            "calls", items.size(),
                            "tokens", items.stream().mapToLong(this::totalTokens).sum(),
                            "cost", cost,
                            "successRate", items.isEmpty() ? 100 : percent(items.stream().filter(this::isSuccessCall).count(), items.size())
                    );
                })
                .sorted(Comparator.comparing(item -> ((Number) item.get("tokens")).longValue(), Comparator.reverseOrder()))
                .toList();
    }

    private List<Map<String, Object>> topTokenUsers(List<MonCallRecord> calls) {
        Map<String, Long> tokensByUser = calls.stream()
                .collect(Collectors.groupingBy(call -> value(call.getUsername(), "UNKNOWN"),
                        Collectors.summingLong(this::totalTokens)));
        return tokensByUser.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_LIMIT)
                .map(entry -> Map.<String, Object>of("name", entry.getKey(), "tokens", entry.getValue()))
                .toList();
    }

    private List<Map<String, Object>> topCalls(List<MonCallRecord> calls, Map<Long, AiModel> models) {
        return calls.stream()
                .sorted(Comparator.comparingLong(this::totalTokens).reversed())
                .limit(TOP_LIMIT)
                .map(call -> {
                    AiModel model = models.get(call.getModelId());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", call.getId());
                    item.put("traceId", value(call.getTraceId(), "-"));
                    item.put("username", value(call.getUsername(), "-"));
                    item.put("modelCode", model == null ? "UNKNOWN" : value(model.getModelCode(), "UNKNOWN"));
                    item.put("tokens", totalTokens(call));
                    item.put("durationMs", safe(call.getDurationMs()));
                    item.put("success", isSuccessCall(call));
                    return item;
                })
                .toList();
    }

    private Map<String, Object> stageStat(String stageKey, List<AutomationStageRun> stages) {
        long success = stages.stream().filter(stage -> "SUCCESS".equalsIgnoreCase(stage.getStatus())).count();
        long failed = stages.stream().filter(stage -> isFailedStage(stage.getStatus())).count();
        double avgDuration = stages.stream()
                .filter(stage -> stage.getDurationMs() != null)
                .mapToInt(AutomationStageRun::getDurationMs)
                .average()
                .orElse(0);
        return Map.of(
                "stage", stageKey,
                "total", stages.size(),
                "success", success,
                "failed", failed,
                "passRate", stages.isEmpty() ? 100 : percent(success, stages.size()),
                "avgDurationMs", round(avgDuration)
        );
    }

    private List<Map<String, Object>> topStagesByFailure(List<AutomationStageRun> stages) {
        return stages.stream()
                .filter(stage -> isFailedStage(stage.getStatus()))
                .collect(Collectors.groupingBy(stage -> value(stage.getStageKey(), "UNKNOWN"), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_LIMIT)
                .map(entry -> Map.<String, Object>of("stage", entry.getKey(), "count", entry.getValue()))
                .toList();
    }

    private Map<String, Object> badcaseView(BadCaseRecord record) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", record.getId());
        item.put("caseCode", value(record.getCaseCode(), "-"));
        item.put("stage", value(record.getStage(), "-"));
        item.put("type", value(record.getBadcaseType(), "-"));
        item.put("severity", value(record.getSeverity(), "-"));
        item.put("projectName", value(record.getProjectName(), "-"));
        item.put("reason", value(record.getFailureReason(), "-"));
        item.put("createTime", record.getCreateTime() == null ? "" : record.getCreateTime().toString());
        return item;
    }

    private Map<String, Object> pipelineView(AutomationPipeline pipeline) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", pipeline.getId());
        item.put("pipelineCode", value(pipeline.getPipelineCode(), "-"));
        item.put("projectName", value(pipeline.getProjectName(), "-"));
        item.put("requirementTitle", value(pipeline.getRequirementTitle(), "-"));
        item.put("status", value(pipeline.getStatus(), "-"));
        item.put("currentStage", value(pipeline.getCurrentStage(), "-"));
        item.put("createTime", pipeline.getCreateTime() == null ? "" : pipeline.getCreateTime().toString());
        return item;
    }

    private List<Map<String, Object>> mapEntries(Map<String, Long> source) {
        return source.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_LIMIT)
                .map(entry -> Map.<String, Object>of("name", entry.getKey(), "value", entry.getValue()))
                .toList();
    }

    private <T> Map<String, Long> countBy(List<T> records, Function<T, String> classifier) {
        return records.stream()
                .collect(Collectors.groupingBy(item -> value(classifier.apply(item), "UNKNOWN"), Collectors.counting()));
    }

    private List<Map<String, Object>> topText(List<BadCaseRecord> records, Function<BadCaseRecord, String> extractor) {
        return countBy(records, extractor).entrySet().stream()
                .filter(entry -> !"UNKNOWN".equals(entry.getKey()))
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_LIMIT)
                .map(entry -> Map.<String, Object>of("reason", entry.getKey(), "count", entry.getValue()))
                .toList();
    }

    private Map<String, Object> analyticsResponse(Map<String, Object> summary,
                                                  Map<String, Object> trends,
                                                  Map<String, Object> breakdowns,
                                                  Map<String, Object> topItems,
                                                  List<String> recommendations) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("summary", summary);
        response.put("trends", trends);
        response.put("breakdowns", breakdowns);
        response.put("topItems", topItems);
        response.put("recommendations", recommendations == null ? List.of() : recommendations);
        return response;
    }

    private BigDecimal costFromBillingOrTokens(List<BillingUsageDaily> billing, List<MonCallRecord> calls,
                                               Map<Long, AiModel> models) {
        BigDecimal billingCost = billing.stream()
                .map(item -> item.getTotalCost() == null ? BigDecimal.ZERO : item.getTotalCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (billingCost.compareTo(BigDecimal.ZERO) > 0) {
            return billingCost.setScale(4, RoundingMode.HALF_UP);
        }
        return calls.stream()
                .map(call -> estimateCallCost(call, models))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal estimateCallCost(MonCallRecord call, Map<Long, AiModel> models) {
        long tokens = totalTokens(call);
        AiModel model = models.get(call.getModelId());
        BigDecimal price = model == null ? null : model.getPricePer1kToken();
        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            return price.multiply(BigDecimal.valueOf(tokens))
                    .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                    .setScale(4, RoundingMode.HALF_UP);
        }
        return defaultTokenUnitCost.multiply(BigDecimal.valueOf(tokens)).setScale(4, RoundingMode.HALF_UP);
    }

    private long totalTokens(MonCallRecord record) {
        if (record.getTotalTokens() != null) return record.getTotalTokens();
        return safe(record.getInputTokens()) + safe(record.getOutputTokens());
    }

    private long safe(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private LocalDate dateOf(LocalDateTime value) {
        return value == null ? LocalDate.now() : value.toLocalDate();
    }

    private double percent(long part, long total) {
        return total == 0 ? 0 : round((double) part * 100 / total);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private boolean isSuccessCall(MonCallRecord record) {
        return record.getSuccess() != null && record.getSuccess() == 1;
    }

    private boolean isPipelineSuccess(AutomationPipeline pipeline) {
        return "SUCCESS".equalsIgnoreCase(pipeline.getStatus()) || "COMPLETED".equalsIgnoreCase(pipeline.getStatus());
    }

    private boolean isPipelineFailed(AutomationPipeline pipeline) {
        return "FAILED".equalsIgnoreCase(pipeline.getStatus()) || "ERROR".equalsIgnoreCase(pipeline.getStatus());
    }

    private boolean isPipelineBlocked(AutomationPipeline pipeline) {
        return "BLOCKED".equalsIgnoreCase(pipeline.getStatus()) || "REJECTED".equalsIgnoreCase(pipeline.getStatus());
    }

    private boolean isFailedStage(String status) {
        return "FAILED".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status)
                || "BLOCKED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status);
    }

    private boolean isHighRisk(AiOutputGovernanceRecord record) {
        return "HIGH".equalsIgnoreCase(record.getRiskLevel())
                || "CRITICAL".equalsIgnoreCase(record.getRiskLevel())
                || (record.getRiskScore() != null && record.getRiskScore() >= 80);
    }

    private List<String> overviewRecommendations(Map<String, Object> summary) {
        List<String> recommendations = new ArrayList<>();
        if (((Number) summary.get("badcaseCount")).longValue() > 0) {
            recommendations.add("Review high-frequency badcase stages and tighten PRD/code generation constraints.");
        }
        if (((Number) summary.get("pipelineSuccessRate")).doubleValue() < 80) {
            recommendations.add("Pipeline success rate is low. Check blocked stages and approval rejection reasons.");
        }
        if (((Number) summary.get("highRiskOutputs")).longValue() > 0) {
            recommendations.add("High-risk AI outputs exist. Add governance rules based on the recorded cases.");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("Platform metrics are stable. Continue monitoring cost and quality trends.");
        }
        return recommendations;
    }

    private List<String> badcaseRecommendations(Map<String, Long> byStage, Map<String, Long> byType,
                                                Map<String, Long> bySeverity) {
        List<String> recommendations = new ArrayList<>();
        byStage.entrySet().stream().max(Map.Entry.comparingByValue())
                .ifPresent(entry -> recommendations.add("Badcases are most concentrated in stage "
                        + entry.getKey() + ". Prioritize its templates and validation rules."));
        byType.entrySet().stream().max(Map.Entry.comparingByValue())
                .ifPresent(entry -> recommendations.add("The most frequent badcase type is "
                        + entry.getKey() + ". Add targeted review rules."));
        if (bySeverity.getOrDefault("P0", 0L) + bySeverity.getOrDefault("P1", 0L) > 0) {
            recommendations.add("P0/P1 badcases exist. Route them to manual review and blocking policies.");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("No obvious badcase cluster was found. Keep the current governance strategy.");
        }
        return recommendations;
    }

    private List<String> tokenRecommendations(List<MonCallRecord> calls, long totalTokens, BigDecimal cost) {
        List<String> recommendations = new ArrayList<>();
        if (totalTokens > 100_000) {
            recommendations.add("Token usage is high. Review top calls and compress context where possible.");
        }
        if (cost.compareTo(new BigDecimal("10")) > 0) {
            recommendations.add("Estimated cost is high. Compare cost and quality by model.");
        }
        double failedRate = calls.isEmpty() ? 0 : percent(calls.stream().filter(call -> !isSuccessCall(call)).count(), calls.size());
        if (failedRate > 5) {
            recommendations.add("Failed call rate is high. Prioritize retry strategy and error prompt handling.");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("Token usage and cost are within the expected range.");
        }
        return recommendations;
    }

    private List<String> pipelineRecommendations(List<Map<String, Object>> stageStats, long blocked, long failed) {
        List<String> recommendations = new ArrayList<>();
        stageStats.stream()
                .min(Comparator.comparing(item -> ((Number) item.get("passRate")).doubleValue()))
                .ifPresent(stage -> recommendations.add("The lowest pass-rate stage is "
                        + stage.get("stage") + ". Prioritize this stage for optimization."));
        if (blocked + failed > 0) {
            recommendations.add("Failed or blocked pipelines exist. Review failed stages and approval comments.");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("Pipelines are stable. Continue tracking stage duration.");
        }
        return recommendations;
    }

    private List<String> modelRecommendations(List<Map<String, Object>> models) {
        List<String> recommendations = new ArrayList<>();
        models.stream()
                .filter(item -> ((Number) item.get("riskScore")).doubleValue() > 0)
                .findFirst()
                .ifPresent(item -> recommendations.add("Model " + item.get("modelCode")
                        + " has higher risk. Review quality issues, governance records, and call cost."));
        models.stream()
                .max(Comparator.comparing(item -> ((BigDecimal) item.get("cost"))))
                .ifPresent(item -> recommendations.add("The highest-cost model is " + item.get("modelCode")
                        + ". Evaluate context compression or model switching."));
        if (recommendations.isEmpty()) {
            recommendations.add("No obvious low-value model was found.");
        }
        return recommendations;
    }

    private record DateRange(LocalDate start, LocalDate end) {
        private LocalDateTime startDateTime() {
            return start.atStartOfDay();
        }

        private LocalDateTime endExclusive() {
            return end.plusDays(1).atStartOfDay();
        }

        private Map<String, Object> toMap() {
            return Map.of("startDate", start.toString(), "endDate", end.toString());
        }
    }

    private final class ModelValueAccumulator {
        private final String modelCode;
        private long calls;
        private long tokens;
        private BigDecimal cost = BigDecimal.ZERO;
        private long qualityRuns;
        private long failedQualityRuns;
        private long qualityIssues;
        private long highRiskOutputs;

        private ModelValueAccumulator(String modelCode) {
            this.modelCode = modelCode;
        }

        private void addCall(MonCallRecord call, BigDecimal callCost) {
            calls++;
            tokens += totalTokens(call);
            cost = cost.add(callCost == null ? BigDecimal.ZERO : callCost);
        }

        private void addQualityRun(AutomationCodeQualityRun run) {
            qualityRuns++;
            if (run.getPassed() != null && run.getPassed() == 0) {
                failedQualityRuns++;
            }
        }

        private void addGovernance(AiOutputGovernanceRecord record) {
            if (isHighRisk(record)) {
                highRiskOutputs++;
            }
        }

        private Map<String, Object> toMap(AiModel model) {
            double qualityFailRate = qualityRuns == 0 ? 0 : percent(failedQualityRuns, qualityRuns);
            double riskScore = qualityFailRate + qualityIssues + highRiskOutputs * 5;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("modelCode", modelCode);
            item.put("modelName", model == null ? modelCode : value(model.getModelName(), modelCode));
            item.put("provider", model == null ? "-" : value(model.getProvider(), "-"));
            item.put("calls", calls);
            item.put("tokens", tokens);
            item.put("cost", cost.setScale(4, RoundingMode.HALF_UP));
            item.put("qualityRuns", qualityRuns);
            item.put("qualityFailRate", qualityFailRate);
            item.put("qualityIssues", qualityIssues);
            item.put("highRiskOutputs", highRiskOutputs);
            item.put("riskScore", round(riskScore));
            return item;
        }
    }
}
