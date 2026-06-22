package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.common.BizException;
import com.aipal.config.JwtConfig;
import com.aipal.entity.AiUserMemory;
import com.aipal.security.RequirePermission;
import com.aipal.security.TenantContext;
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
    @RequirePermission("memory:list")
    public Result<Page<AiUserMemory>> listCompressed(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String userKey,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            HttpServletRequest request) {
        String effectiveUserKey = resolveAccessibleUserKey(request, userKey, userId, username);
        return Result.success(userMemoryService.listCompressedMemories(pageNum, pageSize, effectiveUserKey, null, null));
    }

    @GetMapping("/short-term")
    @RequirePermission("memory:list")
    public Result<List<Object>> listShortTerm(@RequestParam(required = false) String userKey,
                                              HttpServletRequest request) {
        String effectiveUserKey = resolveAccessibleUserKey(request, userKey, null, null);
        return Result.success(userMemoryService.listShortMemories(effectiveUserKey));
    }

    @PostMapping("/compress")
    @RequirePermission("memory:write")
    public Result<AiUserMemory> compress(@RequestParam(required = false) String userKey,
                                         HttpServletRequest request) {
        String effectiveUserKey = resolveAccessibleUserKey(request, userKey, null, null);
        return Result.success(userMemoryService.compressUserMemories(effectiveUserKey));
    }

    @DeleteMapping("/short-term")
    @RequirePermission("memory:write")
    public Result<Boolean> clearShortTerm(@RequestParam(required = false) String userKey,
                                          HttpServletRequest request) {
        String effectiveUserKey = resolveAccessibleUserKey(request, userKey, null, null);
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

    private String resolveAccessibleUserKey(HttpServletRequest request, String requestedUserKey,
                                            Long requestedUserId, String requestedUsername) {
        UserIdentity identity = currentIdentity(request);
        String currentUserKey = userMemoryService.normalizeUserKey(null, identity.userId(), identity.username());
        String requested = requestedUserKey != null && !requestedUserKey.isBlank()
                ? requestedUserKey.trim()
                : userMemoryService.normalizeUserKey(null,
                requestedUserId == null ? identity.userId() : requestedUserId,
                requestedUsername == null || requestedUsername.isBlank() ? identity.username() : requestedUsername);
        if (!TenantContext.hasPermission("memory:policy") && !currentUserKey.equals(requested)) {
            throw new BizException(403, "只能访问当前用户的记忆");
        }
        return requested;
    }

    private record UserIdentity(Long userId, String username) {
    }
}
