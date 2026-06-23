package com.aipal.service.memory;

import com.aipal.memory.MemoryScopeType;
import com.aipal.security.TenantContext;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class MemoryAccessScopeResolver {

    private final MemoryTrustedProjectContext trustedProjectContext;

    public MemoryAccessScopeResolver(MemoryTrustedProjectContext trustedProjectContext) {
        this.trustedProjectContext = trustedProjectContext;
    }

    public MemoryAccessScope resolve(String ignoredRequestedProjectKey) {
        Long tenantId = TenantContext.tenantId();
        Long userId = TenantContext.userId();
        boolean manageTenant = TenantContext.hasPermission("memory:policy");
        MemoryTrustedProjectContext.TrustedProject trustedProject = trustedProjectContext.current()
                .filter(project -> tenantId != null && tenantId.equals(project.tenantId()))
                .orElse(null);
        boolean manageProject = manageTenant || (trustedProject != null && userId != null
                && userId.equals(trustedProject.ownerUserId()));

        Set<MemoryScopeType> allowed = EnumSet.of(MemoryScopeType.TENANT, MemoryScopeType.USER);
        if (trustedProject != null) allowed.add(MemoryScopeType.PROJECT);
        if (userId != null) {
            allowed.add(MemoryScopeType.SESSION);
        }
        return new MemoryAccessScope(
                tenantId,
                userId,
                TenantContext.username(),
                trustedProject == null ? null : trustedProject.projectKey(),
                allowed.stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                manageTenant,
                manageProject
        );
    }

}
