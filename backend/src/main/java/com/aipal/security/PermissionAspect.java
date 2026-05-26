package com.aipal.security;

import com.aipal.common.BizException;
import com.aipal.entity.SysAuditLog;
import com.aipal.mapper.SysAuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Aspect
@Component
@RequiredArgsConstructor
public class PermissionAspect {
    private final SysAuditLogMapper auditLogMapper;
    private final HttpServletRequest request;

    @Around("@within(com.aipal.security.RequirePermission) || @annotation(com.aipal.security.RequirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        RequirePermission annotation = resolveAnnotation(joinPoint);
        String permissionCode = annotation == null ? null : annotation.value();
        if (!TenantContext.hasPermission(permissionCode)) {
            writeAudit(permissionCode, 0, "无权限访问");
            throw new BizException(403, "无权限访问：" + permissionCode);
        }
        try {
            Object result = joinPoint.proceed();
            writeAudit(permissionCode, 1, null);
            return result;
        } catch (Throwable throwable) {
            writeAudit(permissionCode, 0, throwable.getMessage());
            throw throwable;
        }
    }

    private RequirePermission resolveAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RequirePermission methodAnnotation = signature.getMethod().getAnnotation(RequirePermission.class);
        if (methodAnnotation != null) return methodAnnotation;
        return joinPoint.getTarget().getClass().getAnnotation(RequirePermission.class);
    }

    private void writeAudit(String permissionCode, Integer result, String errorMessage) {
        try {
            SysAuditLog log = new SysAuditLog();
            log.setTenantId(TenantContext.tenantId());
            log.setUserId(TenantContext.userId());
            log.setUsername(TenantContext.username());
            log.setPermissionCode(permissionCode);
            log.setRequestPath(request.getRequestURI());
            log.setOperation(request.getMethod());
            log.setResourceType("API");
            log.setIpAddress(request.getRemoteAddr());
            log.setUserAgent(request.getHeader("User-Agent"));
            log.setResult(result);
            log.setErrorMessage(errorMessage);
            log.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(log);
        } catch (Exception ignored) {
            // Audit must not block the business request.
        }
    }
}
