package com.aipal.common;

import cn.hutool.crypto.digest.DigestUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordEncoderTest {

    @Test
    void matchesBcryptAndDoesNotRequireRehash() {
        String encoded = PasswordEncoder.encode("secret1234");

        assertTrue(PasswordEncoder.matches("secret1234", encoded));
        assertFalse(PasswordEncoder.needsRehash(encoded));
    }

    @Test
    void stillMatchesLegacySha256AndMarksItForUpgrade() {
        String legacy = DigestUtil.sha256Hex("secret1234");

        assertTrue(PasswordEncoder.matches("secret1234", legacy));
        assertTrue(PasswordEncoder.needsRehash(legacy));
    }
}
