package com.aipal.security;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class TenantContext {
    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Context context) {
        HOLDER.set(context);
    }

    public static Context get() {
        return HOLDER.get();
    }

    public static Long tenantId() {
        Context context = HOLDER.get();
        return context == null || context.tenantId() == null ? 1L : context.tenantId();
    }

    public static Long userId() {
        Context context = HOLDER.get();
        return context == null ? null : context.userId();
    }

    public static String username() {
        Context context = HOLDER.get();
        return context == null ? null : context.username();
    }

    public static boolean platformAdmin() {
        Context context = HOLDER.get();
        return context != null && context.platformAdmin();
    }

    public static boolean hasPermission(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) return true;
        Context context = HOLDER.get();
        if (context == null) return false;
        return context.platformAdmin() || context.permissions().contains(permissionCode);
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record Context(Long userId, String username, Long tenantId, String tenantCode,
                          boolean platformAdmin, Set<String> roles, Set<String> permissions) {
        public Context {
            roles = roles == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(roles));
            permissions = permissions == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(permissions));
        }
    }
}
