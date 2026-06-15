package com.aipal.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class JwtConfig {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @PostConstruct
    public void validateConfiguration() {
        if (secret == null || secret.isBlank()
                || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret must contain at least 32 bytes");
        }
        if (expiration == null || expiration <= 0) {
            throw new IllegalStateException("jwt.expiration must be greater than zero");
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Long userId, String username, Long tenantId, String tenantCode,
                                List<String> roles, List<String> permissions, boolean platformAdmin) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new IllegalArgumentException("tenantCode must not be blank");
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("tenantId", tenantId);
        claims.put("tenantCode", tenantCode);
        claims.put("roles", roles);
        claims.put("permissions", permissions);
        claims.put("platformAdmin", platformAdmin);
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }

    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    public Long getTenantIdFromToken(String token) {
        Claims claims = parseToken(token);
        Object value = claims.get("tenantId");
        if (value instanceof Number number) return number.longValue();
        return value == null ? null : Long.parseLong(String.valueOf(value));
    }

    public String getTenantCodeFromToken(String token) {
        Claims claims = parseToken(token);
        Object value = claims.get("tenantCode");
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Object value = parseToken(token).get("roles");
        return value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    @SuppressWarnings("unchecked")
    public List<String> getPermissionsFromToken(String token) {
        Object value = parseToken(token).get("permissions");
        return value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    public boolean isPlatformAdminFromToken(String token) {
        Object value = parseToken(token).get("platformAdmin");
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }
}
