package com.aipal.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicApiWhitelistTest {

    @Test
    void agentGraphApiShouldBeWhitelistedInMvcInterceptorAndSpringSecurity() throws Exception {
        String interceptorConfig = Files.readString(Path.of(
                "src/main/java/com/aipal/config/InterceptorConfig.java"));
        String securityConfig = Files.readString(Path.of(
                "src/main/java/com/aipal/config/SecurityConfig.java"));

        assertTrue(interceptorConfig.contains("path.startsWith(\"/api/v1/agent-graph/\")"),
                "Agent graph API must be allowed by InterceptorConfig");
        assertTrue(securityConfig.contains(".requestMatchers(\"/api/v1/agent-graph/**\").permitAll()"),
                "Agent graph API must be allowed by SecurityConfig");
    }
}
