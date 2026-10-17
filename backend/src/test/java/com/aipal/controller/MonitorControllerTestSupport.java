package com.aipal.controller;

import com.aipal.common.GlobalExceptionHandler;
import com.aipal.config.InterceptorConfig;
import com.aipal.config.JwtConfig;
import com.aipal.entity.SysTenant;
import com.aipal.mapper.SysAuditLogMapper;
import com.aipal.security.PermissionAspect;
import com.aipal.security.TenantContext;
import com.aipal.security.TenantTaskRunner;
import com.aipal.service.CallRecordService;
import com.aipal.service.MonitorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Controller contracts use real JWT validation, tenant interception and permission enforcement. */
abstract class MonitorControllerTestSupport {
    protected MockMvc mockMvc;
    protected CallRecordService callRecordService;
    protected MonitorService monitorService;
    protected JwtConfig jwt;

    @BeforeEach void configureAuthenticatedController() {
        callRecordService = mock(CallRecordService.class);
        monitorService = mock(MonitorService.class);
        jwt = new JwtConfig();
        ReflectionTestUtils.setField(jwt, "secret", "monitor-controller-public-test-signing-key-2026");
        ReflectionTestUtils.setField(jwt, "expiration", 60000L);
        jwt.validateConfiguration();
        TenantTaskRunner tenants = mock(TenantTaskRunner.class);
        SysTenant tenant = new SysTenant(); tenant.setId(1L); tenant.setTenantCode("test-tenant");
        when(tenants.requireActiveTenant("test-tenant")).thenReturn(tenant);
        MonitorController controller = new MonitorController(callRecordService, monitorService);
        AspectJProxyFactory proxy = new AspectJProxyFactory(controller);
        proxy.setProxyTargetClass(true);
        proxy.addAspect(new PermissionAspect(mock(SysAuditLogMapper.class), new MockHttpServletRequest()));
        Object securedController = proxy.getProxy();
        mockMvc = MockMvcBuilders.standaloneSetup(securedController)
                .addInterceptors(new InterceptorConfig(jwt, tenants))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach void clearTenant() { TenantContext.clear(); }

    protected MockHttpServletRequestBuilder authorizedGet(String path) {
        return get(path).header("Authorization", "Bearer " + token(List.of("monitor:view", "graph:manage")));
    }

    protected String token(List<String> permissions) {
        return jwt.generateToken(1L, "tester", 1L, "test-tenant", List.of("readonly"), permissions, false);
    }
}
