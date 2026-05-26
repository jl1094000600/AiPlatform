package com.aipal.service;

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
import com.aipal.mapper.SysUserMapper;
import com.aipal.mapper.SysUserRoleMapper;
import com.aipal.mapper.SysUserTenantMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantManagementService {
    private final SysTenantMapper tenantMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysMenuMapper menuMapper;
    private final SysUserTenantMapper userTenantMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;

    public Page<SysTenant> listTenants(int pageNum, int pageSize) {
        return tenantMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SysTenant>().orderByDesc(SysTenant::getCreateTime));
    }

    @Transactional
    public SysTenant saveTenant(SysTenant request) {
        LocalDateTime now = LocalDateTime.now();
        if (request.getId() == null) {
            request.setStatus(request.getStatus() == null ? 1 : request.getStatus());
            request.setCreateTime(now);
        }
        request.setUpdateTime(now);
        if (request.getId() == null) {
            tenantMapper.insert(request);
        } else {
            tenantMapper.updateById(request);
        }
        return request;
    }

    public Page<SysUser> listMembers(Long tenantId, int pageNum, int pageSize) {
        List<Long> userIds = userTenantMapper.selectList(new LambdaQueryWrapper<SysUserTenant>()
                        .eq(SysUserTenant::getTenantId, tenantId)
                        .eq(SysUserTenant::getStatus, 1))
                .stream().map(SysUserTenant::getUserId).toList();
        if (userIds.isEmpty()) return new Page<>(pageNum, pageSize);
        return userMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SysUser>().in(SysUser::getId, userIds).orderByDesc(SysUser::getCreateTime));
    }

    @Transactional
    public Boolean addMember(Long tenantId, Map<String, Object> request) {
        Long userId = asLong(request.get("userId"));
        if (userId == null) throw new IllegalArgumentException("userId is required");
        SysUserTenant membership = userTenantMapper.selectOne(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getTenantId, tenantId)
                .eq(SysUserTenant::getUserId, userId));
        boolean exists = membership != null;
        if (!exists) {
            membership = new SysUserTenant();
            membership.setTenantId(tenantId);
            membership.setUserId(userId);
            membership.setCreateTime(LocalDateTime.now());
        }
        membership.setTenantRole(String.valueOf(request.getOrDefault("tenantRole", "member")));
        membership.setDefaultTenant(asBooleanInt(request.get("defaultTenant")));
        membership.setStatus(1);
        membership.setUpdateTime(LocalDateTime.now());
        if (exists) {
            userTenantMapper.updateById(membership);
        } else {
            userTenantMapper.insert(membership);
        }
        Object roleIds = request.get("roleIds");
        if (roleIds instanceof List<?> list) {
            replaceUserRoles(tenantId, userId, list.stream().map(this::asLong).filter(id -> id != null).toList());
        }
        return true;
    }

    @Transactional
    public Boolean updateMemberRoles(Long tenantId, Long userId, List<Long> roleIds) {
        replaceUserRoles(tenantId, userId, roleIds);
        return true;
    }

    public List<SysRole> listRoles(Long tenantId) {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, tenantId)
                .orderByAsc(SysRole::getId));
    }

    public List<Long> listMemberRoleIds(Long tenantId, Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getTenantId, tenantId)
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
    }

    public List<SysPermission> listPermissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .orderByAsc(SysPermission::getId));
    }

    public List<SysMenu> listMenus() {
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSortOrder));
    }

    public List<Long> listRolePermissionIds(Long tenantId, Long roleId) {
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getTenantId, tenantId)
                        .eq(SysRolePermission::getRoleId, roleId))
                .stream().map(SysRolePermission::getPermissionId).toList();
    }

    @Transactional
    public Boolean updateRolePermissions(Long tenantId, Long roleId, List<Long> permissionIds) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getTenantId, tenantId)
                .eq(SysRolePermission::getRoleId, roleId));
        for (Long permissionId : permissionIds) {
            SysRolePermission item = new SysRolePermission();
            item.setTenantId(tenantId);
            item.setRoleId(roleId);
            item.setPermissionId(permissionId);
            item.setCreateTime(LocalDateTime.now());
            rolePermissionMapper.insert(item);
        }
        return true;
    }

    @Transactional
    public SysMenu saveMenu(SysMenu request) {
        LocalDateTime now = LocalDateTime.now();
        if (request.getId() == null) {
            request.setStatus(request.getStatus() == null ? 1 : request.getStatus());
            request.setVisible(request.getVisible() == null ? 1 : request.getVisible());
            request.setCreateTime(now);
            request.setUpdateTime(now);
            menuMapper.insert(request);
        } else {
            request.setUpdateTime(now);
            menuMapper.updateById(request);
        }
        return request;
    }

    private void replaceUserRoles(Long tenantId, Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getTenantId, tenantId)
                .eq(SysUserRole::getUserId, userId));
        for (Long roleId : roleIds) {
            SysUserRole userRole = new SysUserRole();
            userRole.setTenantId(tenantId);
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRole.setCreateTime(LocalDateTime.now());
            userRoleMapper.insert(userRole);
        }
    }

    private Integer asBooleanInt(Object value) {
        if (value instanceof Boolean bool) return bool ? 1 : 0;
        if (value instanceof Number number) return number.intValue();
        return 0;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }
}
