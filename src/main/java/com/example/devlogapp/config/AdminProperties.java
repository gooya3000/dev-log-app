package com.example.devlogapp.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 관리자 passphrase 설정.
 * application-local.properties (gitignored) 의 devlog.admin.passphrase 를 바인딩.
 * PLAN.md §5.7 참조.
 *
 * 노출 방지:
 *  - 명시적 toString() override 가 평문 대신 [PROTECTED] 반환 (실제 마스킹은 이것이 담당)
 *  - @JsonIgnore: JSON 직렬화 시 노출 방지
 */
@Component
@ConfigurationProperties(prefix = "devlog.admin")
public class AdminProperties {

    @JsonIgnore
    private String passphrase = "";

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    @Override
    public String toString() {
        return "AdminProperties{passphrase=[PROTECTED]}";
    }
}
