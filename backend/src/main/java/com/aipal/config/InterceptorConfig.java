package com.aipal.config;

import com.aipal.common.TraceContext;
import com.aipal.security.TenantContext;
import com.aipal.security.TenantTaskRunner;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashSet;

@Configuration
@RequiredArgsConstructor
public class InterceptorConfig implements HandlerInterceptor {

    private final JwtConfig jwtConfig;
    private final TenantTaskRunner tenantTaskRunner;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        TenantContext.clear();
        TraceContext.clear();
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isEmpty()) {
            traceId = TraceContext.generateTraceId();
        }
        TraceContext.setTraceId(traceId);
        response.setHeader("X-Trace-Id", traceId);

        String path = request.getRequestURI();
        if (path.equals("/api/v1/auth/login") ||
            path.equals("/api/v1/heartbeat/report") ||
            (path.equals("/api/v1/registry/agents") && "POST".equalsIgnoreCase(request.getMethod())) ||
            path.equals("/api/actuator/health") ||
            path.equals("/doc.html") ||
            path.startsWith("/webjars/") ||
            path.startsWith("/swagger-resources/") ||
            path.startsWith("/v3/api-docs/")) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return rejectUnauthorized(response, "Unauthorized");
        }

        String token = authHeader.substring(7);
        if (!jwtConfig.validateToken(token)) {
            return rejectUnauthorized(response, "Invalid token");
        }
        try {
            Long tenantId = jwtConfig.getTenantIdFromToken(token);
            String tenantCode = jwtConfig.getTenantCodeFromToken(token);
            if (tenantId == null || tenantCode == null || tenantCode.isBlank()) {
                return rejectUnauthorized(response, "Token tenant context is required");
            }
            if (!tenantId.equals(tenantTaskRunner.requireActiveTenant(tenantCode).getId())) {
                return rejectUnauthorized(response, "Token tenant context is invalid");
            }
            TenantContext.set(new TenantContext.Context(
                    jwtConfig.getUserIdFromToken(token),
                    jwtConfig.getUsernameFromToken(token),
                    tenantId,
                    tenantCode,
                    jwtConfig.isPlatformAdminFromToken(token),
                    new HashSet<>(jwtConfig.getRolesFromToken(token)),
                    new HashSet<>(jwtConfig.getPermissionsFromToken(token))
            ));
        } catch (RuntimeException exception) {
            return rejectUnauthorized(response, "Invalid token claims");
        }

        return true;
    }

    private boolean rejectUnauthorized(HttpServletResponse response, String message) throws Exception {
        TenantContext.clear();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        TraceContext.clear();
        TenantContext.clear();
    }
}
