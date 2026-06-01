package com.example.devlogapp.web;

import com.example.devlogapp.domain.User;
import com.example.devlogapp.service.UserAdminService;
import com.example.devlogapp.vault.VaultMeta;
import com.example.devlogapp.storage.VaultMetaRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 관리자 사용자 관리 컨트롤러.
 * 새 모델: 사용자 생성(/new) 제거 — 셀프 가입은 /register (Phase R-2 에서 추가).
 * PLAN.md §2 /vault/users/** 매핑 참조.
 */
@Controller
@RequestMapping("/vault/users")
public class AdminUserController {

    private final UserAdminService userAdminService;
    private final VaultMetaRepository vaultMetaRepository;

    public AdminUserController(UserAdminService userAdminService,
                                VaultMetaRepository vaultMetaRepository) {
        this.userAdminService = userAdminService;
        this.vaultMetaRepository = vaultMetaRepository;
    }

    /** id·createdAt 만 뷰에 노출하는 DTO (passphraseHash·wrappedDek 차단). */
    record UserSummary(String id, OffsetDateTime createdAt) {}

    /** GET /vault/users — 사용자 목록. */
    @GetMapping
    public String list(Model model) {
        List<UserSummary> users = vaultMetaRepository.load()
                .map(VaultMeta::getUsers)
                .orElse(List.of())
                .stream()
                .map(u -> new UserSummary(u.getId(), u.getCreatedAt()))
                .toList();
        model.addAttribute("users", users);
        return "vault/users/list";
    }

    /** GET /vault/users/{id}/reset-passphrase — 재설정 확인 화면. */
    @GetMapping("/{id}/reset-passphrase")
    public String resetPassphraseForm(@PathVariable String id, Model model) {
        model.addAttribute("userId", id);
        return "vault/users/confirm-reset";
    }

    /** POST /vault/users/{id}/reset-passphrase — SecureRandom passphrase 발급 → /vault/users/{id}/reset-result (1회 노출). */
    @PostMapping("/{id}/reset-passphrase")
    public String resetPassphrase(@PathVariable String id, RedirectAttributes redirectAttributes) {
        String newPassphrase = userAdminService.resetPassphrase(id);
        redirectAttributes.addFlashAttribute("newPassphrase", newPassphrase);
        return "redirect:/vault/users/" + id + "/reset-result";
    }

    /** GET /vault/users/{id}/reset-result — 새 임시 passphrase 1회 표시. */
    @GetMapping("/{id}/reset-result")
    public String resetResult(@PathVariable String id, Model model) {
        // FlashAttribute 없으면 목록으로
        if (!model.containsAttribute("newPassphrase")) {
            return "redirect:/vault/users";
        }
        model.addAttribute("userId", id);
        return "vault/users/reset-result";
    }

    /** POST /vault/users/{id}/delete — 사용자 매핑 + 디렉토리 제거. */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id) {
        userAdminService.deleteUser(id);
        return "redirect:/vault/users";
    }
}
