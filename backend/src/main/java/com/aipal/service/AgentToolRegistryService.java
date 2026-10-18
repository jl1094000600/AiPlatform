package com.aipal.service;

import com.aipal.common.ErrorCode;

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
        if (toolName == null || toolName.isBlank()) throw ErrorCode.TOOL_NAME_REQUIRED.ex();
        AgentToolDefinition definition = toolDefinitionMapper.selectOne(new LambdaQueryWrapper<AgentToolDefinition>()
                .eq(AgentToolDefinition::getToolName, toolName.trim())
                .eq(AgentToolDefinition::getStatus, 1)
                .last("LIMIT 1"));
        if (definition == null) throw ErrorCode.AGENT_TOOL_NOT_FOUND.ex();
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
                    throw ErrorCode.TOOL_INPUT_NOT_OBJECT.ex();
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
                    throw ErrorCode.TOOL_INPUT_MISSING_FIELD.ex(String.join(",", missing));
                }
            }
            validateProperties(schema, payload);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw ErrorCode.TOOL_INPUT_SCHEMA_INVALID.ex();
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
                throw ErrorCode.TOOL_INPUT_FIELD_MUST_BE_STRING.ex(field);
            }
            if ("integer".equals(type)) {
                int parsed = parseInteger(field, value);
                if (constraints.get("minimum") instanceof Number min && parsed < min.intValue()) {
                    throw ErrorCode.TOOL_INPUT_FIELD_BELOW_MIN.ex(field);
                }
                if (constraints.get("maximum") instanceof Number max && parsed > max.intValue()) {
                    throw ErrorCode.TOOL_INPUT_FIELD_ABOVE_MAX.ex(field);
                }
            }
            if ("object".equals(type) && !(value instanceof Map<?, ?>)) {
                throw ErrorCode.TOOL_INPUT_FIELD_MUST_BE_OBJECT.ex(field);
            }
            if ("array".equals(type) && !(value instanceof List<?>)) {
                throw ErrorCode.TOOL_INPUT_FIELD_MUST_BE_ARRAY.ex(field);
            }
        }
    }

    private int parseInteger(String field, Object value) {
        if (value instanceof Number number) {
            double raw = number.doubleValue();
            int parsed = number.intValue();
            if (Double.compare(raw, parsed) == 0) return parsed;
        }
        throw ErrorCode.TOOL_INPUT_FIELD_MUST_BE_INTEGER.ex(field);
    }

    public record ToolDryRunResult(String toolName, String riskLevel, Boolean accepted, String message) {
    }
}
