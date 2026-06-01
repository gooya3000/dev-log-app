package com.example.devlogapp.web;

import com.example.devlogapp.security.CurrentSession;
import com.example.devlogapp.service.PassphraseMismatchException;
import com.example.devlogapp.service.UserAccountService;
import com.example.devlogapp.web.form.PassphraseChangeForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 본인 passphrase 변경 컨트롤러.
 * 경로 prefix: /logs/profile
 * PLAN.md §2, §4.5.6, §6.2 R-4 참조.
 */
@Controller
@RequestMapping("/logs/profile")
public class ProfileController {

    private final UserAccountService userAccountService;
    private final CurrentSession currentSession;

    public ProfileController(UserAccountService userAccountService,
                             CurrentSession currentSession) {
        this.userAccountService = userAccountService;
        this.currentSession = currentSession;
    }

    /** GET /logs/profile/passphrase — passphrase 변경 폼 */
    @GetMapping("/passphrase")
    public String showChangeForm(Model model) {
        model.addAttribute("passphraseChangeForm", new PassphraseChangeForm());
        return "logs/profile/passphrase";
    }

    /** POST /logs/profile/passphrase — passphrase 변경 처리 */
    @PostMapping("/passphrase")
    public String changePassphrase(
            @Valid @ModelAttribute("passphraseChangeForm") PassphraseChangeForm form,
            BindingResult bindingResult,
            Model model) {

        // new != confirm 검증
        if (!bindingResult.hasErrors()
                && !form.getNewPassphrase().equals(form.getNewPassphraseConfirm())) {
            bindingResult.reject("passphrase.confirm.mismatch",
                    "새 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
        }

        if (bindingResult.hasErrors()) {
            return "logs/profile/passphrase";
        }

        String userId = currentSession.getUserId();
        // r2: 세션 무효(userId null) → /unlock 강제 (PLAN §4.5.6 단계 3)
        if (userId == null) {
            return "redirect:/unlock";
        }

        try {
            userAccountService.changeOwnPassphrase(
                    userId,
                    form.getOldPassphrase(),
                    form.getNewPassphrase());
        } catch (PassphraseMismatchException e) {
            bindingResult.reject("passphrase.mismatch",
                    "현재 비밀번호가 일치하지 않습니다.");
            return "logs/profile/passphrase";
        } catch (IllegalStateException e) {
            // r3: save 실패 또는 세션·vault 불일치 → 일반 에러 (PLAN §4.5.6 단계 10)
            bindingResult.reject("passphrase.error",
                    "비밀번호 변경 중 오류가 발생했습니다. 다시 시도해 주세요.");
            return "logs/profile/passphrase";
        }

        // 폼 필드 참조는 여기서 끊기고 GC 대상이 됨
        return "redirect:/logs?passphrase-changed";
    }
}
