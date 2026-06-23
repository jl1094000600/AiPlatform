package com.aipal.service.memory;

import com.aipal.memory.MemoryScopeType;
import com.aipal.memory.MemoryStatus;
import com.aipal.memory.MemoryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryCaptureService {

    static final int MAX_WORKING_ITEMS = 200;
    static final long WORKING_TTL_HOURS = 8;
    private static final String WORKING_PREFIX = "aipal:memory:working:";
    private static final String WORKING_SCOPE_SET_PREFIX = "aipal:memory:working:scopes:";
    private static final String EXTRACTED_EVENT_PREFIX = "aipal:memory:extracted:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final MemoryAccessScopeResolver accessScopeResolver;

    public void capture(MemoryCaptureEvent event) {
        if (event == null || isBlank(event.sourceType())) {
            return;
        }
        MemoryAccessScope accessScope = accessScopeResolver.resolve(event.projectKey());
        String scopeType = resolveScopeType(event, accessScope);
        String scopeKey = resolveScopeKey(event, accessScope, scopeType);
        if (scopeKey == null) {
            log.warn("Skipping memory capture without an authorized scope, source={}", event.sourceType());
            return;
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("eventId", UUID.randomUUID().toString());
        item.put("tenantId", accessScope.tenantId());
        item.put("scopeType", scopeType);
        item.put("scopeKey", scopeKey);
        item.put("projectType", accessScope.projectKey() == null ? null : "TRUSTED_CONTEXT");
        item.put("projectKey", accessScope.projectKey());
        // Identity is always derived from the authenticated tenant context. Legacy
        // callers may still populate these event fields, but they cannot impersonate
        // another owner through the capture contract.
        item.put("userId", accessScope.userId());
        item.put("username", accessScope.username());
        item.put("sessionId", trimToNull(event.sessionId()));
        item.put("sourceType", event.sourceType().trim());
        item.put("sourceRef", trimToNull(event.sourceRef()));
        item.put("memoryType", defaultValue(event.memoryType(), MemoryType.WORKING.name()));
        item.put("status", MemoryStatus.PENDING_REVIEW.name());
        item.put("sensitivity", defaultValue(event.sensitivity(), "INTERNAL"));
        item.put("inputSummary", truncate(event.inputSummary(), 8_000));
        item.put("outputSummary", truncate(event.outputSummary(), 16_000));
        item.put("tokenCount", event.tokenCount() == null ? 0 : Math.max(0, event.tokenCount()));
        item.put("traceId", trimToNull(event.traceId()));
        item.put("createTime", LocalDateTime.now().toString());

        try {
            String key = workingKey(accessScope.tenantId(), scopeType, scopeKey);
            redisTemplate.opsForList().rightPush(key, item);
            redisTemplate.opsForList().trim(key, -MAX_WORKING_ITEMS, -1);
            redisTemplate.expire(key, WORKING_TTL_HOURS, TimeUnit.HOURS);
            redisTemplate.opsForSet().add(workingScopeSetKey(accessScope.tenantId()), scopeType + "|" + sanitizeScopeKey(scopeKey));
        } catch (RuntimeException ex) {
            // Memory capture is auxiliary: business calls must survive Redis failures.
            log.warn("Failed to capture working memory, source={}, reason={}", event.sourceType(), ex.getMessage());
        }
    }

    public List<Object> listWorkingMemories(String scopeType, String scopeKey) {
        if (isBlank(scopeType) || isBlank(scopeKey)) return List.of();
        try {
            List<Object> values = redisTemplate.opsForList().range(
                    workingKey(accessScopeResolver.resolve(null).tenantId(), scopeType, scopeKey), 0, -1);
            return values == null ? List.of() : values;
        } catch (RuntimeException ex) {
            log.warn("Failed to list working memory, reason={}", ex.getMessage());
            return List.of();
        }
    }

    public void clearWorkingMemories(String scopeType, String scopeKey) {
        if (isBlank(scopeType) || isBlank(scopeKey)) return;
        try {
            redisTemplate.delete(workingKey(accessScopeResolver.resolve(null).tenantId(), scopeType, scopeKey));
        } catch (RuntimeException ex) {
            log.warn("Failed to clear working memory, reason={}", ex.getMessage());
        }
    }

    public List<WorkingScope> listWorkingScopes() {
        Long tenantId = accessScopeResolver.resolve(null).tenantId();
        try {
            java.util.Set<Object> values = redisTemplate.opsForSet().members(workingScopeSetKey(tenantId));
            if (values == null || values.isEmpty()) return List.of();
            return values.stream()
                    .map(String::valueOf)
                    .map(value -> value.split("\\|", 2))
                    .filter(parts -> parts.length == 2)
                    .map(parts -> new WorkingScope(parts[0], parts[1]))
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Failed to list working memory scopes, reason={}", ex.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> claimUnextracted(WorkingScope scope) {
        List<Object> raw = listWorkingMemories(scope.scopeType(), scope.scopeKey());
        List<Map<String, Object>> claimed = new java.util.ArrayList<>();
        Long tenantId = accessScopeResolver.resolve(null).tenantId();
        for (Object value : raw) {
            if (!(value instanceof Map<?, ?> source)) continue;
            Object id = source.get("eventId");
            if (id == null) continue;
            Boolean accepted = redisTemplate.opsForValue().setIfAbsent(
                    EXTRACTED_EVENT_PREFIX + tenantId + ":" + id, "1", WORKING_TTL_HOURS, TimeUnit.HOURS);
            if (!Boolean.TRUE.equals(accepted)) continue;
            Map<String, Object> event = new LinkedHashMap<>();
            source.forEach((key, itemValue) -> event.put(String.valueOf(key), itemValue));
            claimed.add(event);
        }
        return claimed;
    }

    public void releaseClaim(String eventId) {
        if (isBlank(eventId)) return;
        try {
            Long tenantId = accessScopeResolver.resolve(null).tenantId();
            redisTemplate.delete(EXTRACTED_EVENT_PREFIX + tenantId + ":" + eventId);
        } catch (RuntimeException ex) {
            log.warn("Failed to release memory extraction claim, eventId={}, reason={}", eventId, ex.getMessage());
        }
    }

    private String resolveScopeType(MemoryCaptureEvent event, MemoryAccessScope accessScope) {
        if (accessScope.projectKey() != null) return MemoryScopeType.PROJECT.name();
        if (accessScope.userId() != null) return MemoryScopeType.USER.name();
        return MemoryScopeType.TENANT.name();
    }

    private String resolveScopeKey(MemoryCaptureEvent event, MemoryAccessScope accessScope, String scopeType) {
        return switch (MemoryScopeType.valueOf(scopeType)) {
            case USER -> accessScope.userId() == null ? null : "user:" + accessScope.userId();
            case PROJECT -> accessScope.projectKey();
            case SESSION -> trimToNull(event.sessionId());
            case TENANT -> "tenant:" + accessScope.tenantId();
        };
    }

    private String workingKey(Long tenantId, String scopeType, String scopeKey) {
        return WORKING_PREFIX + tenantId + ":" + scopeType + ":" + sanitizeScopeKey(scopeKey);
    }

    private String workingScopeSetKey(Long tenantId) {
        return WORKING_SCOPE_SET_PREFIX + tenantId;
    }

    private String sanitizeScopeKey(String scopeKey) {
        return scopeKey.replaceAll("[^a-zA-Z0-9:_-]", "_");
    }

    private String defaultValue(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "\n...[truncated]";
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record WorkingScope(String scopeType, String scopeKey) {
    }
}
