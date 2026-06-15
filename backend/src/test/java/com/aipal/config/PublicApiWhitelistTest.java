package com.aipal.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.aipal.security.TenantTaskRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PublicApiWhitelistTest {

    private final InterceptorConfig interceptor = new InterceptorConfig(
            mock(JwtConfig.class), mock(TenantTaskRunner.class));

    @Test
    void machineAuthenticatedEndpointsAndHealthRemainPublicAtJwtLayer() throws Exception {
        assertTrue(preHandle("POST", "/api/v1/heartbeat/report").allowed());
        assertTrue(preHandle("POST", "/api/v1/registry/agents").allowed());
        assertTrue(preHandle("GET", "/api/actuator/health").allowed());
        assertFalse(preHandle("GET", "/api/v1/registry/agents").allowed());
        assertFalse(preHandle("POST", "/api/v1/auth/register").allowed());
    }

    @Test
    void businessApisRequireBearerToken() throws Exception {
        Interception result = preHandle("GET", "/api/v1/agent-graph/nodes");

        assertFalse(result.allowed());
        assertEquals(401, result.response().getStatus());
    }

    private Interception preHandle(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        return new Interception(interceptor.preHandle(request, response, new Object()), response);
    }

    private record Interception(boolean allowed, MockHttpServletResponse response) {
    }
}
