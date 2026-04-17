package com.aipal.agent.marketing.service;

import com.aipal.agent.marketing.service.tools.SalesQueryTool;
import com.aipal.agent.marketing.service.tools.StatisticsTool;
import com.aipal.agent.marketing.service.tools.TrendAnalysisTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingAgentService {

    private final SalesQueryTool salesQueryTool;
    private final TrendAnalysisTool trendAnalysisTool;
    private final StatisticsTool statisticsTool;

    private final Map<String, Object> agentMetadata = new ConcurrentHashMap<>();

    public void initialize() {
        agentMetadata.put("agentCode", "marketing-agent");
        agentMetadata.put("agentName", "市场营销Agent");
        agentMetadata.put("capabilities", List.of(
                "sales_query",
                "trend_analysis",
                "statistics",
                "chart_generation"
        ));
        agentMetadata.put("status", "online");
        log.info("Marketing Agent initialized with capabilities: {}", agentMetadata.get("capabilities"));
    }

    public Map<String, Object> processIntent(String intent, Map<String, Object> params) {
        log.info("Processing intent: {}", intent);

        return switch (intent) {
            case "sales_query" -> handleSalesQuery(params);
            case "trend_analysis" -> handleTrendAnalysis(params);
            case "statistics" -> handleStatistics(params);
            case "chart_generation" -> handleChartGeneration(params);
            case "export_data" -> handleExport(params);
            default -> Map.of("status", "error", "message", "Unknown intent: " + intent);
        };
    }

    private Map<String, Object> handleSalesQuery(Map<String, Object> params) {
        try {
            String timeType = (String) params.getOrDefault("timeType", "月");
            String region = (String) params.getOrDefault("region", "全部");
            String productCode = (String) params.getOrDefault("productCode", "全部");

            LocalDate startDate = parseDate(params.get("startDate"));
            LocalDate endDate = parseDate(params.get("endDate"));

            if (startDate == null) startDate = LocalDate.now().minusMonths(1);
            if (endDate == null) endDate = LocalDate.now();

            var request = new SalesQueryTool.SalesQueryRequest(timeType, region, productCode, startDate, endDate);
            var result = salesQueryTool.querySalesData(request);

            return Map.of(
                    "status", "success",
                    "data", Map.of(
                            "timeType", result.timeType(),
                            "region", result.region(),
                            "productCode", result.productCode(),
                            "data", result.data(),
                            "totalAmount", result.totalAmount(),
                            "totalQuantity", result.totalQuantity()
                    )
            );
        } catch (Exception e) {
            log.error("Sales query failed", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    private Map<String, Object> handleTrendAnalysis(Map<String, Object> params) {
        try {
            String dimension = (String) params.getOrDefault("dimension", "region");
            String compareType = (String) params.getOrDefault("compareType", "月");

            LocalDate startDate = parseDate(params.get("startDate"));
            LocalDate endDate = parseDate(params.get("endDate"));

            if (startDate == null) startDate = LocalDate.now().minusMonths(1);
            if (endDate == null) endDate = LocalDate.now();

            var request = new TrendAnalysisTool.TrendAnalysisRequest(dimension, compareType, startDate, endDate);
            var result = trendAnalysisTool.analyzeTrend(request);

            return Map.of(
                    "status", "success",
                    "data", Map.of(
                            "dimension", result.dimension(),
                            "compareType", result.compareType(),
                            "currentPeriod", result.currentPeriod(),
                            "previousPeriod", result.previousPeriod(),
                            "comparisons", result.comparisons()
                    )
            );
        } catch (Exception e) {
            log.error("Trend analysis failed", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    private Map<String, Object> handleStatistics(Map<String, Object> params) {
        try {
            LocalDate startDate = parseDate(params.get("startDate"));
            LocalDate endDate = parseDate(params.get("endDate"));

            if (startDate == null) startDate = LocalDate.now().minusMonths(1);
            if (endDate == null) endDate = LocalDate.now();

            @SuppressWarnings("unchecked")
            List<String> groupBy = (List<String>) params.getOrDefault("groupBy", List.of("region", "product"));

            var request = new StatisticsTool.StatisticsRequest(startDate, endDate, groupBy);
            var result = statisticsTool.generateStatistics(request);

            return Map.of(
                    "status", "success",
                    "data", Map.of(
                            "summary", result.summary(),
                            "rankings", result.rankings(),
                            "chartData", result.chartData()
                    )
            );
        } catch (Exception e) {
            log.error("Statistics generation failed", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    private Map<String, Object> handleChartGeneration(Map<String, Object> params) {
        try {
            String chartType = (String) params.getOrDefault("chartType", "line");
            LocalDate startDate = parseDate(params.get("startDate"));
            LocalDate endDate = parseDate(params.get("endDate"));

            if (startDate == null) startDate = LocalDate.now().minusMonths(1);
            if (endDate == null) endDate = LocalDate.now();

            var chartData = statisticsTool.generateEChartsData(chartType, startDate, endDate);

            return Map.of(
                    "status", "success",
                    "chartData", Map.of(
                            "categories", chartData.categories(),
                            "series", chartData.series()
                    )
            );
        } catch (Exception e) {
            log.error("Chart generation failed", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    private Map<String, Object> handleExport(Map<String, Object> params) {
        try {
            String format = (String) params.getOrDefault("format", "csv");
            LocalDate startDate = parseDate(params.get("startDate"));
            LocalDate endDate = parseDate(params.get("endDate"));

            if (startDate == null) startDate = LocalDate.now().minusMonths(1);
            if (endDate == null) endDate = LocalDate.now();

            String exportedData = statisticsTool.exportData(format, startDate, endDate);

            return Map.of(
                    "status", "success",
                    "format", format,
                    "data", exportedData
            );
        } catch (Exception e) {
            log.error("Export failed", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    private LocalDate parseDate(Object dateObj) {
        if (dateObj == null) return null;
        if (dateObj instanceof LocalDate) return (LocalDate) dateObj;
        if (dateObj instanceof String) {
            try {
                return LocalDate.parse((String) dateObj);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public Map<String, Object> getAgentInfo() {
        return Map.of(
                "agentCode", "marketing-agent",
                "agentName", "市场营销Agent",
                "capabilities", agentMetadata.get("capabilities"),
                "status", agentMetadata.get("status")
        );
    }
}
