package com.aipal.service;

import com.aipal.entity.SysUser;
import com.aipal.entity.SysUserTenant;
import com.aipal.mapper.SysMenuMapper;
import com.aipal.mapper.SysPermissionMapper;
import com.aipal.mapper.SysRoleMapper;
import com.aipal.mapper.SysRolePermissionMapper;
import com.aipal.mapper.SysTenantMapper;
import com.aipal.mapper.SysUserRoleMapper;
import com.aipal.mapper.SysUserTenantMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantAuthServiceTest {

    @Test
    void rejectsUsersWithoutTenantMembershipInsteadOfFallingBackToTenantOne() {
        SysUserTenantMapper userTenantMapper = mock(SysUserTenantMapper.class);
        when(userTenantMapper.selectList(any())).thenReturn(List.of());
        TenantAuthService service = service(userTenantMapper);
        SysUser user = new SysUser();
        user.setId(9L);

        assertThrows(SecurityException.class, () -> service.resolveTenantId(user, null));
        assertEquals(List.of(), service.listUserTenants(user.getId()));
    }

    @Test
    void rejectsRequestedTenantOutsideUserMemberships() {
        SysUserTenantMapper userTenantMapper = mock(SysUserTenantMapper.class);
        SysUserTenant membership = new SysUserTenant();
        membership.setTenantId(3L);
        membership.setStatus(1);
        when(userTenantMapper.selectList(any())).thenReturn(List.of(membership));
        TenantAuthService service = service(userTenantMapper);
        SysUser user = new SysUser();
        user.setId(9L);

        assertThrows(SecurityException.class, () -> service.resolveTenantId(user, 4L));
    }

    private TenantAuthService service(SysUserTenantMapper userTenantMapper) {
        return new TenantAuthService(
                mock(SysTenantMapper.class),
                userTenantMapper,
                mock(SysUserRoleMapper.class),
                mock(SysRoleMapper.class),
                mock(SysRolePermissionMapper.class),
                mock(SysPermissionMapper.class),
                mock(SysMenuMapper.class)
        );
    }
}
