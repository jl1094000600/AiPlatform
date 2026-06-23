package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.AiMemoryPolicy;
import com.aipal.security.RequirePermission;
import com.aipal.service.memory.MemoryPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/memory-policies")
@RequiredArgsConstructor
public class MemoryPolicyController {

    private final MemoryPolicyService policyService;

    @GetMapping("/effective")
    @RequirePermission("memory:policy")
    public Result<AiMemoryPolicy> effective(@RequestParam(required = false) String projectKey) {
        return Result.success(policyService.resolveEffectivePolicy(projectKey));
    }

    @PutMapping
    @RequirePermission("memory:policy")
    public Result<AiMemoryPolicy> save(@RequestBody AiMemoryPolicy policy) {
        return Result.success(policyService.save(policy));
    }
}
