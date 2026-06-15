package com.aipal.controller;

import com.aipal.security.TenantContext;
import com.aipal.service.TenantManagementService;
import com.aipal.common.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class TenantManagementControllerSecurityTest {

    private final TenantManagementController controller =
            new TenantManagementController(mock(TenantManagementService.class));

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void tenantAdministratorCannotTargetAnotherTenant() {
        TenantContext.set(context(7L, false));

        assertThrows(BizException.class, () -> controller.listMembers(1, 20, 8L));
        assertThrows(BizException.class, () -> controller.updateMemberRoles(1L, 8L, java.util.List.of()));
    }

    @Test
    void tenantAdministratorCannotManageTenantRegistry() {
        TenantContext.set(context(7L, false));

        assertThrows(BizException.class, () -> controller.listTenants(1, 20));
    }

    private TenantContext.Context context(Long tenantId, boolean platformAdmin) {
        return new TenantContext.Context(1L, "admin", tenantId, "tenant-" + tenantId,
                platformAdmin, Set.of("tenant-admin"), Set.of("tenant:manage", "member:manage"));
    }
}
