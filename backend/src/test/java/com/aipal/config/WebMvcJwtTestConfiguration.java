package com.aipal.config;

import com.aipal.entity.SysTenant;
import com.aipal.security.TenantTaskRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared fixture for {@code @WebMvcTest} slices that pull in
 * {@link InterceptorConfig}/{@link WebMvcConfig} but do not component-scan
 * {@link JwtConfig}. Supplies a real signing {@link JwtConfig} and a
 * {@link TenantTaskRunner} stub so interceptor auth stays enforced.
 */
@TestConfiguration
public class WebMvcJwtTestConfiguration {

    static final String TEST_SECRET = "web-mvc-test-jwt-secret-at-least-32-bytes";

    @Bean
    public JwtConfig jwtConfig() {
        JwtConfig jwt = new JwtConfig();
        ReflectionTestUtils.setField(jwt, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwt, "expiration", 3_600_000L);
        jwt.validateConfiguration();
        return jwt;
    }

    @Bean
    public TenantTaskRunner tenantTaskRunner() {
        TenantTaskRunner runner = mock(TenantTaskRunner.class);
        when(runner.requireActiveTenant(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            SysTenant tenant = new SysTenant();
            tenant.setId(1L);
            tenant.setTenantCode(code);
            return tenant;
        });
        return runner;
    }
}
