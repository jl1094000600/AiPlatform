package com.aipal.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantContextTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void tenantIdFailsClosedWithoutContext() {
        assertThrows(IllegalStateException.class, TenantContext::tenantId);
    }

    @Test
    void tenantIdReturnsExplicitTenant() {
        TenantContext.set(new TenantContext.Context(
                7L, "tester", 42L, "tenant-42", false, Set.of("developer"), Set.of("agent:list")));

        assertEquals(42L, TenantContext.tenantId());
    }
}
