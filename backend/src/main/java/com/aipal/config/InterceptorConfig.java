package com.aipal.config;

import com.aipal.common.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;

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
        // Allow public endpoints without authentication
        if (path.equals("/api/v1/agents") ||
            path.startsWith("/api/v1/agents/") ||
            path.startsWith("/api/v1/agent-config/") ||
            path.startsWith("/api/v1/agent-quality/") ||
            path.equals("/api/v1/datasets") ||
            path.startsWith("/api/v1/datasets/") ||
            path.equals("/api/v1/models") ||
            path.startsWith("/api/v1/models/") ||
            path.equals("/api/v1/heartbeat/report") ||
            path.startsWith("/api/v1/heartbeat/") ||
            path.equals("/api/v1/registry/agents") ||
            path.startsWith("/api/v1/registry/agents/") ||
            path.equals("/api/v1/monitor/agent-graph") ||
            path.startsWith("/api/v1/monitor/") ||
            path.startsWith("/api/v1/agent-graph/") ||
            path.startsWith("/api/v1/business-dashboard/") ||
            path.startsWith("/api/v1/billing/") ||
            path.startsWith("/api/v1/alerts/") ||
            path.equals("/api/v1/audit-logs") ||
            path.startsWith("/api/v1/audit-logs/") ||
            path.equals("/api/v1/customers") ||
            path.startsWith("/api/v1/customers/") ||
            path.equals("/api/v1/invocations") ||
            path.startsWith("/api/v1/invocations/") ||
            path.startsWith("/api/v1/automation/") ||
            path.startsWith("/api/v1/rag/") ||
            path.equals("/api/v1/skills") ||
            path.startsWith("/api/v1/skills/") ||
            path.equals("/api/v1/user-memories") ||
            path.startsWith("/api/v1/user-memories/") ||
            path.startsWith("/api/v1/auth/") ||
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

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        TraceContext.clear();
    }
}
