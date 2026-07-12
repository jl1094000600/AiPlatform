package com.aipal.service;

import com.aipal.entity.SysUser;
import com.aipal.entity.SysUserTenant;
import com.aipal.mapper.SysUserMapper;
import com.aipal.mapper.SysUserTenantMapper;
import com.aipal.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final SysUserTenantMapper userTenantMapper;

    public Page<SysUser> listUsers(int pageNum, int pageSize) {
        Page<SysUser> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (!TenantContext.platformAdmin()) {
            List<Long> userIds = activeTenantUserIds(TenantContext.tenantId());
            if (userIds.isEmpty()) {
                return page;
            }
            wrapper.in(SysUser::getId, userIds);
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        return userMapper.selectPage(page, wrapper);
    }

    public SysUser getUserById(Long id) {
        requireCurrentTenantAccess(id);
        return userMapper.selectById(id);
    }

    public SysUser getUserByUsername(String username) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        return userMapper.selectOne(wrapper);
    }

    @Transactional
    public boolean saveUser(SysUser user) {
        Long tenantId = TenantContext.get() == null ? null : TenantContext.tenantId();
        if (tenantId != null && user.getDefaultTenantId() == null) {
            user.setDefaultTenantId(tenantId);
        }
        boolean saved = userMapper.insert(user) > 0;
        if (saved && tenantId != null) {
            ensureTenantMembership(user.getId(), tenantId);
        }
        return saved;
    }

    public boolean updateUser(SysUser user) {
        requireCurrentTenantAccess(user.getId());
        return userMapper.updateById(user) > 0;
    }

    public boolean updatePasswordHash(Long userId, String passwordHash) {
        if (userId == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        SysUser update = new SysUser();
        update.setPassword(passwordHash);
        update.setUpdateTime(LocalDateTime.now());
        return userMapper.update(update, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)) > 0;
    }

    public boolean deleteUser(Long id) {
        requireCurrentTenantAccess(id);
        return userMapper.deleteById(id) > 0;
    }

    private List<Long> activeTenantUserIds(Long tenantId) {
        return userTenantMapper.selectList(new LambdaQueryWrapper<SysUserTenant>()
                        .eq(SysUserTenant::getTenantId, tenantId)
                        .eq(SysUserTenant::getStatus, 1))
                .stream()
                .map(SysUserTenant::getUserId)
                .toList();
    }

    private void requireCurrentTenantAccess(Long userId) {
        if (userId == null || TenantContext.platformAdmin() || TenantContext.get() == null) {
            return;
        }
        Long count = userTenantMapper.selectCount(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getTenantId, TenantContext.tenantId())
                .eq(SysUserTenant::getUserId, userId)
                .eq(SysUserTenant::getStatus, 1));
        if (count == null || count == 0) {
            throw new SecurityException("User is not a member of the current tenant");
        }
    }

    private void ensureTenantMembership(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return;
        }
        SysUserTenant existing = userTenantMapper.selectOne(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getUserId, userId)
                .eq(SysUserTenant::getTenantId, tenantId));
        if (existing != null) {
            if (existing.getStatus() == null || existing.getStatus() != 1) {
                existing.setStatus(1);
                existing.setUpdateTime(LocalDateTime.now());
                userTenantMapper.updateById(existing);
            }
            return;
        }
        SysUserTenant membership = new SysUserTenant();
        membership.setUserId(userId);
        membership.setTenantId(tenantId);
        membership.setTenantRole("member");
        membership.setDefaultTenant(0);
        membership.setStatus(1);
        membership.setCreateTime(LocalDateTime.now());
        membership.setUpdateTime(LocalDateTime.now());
        userTenantMapper.insert(membership);
    }
}
