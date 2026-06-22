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

@Service
@RequiredArgsConstructor
public class MemoryRecallService {

    private final AiMemoryItemMapper memoryItemMapper;
    private final MemoryAccessScopeResolver accessScopeResolver;
    private final MemoryBudgetAllocator budgetAllocator;

    public List<MemoryRecallCandidate> recall(MemoryRecallRequest request) {
        MemoryAccessScope scope = accessScopeResolver.resolve(request.projectKey());
        LambdaQueryWrapper<AiMemoryItem> query = new LambdaQueryWrapper<AiMemoryItem>()
                .eq(AiMemoryItem::getTenantId, scope.tenantId())
                .eq(AiMemoryItem::getStatus, MemoryStatus.ACTIVE.name())
                .and(wrapper -> wrapper.isNull(AiMemoryItem::getExpiresAt)
                        .or().gt(AiMemoryItem::getExpiresAt, LocalDateTime.now()))
                .and(wrapper -> wrapper.eq(AiMemoryItem::getScopeType, MemoryScopeType.TENANT.name())
                        .or(user -> user.eq(AiMemoryItem::getScopeType, MemoryScopeType.USER.name())
                                .eq(AiMemoryItem::getOwnerUserId, scope.userId())));
        return memoryItemMapper.selectList(query).stream()
                .map(item -> new MemoryRecallCandidate(item, score(item, request.requestSummary()),
                        budgetAllocator.estimateTokens(item.getContent()), "CANDIDATE"))
                .sorted(Comparator.comparingDouble(MemoryRecallCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.memory().getUpdateTime(), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
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
}
