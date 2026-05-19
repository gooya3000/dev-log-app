package com.example.devlogapp.web;

import com.example.devlogapp.config.AdminProperties;
import com.example.devlogapp.security.AdminAuthenticationProvider;
import com.example.devlogapp.security.CurrentSession;
import com.example.devlogapp.security.SecurityConfig;
import com.example.devlogapp.security.UserAuthenticationProvider;
import com.example.devlogapp.service.DevLogService;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2-A Done 기준 ①~⑤ 검증.
 * PLAN.md §6.2 Phase 2-A 참조.
 */
@WebMvcTest(controllers = {LogController.class, AdminUserController.class, AuthController.class, AdminAuthController.class})
@Import({SecurityConfig.class, AdminAuthenticationProvider.class, UserAuthenticationProvider.class,
         CurrentSession.class})
class SecurityAccessTest {

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

    // ① 미인증 → /logs 접근 시 /unlock 302
    @Test
    void unauthenticated_logs_redirects_to_unlock() throws Exception {
        mockMvc.perform(get("/logs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/unlock"));
    }

    // ② 미인증 → /vault/users 접근 시 /vault/login 302
    @Test
    void unauthenticated_vaultUsers_redirects_to_vaultLogin() throws Exception {
        mockMvc.perform(get("/vault/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/vault/login"));
    }

    // ③ ROLE_USER → /logs 200, /vault/users 403
    @Test
    @WithMockUser(roles = "USER")
    void roleUser_can_access_logs() throws Exception {
        mockMvc.perform(get("/logs"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void roleUser_cannot_access_vaultUsers() throws Exception {
        mockMvc.perform(get("/vault/users"))
                .andExpect(status().isForbidden());
    }

    // ④ ROLE_ADMIN → /vault/users 200, /logs 403
    @Test
    @WithMockUser(roles = "ADMIN")
    void roleAdmin_can_access_vaultUsers() throws Exception {
        mockMvc.perform(get("/vault/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void roleAdmin_cannot_access_logs() throws Exception {
        mockMvc.perform(get("/logs"))
                .andExpect(status().isForbidden());
    }

    // ⑤ CSRF 토큰 없이 POST /logs → 403
    @Test
    @WithMockUser(roles = "USER")
    void post_logs_without_csrf_returns_403() throws Exception {
        mockMvc.perform(post("/logs"))
                .andExpect(status().isForbidden());
    }

    // ⑤ CSRF 토큰 있으면 정상 흐름 (유효성 오류 → 400/200)
    @Test
    @WithMockUser(roles = "USER")
    void post_logs_with_csrf_passes_csrf_check() throws Exception {
        // 필수 필드가 없으므로 400 또는 폼 재렌더(200) 기대, CSRF 오류(403) 아님
        mockMvc.perform(post("/logs").with(csrf()))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 200 || status == 302 : "Expected 200 or 302 but was " + status;
                });
    }
}
