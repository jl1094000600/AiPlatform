package com.aipal.controller;

import com.aipal.common.PasswordEncoder;
import com.aipal.common.Result;
import com.aipal.config.JwtConfig;
import com.aipal.dto.LoginRequest;
import com.aipal.dto.LoginResponse;
import com.aipal.dto.SwitchTenantRequest;
import com.aipal.entity.SysUser;
import com.aipal.security.TenantContext;
import com.aipal.service.TenantAuthService;
import com.aipal.service.UserService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtConfig jwtConfig;
    private final TenantAuthService tenantAuthService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        SysUser user = userService.getUserByUsername(request.getUsername());
        if (user == null) {
            return Result.unauthorized("用户不存在");
        }
        if (!PasswordEncoder.matches(request.getPassword(), user.getPassword())) {
            return Result.unauthorized("密码错误");
        }
        if (user.getStatus() != 1) {
            return Result.forbidden("用户已禁用");
        }
        Long tenantId = tenantAuthService.resolveTenantId(user, null);
        List<String> roles = tenantAuthService.listRoleCodes(user, tenantId);
        List<String> permissions = tenantAuthService.listPermissionCodes(user, tenantId);
        String tenantCode = tenantAuthService.listUserTenants(user.getId()).stream()
                .filter(tenant -> tenantId.equals(tenant.getId()))
                .findFirst()
                .map(tenant -> tenant.getTenantCode())
                .orElse("think_land");
        String token = jwtConfig.generateToken(user.getId(), user.getUsername(), tenantId, tenantCode,
                roles, permissions, tenantAuthService.isPlatformAdmin(user));
        return Result.success(tenantAuthService.buildLoginResponse(user, token, tenantId));
    }

    @PostMapping("/register")
    public Result<Boolean> register(@Valid @RequestBody SysUser user) {
        SysUser existingUser = userService.getUserByUsername(user.getUsername());
        if (existingUser != null) {
            return Result.badRequest("用户名已存在");
        }
        user.setPassword(PasswordEncoder.encode(user.getPassword()));
        user.setStatus(1);
        return Result.success(userService.saveUser(user));
    }

    @GetMapping("/me")
    public Result<LoginResponse> me(HttpServletRequest request) {
        String token = bearerToken(request);
        SysUser user = userService.getUserByUsername(jwtConfig.getUsernameFromToken(token));
        return Result.success(tenantAuthService.buildLoginResponse(user, token, jwtConfig.getTenantIdFromToken(token)));
    }

    @GetMapping("/menus")
    public Result<?> menus() {
        TenantContext.Context context = TenantContext.get();
        return Result.success(tenantAuthService.listMenus(
                context == null ? List.of() : List.copyOf(context.permissions()),
                context != null && context.platformAdmin()));
    }

    @GetMapping("/tenants")
    public Result<?> tenants(HttpServletRequest request) {
        String token = bearerToken(request);
        return Result.success(tenantAuthService.listUserTenants(jwtConfig.getUserIdFromToken(token)));
    }

    @PostMapping("/switch-tenant")
    public Result<LoginResponse> switchTenant(@RequestBody SwitchTenantRequest request,
                                              HttpServletRequest httpRequest) {
        String oldToken = bearerToken(httpRequest);
        SysUser user = userService.getUserByUsername(jwtConfig.getUsernameFromToken(oldToken));
        Long tenantId = tenantAuthService.resolveTenantId(user, request.getTenantId());
        List<String> roles = tenantAuthService.listRoleCodes(user, tenantId);
        List<String> permissions = tenantAuthService.listPermissionCodes(user, tenantId);
        String tenantCode = tenantAuthService.listUserTenants(user.getId()).stream()
                .filter(tenant -> tenantId.equals(tenant.getId()))
                .findFirst()
                .map(tenant -> tenant.getTenantCode())
                .orElse("think_land");
        String token = jwtConfig.generateToken(user.getId(), user.getUsername(), tenantId, tenantCode,
                roles, permissions, tenantAuthService.isPlatformAdmin(user));
        return Result.success(tenantAuthService.buildLoginResponse(user, token, tenantId));
    }

    private String bearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("未登录");
        }
        String token = authHeader.substring(7);
        if (!jwtConfig.validateToken(token)) {
            throw new IllegalArgumentException("登录已失效");
        }
        return token;
    }
}
