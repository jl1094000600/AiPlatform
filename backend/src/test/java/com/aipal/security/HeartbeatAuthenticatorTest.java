package com.aipal.security;

import com.aipal.entity.SysTenant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HeartbeatAuthenticatorTest {
    private static final String SECRET = "test-only-agent-heartbeat-secret-32-bytes";

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void authenticatesTokenAndRunsInsideResolvedTenant() {
        TenantTaskRunner runner = mock(TenantTaskRunner.class);
        SysTenant tenant = new SysTenant();
        tenant.setId(12L);
        tenant.setTenantCode("tenant-12");
        when(runner.requireActiveTenant("tenant-12")).thenReturn(tenant);
        doAnswer(invocation -> {
            Supplier<?> task = invocation.getArgument(2);
            TenantContext.Context previous = TenantContext.get();
            try {
                TenantContext.set(new TenantContext.Context(
                        null, "system:heartbeat", 12L, "tenant-12", false,
                        java.util.Set.of(), java.util.Set.of()));
                return task.get();
            } finally {
                TenantContext.set(previous);
            }
        }).when(runner).callForTenant(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(tenant),
                org.mockito.ArgumentMatchers.any());

        HeartbeatAuthenticator authenticator = new HeartbeatAuthenticator(runner, SECRET);
        AtomicLong observedTenant = new AtomicLong();

        authenticator.authenticateAndRun(
                "tenant-12", HeartbeatAuthenticator.tokenFor(SECRET, "tenant-12"),
                () -> observedTenant.set(TenantContext.tenantId()));

        assertEquals(12L, observedTenant.get());
        assertNull(TenantContext.get());
    }

    @Test
    void rejectsInvalidTokenBeforeRunningTask() {
        HeartbeatAuthenticator authenticator = new HeartbeatAuthenticator(mock(TenantTaskRunner.class), SECRET);

        assertThrows(SecurityException.class,
                () -> authenticator.authenticateAndRun("tenant-12", "00", () -> { }));
    }
}
