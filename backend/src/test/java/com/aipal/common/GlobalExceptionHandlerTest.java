package com.aipal.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void securityAndPermissionFailuresUseRealHttpStatuses() {
        ResponseEntity<Result<?>> unauthorized = handler.handleSecurityException(
                new SecurityException("invalid machine token"));
        ResponseEntity<Result<?>> forbidden = handler.handleBizException(
                new BizException(403, "permission denied"));

        assertEquals(HttpStatus.UNAUTHORIZED, unauthorized.getStatusCode());
        assertEquals(401, unauthorized.getBody().getCode());
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
        assertEquals(403, forbidden.getBody().getCode());
    }
}
