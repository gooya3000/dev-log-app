package com.example.devlogapp.web;

import com.example.devlogapp.security.AdminAuthenticationProvider;
import com.example.devlogapp.security.CurrentSession;
import com.example.devlogapp.security.SecurityConfig;
import com.example.devlogapp.security.UserAuthenticationProvider;
import com.example.devlogapp.service.UserAdminService;
import com.example.devlogapp.service.UserRegistrationService;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.example.devlogapp.vault.Vault;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * RegisterController 슬라이스 테스트.
 * PLAN.md §6.2 R-2 Done 기준 ①~④ 검증.
 *
 * ⑤ ROLE_USER 로 /vault/users 차단 — SecurityAccessTest.roleUser_cannot_access_vaultUsers 에 이미 존재, 건너뜀.
 * ⑥ /vault/users/new 404 — 아래 vaultUsersNew_notMapped_returns404 로 검증.
 */
@WebMvcTest(controllers = {RegisterController.class, AdminUserController.class,
        LogController.class, AuthController.class, AdminAuthController.class})
@Import({SecurityConfig.class, AdminAuthenticationProvider.class, UserAuthenticationProvider.class,
        CurrentSession.class})
class RegisterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRegistrationService userRegistrationService;

    @MockBean
    private UserAdminService userAdminService;

    @MockBean
    private VaultMetaRepository vaultMetaRepository;

    @MockBean
    private com.example.devlogapp.service.DevLogService devLogService;

    @MockBean
    private Vault vault;

    // ① GET /register → 200, register 뷰
    @Test
    void get_register_returns_200_and_register_view() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));
    }

    // ② POST /register 유효한 폼 → 302 redirect /unlock?registered, service.register 1회 호출
    @Test
    void post_register_valid_form_redirects_to_unlock_registered() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("userId", "alice")
                        .param("passphrase", "strongpass123")
                        .param("passphraseConfirm", "strongpass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/unlock?registered"));

        verify(userRegistrationService).register("alice", "strongpass123");
    }

    // ③ POST /register service가 IllegalArgumentException → 폼 재렌더(200) + 일반화 메시지
    //    "이미 사용 중" 같은 구체적 단어가 응답 본문에 없어야 함 (PLAN §5.6 실패 원인 노출 금지)
    @Test
    void post_register_duplicate_userId_rerenders_form_with_general_message() throws Exception {
        doThrow(new IllegalArgumentException("이미 사용 중인 사용자 ID입니다."))
                .when(userRegistrationService).register(anyString(), anyString());

        mockMvc.perform(post("/register").with(csrf())
                        .param("userId", "existingUser")
                        .param("passphrase", "strongpass123")
                        .param("passphraseConfirm", "strongpass123"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("userRegisterForm", "userId"))
                // 구체적 실패 원인 ("이미 사용 중") 이 응답 본문에 노출되지 않음
                .andExpect(content().string(not(containsString("이미 사용 중"))));
    }

    // ④ POST /register passphrase != passphraseConfirm → 폼 재렌더(200) + passphraseConfirm 에러, service 미호출
    @Test
    void post_register_passphrase_mismatch_rerenders_form_without_calling_service() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("userId", "alice")
                        .param("passphrase", "strongpass123")
                        .param("passphraseConfirm", "differentpass456"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("userRegisterForm", "passphraseConfirm"));

        verify(userRegistrationService, never()).register(anyString(), anyString());
    }

    // ⑥ GET /vault/users/new — 더 이상 매핑 없음 → ROLE_ADMIN 으로도 404
    //   (AdminUserController 에 /new 매핑이 제거된 상태를 회귀 방지용으로 확인)
    @Test
    @WithMockUser(roles = "ADMIN")
    void get_vaultUsersNew_not_mapped_returns_404() throws Exception {
        mockMvc.perform(get("/vault/users/new"))
                .andExpect(status().isNotFound());
    }
}
