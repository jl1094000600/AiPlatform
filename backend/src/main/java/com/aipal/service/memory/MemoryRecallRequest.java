package com.aipal.service.memory;

public record MemoryRecallRequest(Long agentId, String projectKey, String requestSummary) {
}
