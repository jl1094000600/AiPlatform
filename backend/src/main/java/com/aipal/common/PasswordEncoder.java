package com.aipal.common;

import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordEncoder {
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    public static String encode(String rawPassword) {
        return BCRYPT.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        if (isBcrypt(encodedPassword)) {
            return BCRYPT.matches(rawPassword, encodedPassword);
        }
        return legacySha256(rawPassword).equals(encodedPassword);
    }

    public static boolean needsRehash(String encodedPassword) {
        return !isBcrypt(encodedPassword);
    }

    private static boolean isBcrypt(String encodedPassword) {
        return encodedPassword != null
                && (encodedPassword.startsWith("$2a$")
                || encodedPassword.startsWith("$2b$")
                || encodedPassword.startsWith("$2y$"));
    }

    private static String legacySha256(String rawPassword) {
        return DigestUtil.sha256Hex(rawPassword);
    }
}
