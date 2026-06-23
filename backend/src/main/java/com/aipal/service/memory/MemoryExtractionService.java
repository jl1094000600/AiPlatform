package com.aipal.service.memory;

import com.aipal.entity.AiMemoryItem;
import com.aipal.mapper.AiMemoryItemMapper;
import com.aipal.memory.MemoryStatus;
import com.aipal.memory.MemoryType;
import com.aipal.security.TenantContext;
import com.aipal.security.TenantTaskRunner;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryExtractionService {

    private final MemoryCaptureService captureService;
    private final MemoryPiiSanitizer piiSanitizer;
    private final AiMemoryItemMapper memoryItemMapper;
    private final TenantTaskRunner tenantTaskRunner;

    @Scheduled(fixedDelayString = "${aipal.memory.extraction-delay-ms:60000}", initialDelayString = "${aipal.memory.extraction-initial-delay-ms:120000}")
    public void extractWorkingMemories() {
        tenantTaskRunner.forEachActiveTenant("memory-extraction", tenant -> extractCurrentTenant());
    }

    @Transactional
    public int extractCurrentTenant() {
        int extracted = 0;
        for (MemoryCaptureService.WorkingScope scope : captureService.listWorkingScopes()) {
            List<Map<String, Object>> events = captureService.claimUnextracted(scope);
            for (Map<String, Object> event : events) {
                try {
                    if (persistIfNew(event)) extracted++;
                } catch (RuntimeException ex) {
                    captureService.releaseClaim(value(event, "eventId"));
                    log.warn("Memory extraction failed and will be retried, sourceRef={}, reason={}",
                            value(event, "sourceRef"), ex.getMessage());
                }
            }
        }
        return extracted;
    }

    private boolean persistIfNew(Map<String, Object> event) {
        String sourceRef = value(event, "sourceRef") + "#" + value(event, "eventId");
        Long existing = memoryItemMapper.selectCount(new LambdaQueryWrapper<AiMemoryItem>()
                .eq(AiMemoryItem::getTenantId, TenantContext.tenantId())
                .eq(AiMemoryItem::getSourceRef, sourceRef));
        if (existing != null && existing > 0) return false;

        String rawContent = nonBlank(value(event, "outputSummary"), value(event, "inputSummary"));
        MemoryPiiSanitizer.SanitizedContent sanitized = piiSanitizer.sanitize(rawContent);
        if (sanitized.content().isBlank()) return false;

        AiMemoryItem item = new AiMemoryItem();
        item.setTenantId(TenantContext.tenantId());
        item.setMemoryCode("MEM_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        item.setMemoryType(classify(sanitized.content()).name());
        item.setScopeType(value(event, "scopeType"));
        item.setScopeKey(value(event, "scopeKey"));
        item.setProjectType(value(event, "projectType"));
        item.setProjectKey(value(event, "projectKey"));
        item.setOwnerUserId(asLong(event.get("userId")));
        item.setOwnerUsername(value(event, "username"));
        item.setTitle(title(sanitized.content()));
        item.setContent(sanitized.content());
        item.setSourceType(value(event, "sourceType"));
        item.setSourceRef(sourceRef);
        item.setSensitivity(sanitized.redacted() ? "CONFIDENTIAL" : defaultValue(value(event, "sensitivity"), "INTERNAL"));
        item.setImportance(50);
        item.setConfidence(BigDecimal.valueOf(sanitized.redacted() ? 0.5 : 0.75));
        item.setStatus(sanitized.redacted() ? MemoryStatus.PENDING_REVIEW.name() : MemoryStatus.ACTIVE.name());
        item.setVersion(1);
        item.setValidFrom(LocalDateTime.now());
        item.setCreatedBy(TenantContext.username());
        item.setIsDeleted(0);
        memoryItemMapper.insert(item);
        return true;
    }

    private MemoryType classify(String content) {
        if (content.contains("偏好") || content.contains("优先")) return MemoryType.PREFERENCE;
        if (content.contains("必须") || content.contains("禁止") || content.contains("约束")) return MemoryType.CONSTRAINT;
        if (content.contains("决定") || content.contains("确认")) return MemoryType.DECISION;
        if (content.contains("经验") || content.contains("复盘")) return MemoryType.EXPERIENCE;
        return MemoryType.FACT;
    }

    private String title(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.substring(0, Math.min(80, normalized.length()));
    }

    private String value(Map<String, Object> event, String key) {
        Object value = event.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String nonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
