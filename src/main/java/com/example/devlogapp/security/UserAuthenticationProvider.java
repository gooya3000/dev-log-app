package com.example.devlogapp.security;

import com.example.devlogapp.domain.User;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.example.devlogapp.vault.KeyWrapper;
import com.example.devlogapp.vault.PassphraseKdf;
import com.example.devlogapp.vault.Vault;
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

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * User passphrase 검증 + userWrappedDek unwrap → Vault 적재.
 * 새 모델: userWrappedDek (K_user) 로 DEK_user unwrap.
 * PLAN.md §4.5.6 사용자 로그인 흐름 참조.
 */
@Component
public class UserAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(UserAuthenticationProvider.class);

    private final VaultMetaRepository vaultMetaRepository;
    private final Vault vault;

    public UserAuthenticationProvider(VaultMetaRepository vaultMetaRepository, Vault vault) {
        this.vaultMetaRepository = vaultMetaRepository;
        this.vault = vault;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        // principal = userId, credentials = passphrase
        String userId = (String) authentication.getPrincipal();
        String passphrase = (String) authentication.getCredentials();

        VaultMeta meta = vaultMetaRepository.load()
                .orElseThrow(() -> new BadCredentialsException("Vault not initialized"));

        User user = meta.getUsers().stream()
                .filter(u -> u.getId().equals(userId))
                .findFirst()
                .orElse(null);

        if (user == null) {
            delay500ms();
            throw new BadCredentialsException("Authentication failed");
        }

        byte[] userSalt = Base64.getDecoder().decode(user.getSalt());
        byte[] derivation = PassphraseKdf.deriveUser(passphrase.toCharArray(), userSalt);
        byte[] kUser = Arrays.copyOfRange(derivation, 0, 32);
        byte[] hUser = Arrays.copyOfRange(derivation, 32, 64);
        Arrays.fill(derivation, (byte) 0);

        byte[] storedHash = Base64.getDecoder().decode(user.getPassphraseHash());
        boolean match = MessageDigest.isEqual(hUser, storedHash);
        Arrays.fill(hUser, (byte) 0);

        if (!match) {
            Arrays.fill(kUser, (byte) 0);
            delay500ms();
            throw new BadCredentialsException("Authentication failed");
        }

        // K_user 로 userWrappedDek unwrap → Vault 적재
        byte[] dek = null;
        try {
            User.WrappedDek uwd = user.getUserWrappedDek();
            dek = KeyWrapper.unwrap(kUser, uwd.getNonce(), uwd.getCt());
            vault.unlock(dek, userId);
            log.info("User unlocked: {}", userId);
        } catch (IllegalStateException e) {
            log.warn("DEK unwrap failed for user: {}", userId);
            delay500ms();
            throw new BadCredentialsException("Authentication failed");
        } finally {
            Arrays.fill(kUser, (byte) 0);
            if (dek != null) Arrays.fill(dek, (byte) 0);
        }

        return new UsernamePasswordAuthenticationToken(
                userId, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
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
