package com.example.devlogapp.web;

import com.example.devlogapp.config.AdminProperties;
import com.example.devlogapp.security.AdminAuthenticationProvider;
import com.example.devlogapp.security.CurrentSession;
import com.example.devlogapp.security.SecurityConfig;
import com.example.devlogapp.security.UserAuthenticationProvider;
import com.example.devlogapp.service.PassphraseMismatchException;
import com.example.devlogapp.service.UserAccountService;
import com.example.devlogapp.service.UserAdminService;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.example.devlogapp.vault.Vault;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ProfileController MockMvc 슬라이스 테스트.
 * PLAN.md §6.2 R-4 Done 기준 ①②③⑥⑦⑧⑨ 충족.
 */
@WebMvcTest(controllers = {
        ProfileController.class,
        LogController.class,
        AdminUserController.class,
        AuthController.class,
        AdminAuthController.class
})
@Import({SecurityConfig.class, AdminAuthenticationProvider.class, UserAuthenticationProvider.class,
        CurrentSession.class})
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserAccountService userAccountService;

    @MockBean
    private com.example.devlogapp.service.DevLogService devLogService;

    @MockBean
    private UserAdminService userAdminService;

    @MockBean
    private VaultMetaRepository vaultMetaRepository;

    @MockBean
    private Vault vault;

    // ① GET /logs/profile/passphrase 200 (ROLE_USER 세션)
    @Test
    @WithMockUser(roles = "USER")
    void get_passphrasePage_returns_200() throws Exception {
        mockMvc.perform(get("/logs/profile/passphrase"))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/profile/passphrase"))
                .andExpect(model().attributeExists("passphraseChangeForm"));
    }

    // ② POST 성공 → 302 /logs?passphrase-changed
    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void post_validChange_redirects_with_passhphraseChanged() throws Exception {
        // CurrentSession.getUserId() 가 @WithMockUser 에서 null 반환 가능 — any() 로 커버
        willDoNothing().given(userAccountService)
                .changeOwnPassphrase(any(), anyString(), anyString());

        mockMvc.perform(post("/logs/profile/passphrase").with(csrf())
                        .param("oldPassphrase", "old-pass")
                        .param("newPassphrase", "new-pass")
                        .param("newPassphraseConfirm", "new-pass"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/logs?passphrase-changed"));
    }

    // ⑥ 옛 passphrase 불일치 → 200 재렌더 + 글로벌 에러
    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void post_wrongOldPassphrase_rerenders_with_error() throws Exception {
        // any() 로 userId null 케이스도 포함
        willThrow(new PassphraseMismatchException("현재 비밀번호가 일치하지 않습니다."))
                .given(userAccountService)
                .changeOwnPassphrase(any(), anyString(), anyString());

        mockMvc.perform(post("/logs/profile/passphrase").with(csrf())
                        .param("oldPassphrase", "wrong-old")
                        .param("newPassphrase", "new-pass")
                        .param("newPassphraseConfirm", "new-pass"))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/profile/passphrase"))
                .andExpect(model().hasErrors());
    }

    // ⑦ new != confirm → 200 재렌더 + 검증 에러
    @Test
    @WithMockUser(roles = "USER")
    void post_confirmMismatch_rerenders_with_error() throws Exception {
        mockMvc.perform(post("/logs/profile/passphrase").with(csrf())
                        .param("oldPassphrase", "old-pass")
                        .param("newPassphrase", "new-pass-1")
                        .param("newPassphraseConfirm", "new-pass-2"))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/profile/passphrase"))
                .andExpect(model().hasErrors());
    }

    // ⑦ @NotBlank 검증 에러 → 200 재렌더
    @Test
    @WithMockUser(roles = "USER")
    void post_blankFields_rerenders_with_fieldErrors() throws Exception {
        mockMvc.perform(post("/logs/profile/passphrase").with(csrf())
                        .param("oldPassphrase", "")
                        .param("newPassphrase", "")
                        .param("newPassphraseConfirm", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/profile/passphrase"))
                .andExpect(model().attributeHasFieldErrors("passphraseChangeForm",
                        "oldPassphrase", "newPassphrase", "newPassphraseConfirm"));
    }

    // ⑧ ROLE_ADMIN 단독 → /logs/profile/passphrase 접근 차단 (ROLE_USER 아님)
    @Test
    @WithMockUser(roles = "ADMIN")
    void roleAdmin_cannot_access_profilePassphrase() throws Exception {
        mockMvc.perform(get("/logs/profile/passphrase"))
                .andExpect(status().isForbidden());
    }

    // ⑨ 미인증 → /unlock 리다이렉트
    @Test
    void unauthenticated_redirects_to_unlock() throws Exception {
        mockMvc.perform(get("/logs/profile/passphrase"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/unlock"));
    }
}
