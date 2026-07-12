package com.aipal.service;

import com.aipal.entity.AgentToolDefinition;
import com.aipal.entity.AiAgentVersion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgentToolSnapshotService {
    static final String SNAPSHOT_KEY = "toolRegistrySnapshot";
    static final String MODE_AGENT_VERSION = "AGENT_VERSION";
    static final String MODE_REGISTRY_ENABLED = "REGISTRY_ENABLED";

    private final AgentToolRegistryService registryService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> snapshotFor(AiAgentVersion version) {
        Whitelist whitelist = extractWhitelist(version == null ? null : version.getConfig());
        List<AgentToolDefinition> enabledTools = registryService.listEnabled();
        Set<String> enabledNames = enabledTools.stream()
                .map(AgentToolDefinition::getToolName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<String> allowedNames = whitelist.configured()
                ? whitelist.names().stream().filter(enabledNames::contains).toList()
                : enabledTools.stream().map(AgentToolDefinition::getToolName).toList();

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("whitelistMode", whitelist.configured() ? MODE_AGENT_VERSION : MODE_REGISTRY_ENABLED);
        snapshot.put("allowedToolNames", allowedNames);
        snapshot.put("missingToolNames", whitelist.configured()
                ? whitelist.names().stream().filter(name -> !enabledNames.contains(name)).toList()
                : List.of());
        snapshot.put("tools", enabledTools.stream()
                .filter(tool -> !whitelist.configured() || allowedNames.contains(tool.getToolName()))
                .map(this::toolMetadata)
                .toList());
        return snapshot;
    }

    private Map<String, Object> toolMetadata(AgentToolDefinition tool) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("toolName", tool.getToolName());
        metadata.put("riskLevel", tool.getRiskLevel());
        metadata.put("requiresApproval", tool.getRequiresApproval());
        metadata.put("timeoutSeconds", tool.getTimeoutSeconds());
        metadata.put("concurrencySafe", tool.getConcurrencySafe());
        metadata.put("idempotent", tool.getIdempotent());
        metadata.put("inputSchemaHash", sha256(defaultString(tool.getInputSchema())));
        metadata.put("outputSchemaHash", sha256(defaultString(tool.getOutputSchema())));
        return metadata;
    }

    private Whitelist extractWhitelist(String configJson) {
        if (configJson == null || configJson.isBlank()) return new Whitelist(false, List.of());
        try {
            Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {});
            for (String key : List.of("toolWhitelist", "allowedTools", "tools")) {
                if (!config.containsKey(key)) continue;
                return new Whitelist(true, normalizeNames(config.get(key)));
            }
        } catch (Exception ignored) {
            return new Whitelist(true, List.of());
        }
        return new Whitelist(false, List.of());
    }

    private List<String> normalizeNames(Object value) {
        if (!(value instanceof Collection<?> values)) return List.of();
        return values.stream()
                .map(this::toolName)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
    }

    private String toolName(Object value) {
        if (value instanceof String name) return name.trim();
        if (value instanceof Map<?, ?> map) {
            Object toolName = map.get("toolName");
            if (toolName == null) toolName = map.get("name");
            return toolName == null ? "" : String.valueOf(toolName).trim();
        }
        return "";
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private record Whitelist(boolean configured, List<String> names) {
    }
}
