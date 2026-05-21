package com.example.devlogapp.security;

import com.example.devlogapp.vault.Vault;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security FilterChain 설정.
 * PLAN.md §4.5.6, §3 참조.
 *
 * Phase 2-A: admin/user 두 FilterChain 분리 + CSRF 활성화.
 *  - adminSecurityFilterChain (@Order(1)): /vault/** 전용 — ROLE_ADMIN, loginPage=/vault/login
 *  - userSecurityFilterChain  (@Order(2)): /logs/**, / 등 — ROLE_USER, loginPage=/unlock
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AdminAuthenticationProvider adminAuthenticationProvider;
    private final UserAuthenticationProvider userAuthenticationProvider;
    private final Vault vault;

    public SecurityConfig(AdminAuthenticationProvider adminAuthenticationProvider,
                           UserAuthenticationProvider userAuthenticationProvider,
                           Vault vault) {
        this.adminAuthenticationProvider = adminAuthenticationProvider;
        this.userAuthenticationProvider = userAuthenticationProvider;
        this.vault = vault;
    }

    /**
     * Admin FilterChain — /vault/** 전용.
     * 우선순위 1: /vault/** 요청을 먼저 가로챔.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/vault/**")
            .authenticationProvider(adminAuthenticationProvider)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/vault/login").permitAll()
                .requestMatchers("/vault/**").hasRole("ADMIN")
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
                .clearAuthentication(true)
                .invalidateHttpSession(true)
                .permitAll()
            );
            // CSRF 활성화 (기본값 — Thymeleaf _csrf 토큰 자동 삽입)

        return http.build();
    }

    /**
     * User FilterChain — /logs/**, /, /unlock 등.
     * 우선순위 2: admin 체인에서 처리 안 된 나머지.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain userSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(userAuthenticationProvider)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/unlock", "/register", "/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/logs/**", "/").hasRole("USER")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/unlock")
                .loginProcessingUrl("/unlock")
                .defaultSuccessUrl("/logs", true)
                .failureUrl("/unlock?error")
                .usernameParameter("userId")
                .passwordParameter("passphrase")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/unlock")
                .addLogoutHandler((req, res, auth) -> vault.lock())
                .clearAuthentication(true)
                .invalidateHttpSession(true)
                .permitAll()
            );
            // CSRF 활성화 — /unlock CSRF ignoring 제거 (Phase 2-A TODO #2 완료)
            // LogoutHandler 로 Vault.lock() 등록 — PLAN §5.6 "POST /logout 시 DEK 메모리 해제"

        return http.build();
    }
}
