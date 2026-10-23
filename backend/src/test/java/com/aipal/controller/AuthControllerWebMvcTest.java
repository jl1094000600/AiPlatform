package com.aipal.controller;

import com.aipal.config.JwtConfig;
import com.aipal.config.SecurityConfig;
import com.aipal.config.WebMvcJwtTestConfiguration;
import com.aipal.dto.LoginResponse;
import com.aipal.entity.SysUser;
import com.aipal.security.TenantTaskRunner;
import com.aipal.service.TenantAuthService;
import com.aipal.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test that previously failed to start: {@code @WebMvcTest} does not
 * component-scan {@link JwtConfig}, so {@code InterceptorConfig} could not
 * resolve its dependency. {@link WebMvcJwtTestConfiguration} supplies the
 * missing beans without loosening interceptor auth.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({WebMvcJwtTestConfiguration.class, SecurityConfig.class})
class AuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtConfig jwtConfig;

    @Autowired
    private TenantTaskRunner tenantTaskRunner;

    @MockBean
    private UserService userService;

    @MockBean
    private TenantAuthService tenantAuthService;

    @Test
    void contextLoadsWithoutMissingJwtConfigBean() {
        // Touch injected fixtures so a slice start failure is visible as a test error.
        org.junit.jupiter.api.Assertions.assertNotNull(jwtConfig);
        org.junit.jupiter.api.Assertions.assertNotNull(tenantTaskRunner);
        org.junit.jupiter.api.Assertions.assertTrue(jwtConfig.validateToken(validToken()));
    }

    @Test
    void loginStaysOnPublicWhitelistWithoutAuthorizationHeader() throws Exception {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("tester");
        user.setPassword(com.aipal.common.PasswordEncoder.encode("password123"));
        user.setStatus(1);
        com.aipal.dto.AuthTenant authTenant = new com.aipal.dto.AuthTenant();
        authTenant.setId(1L);
        authTenant.setTenantCode("test-tenant");
        when(userService.getUserByUsername("tester")).thenReturn(user);
        when(tenantAuthService.resolveTenantId(any(), any())).thenReturn(1L);
        when(tenantAuthService.listRoleCodes(any(), eq(1L))).thenReturn(List.of("readonly"));
        when(tenantAuthService.listPermissionCodes(any(), eq(1L))).thenReturn(List.of("monitor:view"));
        when(tenantAuthService.listUserTenants(1L)).thenReturn(List.of(authTenant));
        when(tenantAuthService.isPlatformAdmin(any())).thenReturn(false);
        when(tenantAuthService.buildLoginResponse(any(), anyString(), eq(1L)))
                .thenReturn(new LoginResponse());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void protectedEndpointRejectsMissingAuthorization() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void protectedEndpointRejectsInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void protectedEndpointAcceptsRealSignedToken() throws Exception {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("tester");
        when(userService.getUserByUsername("tester")).thenReturn(user);
        when(tenantAuthService.buildLoginResponse(any(), anyString(), eq(1L)))
                .thenReturn(new LoginResponse());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    private String validToken() {
        return jwtConfig.generateToken(1L, "tester", 1L, "test-tenant",
                List.of("readonly"), List.of("monitor:view"), false);
    }
}
