package com.aipal.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<?>> handleBizException(BizException e) {
        log.warn("Business exception: code={}, message={}", e.getCode(), e.getMessage());
        HttpStatus status = HttpStatus.resolve(e.getCode());
        return ResponseEntity.status(status == null ? HttpStatus.BAD_REQUEST : status)
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("Validation exception: {}", message);
        return ResponseEntity.badRequest().body(Result.badRequest(message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<?>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Bad request: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Result.badRequest(e.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Result<?>> handleSecurityException(SecurityException e) {
        log.warn("Unauthorized request: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.unauthorized(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Result<?>> handleIllegalStateException(IllegalStateException e) {
        log.error("Illegal state: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(Result.serverError(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e) {
        log.error("Internal server error", e);
        return ResponseEntity.internalServerError().body(Result.serverError("系统内部错误，请稍后重试"));
    }
}
