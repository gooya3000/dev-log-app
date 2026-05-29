package com.example.devlogapp.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 사용자 셀프 가입 폼 (userId + passphrase + passphraseConfirm).
 * PLAN.md §4.5.6 셀프 가입 흐름, §5.6 User passphrase 처리 참조.
 *
 * passphrase 필드는 @ToString.Exclude — 로그에 절대 노출 금지.
 */
@Getter
@Setter
@ToString
public class UserRegisterForm {

    @NotBlank(message = "사용자 ID를 입력하세요.")
    @Pattern(regexp = "[a-zA-Z0-9_-]+", message = "사용자 ID는 영문자·숫자·_·- 만 허용합니다.")
    private String userId;

    @NotBlank(message = "Passphrase를 입력하세요.")
    @ToString.Exclude
    private String passphrase;

    @NotBlank(message = "Passphrase 확인을 입력하세요.")
    @ToString.Exclude
    private String passphraseConfirm;
}
