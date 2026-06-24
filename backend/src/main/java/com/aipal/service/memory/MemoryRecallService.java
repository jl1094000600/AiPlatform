package com.aipal.service.memory;

import com.aipal.entity.AiMemoryItem;
import com.aipal.mapper.AiMemoryItemMapper;
import com.aipal.memory.MemoryScopeType;
import com.aipal.memory.MemoryStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MemoryRecallService {

    private final AiMemoryItemMapper memoryItemMapper;
    private final MemoryAccessScopeResolver accessScopeResolver;
    private final MemoryBudgetAllocator budgetAllocator;
    private final MemoryCaptureService captureService;
    private final MemoryPiiSanitizer piiSanitizer;
    private final MemoryVectorProjectionService vectorProjectionService;

    public List<MemoryRecallCandidate> recall(MemoryRecallRequest request) {
        MemoryAccessScope scope = accessScopeResolver.resolve(request.projectKey());
        LambdaQueryWrapper<AiMemoryItem> query = new LambdaQueryWrapper<AiMemoryItem>()
                .eq(AiMemoryItem::getTenantId, scope.tenantId())
                .eq(AiMemoryItem::getStatus, MemoryStatus.ACTIVE.name())
                .and(wrapper -> wrapper.isNull(AiMemoryItem::getExpiresAt)
                        .or().gt(AiMemoryItem::getExpiresAt, LocalDateTime.now()))
                .and(wrapper -> {
                    wrapper.eq(AiMemoryItem::getScopeType, MemoryScopeType.TENANT.name())
                            .or(user -> user.eq(AiMemoryItem::getScopeType, MemoryScopeType.USER.name())
                                    .eq(AiMemoryItem::getOwnerUserId, scope.userId()));
                    if (scope.projectKey() != null) {
                        wrapper.or(project -> project.eq(AiMemoryItem::getScopeType, MemoryScopeType.PROJECT.name())
                                .eq(AiMemoryItem::getProjectKey, scope.projectKey()));
                    }
                });
        return memoryItemMapper.selectList(query).stream()
                .map(item -> new MemoryRecallCandidate(item, score(item, request.requestSummary()),
                        budgetAllocator.estimateTokens(item.getContent()), "CANDIDATE"))
                .sorted(Comparator.comparingDouble(MemoryRecallCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.memory().getUpdateTime(), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<MemoryRecallCandidate> recallWorking(MemoryRecallRequest request) {
        MemoryAccessScope scope = accessScopeResolver.resolve(null);
        return captureService.listAuthorizedWorkingMemories(scope).stream()
                .filter(Map.class::isInstance)
                .map(value -> (Map<String, Object>) value)
                .map(event -> workingCandidate(event, request.requestSummary()))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingDouble(MemoryRecallCandidate::score).reversed())
                .toList();
    }

    public List<MemoryRecallCandidate> recallVector(MemoryRecallRequest request, com.aipal.entity.AiMemoryPolicy policy) {
        MemoryAccessScope scope = accessScopeResolver.resolve(null);
        return vectorProjectionService.query(scope, policy, request.requestSummary()).stream()
                .map(reference -> vectorCandidate(reference, request.requestSummary(), scope))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingDouble(MemoryRecallCandidate::score).reversed())
                .toList();
    }

    private MemoryRecallCandidate vectorCandidate(MemoryVectorProjectionService.VectorReference reference,
                                                   String requestSummary, MemoryAccessScope scope) {
        AiMemoryItem item = memoryItemMapper.selectById(reference.memoryId());
        if (!isCurrentAndReadable(item, reference.version(), scope)) return null;
        double semanticScore = 1.0d / (1.0d + Math.max(0d, reference.distance()));
        double combinedScore = score(item, requestSummary) * 0.6d + semanticScore * 0.4d;
        return new MemoryRecallCandidate(item, combinedScore, budgetAllocator.estimateTokens(item.getContent()), "VECTOR_CANDIDATE");
    }

    private boolean isCurrentAndReadable(AiMemoryItem item, Integer expectedVersion, MemoryAccessScope scope) {
        if (item == null || expectedVersion == null || !expectedVersion.equals(item.getVersion())) return false;
        if (!scope.tenantId().equals(item.getTenantId()) || !MemoryStatus.ACTIVE.name().equals(item.getStatus())) return false;
        if (item.getExpiresAt() != null && !item.getExpiresAt().isAfter(LocalDateTime.now())) return false;
        if (MemoryScopeType.TENANT.name().equals(item.getScopeType())) return true;
        if (MemoryScopeType.USER.name().equals(item.getScopeType())) return scope.userId() != null && scope.userId().equals(item.getOwnerUserId());
        return MemoryScopeType.PROJECT.name().equals(item.getScopeType())
                && scope.projectKey() != null && scope.projectKey().equals(item.getProjectKey());
    }

    private MemoryRecallCandidate workingCandidate(Map<String, Object> event, String requestSummary) {
        String content = first(event, "outputSummary", "inputSummary");
        MemoryPiiSanitizer.SanitizedContent sanitized = piiSanitizer.sanitize(content);
        if (sanitized.content().isBlank()) return null;
        AiMemoryItem item = new AiMemoryItem();
        String eventId = value(event, "eventId");
        item.setMemoryCode("WORKING_" + (eventId.isBlank() ? Integer.toHexString(sanitized.content().hashCode()) : eventId));
        item.setMemoryType("WORKING");
        item.setScopeType(value(event, "scopeType"));
        item.setScopeKey(value(event, "scopeKey"));
        item.setProjectKey(value(event, "projectKey"));
        item.setContent(sanitized.content());
        item.setSourceType(value(event, "sourceType"));
        item.setSensitivity(sanitized.redacted() ? "CONFIDENTIAL" : value(event, "sensitivity"));
        item.setImportance(55);
        item.setConfidence(java.math.BigDecimal.valueOf(0.6));
        item.setStatus(MemoryStatus.ACTIVE.name());
        item.setUpdateTime(LocalDateTime.now());
        return new MemoryRecallCandidate(item, score(item, requestSummary), budgetAllocator.estimateTokens(item.getContent()), "WORKING_CANDIDATE");
    }

    private double score(AiMemoryItem item, String requestSummary) {
        double importance = (item.getImportance() == null ? 50 : item.getImportance()) / 100.0 * 0.45;
        double confidence = (item.getConfidence() == null ? 0.5 : item.getConfidence().doubleValue()) * 0.4;
        double lexical = containsUsefulTerm(item.getContent(), requestSummary) ? 0.15 : 0;
        return importance + confidence + lexical;
    }

    private boolean containsUsefulTerm(String memory, String request) {
        if (memory == null || request == null || request.isBlank()) return false;
        return java.util.Arrays.stream(request.split("\\s+"))
                .filter(term -> term.length() >= 2)
                .anyMatch(memory::contains);
    }

    private String first(Map<String, Object> event, String first, String second) {
        String firstValue = value(event, first);
        return firstValue.isBlank() ? value(event, second) : firstValue;
    }

    private String value(Map<String, Object> event, String key) {
        Object value = event.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
