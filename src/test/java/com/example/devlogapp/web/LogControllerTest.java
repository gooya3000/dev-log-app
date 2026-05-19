package com.example.devlogapp.web;

import com.example.devlogapp.config.AdminProperties;
import com.example.devlogapp.domain.DevLog;
import com.example.devlogapp.domain.Mood;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * LogController 슬라이스 테스트.
 * Phase 2-A Done 기준 ⑥ — 필수 필드 비어 있으면 폼 재렌더.
 */
@WebMvcTest(controllers = {LogController.class, AdminUserController.class, AuthController.class, AdminAuthController.class})
@Import({SecurityConfig.class, AdminAuthenticationProvider.class, UserAuthenticationProvider.class,
         CurrentSession.class})
class LogControllerTest {

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

    @Test
    @WithMockUser(roles = "USER")
    void get_logs_returns_200() throws Exception {
        given(devLogService.findAll()).willReturn(List.of());
        mockMvc.perform(get("/logs"))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/list"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void get_logs_new_returns_form() throws Exception {
        mockMvc.perform(get("/logs/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/form"));
    }

    // ⑥ 필수 필드 비어 있으면 폼 재렌더 (400/200)
    @Test
    @WithMockUser(roles = "USER")
    void post_logs_missing_required_fields_rerenders_form() throws Exception {
        mockMvc.perform(post("/logs").with(csrf())
                        // title, date, whatIDid, whatILearned 누락
                )
                .andExpect(status().isOk())
                .andExpect(view().name("logs/form"))
                .andExpect(model().attributeHasFieldErrors("devLogForm",
                        "date", "title", "whatIDid", "whatILearned"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void post_logs_valid_form_redirects_to_detail() throws Exception {
        DevLog mockLog = new DevLog("test-id", "2026-05-19", "제목", List.of("tag"),
                "한 일", "배운 것", "", "", Mood.GOOD,
                OffsetDateTime.now(), OffsetDateTime.now(), 1);
        given(devLogService.create(anyString(), anyString(), anyList(),
                anyString(), anyString(), any(), any(), any()))
                .willReturn(mockLog);

        mockMvc.perform(post("/logs").with(csrf())
                        .param("date", "2026-05-19")
                        .param("title", "제목")
                        .param("whatIDid", "한 일")
                        .param("whatILearned", "배운 것"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/logs/test-id"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void get_log_detail_returns_200() throws Exception {
        DevLog mockLog = new DevLog("test-id", "2026-05-19", "제목", List.of(),
                "한 일", "배운 것", "", "", null,
                OffsetDateTime.now(), OffsetDateTime.now(), 1);
        given(devLogService.findById("test-id")).willReturn(Optional.of(mockLog));

        mockMvc.perform(get("/logs/test-id"))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/detail"));
    }
}
