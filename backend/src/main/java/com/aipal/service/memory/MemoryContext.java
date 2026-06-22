package com.aipal.service.memory;

import java.util.List;

public record MemoryContext(
        String traceId,
        String recallMode,
        int policyVersion,
        boolean shouldInject,
        String promptSection,
        int tokenCount,
        List<MemoryRecallCandidate> selected
) {
    public static MemoryContext empty(String traceId, String recallMode, int policyVersion) {
        return new MemoryContext(traceId, recallMode, policyVersion, false, "", 0, List.of());
    }
}
