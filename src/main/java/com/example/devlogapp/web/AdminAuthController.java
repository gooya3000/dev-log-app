package com.example.devlogapp.web;

import com.example.devlogapp.web.form.AdminLoginForm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 관리자 인증 컨트롤러. GET /vault/login 폼 렌더링만 담당.
 * POST /vault/login · POST /vault/logout 은 adminSecurityFilterChain 이 처리.
 * PLAN.md §2, §5.7 참조.
 */
@Controller
@RequestMapping("/vault")
public class AdminAuthController {

    @GetMapping("/login")
    public String loginForm(Model model) {
        model.addAttribute("adminLoginForm", new AdminLoginForm());
        return "vault/login";
    }
}
