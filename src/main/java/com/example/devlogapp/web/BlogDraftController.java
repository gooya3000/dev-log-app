package com.example.devlogapp.web;

import com.example.devlogapp.ai.AiException;
import com.example.devlogapp.domain.DevLog;
import com.example.devlogapp.service.BlogDraftService;
import com.example.devlogapp.service.DevLogService;
import com.example.devlogapp.web.form.BlogDraftForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 블로그 초안 생성 컨트롤러.
 * PLAN.md §2 (POST /logs/{id}/blog-draft), §5.1 — apiKey 는 폼에서 받아 서비스 인자로만 흐름.
 */
@Controller
@RequestMapping("/logs/{id}/blog-draft")
public class BlogDraftController {

    private final BlogDraftService blogDraftService;
    private final DevLogService devLogService;

    public BlogDraftController(BlogDraftService blogDraftService, DevLogService devLogService) {
        this.blogDraftService = blogDraftService;
        this.devLogService = devLogService;
    }

    /** POST /logs/{id}/blog-draft — 폼 검증 → LLM 호출 → 결과 화면. */
    @PostMapping
    public String generate(@PathVariable String id,
                           @Valid @ModelAttribute BlogDraftForm blogDraftForm,
                           BindingResult bindingResult,
                           Model model) {
        if (bindingResult.hasErrors()) {
            populateDetailModel(model, id);
            model.addAttribute("blogDraftError",
                    bindingResult.getFieldErrors().stream()
                            .findFirst()
                            .map(fe -> fe.getDefaultMessage())
                            .orElse("입력값을 확인하세요."));
            return "logs/detail";
        }

        try {
            String markdown = blogDraftService.generateDraft(id, blogDraftForm.getApiKey());
            DevLog log = devLogService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("DevLog not found: " + id));
            model.addAttribute("log", log);
            model.addAttribute("markdown", markdown);
            return "logs/blog-draft";
        } catch (AiException e) {
            populateDetailModel(model, id);
            model.addAttribute("aiError", aiErrorMessage(e));
            return "logs/detail";
        }
    }

    /** detail 화면 렌더에 필요한 모델 일괄 채움. */
    private void populateDetailModel(Model model, String id) {
        DevLog log = devLogService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DevLog not found: " + id));
        model.addAttribute("log", log);
    }

    /**
     * AiException 의 메시지를 그대로 노출하지 않고 화이트리스트 문구로 매핑.
     * 향후 어댑터가 응답 본문을 메시지에 끼워 넣더라도 키·민감 정보가 새지 않도록 차단 (§5.3).
     */
    private String aiErrorMessage(AiException e) {
        int status = e.getHttpStatus();
        if (status == 401 || status == 403) {
            return "API 키 인증에 실패했습니다. 키를 확인하세요.";
        }
        if (status == 429) {
            return "LLM 호출 한도에 도달했습니다. 잠시 후 다시 시도하세요.";
        }
        if (status >= 500) {
            return "LLM 서버 오류로 초안 생성에 실패했습니다.";
        }
        return "블로그 초안 생성에 실패했습니다.";
    }
}
