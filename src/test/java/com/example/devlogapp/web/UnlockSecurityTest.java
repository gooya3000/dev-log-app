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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /unlock 실패 시 passphrase 평문 노출 없음 검증.
 * Phase 2-A Done 기준 ⑦.
 */
@WebMvcTest(controllers = {LogController.class, AdminUserController.class, AuthController.class, AdminAuthController.class})
@Import({SecurityConfig.class, AdminAuthenticationProvider.class, UserAuthenticationProvider.class,
         CurrentSession.class})
class UnlockSecurityTest {

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

    /** ⑦ POST /unlock 실패 시 응답 본문에 passphrase 평문이 노출되지 않음. */
    @Test
    void unlock_failure_does_not_expose_passphrase() throws Exception {
        // UserAuthenticationProvider 가 BadCredentials 던지도록 설정
        given(vaultMetaRepository.load()).willReturn(java.util.Optional.empty());

        String secretPassphrase = "super-secret-passphrase-12345";

        MvcResult result = mockMvc.perform(post("/unlock").with(csrf())
                        .param("userId", "self")
                        .param("passphrase", secretPassphrase))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/unlock?error"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain(secretPassphrase);
    }

    /** POST /unlock GET 폼은 200. */
    @Test
    void get_unlock_returns_200() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/unlock"))
                .andExpect(status().isOk());
    }
}
