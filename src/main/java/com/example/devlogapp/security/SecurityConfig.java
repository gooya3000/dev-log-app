package com.example.devlogapp.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security FilterChain 설정.
 * PLAN.md §4.5.6, §3 참조.
 *
 * - /vault/login, /unlock, static → permitAll
 * - /vault/** → ROLE_ADMIN
 * - /logs/**, / → ROLE_USER
 * - CSRF 활성화
 *
 * Phase 1 임시 상태:
 *   현재 단일 FilterChain 의 formLogin 이 admin 흐름(/vault/login → /vault/users)만 박혀 있어
 *   user 미인증 요청도 /vault/login 으로 리다이렉트된다. PLAN.md §2 의
 *   "/logs/** 미인증 → /unlock" 정책과 어긋남.
 *   또한 /unlock 에 대한 csrf.ignoringRequestMatchers 도 임시 — §4.5.6 의 "CSRF 활성화" 와 충돌.
 *
 * Phase 2-A TODO:
 *   1) FilterChain 을 두 개로 분리 (admin /vault/**, user /logs/**+/) — 각자 loginPage·success/failure URL 분리.
 *   2) /unlock CSRF ignoring 제거 — Thymeleaf 폼에 _csrf 토큰 심기.
 *   3) AuthController/AdminAuthController 가 각 FilterChain 의 loginProcessingUrl 을 받도록 정합.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AdminAuthenticationProvider adminAuthenticationProvider;
    private final UserAuthenticationProvider userAuthenticationProvider;

    public SecurityConfig(AdminAuthenticationProvider adminAuthenticationProvider,
                           UserAuthenticationProvider userAuthenticationProvider) {
        this.adminAuthenticationProvider = adminAuthenticationProvider;
        this.userAuthenticationProvider = userAuthenticationProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(adminAuthenticationProvider)
            .authenticationProvider(userAuthenticationProvider)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/vault/login", "/unlock", "/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/vault/**").hasRole("ADMIN")
                .requestMatchers("/logs/**", "/").hasRole("USER")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/vault/login")
                .loginProcessingUrl("/vault/login")
                .defaultSuccessUrl("/vault/users", true)
                .failureUrl("/vault/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/vault/logout")
                .logoutSuccessUrl("/vault/login")
                .permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/unlock")  // 사용자 unlock POST (별도 처리)
            );

        return http.build();
    }
}
