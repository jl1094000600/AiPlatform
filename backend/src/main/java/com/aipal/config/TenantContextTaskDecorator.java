package com.aipal.config;

import com.aipal.common.TraceContext;
import com.aipal.security.TenantContext;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

public class TenantContextTaskDecorator implements TaskDecorator {

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        TenantContext.Context tenantContext = TenantContext.get();
        String traceId = TraceContext.currentTraceId();
        return () -> {
            TenantContext.Context previousTenant = TenantContext.get();
            String previousTraceId = TraceContext.currentTraceId();
            try {
                TenantContext.set(tenantContext);
                TraceContext.setTraceId(traceId);
                runnable.run();
            } finally {
                TenantContext.set(previousTenant);
                if (previousTraceId == null) {
                    TraceContext.clear();
                } else {
                    TraceContext.setTraceId(previousTraceId);
                }
            }
        };
    }
}
