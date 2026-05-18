package com.example.devlogapp.vault;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

/**
 * PBKDF2-HMAC-SHA256 KDF.
 * - Admin: 32바이트 출력 (K_admin)
 * - User: 64바이트 출력 (전반 32 = K_user, 후반 32 = H_user)
 * PLAN.md §4.5.4 참조.
 */
public final class PassphraseKdf {

    public static final int KDF_ITERATIONS = 600_000;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private PassphraseKdf() {}

    /**
     * Admin KDF: 32바이트 K_admin 반환.
     * 호출자가 사용 후 Arrays.fill(result, (byte)0) 으로 폐기.
     */
    public static byte[] deriveAdmin(char[] passphrase, byte[] salt) {
        return pbkdf2(passphrase, salt, KDF_ITERATIONS, 32);
    }

    /**
     * User KDF: 64바이트 반환 (전반 32 = K_user, 후반 32 = H_user).
     * 호출자가 사용 후 Arrays.fill(result, (byte)0) 으로 폐기.
     */
    public static byte[] deriveUser(char[] passphrase, byte[] salt) {
        return pbkdf2(passphrase, salt, KDF_ITERATIONS, 64);
    }

    private static byte[] pbkdf2(char[] passphrase, byte[] salt, int iterations, int keyLenBytes) {
        try {
            PBEKeySpec spec = new PBEKeySpec(passphrase, salt, iterations, keyLenBytes * 8);
            try {
                SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
                return skf.generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 derivation failed", e);
        }
    }
}
