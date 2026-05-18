package com.example.devlogapp.security;

import com.example.devlogapp.config.AdminProperties;
import com.example.devlogapp.config.JacksonConfig;
import com.example.devlogapp.service.VaultBootstrapService;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ② AdminAuthenticationProvider — 올바른 admin passphrase → ROLE_ADMIN 부여,
 *                                   잘못된 passphrase → AuthenticationException
 */
class AdminAuthenticationProviderTest {

    private static final String ADMIN_PASSPHRASE = "strong-admin-pass-for-test";

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();
    private AdminAuthenticationProvider provider;

    @BeforeEach
    void setup(@TempDir Path tempDir) {
        AdminProperties adminProperties = new AdminProperties();
        adminProperties.setPassphrase(ADMIN_PASSPHRASE);

        VaultMetaRepository repo = new VaultMetaRepository(objectMapper, tempDir);

        // bootstrap 먼저 — adminWrappedDek 생성
        VaultBootstrapService bootstrap = new VaultBootstrapService(adminProperties, repo);
        bootstrap.bootstrap();

        provider = new AdminAuthenticationProvider(adminProperties, repo);
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
