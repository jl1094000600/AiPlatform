package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.config.JwtConfig;
import com.aipal.entity.AiUserMemory;
import com.aipal.security.RequirePermission;
import com.aipal.service.UserMemoryService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user-memories")
@RequiredArgsConstructor
public class UserMemoryController {

    private final UserMemoryService userMemoryService;
    private final JwtConfig jwtConfig;

    @GetMapping
    @RequirePermission("agent:list")
    public Result<Page<AiUserMemory>> listCompressed(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String userKey,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            HttpServletRequest request) {
        UserIdentity identity = currentIdentity(request);
        String effectiveUserKey = userKey != null && !userKey.isBlank()
                ? userKey : userMemoryService.normalizeUserKey(null, userId != null ? userId : identity.userId(), username != null ? username : identity.username());
        return Result.success(userMemoryService.listCompressedMemories(pageNum, pageSize, effectiveUserKey, null, null));
    }

    @GetMapping("/short-term")
    @RequirePermission("agent:list")
    public Result<List<Object>> listShortTerm(@RequestParam(required = false) String userKey,
                                              HttpServletRequest request) {
        UserIdentity identity = currentIdentity(request);
        String effectiveUserKey = userKey != null && !userKey.isBlank()
                ? userKey : userMemoryService.normalizeUserKey(null, identity.userId(), identity.username());
        return Result.success(userMemoryService.listShortMemories(effectiveUserKey));
    }

    @PostMapping("/compress")
    @RequirePermission("agent:update")
    public Result<AiUserMemory> compress(@RequestParam(required = false) String userKey,
                                         HttpServletRequest request) {
        UserIdentity identity = currentIdentity(request);
        String effectiveUserKey = userKey != null && !userKey.isBlank()
                ? userKey : userMemoryService.normalizeUserKey(null, identity.userId(), identity.username());
        return Result.success(userMemoryService.compressUserMemories(effectiveUserKey));
    }

    @DeleteMapping("/short-term")
    @RequirePermission("agent:update")
    public Result<Boolean> clearShortTerm(@RequestParam(required = false) String userKey,
                                          HttpServletRequest request) {
        UserIdentity identity = currentIdentity(request);
        String effectiveUserKey = userKey != null && !userKey.isBlank()
                ? userKey : userMemoryService.normalizeUserKey(null, identity.userId(), identity.username());
        userMemoryService.clearShortMemories(effectiveUserKey);
        return Result.success(true);
    }

    private UserIdentity currentIdentity(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return new UserIdentity(null, null);
        }
        String token = authHeader.substring(7);
        try {
            if (!jwtConfig.validateToken(token)) {
                return new UserIdentity(null, null);
            }
            return new UserIdentity(jwtConfig.getUserIdFromToken(token), jwtConfig.getUsernameFromToken(token));
        } catch (Exception ignored) {
            return new UserIdentity(null, null);
        }
    }

    private record UserIdentity(Long userId, String username) {
    }
}
