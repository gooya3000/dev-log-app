package com.example.devlogapp.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 사용자 생성 폼 (userId + 초기 passphrase).
 * PLAN.md §4.5.6 사용자 생성 흐름 참조.
 */
@Getter
@Setter
@ToString
public class UserCreateForm {

    @NotBlank(message = "사용자 ID를 입력하세요.")
    @Pattern(regexp = "[a-zA-Z0-9_-]+", message = "사용자 ID는 영문자·숫자·_·- 만 허용합니다.")
    private String userId;

    @NotBlank(message = "초기 Passphrase를 입력하세요.")
    @ToString.Exclude
    private String passphrase;
}
