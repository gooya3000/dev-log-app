package com.example.devlogapp.security;

import com.example.devlogapp.config.AdminProperties;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.example.devlogapp.vault.KeyWrapper;
import com.example.devlogapp.vault.PassphraseKdf;
import com.example.devlogapp.vault.VaultMeta;
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
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Admin passphrase 검증 + adminWrappedDek sanity unwrap.
 * PLAN.md §4.5.6 관리자 로그인 흐름 참조.
 */
@Component
public class AdminAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthenticationProvider.class);
    private static final String ADMIN_PRINCIPAL = "admin";

    private final AdminProperties adminProperties;
    private final VaultMetaRepository vaultMetaRepository;

    public AdminAuthenticationProvider(AdminProperties adminProperties,
                                        VaultMetaRepository vaultMetaRepository) {
        this.adminProperties = adminProperties;
        this.vaultMetaRepository = vaultMetaRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String inputPassphrase = (String) authentication.getCredentials();

        // constant-time 비교 (timing attack 완화)
        boolean match = MessageDigest.isEqual(
                adminProperties.getPassphrase().getBytes(StandardCharsets.UTF_8),
                inputPassphrase.getBytes(StandardCharsets.UTF_8));

        if (!match) {
            delay500ms();
            throw new BadCredentialsException("Invalid admin passphrase");
        }

        // sanity unwrap — 저장된 adminWrappedDek 이 실제로 풀리는지 확인
        VaultMeta meta = vaultMetaRepository.load()
                .orElseThrow(() -> new BadCredentialsException("Vault not initialized"));

        VaultMeta.AdminWrappedDek wrapped = meta.getAdminWrappedDek();
        byte[] adminSalt = Base64.getDecoder().decode(wrapped.getSalt());
        byte[] kAdmin = PassphraseKdf.deriveAdmin(inputPassphrase.toCharArray(), adminSalt);
        byte[] dek = null;
        try {
            dek = KeyWrapper.unwrap(kAdmin, wrapped.getNonce(), wrapped.getCt());
            // DEK 검증 완료 — 32바이트인지 확인
            if (dek.length != 32) {
                throw new BadCredentialsException("Vault meta corrupted");
            }
        } catch (IllegalStateException e) {
            log.warn("Admin passphrase sanity unwrap failed");
            delay500ms();
            throw new BadCredentialsException("Invalid admin passphrase");
        } finally {
            KeyWrapper.wipe(kAdmin);
            if (dek != null) Arrays.fill(dek, (byte) 0);
        }

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
