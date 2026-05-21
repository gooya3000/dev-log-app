package com.example.devlogapp.security;

import com.example.devlogapp.config.AdminProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ② AdminAuthenticationProvider — 올바른 admin passphrase → ROLE_ADMIN 부여,
 *                                   잘못된 passphrase → AuthenticationException.
 * 새 모델: adminWrappedDek sanity 없이 constant-time 비교만.
 */
class AdminAuthenticationProviderTest {

    private static final String ADMIN_PASSPHRASE = "strong-admin-pass-for-test";

    private AdminAuthenticationProvider provider;

    @BeforeEach
    void setup() {
        AdminProperties adminProperties = new AdminProperties();
        adminProperties.setPassphrase(ADMIN_PASSPHRASE);
        provider = new AdminAuthenticationProvider(adminProperties);
    }

    @Test
    void correctPassphrase_returnsRoleAdmin() {
        // ② 올바른 admin passphrase → ROLE_ADMIN authority 포함 Authentication 반환
        Authentication token = new UsernamePasswordAuthenticationToken("admin", ADMIN_PASSPHRASE);
        Authentication result = provider.authenticate(token);

        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN");
    }

    @Test
    void wrongPassphrase_throwsBadCredentials() {
        // ② 잘못된 passphrase → AuthenticationException
        Authentication token = new UsernamePasswordAuthenticationToken("admin", "wrong-passphrase");

        assertThatThrownBy(() -> provider.authenticate(token))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void emptyPassphrase_throwsBadCredentials() {
        Authentication token = new UsernamePasswordAuthenticationToken("admin", "");

        assertThatThrownBy(() -> provider.authenticate(token))
                .isInstanceOf(BadCredentialsException.class);
    }
}
