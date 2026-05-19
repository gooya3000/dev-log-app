package com.example.devlogapp.ai;

/**
 * LLM 어댑터 인터페이스.
 * apiKey 는 메서드 인자로만 흐른다 — 필드·캐시·로그 어디에도 저장하지 않는다. (PLAN.md §5.1~§5.3)
 */
public interface AiClient {

    /**
     * @param prompt 전달할 프롬프트
     * @param apiKey 외부 LLM 서비스 API 키. 이 메서드 호출 완료 즉시 폐기된다.
     * @return LLM 응답 텍스트
     */
    String generate(String prompt, String apiKey);
}
