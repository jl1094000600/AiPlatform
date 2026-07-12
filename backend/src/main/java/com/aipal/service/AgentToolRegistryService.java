package com.aipal.service;

import com.aipal.common.BizException;
import com.aipal.entity.AgentToolDefinition;
import com.aipal.mapper.AgentToolDefinitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentToolRegistryService {
    private final AgentToolDefinitionMapper toolDefinitionMapper;
    private final ObjectMapper objectMapper;

    public List<AgentToolDefinition> listEnabled() {
        return toolDefinitionMapper.selectList(new LambdaQueryWrapper<AgentToolDefinition>()
                .eq(AgentToolDefinition::getStatus, 1)
                .orderByAsc(AgentToolDefinition::getRiskLevel)
                .orderByAsc(AgentToolDefinition::getToolName));
    }

    public AgentToolDefinition requireEnabled(String toolName) {
        if (toolName == null || toolName.isBlank()) throw new BizException(400, "toolName is required");
        AgentToolDefinition definition = toolDefinitionMapper.selectOne(new LambdaQueryWrapper<AgentToolDefinition>()
                .eq(AgentToolDefinition::getToolName, toolName.trim())
                .eq(AgentToolDefinition::getStatus, 1)
                .last("LIMIT 1"));
        if (definition == null) throw new BizException(404, "Agent tool not found or disabled");
        return definition;
    }

    public ToolDryRunResult dryRun(String toolName, Object input) {
        AgentToolDefinition definition = requireEnabled(toolName);
        validateRequiredFields(definition, input);
        return new ToolDryRunResult(definition.getToolName(), definition.getRiskLevel(),
                Boolean.TRUE, "Tool registry dry-run accepted; execution is handled by Agent Runtime v2.");
    }

    private void validateRequiredFields(AgentToolDefinition definition, Object input) {
        if (definition.getInputSchema() == null || definition.getInputSchema().isBlank()) return;
        try {
            Map<String, Object> schema = objectMapper.readValue(definition.getInputSchema(), new TypeReference<>() {});
            if (!(input instanceof Map<?, ?> payload)) {
                if (schema.containsKey("required") || "object".equals(schema.get("type"))) {
                    throw new BizException(400, "Tool input must be an object");
                }
                return;
            }
            Object required = schema.get("required");
            if (required instanceof List<?> fields && !fields.isEmpty()) {
                List<String> missing = fields.stream()
                        .map(String::valueOf)
                        .filter(field -> !payload.containsKey(field) || payload.get(field) == null
                                || String.valueOf(payload.get(field)).isBlank())
                        .toList();
                if (!missing.isEmpty()) {
                    throw new BizException(400, "Missing required tool input: " + String.join(",", missing));
                }
            }
            validateProperties(schema, payload);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "Tool input schema is invalid");
        }
    }

    private void validateProperties(Map<String, Object> schema, Map<?, ?> payload) {
        Object properties = schema.get("properties");
        if (!(properties instanceof Map<?, ?> fields)) return;
        for (Map.Entry<?, ?> entry : fields.entrySet()) {
            String field = String.valueOf(entry.getKey());
            if (!payload.containsKey(field) || payload.get(field) == null) continue;
            if (!(entry.getValue() instanceof Map<?, ?> constraints)) continue;
            Object value = payload.get(field);
            String type = constraints.get("type") == null ? null : String.valueOf(constraints.get("type"));
            if ("string".equals(type) && !(value instanceof String)) {
                throw new BizException(400, "Tool input field must be string: " + field);
            }
            if ("integer".equals(type)) {
                int parsed = parseInteger(field, value);
                if (constraints.get("minimum") instanceof Number min && parsed < min.intValue()) {
                    throw new BizException(400, "Tool input field below minimum: " + field);
                }
                if (constraints.get("maximum") instanceof Number max && parsed > max.intValue()) {
                    throw new BizException(400, "Tool input field above maximum: " + field);
                }
            }
            if ("object".equals(type) && !(value instanceof Map<?, ?>)) {
                throw new BizException(400, "Tool input field must be object: " + field);
            }
            if ("array".equals(type) && !(value instanceof List<?>)) {
                throw new BizException(400, "Tool input field must be array: " + field);
            }
        }
    }

    private int parseInteger(String field, Object value) {
        if (value instanceof Number number) {
            double raw = number.doubleValue();
            int parsed = number.intValue();
            if (Double.compare(raw, parsed) == 0) return parsed;
        }
        throw new BizException(400, "Tool input field must be integer: " + field);
    }

    public record ToolDryRunResult(String toolName, String riskLevel, Boolean accepted, String message) {
    }
}
