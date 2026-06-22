package com.aipal.service.memory;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class MemoryBudgetAllocator {

    public Allocation allocate(List<MemoryRecallCandidate> rankedCandidates, int tokenBudget) {
        int remaining = Math.max(0, tokenBudget);
        List<MemoryRecallCandidate> accepted = new ArrayList<>();
        List<MemoryRecallCandidate> rejected = new ArrayList<>();
        for (MemoryRecallCandidate candidate : rankedCandidates) {
            if (candidate.estimatedTokens() > remaining) {
                rejected.add(candidate.withReason("TOKEN_BUDGET_EXCEEDED"));
                continue;
            }
            accepted.add(candidate.withReason("SELECTED"));
            remaining -= candidate.estimatedTokens();
        }
        return new Allocation(List.copyOf(accepted), List.copyOf(rejected), tokenBudget - remaining);
    }

    public int estimateTokens(String content) {
        if (content == null || content.isBlank()) return 0;
        return Math.max(1, (content.length() + 3) / 4);
    }

    public record Allocation(List<MemoryRecallCandidate> accepted,
                             List<MemoryRecallCandidate> rejected,
                             int tokenCount) {
    }
}
