package com.example.devlogapp.web;

import com.example.devlogapp.ai.AiClient;
import com.example.devlogapp.ai.AiException;
import com.example.devlogapp.ai.prompt.BlogPromptBuilder;
import com.example.devlogapp.domain.DevLog;
import com.example.devlogapp.domain.Mood;
import com.example.devlogapp.security.AdminAuthenticationProvider;
import com.example.devlogapp.security.CurrentSession;
import com.example.devlogapp.security.SecurityConfig;
import com.example.devlogapp.security.UserAuthenticationProvider;
import com.example.devlogapp.service.BlogDraftService;
import com.example.devlogapp.service.DevLogService;
import com.example.devlogapp.service.UserAdminService;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.example.devlogapp.vault.Vault;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Phase 3 통합 슬라이스 테스트 — POST /logs/{id}/blog-draft.
 * 검증:
 *  ① 정상 흐름 — 200 + blog-draft 뷰 + markdown 모델 노출 + apiKey 가 AiClient 까지 전달
 *  ② apiKey 가 응답 본문/모델에 노출되지 않음 (§5.3)
 *  ③ AiException 발생 시 detail 로 폴백 + 에러 메시지 노출 + apiKey 가 메시지에 포함 안 됨
 *  ④ ROLE_USER 만 호출 가능 (미인증 시 /unlock 리다이렉트는 SecurityAccessTest 에서 별도)
 *  ⑤ 필수 필드(apiKey) 누락 시 detail 폼 재렌더
 */
@WebMvcTest(controllers = {BlogDraftController.class, LogController.class,
                            AuthController.class, AdminAuthController.class, AdminUserController.class})
@Import({SecurityConfig.class, AdminAuthenticationProvider.class, UserAuthenticationProvider.class,
         CurrentSession.class, BlogDraftService.class, BlogPromptBuilder.class})
class BlogDraftControllerTest {

    private static final String SECRET_KEY = "sk-test-leak-canary-XYZ123";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DevLogService devLogService;

    @MockBean
    private UserAdminService userAdminService;

    @MockBean
    private VaultMetaRepository vaultMetaRepository;

    @MockBean
    private Vault vault;

    @MockBean
    private AiClient aiClient;

    @Test
    @WithMockUser(roles = "USER")
    void post_blog_draft_returns_markdown_and_forwards_api_key_to_client() throws Exception {
        DevLog log = sampleLog("abc123");
        given(devLogService.findById("abc123")).willReturn(Optional.of(log));
        String expectedMarkdown = "# 회고 기반 블로그 초안\n\n본문입니다.";
        given(aiClient.generate(org.mockito.ArgumentMatchers.anyString(), eq(SECRET_KEY)))
                .willReturn(expectedMarkdown);

        MvcResult result = mockMvc.perform(post("/logs/abc123/blog-draft")
                        .with(csrf())
                        .param("apiKey", SECRET_KEY))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/blog-draft"))
                .andExpect(model().attribute("markdown", expectedMarkdown))
                .andReturn();

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiClient).generate(promptCaptor.capture(), eq(SECRET_KEY));
        assertThat(promptCaptor.getValue()).contains(log.getTitle());

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .as("apiKey 가 응답 본문에 절대 노출되면 안 된다 (§5.3)")
                .doesNotContain(SECRET_KEY);

        // 모델의 attribute 직렬화 값에도 키 원문이 새지 않아야 함
        result.getModelAndView().getModel().forEach((key, value) -> {
            if (value != null) {
                assertThat(value.toString())
                        .as("model attribute '%s' 에 apiKey 원문이 새면 안 된다", key)
                        .doesNotContain(SECRET_KEY);
            }
        });
    }

    @Test
    @WithMockUser(roles = "USER")
    void post_blog_draft_with_missing_api_key_rerenders_detail() throws Exception {
        DevLog log = sampleLog("abc123");
        given(devLogService.findById("abc123")).willReturn(Optional.of(log));

        mockMvc.perform(post("/logs/abc123/blog-draft").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/detail"))
                .andExpect(model().attributeHasFieldErrors("blogDraftForm", "apiKey"))
                .andExpect(model().attributeExists("blogDraftError"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void post_blog_draft_falls_back_to_detail_on_ai_error_without_leaking_key() throws Exception {
        DevLog log = sampleLog("abc123");
        given(devLogService.findById("abc123")).willReturn(Optional.of(log));
        given(aiClient.generate(org.mockito.ArgumentMatchers.anyString(), eq(SECRET_KEY)))
                .willThrow(new AiException(401));

        MvcResult result = mockMvc.perform(post("/logs/abc123/blog-draft")
                        .with(csrf())
                        .param("apiKey", SECRET_KEY))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/detail"))
                .andExpect(model().attributeExists("aiError"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(SECRET_KEY);

        // 화이트리스트 메시지 — 401 은 "API 키 인증에 실패" 로 매핑
        Object aiError = result.getModelAndView().getModel().get("aiError");
        assertThat(aiError).isNotNull()
                .asString()
                .contains("API 키 인증에 실패");
    }

    private DevLog sampleLog(String id) {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-19T10:00:00+09:00");
        return new DevLog(id, "2026-05-19", "Spring Boot 회고",
                List.of("spring"), "한 일", "배운 것", "막힌 부분", "내일 할 일",
                Mood.GOOD, now, now, 1);
    }
}
