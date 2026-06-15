package com.aipal.security;

import com.aipal.entity.SysTenant;
import com.aipal.mapper.SysTenantMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantTaskRunnerTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void runsEveryActiveTenantWithIsolatedSystemContextAndRestoresCaller() {
        SysTenantMapper mapper = mock(SysTenantMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(tenant(11L, "tenant-a"), tenant(22L, "tenant-b")));
        TenantTaskRunner runner = new TenantTaskRunner(mapper);
        TenantContext.Context caller = new TenantContext.Context(
                7L, "caller", 99L, "caller-tenant", false, Set.of(), Set.of());
        TenantContext.set(caller);
        List<String> observed = new ArrayList<>();

        runner.forEachActiveTenant("heartbeat", tenant -> observed.add(
                TenantContext.tenantId() + ":" + TenantContext.username() + ":" + tenant.getTenantCode()));

        assertEquals(List.of(
                "11:system:heartbeat:tenant-a",
                "22:system:heartbeat:tenant-b"), observed);
        assertEquals(caller, TenantContext.get());
    }

    private SysTenant tenant(Long id, String code) {
        SysTenant tenant = new SysTenant();
        tenant.setId(id);
        tenant.setTenantCode(code);
        tenant.setStatus(1);
        return tenant;
    }
}
