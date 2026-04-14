package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.AgentAuthRequest;
import com.aipal.entity.BizAgentAuth;
import com.aipal.service.BizAgentAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/business-modules/{moduleId}/agent-auth")
@RequiredArgsConstructor
public class AgentAuthController {

    private final BizAgentAuthService bizAgentAuthService;

    @GetMapping
    public Result<List<BizAgentAuth>> listAgentAuths(@PathVariable Long moduleId) {
        return Result.success(bizAgentAuthService.getAuthsByModuleId(moduleId));
    }

    @PostMapping
    public Result<Boolean> authorizeAgent(
            @PathVariable Long moduleId,
            @RequestBody AgentAuthRequest request) {
        request.setBizModuleId(moduleId);
        return Result.success(bizAgentAuthService.authorizeAgent(request));
    }

    @PutMapping("/{authId}")
    public Result<Boolean> updateAgentAuth(
            @PathVariable Long moduleId,
            @PathVariable Long authId,
            @RequestBody AgentAuthRequest request) {
        request.setBizModuleId(moduleId);
        return Result.success(bizAgentAuthService.updateAgentAuth(authId, request));
    }

    @DeleteMapping("/{authId}")
    public Result<Boolean> deleteAgentAuth(
            @PathVariable Long moduleId,
            @PathVariable Long authId) {
        return Result.success(bizAgentAuthService.deleteAgentAuth(authId));
    }
}
