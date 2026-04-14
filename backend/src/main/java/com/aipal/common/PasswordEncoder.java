package com.aipal.common;

import cn.hutool.crypto.digest.DigestUtil;

public class PasswordEncoder {

    public static String encode(String rawPassword) {
        return DigestUtil.sha256Hex(rawPassword);
    }

    public static boolean matches(String rawPassword, String encodedPassword) {
        return encode(rawPassword).equals(encodedPassword);
    }
}
