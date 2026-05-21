package com.example.devlogapp.web;

import com.example.devlogapp.service.UserRegistrationService;
import com.example.devlogapp.web.form.UserRegisterForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 사용자 셀프 가입 컨트롤러.
 * PLAN.md §2 /register 매핑, §4.5.6 셀프 가입 흐름 참조.
 *
 * 비즈니스 로직은 UserRegistrationService에만.
 * passphrase 필드는 로그에 찍히지 않도록 폼 객체를 통째로 로깅하지 않음.
 */
@Controller
public class RegisterController {

    private final UserRegistrationService userRegistrationService;

    public RegisterController(UserRegistrationService userRegistrationService) {
        this.userRegistrationService = userRegistrationService;
    }

    /** GET /register — 가입 폼 렌더. */
    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("userRegisterForm", new UserRegisterForm());
        return "register";
    }

    /**
     * POST /register — 가입 처리.
     *
     * IllegalArgumentException (userId 중복 등): form 재렌더 (200) 로 처리.
     * — 이유: 사용자에게 폼을 다시 보여주고 수정할 기회를 주는 것이 UX상 자연스러움.
     *   4xx 응답은 AJAX 클라이언트에 적합하나, Thymeleaf 폼 재렌더 패턴에서는 200이 표준.
     *   Spring MVC @Valid 오류도 200+폼 재렌더이므로 일관성 유지.
     */
    @PostMapping("/register")
    public String register(@Valid @ModelAttribute UserRegisterForm userRegisterForm,
                           BindingResult bindingResult) {
        // 1. @Valid 오류 먼저 확인
        if (bindingResult.hasErrors()) {
            return "register";
        }

        // 2. passphrase 일치 확인
        if (!userRegisterForm.getPassphrase().equals(userRegisterForm.getPassphraseConfirm())) {
            bindingResult.rejectValue("passphraseConfirm", "mismatch",
                    "Passphrase 확인이 일치하지 않습니다.");
            return "register";
        }

        // 3. 가입 처리
        try {
            userRegistrationService.register(
                    userRegisterForm.getUserId(),
                    userRegisterForm.getPassphrase());
        } catch (IllegalArgumentException e) {
            // userId 중복 또는 기타 가입 거부 — PLAN §5.6 "구체적 실패 원인 노출 금지"
            // "가입할 수 없습니다." 로 일반화하여 userId 존재 여부 누출 차단
            bindingResult.rejectValue("userId", "duplicate", "가입할 수 없습니다.");
            return "register";
        }

        return "redirect:/unlock?registered";
    }
}
