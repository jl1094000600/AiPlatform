package com.aipal.service;

import com.aipal.dto.AuthMenu;
import com.aipal.dto.AuthTenant;
import com.aipal.dto.LoginResponse;
import com.aipal.entity.SysMenu;
import com.aipal.entity.SysPermission;
import com.aipal.entity.SysRole;
import com.aipal.entity.SysRolePermission;
import com.aipal.entity.SysTenant;
import com.aipal.entity.SysUser;
import com.aipal.entity.SysUserRole;
import com.aipal.entity.SysUserTenant;
import com.aipal.mapper.SysMenuMapper;
import com.aipal.mapper.SysPermissionMapper;
import com.aipal.mapper.SysRoleMapper;
import com.aipal.mapper.SysRolePermissionMapper;
import com.aipal.mapper.SysTenantMapper;
import com.aipal.mapper.SysUserRoleMapper;
import com.aipal.mapper.SysUserTenantMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantAuthService {
    private final SysTenantMapper tenantMapper;
    private final SysUserTenantMapper userTenantMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysMenuMapper menuMapper;

    public LoginResponse buildLoginResponse(SysUser user, String token, Long requestedTenantId) {
        Long tenantId = resolveTenantId(user, requestedTenantId);
        AuthTenant currentTenant = toAuthTenant(requireTenant(tenantId), defaultFlag(user, tenantId));
        List<AuthTenant> tenants = listUserTenants(user.getId());
        List<String> roles = listRoleCodes(user, tenantId);
        List<String> permissions = listPermissionCodes(user, tenantId);
        List<AuthMenu> menus = listMenus(permissions, isPlatformAdmin(user));
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());
        response.setTenant(currentTenant);
        response.setTenants(tenants);
        response.setRoles(roles);
        response.setPermissions(permissions);
        response.setMenus(menus);
        response.setPlatformAdmin(isPlatformAdmin(user));
        return response;
    }

    public Long resolveTenantId(SysUser user, Long requestedTenantId) {
        if (user == null) throw new IllegalArgumentException("用户不存在");
        List<SysUserTenant> memberships = userTenantMapper.selectList(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getUserId, user.getId())
                .eq(SysUserTenant::getStatus, 1));
        if (requestedTenantId != null) {
            if (memberships.stream().anyMatch(item -> requestedTenantId.equals(item.getTenantId()))) {
                return requestedTenantId;
            }
            throw new SecurityException("无权访问指定租户");
        }
        if (user.getDefaultTenantId() != null && memberships.stream().anyMatch(item -> user.getDefaultTenantId().equals(item.getTenantId()))) {
            return user.getDefaultTenantId();
        }
        return memberships.stream()
                .filter(item -> Objects.equals(item.getDefaultTenant(), 1))
                .map(SysUserTenant::getTenantId)
                .findFirst()
                .or(() -> memberships.stream().map(SysUserTenant::getTenantId).findFirst())
                .orElseThrow(() -> new SecurityException("用户未加入任何有效租户"));
    }

    public List<AuthTenant> listUserTenants(Long userId) {
        List<SysUserTenant> memberships = userTenantMapper.selectList(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getUserId, userId)
                .eq(SysUserTenant::getStatus, 1));
        if (memberships.isEmpty()) return List.of();
        Map<Long, Integer> defaultFlags = memberships.stream()
                .collect(Collectors.toMap(SysUserTenant::getTenantId, item -> item.getDefaultTenant() == null ? 0 : item.getDefaultTenant(), (a, b) -> a));
        return tenantMapper.selectList(new LambdaQueryWrapper<SysTenant>()
                        .in(SysTenant::getId, defaultFlags.keySet())
                        .eq(SysTenant::getStatus, 1))
                .stream()
                .map(tenant -> toAuthTenant(tenant, defaultFlags.getOrDefault(tenant.getId(), 0)))
                .toList();
    }

    public List<String> listRoleCodes(SysUser user, Long tenantId) {
        if (isPlatformAdmin(user)) {
            return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().eq(SysRole::getTenantId, tenantId))
                    .stream().map(SysRole::getRoleCode).distinct().toList();
        }
        List<SysUserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, user.getId())
                .eq(SysUserRole::getTenantId, tenantId));
        if (userRoles.isEmpty()) return List.of("readonly");
        return roleMapper.selectBatchIds(userRoles.stream().map(SysUserRole::getRoleId).toList())
                .stream().map(SysRole::getRoleCode).distinct().toList();
    }

    public List<String> listPermissionCodes(SysUser user, Long tenantId) {
        if (isPlatformAdmin(user)) {
            return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>())
                    .stream().map(SysPermission::getPermissionCode).distinct().toList();
        }
        List<SysUserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, user.getId())
                .eq(SysUserRole::getTenantId, tenantId));
        if (userRoles.isEmpty()) return List.of("dashboard:view");
        Set<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toSet());
        List<SysRolePermission> rolePermissions = rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getTenantId, tenantId)
                .in(SysRolePermission::getRoleId, roleIds));
        if (rolePermissions.isEmpty()) return List.of("dashboard:view");
        return permissionMapper.selectBatchIds(rolePermissions.stream().map(SysRolePermission::getPermissionId).collect(Collectors.toSet()))
                .stream().map(SysPermission::getPermissionCode).distinct().toList();
    }

    public List<AuthMenu> listMenus(List<String> permissions, boolean platformAdmin) {
        Set<String> permissionSet = new HashSet<>(permissions == null ? List.of() : permissions);
        List<AuthMenu> flat = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getStatus, 1)
                        .eq(SysMenu::getVisible, 1)
                        .orderByAsc(SysMenu::getSortOrder))
                .stream()
                .filter(menu -> platformAdmin || menu.getPermissionCode() == null || menu.getPermissionCode().isBlank()
                        || permissionSet.contains(menu.getPermissionCode()))
                .map(this::toAuthMenu)
                .toList();
        return toTree(flat);
    }

    private List<AuthMenu> toTree(List<AuthMenu> flat) {
        Map<Long, AuthMenu> byId = new LinkedHashMap<>();
        flat.forEach(menu -> byId.put(menu.getId(), menu));
        List<AuthMenu> roots = new ArrayList<>();
        for (AuthMenu menu : flat) {
            if (menu.getParentId() != null && byId.containsKey(menu.getParentId())) {
                byId.get(menu.getParentId()).getChildren().add(menu);
            } else {
                roots.add(menu);
            }
        }
        Comparator<AuthMenu> comparator = Comparator.comparing(menu -> menu.getSortOrder() == null ? 0 : menu.getSortOrder());
        roots.removeIf(menu -> (menu.getPath() == null || menu.getPath().isBlank()) && menu.getChildren().isEmpty());
        roots.sort(comparator);
        byId.values().forEach(menu -> menu.getChildren().sort(comparator));
        return roots;
    }

    private AuthTenant toAuthTenant(SysTenant tenant, Integer defaultTenant) {
        return new AuthTenant(tenant.getId(), tenant.getTenantCode(), tenant.getTenantName(), tenant.getStatus(), defaultTenant);
    }

    private AuthMenu toAuthMenu(SysMenu menu) {
        AuthMenu authMenu = new AuthMenu();
        authMenu.setId(menu.getId());
        authMenu.setMenuCode(menu.getMenuCode());
        authMenu.setMenuName(menu.getMenuName());
        authMenu.setPath(menu.getPath());
        authMenu.setIcon(menu.getIcon());
        authMenu.setPermissionCode(menu.getPermissionCode());
        authMenu.setParentId(menu.getParentId());
        authMenu.setSortOrder(menu.getSortOrder());
        authMenu.setChildren(new ArrayList<>());
        return authMenu;
    }

    private SysTenant requireTenant(Long tenantId) {
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || !Objects.equals(tenant.getStatus(), 1)
                || (tenant.getExpireTime() != null && !tenant.getExpireTime().isAfter(java.time.LocalDateTime.now()))) {
            throw new SecurityException("租户不存在或不可用：" + tenantId);
        }
        return tenant;
    }

    private Integer defaultFlag(SysUser user, Long tenantId) {
        return tenantId != null && tenantId.equals(user.getDefaultTenantId()) ? 1 : 0;
    }

    public boolean isPlatformAdmin(SysUser user) {
        return user != null && Objects.equals(user.getPlatformAdmin(), 1);
    }
}
