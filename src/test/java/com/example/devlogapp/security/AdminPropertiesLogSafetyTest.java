package com.example.devlogapp.security;

import com.example.devlogapp.config.AdminProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ⑨ admin passphrase 로그 노출 없음 — AdminProperties.toString() 에 평문 미포함
 */
class AdminPropertiesLogSafetyTest {

    @Test
    void adminProperties_toString_doesNotExposePassphrase() {
        // ⑨ @ToString.Exclude + 명시적 toString override 효과 확인
        AdminProperties props = new AdminProperties();
        props.setPassphrase("super-secret-admin-passphrase");

        String toString = props.toString();

        assertThat(toString).doesNotContain("super-secret-admin-passphrase");
        assertThat(toString).contains("PROTECTED");
    }

    @Test
    void adminProperties_defaultToString_notBlank() {
        AdminProperties props = new AdminProperties();
        props.setPassphrase("another-secret");

        String toString = props.toString();

        // 로그에서 passphrase 원문이 보이면 안 됨
        assertThat(toString).doesNotContain("another-secret");
    }
}
