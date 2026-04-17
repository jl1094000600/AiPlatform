package com.aipal.agent.marketing.controller;

import com.aipal.agent.marketing.service.MarketingAgentService;
import com.aipal.agent.marketing.service.tools.SalesQueryTool;
import com.aipal.agent.marketing.service.tools.TrendAnalysisTool;
import com.aipal.agent.marketing.service.tools.StatisticsTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/agent/marketing")
@RequiredArgsConstructor
public class MarketingAgentController {

    private final MarketingAgentService marketingAgentService;
    private final SalesQueryTool salesQueryTool;
    private final TrendAnalysisTool trendAnalysisTool;
    private final StatisticsTool statisticsTool;

    @PostMapping("/tools/sales-data-query")
    public ResponseEntity<Map<String, Object>> querySalesData(@RequestBody Map<String, Object> request) {
        log.info("Received sales-data-query request: {}", request);

        String timeType = (String) request.getOrDefault("timeType", "月");
        String region = (String) request.getOrDefault("region", "全部");
        String productCode = (String) request.getOrDefault("productCode", "全部");
        LocalDate startDate = parseDate(request.get("startDate"), LocalDate.now().minusMonths(1));
        LocalDate endDate = parseDate(request.get("endDate"), LocalDate.now());

        SalesQueryTool.SalesQueryRequest queryRequest = new SalesQueryTool.SalesQueryRequest(
                timeType, region, productCode, startDate, endDate);

        SalesQueryTool.SalesQueryResult result = salesQueryTool.querySalesData(queryRequest);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "toolName", "sales_data_query",
                "result", Map.of(
                        "queryParams", Map.of(
                                "timeType", result.timeType(),
                                "region", result.region(),
                                "productCode", result.productCode(),
                                "startDate", startDate.toString(),
                                "endDate", endDate.toString()
                        ),
                        "data", result.data(),
                        "totalAmount", result.totalAmount(),
                        "totalQuantity", result.totalQuantity()
                )
        ));
    }

    @PostMapping("/tools/trend-analysis")
    public ResponseEntity<Map<String, Object>> analyzeTrend(@RequestBody Map<String, Object> request) {
        log.info("Received trend-analysis request: {}", request);

        String dimension = (String) request.getOrDefault("dimension", "region");
        String compareType = (String) request.getOrDefault("compareType", "月");
        LocalDate startDate = parseDate(request.get("startDate"), LocalDate.now().minusMonths(1));
        LocalDate endDate = parseDate(request.get("endDate"), LocalDate.now());

        TrendAnalysisTool.TrendAnalysisRequest trendRequest = new TrendAnalysisTool.TrendAnalysisRequest(
                dimension, compareType, startDate, endDate);

        TrendAnalysisTool.TrendAnalysisResult result = trendAnalysisTool.analyzeTrend(trendRequest);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "toolName", "trend_analysis",
                "result", Map.of(
                        "dimension", result.dimension(),
                        "compareType", result.compareType(),
                        "currentPeriod", result.currentPeriod(),
                        "previousPeriod", result.previousPeriod(),
                        "comparisons", result.comparisons()
                )
        ));
    }

    @PostMapping("/tools/statistics-ranking")
    public ResponseEntity<Map<String, Object>> getStatisticsRanking(@RequestBody Map<String, Object> request) {
        log.info("Received statistics-ranking request: {}", request);

        LocalDate startDate = parseDate(request.get("startDate"), LocalDate.now().minusMonths(1));
        LocalDate endDate = parseDate(request.get("endDate"), LocalDate.now());
        @SuppressWarnings("unchecked")
        List<String> groupBy = (List<String>) request.getOrDefault("groupBy", List.of("region", "product"));

        StatisticsTool.StatisticsRequest statsRequest = new StatisticsTool.StatisticsRequest(
                startDate, endDate, groupBy);

        StatisticsTool.StatisticsResult result = statisticsTool.generateStatistics(statsRequest);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "toolName", "statistics_ranking",
                "result", Map.of(
                        "summary", result.summary(),
                        "rankings", result.rankings(),
                        "chartData", result.chartData()
                )
        ));
    }

    @PostMapping("/invoke")
    public ResponseEntity<Map<String, Object>> invoke(@RequestBody Map<String, Object> request) {
        String intent = (String) request.getOrDefault("intent", "unknown");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Map.of());

        log.info("Received invoke request: intent={}", intent);
        return ResponseEntity.ok(marketingAgentService.processIntent(intent, params));
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getInfo() {
        return ResponseEntity.ok(marketingAgentService.getAgentInfo());
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "agent", "marketing-agent",
                "version", "1.0.0",
                "capabilities", List.of("sales_data_query", "trend_analysis", "statistics_ranking")
        ));
    }

    private LocalDate parseDate(Object dateObj, LocalDate defaultValue) {
        if (dateObj == null) return defaultValue;
        if (dateObj instanceof LocalDate) return (LocalDate) dateObj;
        if (dateObj instanceof String) {
            try {
                return LocalDate.parse((String) dateObj);
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
