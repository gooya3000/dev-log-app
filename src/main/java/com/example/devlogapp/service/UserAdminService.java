package com.example.devlogapp.service;

import com.example.devlogapp.config.AdminProperties;
import com.example.devlogapp.domain.User;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.example.devlogapp.vault.KeyWrapper;
import com.example.devlogapp.vault.PassphraseKdf;
import com.example.devlogapp.vault.UserNotFoundException;
import com.example.devlogapp.vault.VaultMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 사용자 생성/삭제/passphrase 재설정 (ROLE_ADMIN 전용 유스케이스).
 * admin passphrase 인자로 받아 DEK 임시 unwrap.
 * PLAN.md §4.5.6 참조.
 */
@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminProperties adminProperties;
    private final VaultMetaRepository vaultMetaRepository;

    public UserAdminService(AdminProperties adminProperties,
                             VaultMetaRepository vaultMetaRepository) {
        this.adminProperties = adminProperties;
        this.vaultMetaRepository = vaultMetaRepository;
    }

    /**
     * 사용자 생성.
     * @param userId       사용자 ID
     * @param passphrase   초기 passphrase (관리자가 지정 또는 외부에서 random 생성 후 전달)
     * @throws IllegalStateException vault 미초기화, DEK unwrap 실패
     */
    public void createUser(String userId, String passphrase) {
        VaultMeta meta = loadMeta();

        byte[] dek = unwrapDekWithAdminKey(meta);
        try {
            byte[] userSalt = new byte[16];
            RANDOM.nextBytes(userSalt);

            byte[] derivation = PassphraseKdf.deriveUser(passphrase.toCharArray(), userSalt);
            byte[] kUser = Arrays.copyOfRange(derivation, 0, 32);
            byte[] hUser = Arrays.copyOfRange(derivation, 32, 64);
            Arrays.fill(derivation, (byte) 0);

            try {
                KeyWrapper.WrapResult wrapped = KeyWrapper.wrap(kUser, dek);
                User.WrappedDek wrappedDek = new User.WrappedDek(
                        "AES-256-GCM", wrapped.nonceB64(), wrapped.ctB64());

                User newUser = new User(
                        userId,
                        Base64.getEncoder().encodeToString(userSalt),
                        Base64.getEncoder().encodeToString(hUser),
                        wrappedDek);

                List<User> users = new ArrayList<>(meta.getUsers());
                users.add(newUser);
                vaultMetaRepository.save(meta.withUsers(users));
                log.info("User created: {}", userId);
            } finally {
                Arrays.fill(kUser, (byte) 0);
                Arrays.fill(hUser, (byte) 0);
            }
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    /**
     * 사용자 passphrase 재설정.
     * DEK 는 그대로 — wrappedDek 만 새 passphrase 로 재발급.
     * PLAN.md §4.5.6 사용자 passphrase 재설정 흐름 참조.
     *
     * @param userId      대상 사용자 ID
     * @param newPassphrase 새 임시 passphrase
     */
    public void resetPassphrase(String userId, String newPassphrase) {
        VaultMeta meta = loadMeta();

        // 대상 사용자 존재 확인
        meta.getUsers().stream()
                .filter(u -> u.getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new UserNotFoundException(userId));

        byte[] dek = unwrapDekWithAdminKey(meta);
        try {
            byte[] newUserSalt = new byte[16];
            RANDOM.nextBytes(newUserSalt);

            byte[] newDerivation = PassphraseKdf.deriveUser(newPassphrase.toCharArray(), newUserSalt);
            byte[] newKUser = Arrays.copyOfRange(newDerivation, 0, 32);
            byte[] newHUser = Arrays.copyOfRange(newDerivation, 32, 64);
            Arrays.fill(newDerivation, (byte) 0);

            try {
                KeyWrapper.WrapResult wrapped = KeyWrapper.wrap(newKUser, dek);
                User.WrappedDek newWrappedDek = new User.WrappedDek(
                        "AES-256-GCM", wrapped.nonceB64(), wrapped.ctB64());

                User updatedUser = new User(
                        userId,
                        Base64.getEncoder().encodeToString(newUserSalt),
                        Base64.getEncoder().encodeToString(newHUser),
                        newWrappedDek);

                List<User> users = meta.getUsers().stream()
                        .map(u -> u.getId().equals(userId) ? updatedUser : u)
                        .toList();

                vaultMetaRepository.save(meta.withUsers(users));
                log.info("Passphrase reset for user: {}", userId);
            } finally {
                Arrays.fill(newKUser, (byte) 0);
                Arrays.fill(newHUser, (byte) 0);
            }
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    /**
     * 사용자 매핑 엔트리 제거.
     * 해당 사용자는 이후 데이터 접근 불가.
     */
    public void deleteUser(String userId) {
        VaultMeta meta = loadMeta();

        List<User> users = meta.getUsers().stream()
                .filter(u -> !u.getId().equals(userId))
                .toList();

        vaultMetaRepository.save(meta.withUsers(users));
        log.info("User deleted: {}", userId);
    }

    /** admin passphrase 로 K_admin 도출 후 DEK unwrap. 호출자가 Arrays.fill 로 폐기. */
    private byte[] unwrapDekWithAdminKey(VaultMeta meta) {
        VaultMeta.AdminWrappedDek wrapped = meta.getAdminWrappedDek();
        byte[] adminSalt = Base64.getDecoder().decode(wrapped.getSalt());
        byte[] kAdmin = PassphraseKdf.deriveAdmin(
                adminProperties.getPassphrase().toCharArray(), adminSalt);
        try {
            return KeyWrapper.unwrap(kAdmin, wrapped.getNonce(), wrapped.getCt());
        } finally {
            Arrays.fill(kAdmin, (byte) 0);
        }
    }

    private VaultMeta loadMeta() {
        return vaultMetaRepository.load()
                .orElseThrow(() -> new IllegalStateException("Vault not initialized"));
    }
}
