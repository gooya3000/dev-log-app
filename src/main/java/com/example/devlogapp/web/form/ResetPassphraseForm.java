package com.example.devlogapp.web.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 사용자 passphrase 재설정 폼.
 * PLAN.md §4.5.6 passphrase 재설정 흐름 참조.
 */
@Getter
@Setter
@ToString
public class ResetPassphraseForm {

    @NotBlank(message = "새 Passphrase를 입력하세요.")
    @ToString.Exclude
    private String passphrase;
}
