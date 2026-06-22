package com.aipal.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigurationTest {

    @Test
    void corsUsesOnlyExplicitOrigins() {
        SecurityConfig securityConfig = new SecurityConfig(List.of("https://console.example.com"));
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/agents");
        CorsConfiguration cors = securityConfig.corsConfigurationSource().getCorsConfiguration(request);

        assertEquals(List.of("https://console.example.com"), cors.getAllowedOrigins());
        assertFalse(cors.getAllowedHeaders().contains("*"));
        assertTrue(Boolean.TRUE.equals(cors.getAllowCredentials()));
    }

    @Test
    void corsRejectsWildcardOriginWhenCredentialsAreEnabled() {
        assertThrows(IllegalArgumentException.class, () -> new SecurityConfig(List.of("*")));
    }

    @Test
    void applicationConfigurationContainsNoSensitiveDefaults() throws Exception {
        String application = Files.readString(Path.of("src/main/resources/application.yml"));
        String tenantInitializer = Files.readString(Path.of(
                "src/main/java/com/aipal/config/TenantSecuritySchemaInitializer.java"));
        String databaseInit = Files.readString(Path.of("sql/init.sql"));
        String imageAgent = Files.readString(Path.of("../image-agent/src/main/resources/application.yml"));
        String intentAgent = Files.readString(Path.of("../intent-agent/src/main/resources/application.yml"));
        String marketingAgent = Files.readString(Path.of("../marketing-agent/src/main/resources/application.yml"));
        String loginPage = Files.readString(Path.of("../front/src/views/Login.vue"));
        String deploymentGuide = Files.readString(Path.of("../docs/deployment-ubuntu.md"));

        assertTrue(application.contains("${DB_PASSWORD}"));
        assertTrue(application.contains("optional:file:.env[.properties]"));
        assertTrue(application.contains("optional:file:../.env[.properties]"));
        assertTrue(application.contains("${JWT_SECRET}"));
        assertTrue(application.contains("${BOOTSTRAP_ADMIN_PASSWORD_HASH}"));
        assertTrue(application.contains("${AGENT_HEARTBEAT_SECRET}"));
        assertFalse(application.contains("Jl19951106"));
        assertFalse(application.contains("your-api-key-here"));
        assertFalse(application.contains("aWFnYWlwbGF0Zm9ybXMyMDI2c2VjcmV0a2V5"));
        assertFalse(tenantInitializer.contains("240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9"));
        assertFalse(databaseInit.contains("240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9"));
        assertTrue(imageAgent.contains("${DB_PASSWORD}"));
        assertTrue(intentAgent.contains("${DB_PASSWORD}"));
        assertTrue(marketingAgent.contains("${DB_PASSWORD}"));
        assertFalse(imageAgent.contains("Jl19951106"));
        assertFalse(intentAgent.contains("Jl19951106"));
        assertFalse(marketingAgent.contains("Jl19951106"));
        assertFalse(imageAgent.contains("dummy-key-for-init"));
        assertFalse(intentAgent.contains("dummy-key-for-init"));
        assertFalse(marketingAgent.contains("dummy-key-for-init"));
        assertFalse(loginPage.contains("admin123"));
        assertFalse(deploymentGuide.contains("admin123"));
    }
}
