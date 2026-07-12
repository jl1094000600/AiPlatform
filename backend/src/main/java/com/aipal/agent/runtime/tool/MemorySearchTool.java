package com.aipal.agent.runtime.tool;

import com.aipal.entity.AiMemoryItem;
import com.aipal.service.memory.MemoryContext;
import com.aipal.service.memory.MemoryOrchestrator;
import com.aipal.service.memory.MemoryRecallCandidate;
import com.aipal.service.memory.MemoryRecallRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MemorySearchTool implements AgentTool {
    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 20;

    private final MemoryOrchestrator memoryOrchestrator;

    @Override
    public String name() {
        return "memory.search";
    }

    @Override
    public ToolResult execute(ToolInvocationContext context, Object input) {
        Map<?, ?> payload = asMap(input);
        String query = requiredString(payload, "query");
        int topK = boundedInt(payload.get("topK"), DEFAULT_TOP_K, 1, MAX_TOP_K);
        MemoryContext memory = memoryOrchestrator.prepareContext(
                new MemoryRecallRequest(context.agentId(), context.projectKey(), query));
        List<Map<String, Object>> items = memory.selected().stream()
                .limit(topK)
                .map(this::toItem)
                .toList();

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("traceId", memory.traceId());
        output.put("recallMode", memory.recallMode());
        output.put("policyVersion", memory.policyVersion());
        output.put("tokenCount", memory.tokenCount());
        output.put("items", items);
        return new ToolResult(output, "memory.search returned " + items.size() + " item(s)",
                estimateTokens(query), memory.tokenCount());
    }

    private Map<String, Object> toItem(MemoryRecallCandidate candidate) {
        AiMemoryItem memory = candidate.memory();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("memoryCode", memory.getMemoryCode());
        item.put("memoryVersion", memory.getVersion());
        item.put("title", memory.getTitle());
        item.put("sourceType", memory.getSourceType());
        item.put("scopeType", memory.getScopeType());
        item.put("score", candidate.score());
        item.put("estimatedTokens", candidate.estimatedTokens());
        item.put("reason", candidate.reason());
        item.put("contentPreview", truncate(memory.getContent(), 1000));
        return item;
    }

    private Map<?, ?> asMap(Object input) {
        if (input instanceof Map<?, ?> map) return map;
        throw new IllegalArgumentException("memory.search input must be an object");
    }

    private String requiredString(Map<?, ?> payload, String field) {
        Object value = payload.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return String.valueOf(value).trim();
    }

    private int boundedInt(Object value, int defaultValue, int min, int max) {
        if (value == null) return defaultValue;
        int parsed;
        if (value instanceof Number number) parsed = number.intValue();
        else parsed = Integer.parseInt(String.valueOf(value));
        return Math.max(min, Math.min(max, parsed));
    }

    private int estimateTokens(String value) {
        return value == null ? 0 : Math.max(1, value.length() / 4);
    }

    private String truncate(String value, int max) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), max));
    }
}
