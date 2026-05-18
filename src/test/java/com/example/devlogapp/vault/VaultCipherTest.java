package com.example.devlogapp.vault;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ⑦ VaultCipher — envelope ct 1바이트 변조 시 GCM AEADBadTagException
 */
class VaultCipherTest {

    @Test
    void encryptThenDecrypt_roundtrip() {
        byte[] dek = new byte[32];
        new SecureRandom().nextBytes(dek);

        String plaintext = "{\"id\":\"test\",\"title\":\"Hello\"}";
        EnvelopeV1 envelope = VaultCipher.encrypt(plaintext, dek);
        String decrypted = VaultCipher.decrypt(envelope, dek);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void tamperCt_throwsException() {
        byte[] dek = new byte[32];
        new SecureRandom().nextBytes(dek);

        String plaintext = "{\"id\":\"test\",\"title\":\"Hello World\"}";
        EnvelopeV1 envelope = VaultCipher.encrypt(plaintext, dek);

        // ct 의 첫 번째 바이트를 XOR 로 변조
        byte[] ctBytes = Base64.getDecoder().decode(envelope.getCt());
        ctBytes[0] ^= 0xFF;
        String tamperedCt = Base64.getEncoder().encodeToString(ctBytes);

        EnvelopeV1 tampered = new EnvelopeV1(envelope.getV(), envelope.getAlg(),
                envelope.getNonce(), tamperedCt);

        assertThatThrownBy(() -> VaultCipher.decrypt(tampered, dek))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    void wrongKey_throwsException() {
        byte[] dek = new byte[32];
        new SecureRandom().nextBytes(dek);

        byte[] wrongDek = new byte[32];
        new SecureRandom().nextBytes(wrongDek);

        EnvelopeV1 envelope = VaultCipher.encrypt("secret", dek);

        assertThatThrownBy(() -> VaultCipher.decrypt(envelope, wrongDek))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void eachEncrypt_differentNonce() {
        byte[] dek = new byte[32];
        new SecureRandom().nextBytes(dek);

        String plaintext = "same plaintext";
        EnvelopeV1 e1 = VaultCipher.encrypt(plaintext, dek);
        EnvelopeV1 e2 = VaultCipher.encrypt(plaintext, dek);

        // nonce 재사용 금지 검증
        assertThat(e1.getNonce()).isNotEqualTo(e2.getNonce());
        // ct 도 달라야 함 (nonce 가 다르면)
        assertThat(e1.getCt()).isNotEqualTo(e2.getCt());
    }
}
