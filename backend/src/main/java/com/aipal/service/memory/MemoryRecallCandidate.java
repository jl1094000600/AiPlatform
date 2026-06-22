package com.aipal.service.memory;

import com.aipal.entity.AiMemoryItem;

public record MemoryRecallCandidate(AiMemoryItem memory, double score, int estimatedTokens, String reason) {
    public MemoryRecallCandidate withReason(String value) {
        return new MemoryRecallCandidate(memory, score, estimatedTokens, value);
    }
}
