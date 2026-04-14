package com.aipal.controller;

import com.aipal.common.PasswordEncoder;
import com.aipal.common.Result;
import com.aipal.config.JwtConfig;
import com.aipal.dto.LoginRequest;
import com.aipal.dto.LoginResponse;
import com.aipal.entity.SysUser;
import com.aipal.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtConfig jwtConfig;

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
        String token = jwtConfig.generateToken(user.getId(), user.getUsername());
        LoginResponse response = new LoginResponse(token, user.getId(), user.getUsername(), user.getRealName(), List.of());
        return Result.success(response);
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
}
