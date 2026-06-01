package com.example.devlogapp.web.form;

import jakarta.validation.constraints.NotBlank;
import lombok.ToString;

/**
 * 본인 passphrase 변경 폼.
 * PLAN.md §5.6: 모든 passphrase 필드 @ToString.Exclude.
 */
@ToString
public class PassphraseChangeForm {

    @ToString.Exclude
    @NotBlank(message = "현재 비밀번호를 입력하세요.")
    private String oldPassphrase;

    @ToString.Exclude
    @NotBlank(message = "새 비밀번호를 입력하세요.")
    private String newPassphrase;

    @ToString.Exclude
    @NotBlank(message = "새 비밀번호 확인을 입력하세요.")
    private String newPassphraseConfirm;

    // no-arg constructor
    public PassphraseChangeForm() {}

    public String getOldPassphrase() { return oldPassphrase; }
    public void setOldPassphrase(String oldPassphrase) { this.oldPassphrase = oldPassphrase; }

    public String getNewPassphrase() { return newPassphrase; }
    public void setNewPassphrase(String newPassphrase) { this.newPassphrase = newPassphrase; }

    public String getNewPassphraseConfirm() { return newPassphraseConfirm; }
    public void setNewPassphraseConfirm(String newPassphraseConfirm) { this.newPassphraseConfirm = newPassphraseConfirm; }
}
