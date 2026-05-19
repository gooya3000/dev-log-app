package com.example.devlogapp.web.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 사용자 passphrase 입력 폼.
 * PLAN.md §5.6 참조.
 */
@Getter
@Setter
@ToString
public class UserUnlockForm {

    /** 사용자 ID. 기본값 "self" (단일 사용자 모드). */
    private String userId = "self";

    @NotBlank(message = "Passphrase를 입력하세요.")
    @ToString.Exclude
    private String passphrase;
}
