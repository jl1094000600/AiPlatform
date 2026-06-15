package com.aipal.security;

import com.aipal.entity.SysTenant;
import com.aipal.mapper.SysTenantMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantTaskRunner {

    private final SysTenantMapper tenantMapper;

    public SysTenant requireActiveTenant(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new IllegalArgumentException("tenantCode is required");
        }
        SysTenant tenant = tenantMapper.selectOne(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getTenantCode, tenantCode.trim())
                .eq(SysTenant::getStatus, 1)
                .and(wrapper -> wrapper.isNull(SysTenant::getExpireTime)
                        .or().gt(SysTenant::getExpireTime, LocalDateTime.now()))
                .last("LIMIT 1"));
        if (tenant == null) {
            throw new IllegalArgumentException("Active tenant not found: " + tenantCode);
        }
        return tenant;
    }

    public void runForTenant(String taskName, SysTenant tenant, Runnable task) {
        TenantContext.Context context = systemContext(taskName, tenant);
        TenantContext.runWithContext(context, task);
    }

    public <T> T callForTenant(String taskName, SysTenant tenant, Supplier<T> task) {
        TenantContext.Context previous = TenantContext.get();
        try {
            TenantContext.set(systemContext(taskName, tenant));
            return task.get();
        } finally {
            TenantContext.set(previous);
        }
    }

    public void forEachActiveTenant(String taskName, Consumer<SysTenant> task) {
        List<SysTenant> tenants = tenantMapper.selectList(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getStatus, 1)
                .and(wrapper -> wrapper.isNull(SysTenant::getExpireTime)
                        .or().gt(SysTenant::getExpireTime, LocalDateTime.now()))
                .orderByAsc(SysTenant::getId));
        for (SysTenant tenant : tenants) {
            try {
                runForTenant(taskName, tenant, () -> task.accept(tenant));
            } catch (RuntimeException exception) {
                log.error("Tenant task {} failed for tenant {} ({})",
                        taskName, tenant.getId(), tenant.getTenantCode(), exception);
            }
        }
    }

    private TenantContext.Context systemContext(String taskName, SysTenant tenant) {
        return new TenantContext.Context(
                null,
                "system:" + taskName,
                tenant.getId(),
                tenant.getTenantCode(),
                false,
                Set.of("system-task"),
                Set.of()
        );
    }
}
