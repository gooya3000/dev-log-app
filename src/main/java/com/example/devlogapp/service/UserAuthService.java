package com.example.devlogapp.service;

import com.example.devlogapp.domain.User;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.example.devlogapp.vault.KeyWrapper;
import com.example.devlogapp.vault.PassphraseKdf;
import com.example.devlogapp.vault.Vault;
import com.example.devlogapp.vault.VaultMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * 사용자 로그인 서비스 — passphrase 검증 + DEK unwrap → Vault 적재.
 * PLAN.md §4.5.6 사용자 로그인 흐름 참조.
 */
@Service
public class UserAuthService {

    private static final Logger log = LoggerFactory.getLogger(UserAuthService.class);

    private final VaultMetaRepository vaultMetaRepository;
    private final Vault vault;

    public UserAuthService(VaultMetaRepository vaultMetaRepository, Vault vault) {
        this.vaultMetaRepository = vaultMetaRepository;
        this.vault = vault;
    }

    /**
     * passphrase 로 H_user 검증 → DEK unwrap → Vault 적재.
     * @param userId     사용자 ID
     * @param passphrase 입력 passphrase
     * @throws IllegalArgumentException 사용자 없음 또는 passphrase 불일치
     */
    public void unlock(String userId, String passphrase) {
        VaultMeta meta = vaultMetaRepository.load()
                .orElseThrow(() -> new IllegalStateException("Vault not initialized"));

        User user = meta.getUsers().stream()
                .filter(u -> u.getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Authentication failed"));

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
            throw new IllegalArgumentException("Authentication failed");
        }

        byte[] dek = null;
        try {
            User.WrappedDek wd = user.getWrappedDek();
            dek = KeyWrapper.unwrap(kUser, wd.getNonce(), wd.getCt());
            vault.unlock(dek, userId);
            log.info("User unlocked: {}", userId);
        } catch (IllegalStateException e) {
            log.warn("DEK unwrap failed for user: {}", userId);
            throw new IllegalArgumentException("Authentication failed", e);
        } finally {
            Arrays.fill(kUser, (byte) 0);
            if (dek != null) Arrays.fill(dek, (byte) 0);
        }
    }
}
