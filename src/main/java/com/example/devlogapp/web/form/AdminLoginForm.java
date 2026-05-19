package com.example.devlogapp.web.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 관리자 passphrase 입력 폼.
 * PLAN.md §5.7 참조.
 */
@Getter
@Setter
@ToString
public class AdminLoginForm {

    @NotBlank(message = "Passphrase를 입력하세요.")
    @ToString.Exclude
    private String passphrase;
}
