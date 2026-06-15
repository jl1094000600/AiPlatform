package com.aipal.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtConfigTest {

    @Test
    void emptyBearerTokenIsRejectedWithoutThrowing() {
        JwtConfig jwtConfig = configuredJwt();

        assertFalse(jwtConfig.validateToken(""));
    }

    @Test
    void tokenGenerationRequiresExplicitTenant() {
        JwtConfig jwtConfig = configuredJwt();

        assertThrows(NullPointerException.class, () -> jwtConfig.generateToken(
                1L, "tester", null, "tenant", List.of(), List.of(), false));
        assertThrows(IllegalArgumentException.class, () -> jwtConfig.generateToken(
                1L, "tester", 1L, " ", List.of(), List.of(), false));
    }

    @Test
    void startupValidationRejectsWeakSecret() {
        JwtConfig jwtConfig = new JwtConfig();
        ReflectionTestUtils.setField(jwtConfig, "secret", "too-short");
        ReflectionTestUtils.setField(jwtConfig, "expiration", 60_000L);

        assertThrows(IllegalStateException.class, jwtConfig::validateConfiguration);
    }

    private JwtConfig configuredJwt() {
        JwtConfig jwtConfig = new JwtConfig();
        ReflectionTestUtils.setField(jwtConfig, "secret", "test-only-jwt-secret-that-is-at-least-32-bytes-long");
        ReflectionTestUtils.setField(jwtConfig, "expiration", 60_000L);
        return jwtConfig;
    }
}
