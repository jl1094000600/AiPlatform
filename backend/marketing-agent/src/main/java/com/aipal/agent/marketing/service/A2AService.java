package com.aipal.agent.marketing.service;

import com.aipal.agent.marketing.config.AgentConfig;
import com.aipal.agent.marketing.service.tools.SalesQueryTool;
import com.aipal.agent.marketing.service.tools.StatisticsTool;
import com.aipal.agent.marketing.service.tools.TrendAnalysisTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class A2AService {

    private final AgentConfig agentConfig;
    private final SalesQueryTool salesQueryTool;
    private final TrendAnalysisTool trendAnalysisTool;
    private final StatisticsTool statisticsTool;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void init() {
        log.info("Marketing Agent A2A service initialized for agent: {}", agentConfig.getCode());
    }

    public Object handleMessage(Map<String, Object> payload) {
        String action = (String) payload.get("action");
        String intent = (String) payload.get("intent");

        log.info("Handling A2A message: action={}, intent={}", action, intent);

        try {
            return switch (action != null ? action : intent) {
                case "sales_query" -> handleSalesQuery(payload);
                case "trend_analysis" -> handleTrendAnalysis(payload);
                case "statistics" -> handleStatistics(payload);
                default -> Map.of("status", "success", "message", "Marketing Agent acknowledged");
            };
        } catch (Exception e) {
            log.error("Error handling A2A message", e);
            return Map.of("status", "error", "error", e.getMessage());
        }
    }

    private Object handleSalesQuery(Map<String, Object> payload) {
        String timeType = (String) payload.get("timeType");
        String region = (String) payload.get("region");
        String productCode = (String) payload.get("productCode");
        String startDate = (String) payload.get("startDate");
        String endDate = (String) payload.get("endDate");

        SalesQueryTool.SalesQueryRequest request = new SalesQueryTool.SalesQueryRequest(
                timeType, region, productCode,
                startDate != null ? java.time.LocalDate.parse(startDate) : null,
                endDate != null ? java.time.LocalDate.parse(endDate) : null
        );

        return salesQueryTool.querySalesData(request);
    }

    private Object handleTrendAnalysis(Map<String, Object> payload) {
        String dimension = (String) payload.get("dimension");
        String compareType = (String) payload.get("compareType");
        String startDate = (String) payload.get("startDate");
        String endDate = (String) payload.get("endDate");

        TrendAnalysisTool.TrendAnalysisRequest request = new TrendAnalysisTool.TrendAnalysisRequest(
                dimension, compareType,
                java.time.LocalDate.parse(startDate),
                java.time.LocalDate.parse(endDate)
        );

        return trendAnalysisTool.analyzeTrend(request);
    }

    private Object handleStatistics(Map<String, Object> payload) {
        String startDate = (String) payload.get("startDate");
        String endDate = (String) payload.get("endDate");

        StatisticsTool.StatisticsRequest request = new StatisticsTool.StatisticsRequest(
                java.time.LocalDate.parse(startDate),
                java.time.LocalDate.parse(endDate),
                null
        );

        return statisticsTool.generateStatistics(request);
    }

    @Scheduled(fixedDelay = 5000)
    public void pollMessages() {
        log.debug("Polling A2A messages...");
    }
}
