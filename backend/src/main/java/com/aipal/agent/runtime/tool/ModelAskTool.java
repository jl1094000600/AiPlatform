package com.aipal.agent.runtime.tool;

import com.aipal.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ModelAskTool implements AgentTool {
    private final AgentService agentService;

    @Override
    public String name() {
        return "model.ask";
    }

    @Override
    public ToolResult execute(ToolInvocationContext context, Object input) {
        Map<?, ?> payload = asMap(input);
        String prompt = requiredString(payload, "prompt");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("prompt", prompt);
        Map<String, Object> result = agentService.callAgentFrozen(context.agentId(), params, context.ownerUserId(), null,
                context.modelId(), context.promptId(), context.promptVersionId());

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("answer", result.getOrDefault("message", "Agent invocation completed"));
        output.put("traceId", result.get("traceId"));
        output.put("agentName", result.get("agentName"));
        output.put("modelCode", result.get("modelCode"));
        output.put("promptId", result.get("promptId"));
        output.put("promptVersionId", result.get("promptVersionId"));
        return new ToolResult(output, "model.ask completed", estimateTokens(prompt), 200);
    }

    private Map<?, ?> asMap(Object input) {
        if (input instanceof Map<?, ?> map) return map;
        throw new IllegalArgumentException("model.ask input must be an object");
    }

    private String requiredString(Map<?, ?> payload, String field) {
        Object value = payload.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return String.valueOf(value).trim();
    }

    private int estimateTokens(String value) {
        return value == null ? 0 : Math.max(1, value.length() / 4);
    }
}
