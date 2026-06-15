package com.aipal.service;

import com.aipal.entity.AiModel;
import com.aipal.entity.AiUserMemory;
import com.aipal.mapper.AiModelMapper;
import com.aipal.mapper.AiUserMemoryMapper;
import com.aipal.security.TenantContext;
import com.aipal.security.TenantTaskRunner;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserMemoryService {

    private static final String SHORT_MEMORY_PREFIX = "aipal:memory:short:";
    private static final String MEMORY_USER_SET = "aipal:memory:users";
    private static final int MAX_SHORT_MEMORY_ITEMS = 200;
    private static final int MAX_MEMORY_TEXT_CHARS = 80_000;

    private final RedisTemplate<String, Object> redisTemplate;
    private final AiUserMemoryMapper userMemoryMapper;
    private final AiModelMapper modelMapper;
    private final ObjectMapper objectMapper;

    @Autowired
    private TenantTaskRunner tenantTaskRunner;

    public void appendPipelineMemory(String userKey, Long userId, String username,
                                     Long pipelineId, Long stageRunId, String stage,
                                     String inputSummary, String outputSummary,
                                     Integer inputTokens, Integer outputTokens, Integer totalTokens) {
        String effectiveUserKey = normalizeUserKey(userKey, userId, username);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("memoryId", UUID.randomUUID().toString());
        item.put("userKey", effectiveUserKey);
        item.put("userId", userId);
        item.put("username", username);
        item.put("sourceType", "PIPELINE");
        item.put("pipelineId", pipelineId);
        item.put("stageRunId", stageRunId);
        item.put("stage", stage);
        item.put("inputSummary", truncate(inputSummary, 8_000));
        item.put("outputSummary", truncate(outputSummary, 16_000));
        item.put("inputTokens", inputTokens == null ? 0 : inputTokens);
        item.put("outputTokens", outputTokens == null ? 0 : outputTokens);
        item.put("totalTokens", totalTokens == null ? 0 : totalTokens);
        item.put("createTime", LocalDateTime.now().toString());

        try {
            String key = shortMemoryKey(effectiveUserKey);
            redisTemplate.opsForList().rightPush(key, item);
            redisTemplate.opsForList().trim(key, -MAX_SHORT_MEMORY_ITEMS, -1);
            redisTemplate.expire(key, 8, TimeUnit.HOURS);
            redisTemplate.opsForSet().add(memoryUserSetKey(), effectiveUserKey);
        } catch (RuntimeException e) {
            log.warn("Failed to append short-term user memory for {}: {}", effectiveUserKey, e.getMessage());
        }
    }

    public List<Object> listShortMemories(String userKey) {
        if (userKey == null || userKey.isBlank()) {
            return List.of();
        }
        try {
            List<Object> values = redisTemplate.opsForList().range(shortMemoryKey(userKey), 0, -1);
            return values == null ? List.of() : values;
        } catch (RuntimeException e) {
            log.warn("Failed to list short-term user memory for {}: {}", userKey, e.getMessage());
            return List.of();
        }
    }

    public Page<AiUserMemory> listCompressedMemories(int pageNum, int pageSize, String userKey, Long userId, String username) {
        Page<AiUserMemory> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AiUserMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(userKey != null && !userKey.isBlank(), AiUserMemory::getUserKey, userKey)
                .eq(userId != null, AiUserMemory::getUserId, userId)
                .eq(username != null && !username.isBlank(), AiUserMemory::getUsername, username)
                .orderByDesc(AiUserMemory::getCreateTime);
        return userMemoryMapper.selectPage(page, wrapper);
    }

    public AiUserMemory compressUserMemories(String userKey) {
        List<Object> raw = listShortMemories(userKey);
        if (raw.isEmpty()) {
            return null;
        }
        AiUserMemory memory = compressRawMemories(userKey, raw);
        userMemoryMapper.insert(memory);
        clearShortMemories(userKey);
        return memory;
    }

    public void clearShortMemories(String userKey) {
        if (userKey == null || userKey.isBlank()) return;
        try {
            redisTemplate.delete(shortMemoryKey(userKey));
        } catch (RuntimeException e) {
            log.warn("Failed to clear short-term user memory for {}: {}", userKey, e.getMessage());
        }
    }

    @Scheduled(fixedRate = 14_400_000, initialDelay = 120_000)
    public void compressAllShortMemories() {
        tenantTaskRunner.forEachActiveTenant("user-memory-compression", tenant -> compressCurrentTenantMemories());
    }

    private void compressCurrentTenantMemories() {
        Set<Object> userKeys;
        try {
            userKeys = redisTemplate.opsForSet().members(memoryUserSetKey());
        } catch (RuntimeException e) {
            log.warn("Failed to scan user memory keys: {}", e.getMessage());
            return;
        }
        if (userKeys == null || userKeys.isEmpty()) {
            return;
        }
        for (Object value : userKeys) {
            String userKey = String.valueOf(value);
            try {
                compressUserMemories(userKey);
            } catch (Exception e) {
                log.warn("Failed to compress user memory for {}: {}", userKey, e.getMessage());
            }
        }
    }

    public String normalizeUserKey(String userKey, Long userId, String username) {
        if (userKey != null && !userKey.isBlank()) return userKey;
        if (userId != null) return "user:" + userId;
        if (username != null && !username.isBlank()) return "username:" + username;
        return "system:unknown";
    }

    private AiUserMemory compressRawMemories(String userKey, List<Object> raw) {
        List<Map<String, Object>> items = normalizeRawItems(raw);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = items.stream()
                .map(item -> parseTime(item.get("createTime")))
                .filter(time -> time != null)
                .min(LocalDateTime::compareTo)
                .orElse(now);
        LocalDateTime end = items.stream()
                .map(item -> parseTime(item.get("createTime")))
                .filter(time -> time != null)
                .max(LocalDateTime::compareTo)
                .orElse(now);

        Map<String, Object> first = items.isEmpty() ? Map.of() : items.get(0);
        String summary = compressWithModelOrFallback(items);
        AiUserMemory memory = new AiUserMemory();
        memory.setMemoryCode("MEM_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        memory.setUserKey(userKey);
        memory.setUserId(asLong(first.get("userId")));
        memory.setUsername(asString(first.get("username")));
        memory.setSourceType("PIPELINE");
        memory.setSourceId(asLong(first.get("pipelineId")));
        memory.setSummaryContent(summary);
        memory.setRawCount(items.size());
        memory.setCompressionModel(resolveCompressionModelName());
        memory.setMemoryStartTime(start);
        memory.setMemoryEndTime(end);
        memory.setCreateTime(now);
        memory.setUpdateTime(now);
        memory.setIsDeleted(0);
        return memory;
    }

    private List<Map<String, Object>> normalizeRawItems(List<Object> raw) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object value : raw) {
            if (value instanceof Map<?, ?> source) {
                Map<String, Object> item = new LinkedHashMap<>();
                source.forEach((key, itemValue) -> item.put(String.valueOf(key), itemValue));
                items.add(item);
            }
        }
        return items;
    }

    private String compressWithModelOrFallback(List<Map<String, Object>> items) {
        String memoryText = truncate(toMemoryText(items), MAX_MEMORY_TEXT_CHARS);
        AiModel model = resolveCompressionModel();
        if (model == null) {
            return fallbackSummary(items);
        }
        try {
            String endpoint = model.getEndpoint().endsWith("/")
                    ? model.getEndpoint() + "chat/completions"
                    : model.getEndpoint() + "/chat/completions";
            String prompt = """
                    Compress the following user pipeline memories into a stable long-term memory.
                    Keep user intent, project facts, constraints, generated decisions, and useful preferences.
                    Do not include unrelated users. Return concise Markdown in Chinese.

                    Memories:
                    %s
                    """.formatted(memoryText);
            Map<String, Object> body = Map.of(
                    "model", model.getModelCode(),
                    "temperature", model.getDefaultTemperature() == null ? BigDecimal.valueOf(0.2) : model.getDefaultTemperature(),
                    "max_tokens", Math.min(model.getMaxTokens() == null ? 2048 : model.getMaxTokens(), 4096),
                    "messages", List.of(
                            Map.of("role", "system", "content", "You compress user memory safely and never mix users."),
                            Map.of("role", "user", "content", prompt)
                    )
            );
            String response = RestClient.create()
                    .post()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + model.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            return content.isBlank() ? fallbackSummary(items) : content;
        } catch (Exception e) {
            log.warn("Failed to compress user memory with model: {}", e.getMessage());
            return fallbackSummary(items);
        }
    }

    private AiModel resolveCompressionModel() {
        return modelMapper.selectOne(
                new LambdaQueryWrapper<AiModel>()
                        .eq(AiModel::getStatus, 1)
                        .isNotNull(AiModel::getEndpoint)
                        .isNotNull(AiModel::getApiKey)
                        .orderByDesc(AiModel::getUpdateTime)
                        .last("LIMIT 1")
        );
    }

    private String resolveCompressionModelName() {
        AiModel model = resolveCompressionModel();
        return model == null ? "local-fallback" : model.getModelCode();
    }

    private String fallbackSummary(List<Map<String, Object>> items) {
        StringBuilder builder = new StringBuilder("# 用户记忆压缩摘要\n\n");
        builder.append("- 原始记忆条数：").append(items.size()).append('\n');
        for (Map<String, Object> item : items) {
            builder.append("- ")
                    .append(asString(item.get("createTime"))).append(" / ")
                    .append(asString(item.get("stage"))).append(" / pipeline ")
                    .append(asString(item.get("pipelineId"))).append("：")
                    .append(truncate(asString(item.get("outputSummary")), 500))
                    .append('\n');
        }
        return builder.toString();
    }

    private String toMemoryText(List<Map<String, Object>> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return items.toString();
        }
    }

    private String shortMemoryKey(String userKey) {
        return SHORT_MEMORY_PREFIX + TenantContext.tenantId() + ":"
                + userKey.replaceAll("[^a-zA-Z0-9:_-]", "_");
    }

    private String memoryUserSetKey() {
        return MEMORY_USER_SET + ":" + TenantContext.tenantId();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "\n...[truncated]";
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private LocalDateTime parseTime(Object value) {
        if (value == null) return null;
        try {
            return LocalDateTime.parse(String.valueOf(value));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
