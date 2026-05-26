package com.aipal.config;

import com.aipal.common.TraceContext;
import com.aipal.security.TenantContext;
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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isEmpty()) {
            traceId = TraceContext.generateTraceId();
        }
        TraceContext.setTraceId(traceId);

        String path = request.getRequestURI();
        if (path.equals("/api/v1/auth/login") ||
            path.equals("/api/v1/auth/register") ||
            path.equals("/api/v1/heartbeat/report") ||
            path.startsWith("/api/v1/heartbeat/") ||
            path.equals("/doc.html") ||
            path.startsWith("/webjars/") ||
            path.startsWith("/swagger-resources/") ||
            path.startsWith("/v3/api-docs/")) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":401,\"message\":\"Unauthorized\"}");
            return false;
        }

        String token = authHeader.substring(7);
        if (!jwtConfig.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":401,\"message\":\"Invalid token\"}");
            return false;
        }
        TenantContext.set(new TenantContext.Context(
                jwtConfig.getUserIdFromToken(token),
                jwtConfig.getUsernameFromToken(token),
                jwtConfig.getTenantIdFromToken(token),
                jwtConfig.getTenantCodeFromToken(token),
                jwtConfig.isPlatformAdminFromToken(token),
                new HashSet<>(jwtConfig.getRolesFromToken(token)),
                new HashSet<>(jwtConfig.getPermissionsFromToken(token))
        ));

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        TraceContext.clear();
        TenantContext.clear();
    }
}
