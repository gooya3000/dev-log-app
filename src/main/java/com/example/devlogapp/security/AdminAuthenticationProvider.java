package com.example.devlogapp.security;

import com.example.devlogapp.config.AdminProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Admin passphrase 검증.
 * 새 모델: adminWrappedDek sanity unwrap 없음 (bootstrap 에서 DEK 안 만듦).
 * application-local.properties 값과 constant-time 비교만 수행.
 * PLAN.md §4.5.6 관리자 로그인 흐름 참조.
 */
@Component
public class AdminAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthenticationProvider.class);
    private static final String ADMIN_PRINCIPAL = "admin";

    private final AdminProperties adminProperties;

    public AdminAuthenticationProvider(AdminProperties adminProperties) {
        this.adminProperties = adminProperties;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String inputPassphrase = (String) authentication.getCredentials();

        if (inputPassphrase == null || inputPassphrase.isBlank()) {
            delay500ms();
            throw new BadCredentialsException("Invalid admin passphrase");
        }

        // constant-time 비교 (timing attack 완화)
        boolean match = MessageDigest.isEqual(
                adminProperties.getPassphrase().getBytes(StandardCharsets.UTF_8),
                inputPassphrase.getBytes(StandardCharsets.UTF_8));

        if (!match) {
            delay500ms();
            throw new BadCredentialsException("Invalid admin passphrase");
        }

        log.info("Admin authenticated successfully");
        return new UsernamePasswordAuthenticationToken(
                ADMIN_PRINCIPAL, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private void delay500ms() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
