package com.aipal.service.memory;

import com.aipal.memory.MemoryScopeType;
import com.aipal.security.TenantContext;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class MemoryAccessScopeResolver {

    public MemoryAccessScope resolve(String ignoredRequestedProjectKey) {
        Long tenantId = TenantContext.tenantId();
        Long userId = TenantContext.userId();
        boolean manageTenant = TenantContext.hasPermission("memory:policy");
        boolean manageProject = manageTenant || TenantContext.hasPermission("memory:write");

        Set<MemoryScopeType> allowed = EnumSet.of(MemoryScopeType.TENANT, MemoryScopeType.PROJECT, MemoryScopeType.USER);
        if (userId != null) {
            allowed.add(MemoryScopeType.SESSION);
        }
        return new MemoryAccessScope(
                tenantId,
                userId,
                TenantContext.username(),
                // A caller-supplied project key is not an authorization boundary. Project
                // scope will be added only by trusted pipeline/agent context in M-401/M-403.
                null,
                allowed.stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                manageTenant,
                manageProject
        );
    }

}
