package com.example.devlogapp.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 회고 작성/수정 폼.
 * PLAN.md §1.2, §3 web.form 패키지 참조.
 */
@Getter
@Setter
public class DevLogForm {

    @NotBlank(message = "날짜를 입력하세요.")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "날짜 형식은 YYYY-MM-DD 입니다.")
    private String date;

    @NotBlank(message = "제목을 입력하세요.")
    private String title;

    /** 콤마 구분 문자열로 입력받음. 예: "spring, java, io" */
    private String tags;

    @NotBlank(message = "오늘 한 일을 입력하세요.")
    private String whatIDid;

    @NotBlank(message = "배운 것을 입력하세요.")
    private String whatILearned;

    private String problems;

    private String tomorrow;

    /** mood 값: GOOD / SOSO / BAD (선택). */
    private String mood;

    /**
     * tags 문자열을 List 로 변환.
     * 콤마로 분리 후 공백 제거, 빈 항목 제외.
     */
    public List<String> tagList() {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());
    }
}
