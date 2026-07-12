package com.aipal.service;

import com.aipal.entity.SysUserTenant;
import com.aipal.mapper.SysUserMapper;
import com.aipal.mapper.SysUserTenantMapper;
import com.aipal.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void tenantAdminCannotReadUserOutsideCurrentTenant() {
        SysUserTenantMapper userTenantMapper = mock(SysUserTenantMapper.class);
        when(userTenantMapper.selectCount(any())).thenReturn(0L);
        UserService service = new UserService(mock(SysUserMapper.class), userTenantMapper);
        TenantContext.set(new TenantContext.Context(7L, "tenant-admin", 42L, "tenant-42",
                false, Set.of("tenant_admin"), Set.of("member:manage")));

        assertThrows(SecurityException.class, () -> service.getUserById(99L));
    }

    @Test
    void platformAdminCanReadGlobalUser() {
        UserService service = new UserService(mock(SysUserMapper.class), mock(SysUserTenantMapper.class));
        TenantContext.set(new TenantContext.Context(1L, "platform-admin", 1L, "default",
                true, Set.of("platform_admin"), Set.of("member:manage")));

        service.getUserById(99L);
    }
}
