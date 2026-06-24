package com.aipal.service.memory;

import com.aipal.entity.AiMemoryItem;
import com.aipal.entity.AiMemoryPolicy;
import com.aipal.entity.AiModel;
import com.aipal.mapper.AiModelMapper;
import com.aipal.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryVectorProjectionService {

    private final RagService ragService;
    private final AiModelMapper modelMapper;

    @Value("${aipal.memory.vector.enabled:false}")
    private boolean vectorEnabled;
    @Value("${aipal.memory.vector.chroma-url:http://localhost:9000}")
    private String chromaUrl;
    @Value("${aipal.memory.vector.embedding-model-id:2}")
    private Long embeddingModelId;

    public void projectIfAllowed(AiMemoryItem memory, AiMemoryPolicy policy) {
        if (!vectorEnabled || policy == null || policy.getVectorEnabled() == null || policy.getVectorEnabled() == 0) return;
        if (memory == null || !"ACTIVE".equals(memory.getStatus()) || memory.getContent() == null || memory.getContent().isBlank()
                || "PII".equals(memory.getSensitivity())) return;
        try {
            AiModel model = modelMapper.selectById(embeddingModelId);
            if (model == null) throw new IllegalStateException("Embedding model not found: " + embeddingModelId);
            ragService.upsertVectorProjection(model, chromaUrl, collectionName(memory.getTenantId()), vectorId(memory),
                    memory.getContent(), metadata(memory));
        } catch (RuntimeException ex) {
            log.warn("Memory vector projection deferred, memoryId={}, reason={}", memory.getId(), ex.getMessage());
        }
    }

    public void delete(AiMemoryItem memory) {
        if (!vectorEnabled || memory == null || memory.getTenantId() == null || memory.getId() == null) return;
        try {
            ragService.deleteVectorProjection(chromaUrl, collectionName(memory.getTenantId()), vectorId(memory));
        } catch (RuntimeException ex) {
            log.warn("Memory vector deletion deferred, memoryId={}, reason={}", memory.getId(), ex.getMessage());
        }
    }

    public List<VectorReference> query(MemoryAccessScope scope, AiMemoryPolicy policy, String requestSummary) {
        if (!vectorEnabled || policy == null || policy.getVectorEnabled() == null || policy.getVectorEnabled() == 0
                || scope == null || scope.tenantId() == null || requestSummary == null || requestSummary.isBlank()) return List.of();
        try {
            AiModel model = modelMapper.selectById(embeddingModelId);
            if (model == null) throw new IllegalStateException("Embedding model not found: " + embeddingModelId);
            return ragService.queryVectorProjection(model, chromaUrl, collectionName(scope.tenantId()), requestSummary,
                            authorizationWhere(scope, policy), 20)
                    .stream()
                    .map(hit -> reference(hit))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        } catch (RuntimeException ex) {
            // Fail closed: vector infrastructure is optional and must never broaden recall.
            log.warn("Memory vector query skipped, tenantId={}, reason={}", scope.tenantId(), ex.getMessage());
            return List.of();
        }
    }

    private String collectionName(Long tenantId) { return "memory_tenant_" + tenantId; }
    private String vectorId(AiMemoryItem memory) { return "memory-" + memory.getId(); }
    private Map<String, Object> metadata(AiMemoryItem memory) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tenantId", memory.getTenantId());
        metadata.put("projectKey", memory.getProjectKey() == null ? "" : memory.getProjectKey());
        metadata.put("ownerUserId", memory.getOwnerUserId() == null ? 0L : memory.getOwnerUserId());
        metadata.put("scopeType", memory.getScopeType());
        metadata.put("sensitivity", memory.getSensitivity());
        metadata.put("memoryId", memory.getId());
        metadata.put("version", memory.getVersion());
        metadata.put("status", memory.getStatus());
        return metadata;
    }

    private Map<String, Object> authorizationWhere(MemoryAccessScope scope, AiMemoryPolicy policy) {
        List<Map<String, Object>> visibility = new java.util.ArrayList<>();
        visibility.add(Map.of("scopeType", "TENANT"));
        if (scope.userId() != null) {
            visibility.add(Map.of("$and", List.of(Map.of("scopeType", "USER"), Map.of("ownerUserId", scope.userId()))));
        }
        if (scope.projectKey() != null) {
            visibility.add(Map.of("$and", List.of(Map.of("scopeType", "PROJECT"), Map.of("projectKey", scope.projectKey()))));
        }
        List<String> allowedSensitivity = allowedSensitivity(policy.getMaxSensitivity());
        return Map.of("$and", List.of(
                Map.of("tenantId", scope.tenantId()),
                Map.of("status", "ACTIVE"),
                Map.of("sensitivity", Map.of("$in", allowedSensitivity)),
                Map.of("$or", visibility)
        ));
    }

    private List<String> allowedSensitivity(String maxSensitivity) {
        if ("PII".equals(maxSensitivity)) return List.of("PUBLIC", "INTERNAL", "CONFIDENTIAL", "PII");
        if ("CONFIDENTIAL".equals(maxSensitivity)) return List.of("PUBLIC", "INTERNAL", "CONFIDENTIAL");
        return List.of("PUBLIC", "INTERNAL");
    }

    private VectorReference reference(RagService.VectorQueryHit hit) {
        try {
            Object memoryId = hit.metadata().get("memoryId");
            Object version = hit.metadata().get("version");
            if (!(memoryId instanceof Number id) || !(version instanceof Number itemVersion)) return null;
            return new VectorReference(id.longValue(), itemVersion.intValue(), hit.distance());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public record VectorReference(Long memoryId, Integer version, double distance) {
    }
}
