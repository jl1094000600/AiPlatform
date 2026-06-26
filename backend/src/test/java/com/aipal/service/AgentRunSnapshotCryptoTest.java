package com.aipal.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentRunSnapshotCryptoTest {

    @Test
    void encryptsAndDecryptsWithAad() {
        AgentRunSnapshotCrypto crypto = crypto();

        AgentRunSnapshotCrypto.EncryptedSnapshot encrypted = crypto.encrypt("private prompt snapshot", "tenant|run|hash");

        assertEquals("private prompt snapshot", crypto.decrypt(encrypted, "tenant|run|hash"));
    }

    @Test
    void usesRandomIvForSamePlaintext() {
        AgentRunSnapshotCrypto crypto = crypto();

        AgentRunSnapshotCrypto.EncryptedSnapshot first = crypto.encrypt("same plaintext", "same aad");
        AgentRunSnapshotCrypto.EncryptedSnapshot second = crypto.encrypt("same plaintext", "same aad");

        assertNotEquals(first.ivB64(), second.ivB64());
        assertNotEquals(first.ciphertextB64(), second.ciphertextB64());
    }

    @Test
    void rejectsWrongAadAndUnknownKey() {
        AgentRunSnapshotCrypto crypto = crypto();
        AgentRunSnapshotCrypto.EncryptedSnapshot encrypted = crypto.encrypt("secret", "aad-v1");

        assertThrows(IllegalStateException.class, () -> crypto.decrypt(encrypted, "aad-v2"));
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(
                new AgentRunSnapshotCrypto.EncryptedSnapshot("other-key", encrypted.ivB64(), encrypted.ciphertextB64()), "aad-v1"));
    }

    @Test
    void rejectsMissingOrWrongSizedKey() {
        assertThrows(IllegalStateException.class, () -> new AgentRunSnapshotCrypto("", key("12345678901234567890123456789012")));
        assertThrows(IllegalStateException.class, () -> new AgentRunSnapshotCrypto("runtime-v1", key("too-short")));
    }

    private AgentRunSnapshotCrypto crypto() {
        return new AgentRunSnapshotCrypto("runtime-v1", key("0123456789abcdef0123456789abcdef"));
    }

    private String key(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
