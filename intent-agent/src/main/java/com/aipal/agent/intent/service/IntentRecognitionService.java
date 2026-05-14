package com.aipal.agent.intent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class IntentRecognitionService {

    private static final String UNKNOWN_INTENT = "unknown";

    private final List<IntentRule> rules = List.of(
            new IntentRule(
                    "image_recognition",
                    "image-recognition-agent",
                    List.of("image_recognition", "file_parsing", "document_parsing"),
                    List.of("image", "picture", "photo", "recognize", "ocr", "file", "document", "scan",
                            "parse", "vision", "\u56fe\u7247", "\u56fe\u50cf", "\u8bc6\u522b",
                            "\u6587\u4ef6", "\u6587\u6863", "\u89e3\u6790")),
            new IntentRule(
                    "marketing_analysis",
                    "marketing-agent",
                    List.of("sales_query", "trend_analysis", "statistics", "chart_generation"),
                    List.of("sales", "marketing", "revenue", "trend", "statistics", "chart", "ranking",
                            "report", "analysis", "growth", "\u9500\u552e", "\u8425\u9500",
                            "\u8425\u6536", "\u8d8b\u52bf", "\u7edf\u8ba1", "\u6392\u884c",
                            "\u56fe\u8868")),
            new IntentRule(
                    "tts_synthesis",
                    "tts-agent",
                    List.of("synthesize", "voices"),
                    List.of("tts", "speech", "voice", "audio", "read aloud", "synthesize",
                            "narration", "dub", "sound", "\u8bed\u97f3", "\u6717\u8bfb",
                            "\u97f3\u9891", "\u914d\u97f3", "\u5408\u6210"))
    );

    public Map<String, Object> processIntent(String intent, Map<String, Object> params) {
        return switch (normalize(intent)) {
            case "classify", "recognize", "route", "route_intent" -> classify(params);
            case "list_intents" -> listIntents();
            default -> Map.of("status", "error", "message", "Unknown intent: " + intent);
        };
    }

    public Map<String, Object> classify(Map<String, Object> params) {
        String text = String.valueOf(params.getOrDefault("text", params.getOrDefault("query", ""))).trim();
        if (text.isEmpty()) {
            return Map.of(
                    "status", "error",
                    "message", "text or query is required"
            );
        }

        IntentMatch bestMatch = rules.stream()
                .map(rule -> rule.match(text))
                .max((left, right) -> Integer.compare(left.score(), right.score()))
                .orElse(IntentMatch.unknown());

        log.info("Classified intent: textLength={}, intent={}, score={}",
                text.length(), bestMatch.intent(), bestMatch.score());

        if (bestMatch.score() <= 0) {
            return Map.of(
                    "status", "success",
                    "intent", UNKNOWN_INTENT,
                    "confidence", 0.0,
                    "targetAgent", "",
                    "capabilities", List.of(),
                    "routingRequired", false
            );
        }

        return Map.of(
                "status", "success",
                "intent", bestMatch.intent(),
                "confidence", bestMatch.confidence(),
                "targetAgent", bestMatch.targetAgent(),
                "capabilities", bestMatch.capabilities(),
                "routingRequired", true
        );
    }

    public Map<String, Object> listIntents() {
        Map<String, Object> supportedIntents = new LinkedHashMap<>();
        for (IntentRule rule : rules) {
            supportedIntents.put(rule.intent(), Map.of(
                    "targetAgent", rule.targetAgent(),
                    "capabilities", rule.capabilities()
            ));
        }
        return Map.of(
                "status", "success",
                "supportedIntents", supportedIntents
        );
    }

    public Map<String, Object> getAgentInfo() {
        return Map.of(
                "agentCode", "intent-agent",
                "agentName", "Intent Recognition Agent",
                "capabilities", capabilities(),
                "status", "online"
        );
    }

    public List<String> capabilities() {
        return List.of("intent_classification", "intent_routing", "entity_extraction");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record IntentRule(
            String intent,
            String targetAgent,
            List<String> capabilities,
            List<String> keywords
    ) {
        IntentMatch match(String text) {
            String normalizedText = text.toLowerCase(Locale.ROOT);
            int score = 0;
            for (String keyword : keywords) {
                if (normalizedText.contains(keyword.toLowerCase(Locale.ROOT))) {
                    score++;
                }
            }
            double confidence = Math.min(0.99, 0.45 + score * 0.18);
            return new IntentMatch(intent, targetAgent, capabilities, score, confidence);
        }
    }

    private record IntentMatch(
            String intent,
            String targetAgent,
            List<String> capabilities,
            int score,
            double confidence
    ) {
        static IntentMatch unknown() {
            return new IntentMatch(UNKNOWN_INTENT, "", List.of(), 0, 0.0);
        }
    }
}
