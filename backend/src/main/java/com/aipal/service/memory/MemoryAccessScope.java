package com.aipal.service.memory;

import java.util.Set;

/**
 * Server-derived authorization boundary used by all memory reads and writes.
 * Tenant id is never accepted from a caller payload.
 */
public record MemoryAccessScope(
        Long tenantId,
        Long userId,
        String username,
        String projectKey,
        Set<String> readableScopeTypes,
        boolean canManageTenantMemory,
        boolean canManageProjectMemory
) {
}
