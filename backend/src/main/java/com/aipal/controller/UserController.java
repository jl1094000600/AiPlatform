package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.SysUser;
import com.aipal.service.UserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public Result<Page<SysUser>> listUsers(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(userService.listUsers(pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public Result<SysUser> getUser(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    @PostMapping
    public Result<Boolean> createUser(@RequestBody SysUser user) {
        return Result.success(userService.saveUser(user));
    }

    @PutMapping("/{id}")
    public Result<Boolean> updateUser(@PathVariable Long id, @RequestBody SysUser user) {
        user.setId(id);
        return Result.success(userService.updateUser(user));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteUser(@PathVariable Long id) {
        return Result.success(userService.deleteUser(id));
    }
}
