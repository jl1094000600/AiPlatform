package com.aipal.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM primitive for private execution snapshots. Plaintext must never leave this component. */
@Component
public class AgentRunSnapshotCrypto {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private final SecretKeySpec key;
    private final String keyId;
    private final SecureRandom random = new SecureRandom();

    public AgentRunSnapshotCrypto(
            @Value("${aipal.agent-runtime.snapshot-encryption.key-id}") String keyId,
            @Value("${aipal.agent-runtime.snapshot-encryption.key-b64}") String keyB64) {
        byte[] decoded = Base64.getDecoder().decode(keyB64);
        if (keyId == null || keyId.isBlank() || decoded.length != 32) {
            throw new IllegalStateException("Agent runtime snapshot encryption requires a key id and 32-byte key");
        }
        this.keyId = keyId;
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public EncryptedSnapshot encrypt(String plaintext, String aad) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSnapshot(keyId, Base64.getEncoder().encodeToString(iv), Base64.getEncoder().encodeToString(ciphertext));
        } catch (Exception exception) { throw new IllegalStateException("Unable to encrypt execution snapshot", exception); }
    }

    public String decrypt(EncryptedSnapshot encrypted, String aad) {
        try {
            if (!keyId.equals(encrypted.keyId())) throw new IllegalStateException("Unknown execution snapshot key id");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.getDecoder().decode(encrypted.ivB64())));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getDecoder().decode(encrypted.ciphertextB64())), StandardCharsets.UTF_8);
        } catch (Exception exception) { throw new IllegalStateException("Execution snapshot integrity check failed", exception); }
    }

    public record EncryptedSnapshot(String keyId, String ivB64, String ciphertextB64) {}
}
