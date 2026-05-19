package com.example.devlogapp.web;

import com.example.devlogapp.web.form.UserUnlockForm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 사용자 인증 컨트롤러. GET /unlock 폼 렌더링만 담당.
 * POST /unlock (passphrase 검증) · POST /logout (Vault.lock + SecurityContext 정리)은
 * Spring Security userSecurityFilterChain 이 처리.
 * PLAN.md §2, §5.6 참조.
 */
@Controller
public class AuthController {

    @GetMapping("/unlock")
    public String unlockForm(Model model) {
        model.addAttribute("userUnlockForm", new UserUnlockForm());
        return "unlock";
    }
}
