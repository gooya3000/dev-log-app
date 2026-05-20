package com.example.devlogapp.web.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 블로그 초안 생성용 폼.
 * PLAN.md §3 web.form, §5.1~§5.3 — apiKey 는 매 요청마다 폼으로만 입력받고 1회 사용 후 폐기.
 * @ToString.Exclude 로 로그·예외 메시지에 키 원문이 새 나가지 않도록 차단.
 */
@Getter
@Setter
@ToString
public class BlogDraftForm {

    @NotBlank(message = "API 키를 입력하세요.")
    @ToString.Exclude
    private String apiKey;
}
