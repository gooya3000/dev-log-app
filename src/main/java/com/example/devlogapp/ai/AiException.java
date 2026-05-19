package com.example.devlogapp.ai;

/**
 * LLM 호출 실패 예외.
 * 메시지에는 HTTP 상태코드와 짧은 일반 문구만 포함. apiKey·응답 본문 절대 포함 금지. (PLAN.md §5.3)
 */
public class AiException extends RuntimeException {

    private final int httpStatus;

    public AiException(int httpStatus) {
        super("LLM 호출 실패 (HTTP " + httpStatus + ")");
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
