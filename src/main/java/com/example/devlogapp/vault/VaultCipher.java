package com.example.devlogapp.vault;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 회고 파일 AES-256-GCM 암호화/복호화.
 * nonce 는 매 암호화마다 새 12바이트 random. 재사용 절대 금지.
 * PLAN.md §4.5.5 참조.
 */
public final class VaultCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private VaultCipher() {}

    /**
     * 평문 JSON 문자열 → EnvelopeV1 (암호화).
     * @param plaintext 평문 DevLog JSON
     * @param dek       32바이트 DEK
     */
    public static EnvelopeV1 encrypt(String plaintext, byte[] dek) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(dek, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            Base64.Encoder enc = Base64.getEncoder();
            return new EnvelopeV1(1, "AES-256-GCM", enc.encodeToString(nonce), enc.encodeToString(ct));
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * EnvelopeV1 → 평문 JSON 문자열 (복호화).
     * 잘못된 키 또는 변조 시 IllegalStateException (원인: AEADBadTagException).
     * @param envelope 암호화된 envelope
     * @param dek      32바이트 DEK
     */
    public static String decrypt(EnvelopeV1 envelope, byte[] dek) {
        try {
            Base64.Decoder dec = Base64.getDecoder();
            byte[] nonce = dec.decode(envelope.getNonce());
            byte[] ct = dec.decode(envelope.getCt());

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(dek, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] plain = cipher.doFinal(ct);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
