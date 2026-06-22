package com.aipal.service.memory;

/**
 * Normalized event emitted by business flows. The tenant is always taken from
 * TenantContext by MemoryCaptureService rather than accepted from this event.
 */
public record MemoryCaptureEvent(
        String sourceType,
        String sourceRef,
        String memoryType,
        String scopeType,
        String scopeKey,
        String projectType,
        String projectKey,
        Long userId,
        String username,
        String sessionId,
        String inputSummary,
        String outputSummary,
        Integer tokenCount,
        String traceId,
        String sensitivity
) {
}
