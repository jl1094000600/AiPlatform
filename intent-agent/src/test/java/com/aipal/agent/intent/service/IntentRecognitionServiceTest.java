package com.aipal.agent.intent.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentRecognitionServiceTest {

    private final IntentRecognitionService service = new IntentRecognitionService();

    @Test
    void shouldRouteMarketingIntent() {
        Map<String, Object> result = service.classify(Map.of("text", "Generate sales trend statistics chart"));

        assertEquals("success", result.get("status"));
        assertEquals("marketing_analysis", result.get("intent"));
        assertEquals("marketing-agent", result.get("targetAgent"));
        assertTrue((Boolean) result.get("routingRequired"));
    }

    @Test
    void shouldRouteImageIntent() {
        Map<String, Object> result = service.classify(Map.of("text", "Please OCR this document image"));

        assertEquals("success", result.get("status"));
        assertEquals("image_recognition", result.get("intent"));
        assertEquals("image-recognition-agent", result.get("targetAgent"));
    }

    @Test
    void shouldReturnUnknownWhenNoKeywordMatches() {
        Map<String, Object> result = service.classify(Map.of("text", "hello"));

        assertEquals("success", result.get("status"));
        assertEquals("unknown", result.get("intent"));
        assertEquals("", result.get("targetAgent"));
        assertEquals(false, result.get("routingRequired"));
    }

    @Test
    void shouldRouteChineseMarketingIntent() {
        Map<String, Object> result = service.classify(Map.of("text", "\u9500\u552e\u8d8b\u52bf\u7edf\u8ba1"));

        assertEquals("success", result.get("status"));
        assertEquals("marketing_analysis", result.get("intent"));
        assertEquals("marketing-agent", result.get("targetAgent"));
    }
}
