package com.creatorrx.auth;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM authenticated encryption. Key is supplied outside the database. */
@Singleton
@Requires(env = "database")
public class RefreshTokenCipher {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenCipher() {
        String encoded = System.getenv("CREATORRX_TOKEN_ENCRYPTION_KEY");
        if (encoded == null || encoded.isBlank())
            throw new IllegalStateException("CREATORRX_TOKEN_ENCRYPTION_KEY must be configured for database mode");
        byte[] raw = Base64.getDecoder().decode(encoded);
        if (raw.length != 32) throw new IllegalArgumentException("Encryption key must decode to 32 bytes");
        key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String refreshToken, String creatorId) throws Exception {
        if (refreshToken == null || refreshToken.isBlank()) throw new IllegalArgumentException("refreshToken");
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(creatorId.getBytes(StandardCharsets.UTF_8));
        byte[] ciphertext = cipher.doFinal(refreshToken.getBytes(StandardCharsets.UTF_8));
        byte[] packed = new byte[nonce.length + ciphertext.length];
        System.arraycopy(nonce, 0, packed, 0, nonce.length);
        System.arraycopy(ciphertext, 0, packed, nonce.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(packed);
    }

    public String decrypt(String encoded, String creatorId) throws Exception {
        byte[] packed = Base64.getDecoder().decode(encoded);
        if (packed.length < 29) throw new IllegalArgumentException("Invalid encrypted token");
        byte[] nonce = java.util.Arrays.copyOfRange(packed, 0, 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(creatorId.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(packed, 12, packed.length - 12), StandardCharsets.UTF_8);
    }
}
