package com.aipal.service.memory;

import com.aipal.entity.AiMemoryRecallTrace;
import com.aipal.mapper.AiMemoryRecallTraceMapper;
import com.aipal.security.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MemoryTraceService {

    private final AiMemoryRecallTraceMapper traceMapper;
    private final ObjectMapper objectMapper;

    public void record(String traceId, MemoryRecallRequest request, String recallMode, Integer policyVersion,
                       List<MemoryRecallCandidate> candidates, List<MemoryRecallCandidate> injected,
                       int tokenCount, long durationMs) {
        AiMemoryRecallTrace trace = new AiMemoryRecallTrace();
        trace.setTenantId(TenantContext.tenantId());
        trace.setTraceId(traceId);
        trace.setUserId(TenantContext.userId());
        trace.setAgentId(request.agentId());
        trace.setProjectKey(request.projectKey());
        trace.setRecallMode(recallMode);
        trace.setPolicyVersion(policyVersion);
        trace.setRequestSummary(truncate(request.requestSummary(), 2_000));
        trace.setCandidatesJson(toJson(candidates));
        trace.setInjectedJson(toJson(injected));
        trace.setTokenCount(tokenCount);
        trace.setDurationMs(durationMs);
        trace.setCreateTime(LocalDateTime.now());
        traceMapper.insert(trace);
    }

    private String toJson(List<MemoryRecallCandidate> candidates) {
        List<Map<String, Object>> view = candidates.stream().map(candidate -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("memoryId", candidate.memory().getId());
            item.put("memoryCode", candidate.memory().getMemoryCode());
            item.put("memoryType", candidate.memory().getMemoryType());
            item.put("sourceType", candidate.memory().getSourceType());
            item.put("score", candidate.score());
            item.put("tokens", candidate.estimatedTokens());
            item.put("reason", candidate.reason());
            return item;
        }).toList();
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
