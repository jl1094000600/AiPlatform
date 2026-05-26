package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.SysMenu;
import com.aipal.entity.SysTenant;
import com.aipal.security.RequirePermission;
import com.aipal.security.TenantContext;
import com.aipal.service.TenantManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenant-admin")
@RequiredArgsConstructor
public class TenantManagementController {
    private final TenantManagementService tenantManagementService;

    @GetMapping("/tenants")
    @RequirePermission("tenant:manage")
    public Result<?> listTenants(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(tenantManagementService.listTenants(pageNum, pageSize));
    }

    @PostMapping("/tenants")
    @RequirePermission("tenant:manage")
    public Result<?> createTenant(@RequestBody SysTenant request) {
        return Result.success(tenantManagementService.saveTenant(request));
    }

    @PutMapping("/tenants/{id}")
    @RequirePermission("tenant:manage")
    public Result<?> updateTenant(@PathVariable Long id, @RequestBody SysTenant request) {
        request.setId(id);
        return Result.success(tenantManagementService.saveTenant(request));
    }

    @GetMapping("/members")
    @RequirePermission("member:manage")
    public Result<?> listMembers(@RequestParam(defaultValue = "1") int pageNum,
                                 @RequestParam(defaultValue = "20") int pageSize,
                                 @RequestParam(required = false) Long tenantId) {
        return Result.success(tenantManagementService.listMembers(resolveTenantId(tenantId), pageNum, pageSize));
    }

    @PostMapping("/members")
    @RequirePermission("member:manage")
    public Result<?> addMember(@RequestBody Map<String, Object> request) {
        Long tenantId = request.get("tenantId") instanceof Number number ? number.longValue() : TenantContext.tenantId();
        return Result.success(tenantManagementService.addMember(tenantId, request));
    }

    @PutMapping("/members/{userId}/roles")
    @RequirePermission("member:manage")
    public Result<?> updateMemberRoles(@PathVariable Long userId,
                                       @RequestParam(required = false) Long tenantId,
                                       @RequestBody List<Long> roleIds) {
        return Result.success(tenantManagementService.updateMemberRoles(resolveTenantId(tenantId), userId, roleIds));
    }

    @GetMapping("/roles")
    @RequirePermission("role:manage")
    public Result<?> listRoles(@RequestParam(required = false) Long tenantId) {
        return Result.success(tenantManagementService.listRoles(resolveTenantId(tenantId)));
    }

    @GetMapping("/members/{userId}/roles")
    @RequirePermission("member:manage")
    public Result<?> listMemberRoleIds(@PathVariable Long userId,
                                       @RequestParam(required = false) Long tenantId) {
        return Result.success(tenantManagementService.listMemberRoleIds(resolveTenantId(tenantId), userId));
    }

    @GetMapping("/permissions")
    @RequirePermission("role:manage")
    public Result<?> listPermissions() {
        return Result.success(tenantManagementService.listPermissions());
    }

    @PutMapping("/roles/{roleId}/permissions")
    @RequirePermission("role:manage")
    public Result<?> updateRolePermissions(@PathVariable Long roleId,
                                           @RequestParam(required = false) Long tenantId,
                                           @RequestBody List<Long> permissionIds) {
        return Result.success(tenantManagementService.updateRolePermissions(resolveTenantId(tenantId), roleId, permissionIds));
    }

    @GetMapping("/roles/{roleId}/permissions")
    @RequirePermission("role:manage")
    public Result<?> listRolePermissionIds(@PathVariable Long roleId,
                                           @RequestParam(required = false) Long tenantId) {
        return Result.success(tenantManagementService.listRolePermissionIds(resolveTenantId(tenantId), roleId));
    }

    @GetMapping("/menus")
    @RequirePermission("menu:manage")
    public Result<?> listMenus() {
        return Result.success(tenantManagementService.listMenus());
    }

    @PostMapping("/menus")
    @RequirePermission("menu:manage")
    public Result<?> saveMenu(@RequestBody SysMenu request) {
        return Result.success(tenantManagementService.saveMenu(request));
    }

    @PutMapping("/menus/{id}")
    @RequirePermission("menu:manage")
    public Result<?> updateMenu(@PathVariable Long id, @RequestBody SysMenu request) {
        request.setId(id);
        return Result.success(tenantManagementService.saveMenu(request));
    }

    private Long resolveTenantId(Long tenantId) {
        return tenantId == null ? TenantContext.tenantId() : tenantId;
    }
}
