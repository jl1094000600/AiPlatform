package com.aipal.service.memory;

import com.aipal.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryAccessScopeResolverTest {

    private final MemoryTrustedProjectContext trustedProjectContext = new MemoryTrustedProjectContext();
    private final MemoryAccessScopeResolver resolver = new MemoryAccessScopeResolver(trustedProjectContext);

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void resolvesTenantAndUserFromServerContext() {
        TenantContext.set(new TenantContext.Context(42L, "alice", 7L, "tenant-a", false,
                Set.of("developer"), Set.of("memory:write")));

        MemoryAccessScope scope = resolver.resolve(" delivery-platform ");

        assertEquals(7L, scope.tenantId());
        assertEquals(42L, scope.userId());
        assertEquals("alice", scope.username());
        assertNull(scope.projectKey());
        assertFalse(scope.canManageTenantMemory());
        assertFalse(scope.canManageProjectMemory());
        assertTrue(scope.readableScopeTypes().contains("USER"));
    }

    @Test
    void ignoresCallerSuppliedProjectScopeUntilTrustedProjectContextExists() {
        TenantContext.set(new TenantContext.Context(10L, "bob", 3L, "tenant-b", false,
                Set.of(), Set.of("memory:read")));

        MemoryAccessScope scope = resolver.resolve("another-tenant-project");

        assertEquals(3L, scope.tenantId());
        assertNull(scope.projectKey());
        assertFalse(scope.canManageProjectMemory());
    }

    @Test
    void resolvesOnlyPipelineDerivedProjectScope() {
        TenantContext.set(new TenantContext.Context(10L, "bob", 3L, "tenant-b", false,
                Set.of(), Set.of("memory:read")));
        com.aipal.entity.AutomationPipeline pipeline = new com.aipal.entity.AutomationPipeline();
        pipeline.setId(99L);
        pipeline.setInitiatorUserId(10L);

        try (MemoryTrustedProjectContext.ProjectScope ignored = trustedProjectContext.openPipeline(pipeline)) {
            MemoryAccessScope scope = resolver.resolve("forged-project");
            assertEquals("pipeline:99", scope.projectKey());
            assertTrue(scope.readableScopeTypes().contains("PROJECT"));
            assertTrue(scope.canManageProjectMemory());
        }
    }
}
