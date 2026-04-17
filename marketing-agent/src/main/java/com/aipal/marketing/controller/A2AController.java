package com.aipal.marketing.controller;

import cn.hutool.core.util.IdUtil;
import com.aipal.marketing.config.MarketingAgentConfig;
import com.aipal.marketing.dto.*;
import com.aipal.marketing.service.MarketingAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/agent/marketing/a2a")
@RequiredArgsConstructor
public class A2AController {

    private final MarketingAgentService marketingAgentService;
    private final MarketingAgentConfig agentConfig;

    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> handleMessage(@RequestBody Map<String, Object> message) {
        log.info("Received A2A message: source={}, action={}",
                message.get("sourceAgent"), message.get("action"));

        String action = (String) message.getOrDefault("action", "invoke");

        if ("invoke".equals(action)) {
            return ResponseEntity.ok(handleInvoke(message));
        } else if ("delegate".equals(action)) {
            return ResponseEntity.ok(handleDelegate(message));
        } else if ("ping".equals(action)) {
            return ResponseEntity.ok(handlePing(message));
        }

        return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Unknown action: " + action
        ));
    }

    private Map<String, Object> handleInvoke(Map<String, Object> message) {
        String intent = (String) message.getOrDefault("intent",
                ((Map<String, Object>) message.getOrDefault("payload", Map.of())).get("intent"));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.getOrDefault("payload", Map.of());

        MarketingToolResponse result = processIntent(intent, payload);

        return Map.of(
                "messageId", IdUtil.fastSimpleUUID(),
                "sourceAgent", agentConfig.getCode(),
                "targetAgent", message.get("sourceAgent"),
                "sessionId", message.get("sessionId"),
                "action", "respond",
                "correlationId", message.get("messageId"),
                "payload", Map.of(
                        "status", result.isSuccess() ? "success" : "error",
                        "tool", result.getToolName(),
                        "data", result.getResult(),
                        "errors", result.getErrors()
                ),
                "timestamp", java.time.LocalDateTime.now().toString()
        );
    }

    private Map<String, Object> handleDelegate(Map<String, Object> message) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.getOrDefault("payload", Map.of());

        String targetIntent = (String) payload.getOrDefault("intent", "unknown");
        MarketingToolResponse result = processIntent(targetIntent, payload);

        return Map.of(
                "messageId", IdUtil.fastSimpleUUID(),
                "sourceAgent", agentConfig.getCode(),
                "targetAgent", message.get("sourceAgent"),
                "sessionId", message.get("sessionId"),
                "action", "respond",
                "correlationId", message.get("messageId"),
                "payload", Map.of(
                        "status", result.isSuccess() ? "success" : "error",
                        "tool", result.getToolName(),
                        "data", result.getResult()
                ),
                "timestamp", java.time.LocalDateTime.now().toString()
        );
    }

    private Map<String, Object> handlePing(Map<String, Object> message) {
        return Map.of(
                "messageId", IdUtil.fastSimpleUUID(),
                "sourceAgent", agentConfig.getCode(),
                "targetAgent", message.get("sourceAgent"),
                "sessionId", message.get("sessionId"),
                "action", "respond",
                "correlationId", message.get("messageId"),
                "payload", Map.of(
                        "status", "success",
                        "message", "pong",
                        "capabilities", java.util.List.of("sales_data_query", "trend_analysis", "statistics_ranking")
                ),
                "timestamp", java.time.LocalDateTime.now().toString()
        );
    }

    private MarketingToolResponse processIntent(String intent, Map<String, Object> payload) {
        try {
            return switch (intent) {
                case "sales_data_query" -> {
                    SalesQueryRequest request = new SalesQueryRequest();
                    request.setStartDate((String) payload.get("startDate"));
                    request.setEndDate((String) payload.get("endDate"));
                    request.setRegion((String) payload.get("region"));
                    request.setProductCategory((String) payload.get("productCategory"));
                    request.setAggregation((String) payload.getOrDefault("aggregation", "day"));
                    yield marketingAgentService.querySalesData(request);
                }
                case "trend_analysis" -> {
                    TrendAnalysisRequest request = new TrendAnalysisRequest();
                    request.setCompareType((String) payload.get("compareType"));

                    @SuppressWarnings("unchecked")
                    Map<String, String> currentPeriod = (Map<String, String>) payload.get("currentPeriod");
                    @SuppressWarnings("unchecked")
                    Map<String, String> previousPeriod = (Map<String, String>) payload.get("previousPeriod");
                    if (currentPeriod != null) request.setCurrentPeriod(new TrendAnalysisRequest.Period(
                            currentPeriod.get("start"), currentPeriod.get("end")));
                    if (previousPeriod != null) request.setPreviousPeriod(new TrendAnalysisRequest.Period(
                            previousPeriod.get("start"), previousPeriod.get("end")));

                    request.setGroupBy((String) payload.get("groupBy"));
                    yield marketingAgentService.analyzeTrend(request);
                }
                case "statistics_ranking" -> {
                    StatisticsRankingRequest request = new StatisticsRankingRequest();
                    request.setStartDate((String) payload.get("startDate"));
                    request.setEndDate((String) payload.get("endDate"));
                    request.setDimension((String) payload.getOrDefault("dimension", "region"));
                    Object topN = payload.getOrDefault("topN", 10);
                    request.setTopN(topN instanceof Integer ? (Integer) topN : Integer.parseInt(topN.toString()));
                    request.setRankingType((String) payload.getOrDefault("rankingType", "sales"));
                    request.setDataSource((String) payload.getOrDefault("dataSource", "sales"));
                    yield marketingAgentService.getStatisticsRanking(request);
                }
                default -> MarketingToolResponse.error(intent, "UNKNOWN_TOOL", "Unknown tool: " + intent);
            };
        } catch (Exception e) {
            log.error("Failed to process intent: {}", intent, e);
            return MarketingToolResponse.error(intent, "PROCESSING_ERROR", e.getMessage());
        }
    }
}
