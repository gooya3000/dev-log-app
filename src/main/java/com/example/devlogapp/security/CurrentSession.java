package com.example.devlogapp.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 현재 Spring Security 컨텍스트의 권한·userId 헬퍼.
 */
@Component
public class CurrentSession {

    /** 현재 ROLE_ADMIN 세션 여부. */
    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    /** 현재 ROLE_USER 세션 여부. */
    public boolean isUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_USER"::equals);
    }

    /**
     * 현재 unlock 한 userId 반환.
     * UserAuthenticationToken 의 principal 이 userId String.
     */
    public String getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        return principal instanceof String s ? s : null;
    }
}
