package com.aipal.security;

import com.aipal.entity.SysTenant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Supplier;

@Component
public class HeartbeatAuthenticator {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final TenantTaskRunner tenantTaskRunner;
    private final String heartbeatSecret;

    public HeartbeatAuthenticator(
            TenantTaskRunner tenantTaskRunner,
            @Value("${security.agent-heartbeat-secret}") String heartbeatSecret) {
        if (heartbeatSecret == null || heartbeatSecret.length() < 32) {
            throw new IllegalArgumentException(
                    "security.agent-heartbeat-secret must contain at least 32 characters");
        }
        this.tenantTaskRunner = tenantTaskRunner;
        this.heartbeatSecret = heartbeatSecret;
    }

    public void authenticateAndRun(String tenantCode, String token, Runnable task) {
        authenticateAndCall(tenantCode, token, () -> {
            task.run();
            return null;
        });
    }

    public <T> T authenticateAndCall(String tenantCode, String token, Supplier<T> task) {
        if (token == null || token.isBlank()) {
            throw new SecurityException("Agent heartbeat token is required");
        }
        byte[] expected = sign(heartbeatSecret, tenantCode);
        byte[] actual;
        try {
            actual = HexFormat.of().parseHex(token.trim());
        } catch (IllegalArgumentException exception) {
            throw new SecurityException("Invalid agent heartbeat token", exception);
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new SecurityException("Invalid agent heartbeat token");
        }
        SysTenant tenant;
        try {
            tenant = tenantTaskRunner.requireActiveTenant(tenantCode);
        } catch (IllegalArgumentException exception) {
            throw new SecurityException("Invalid agent credentials", exception);
        }
        return tenantTaskRunner.callForTenant("agent-auth", tenant, task);
    }

    static String tokenFor(String secret, String tenantCode) {
        return HexFormat.of().formatHex(sign(secret, tenantCode));
    }

    private static byte[] sign(String secret, String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new IllegalArgumentException("tenantCode is required");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("heartbeat secret is required");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(tenantCode.trim().getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to calculate heartbeat token", exception);
        }
    }
}
