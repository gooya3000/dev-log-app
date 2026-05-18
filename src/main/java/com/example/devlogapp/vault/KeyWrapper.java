package com.example.devlogapp.vault;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * DEK wrap / unwrap (AES-256-GCM).
 * PLAN.md §4.5.5 참조.
 */
public final class KeyWrapper {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private KeyWrapper() {}

    /**
     * wrapKey(wrappingKey, dek) — dek 를 wrappingKey 로 AES-GCM 암호화.
     * @param wrappingKey 32바이트 AES 키 (K_admin or K_user)
     * @param dek         32바이트 DEK
     * @return [nonce(12) || ciphertext+tag] — base64 인코딩된 nonce/ct 쌍 반환
     */
    public static WrapResult wrap(byte[] wrappingKey, byte[] dek) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(wrappingKey, "AES"), spec);
            byte[] ct = cipher.doFinal(dek);

            Base64.Encoder enc = Base64.getEncoder();
            return new WrapResult(enc.encodeToString(nonce), enc.encodeToString(ct));
        } catch (Exception e) {
            throw new IllegalStateException("DEK wrap failed", e);
        }
    }

    /**
     * unwrap(wrappingKey, nonceB64, ctB64) → DEK 32바이트.
     * 잘못된 키/변조 시 javax.crypto.AEADBadTagException (RuntimeException wrapping).
     * 호출자가 사용 후 Arrays.fill(dek, (byte)0) 폐기.
     */
    public static byte[] unwrap(byte[] wrappingKey, String nonceB64, String ctB64) {
        try {
            Base64.Decoder dec = Base64.getDecoder();
            byte[] nonce = dec.decode(nonceB64);
            byte[] ct = dec.decode(ctB64);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, nonce);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(wrappingKey, "AES"), spec);
            return cipher.doFinal(ct);
        } catch (Exception e) {
            throw new IllegalStateException("DEK unwrap failed", e);
        }
    }

    /**
     * wrappingKey 배열을 즉시 0으로 덮어씌워 폐기.
     */
    public static void wipe(byte[] key) {
        if (key != null) Arrays.fill(key, (byte) 0);
    }

    public record WrapResult(String nonceB64, String ctB64) {}
}
