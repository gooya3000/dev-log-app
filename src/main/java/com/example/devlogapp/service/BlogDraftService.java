package com.example.devlogapp.service;

import com.example.devlogapp.ai.AiClient;
import com.example.devlogapp.ai.prompt.BlogPromptBuilder;
import com.example.devlogapp.domain.DevLog;
import org.springframework.stereotype.Service;

/**
 * 회고 1건을 LLM 에 보내 기술 블로그 초안(Markdown)을 받아오는 오케스트레이션.
 * PLAN.md §3 service, §5.1 — apiKey 는 메서드 인자로만 전달, 어떤 필드/캐시/로그에도 저장 금지.
 */
@Service
public class BlogDraftService {

    private final DevLogService devLogService;
    private final BlogPromptBuilder promptBuilder;
    private final AiClient aiClient;

    public BlogDraftService(DevLogService devLogService,
                            BlogPromptBuilder promptBuilder,
                            AiClient aiClient) {
        this.devLogService = devLogService;
        this.promptBuilder = promptBuilder;
        this.aiClient = aiClient;
    }

    /**
     * @param id     회고 id
     * @param apiKey 사용자가 폼에 입력한 LLM API 키. 호출 직후 메서드 스코프에서 사라짐.
     * @return LLM 이 생성한 Markdown 초안
     */
    public String generateDraft(String id, String apiKey) {
        DevLog log = devLogService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DevLog not found: " + id));
        String prompt = promptBuilder.build(log);
        return aiClient.generate(prompt, apiKey);
    }
}
