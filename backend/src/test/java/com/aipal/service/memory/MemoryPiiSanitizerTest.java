package com.aipal.service.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryPiiSanitizerTest {

    private final MemoryPiiSanitizer sanitizer = new MemoryPiiSanitizer();

    @Test
    void redactsCommonPersonalAndCredentialValues() {
        MemoryPiiSanitizer.SanitizedContent result = sanitizer.sanitize(
                "联系 alice@example.com 或 13800138000，身份证 110101199001011234，Bearer abcdefghijklmnop");

        assertTrue(result.redacted());
        assertTrue(result.content().contains("[EMAIL_REDACTED]"));
        assertTrue(result.content().contains("[PHONE_REDACTED]"));
        assertTrue(result.content().contains("[ID_REDACTED]"));
        assertFalse(result.content().contains("alice@example.com"));
    }
}
