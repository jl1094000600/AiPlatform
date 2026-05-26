package com.aipal.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JwtConfig {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Long userId, String username) {
        return generateToken(userId, username, 1L, "think_land", List.of(), List.of(), false);
    }

    public String generateToken(Long userId, String username, Long tenantId, String tenantCode,
                                List<String> roles, List<String> permissions, boolean platformAdmin) {
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
        } catch (JwtException e) {
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
        return value == null ? 1L : Long.parseLong(String.valueOf(value));
    }

    public String getTenantCodeFromToken(String token) {
        Claims claims = parseToken(token);
        Object value = claims.get("tenantCode");
        return value == null ? "think_land" : String.valueOf(value);
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
