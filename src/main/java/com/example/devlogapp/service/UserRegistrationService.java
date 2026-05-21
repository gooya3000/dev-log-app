package com.example.devlogapp.service;

import com.example.devlogapp.config.AdminProperties;
import com.example.devlogapp.config.StorageProperties;
import com.example.devlogapp.domain.User;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.example.devlogapp.vault.KeyWrapper;
import com.example.devlogapp.vault.PassphraseKdf;
import com.example.devlogapp.vault.VaultMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 사용자 셀프 가입 서비스.
 * DEK_user 랜덤 발급 → userWrappedDek + adminWrappedDek 두 사본 저장.
 * PLAN.md §4.5.6 셀프 가입 흐름 참조.
 */
@Service
public class UserRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminProperties adminProperties;
    private final VaultMetaRepository vaultMetaRepository;
    private final Path logsRoot;

    @Autowired
    public UserRegistrationService(AdminProperties adminProperties,
                                    VaultMetaRepository vaultMetaRepository,
                                    StorageProperties storageProperties) {
        this.adminProperties = adminProperties;
        this.vaultMetaRepository = vaultMetaRepository;
        this.logsRoot = Path.of(storageProperties.getRoot());
    }

    /** 테스트용 — logsRoot 직접 주입. */
    public UserRegistrationService(AdminProperties adminProperties,
                                    VaultMetaRepository vaultMetaRepository,
                                    Path logsRoot) {
        this.adminProperties = adminProperties;
        this.vaultMetaRepository = vaultMetaRepository;
        this.logsRoot = logsRoot;
    }

    /**
     * 셀프 가입.
     * PLAN.md §4.5.6 셀프 가입 의사코드 단계 그대로.
     *
     * @param userId     사용자 ID
     * @param passphrase 사용자 passphrase
     * @throws IllegalArgumentException userId 중복
     * @throws IllegalStateException    vault 미초기화
     */
    public void register(String userId, String passphrase) {
        VaultMeta meta = vaultMetaRepository.load()
                .orElseThrow(() -> new IllegalStateException("Vault not initialized"));

        // 1. userId 중복 검사
        boolean duplicate = meta.getUsers().stream()
                .anyMatch(u -> u.getId().equals(userId));
        if (duplicate) {
            throw new IllegalArgumentException("이미 사용 중인 사용자 ID입니다.");
        }

        byte[] dekUser = new byte[32];
        byte[] userSalt = new byte[16];
        byte[] derivation = null;
        byte[] kUser = null;
        byte[] hUser = null;
        byte[] kAdmin = null;

        try {
            // 2. DEK_user = SecureRandom.nextBytes(32)
            RANDOM.nextBytes(dekUser);
            // 3. userSalt = SecureRandom.nextBytes(16)
            RANDOM.nextBytes(userSalt);

            // 4. PBKDF2 64바이트 → K_user / H_user 분리
            derivation = PassphraseKdf.deriveUser(passphrase.toCharArray(), userSalt);
            kUser = Arrays.copyOfRange(derivation, 0, 32);
            hUser = Arrays.copyOfRange(derivation, 32, 64);
            Arrays.fill(derivation, (byte) 0);
            derivation = null;

            // 5. userWrappedDek = AES-GCM(DEK_user, K_user, randomNonce1)
            KeyWrapper.WrapResult userWrap = KeyWrapper.wrap(kUser, dekUser);
            User.WrappedDek userWrappedDek = new User.WrappedDek(
                    "AES-256-GCM", userWrap.nonceB64(), userWrap.ctB64());

            // 6-7. adminSalt 읽기 → K_admin = PBKDF2(adminPassphrase, adminSalt, 32)
            byte[] adminSaltBytes = Base64.getDecoder().decode(meta.getAdminSalt());
            kAdmin = PassphraseKdf.deriveAdmin(
                    adminProperties.getPassphrase().toCharArray(), adminSaltBytes);

            // 8. adminWrappedDek = AES-GCM(DEK_user, K_admin, randomNonce2)
            KeyWrapper.WrapResult adminWrap = KeyWrapper.wrap(kAdmin, dekUser);
            User.WrappedDek adminWrappedDek = new User.WrappedDek(
                    "AES-256-GCM", adminWrap.nonceB64(), adminWrap.ctB64());

            // 9. users[] 에 새 엔트리 추가
            User newUser = new User(
                    userId,
                    Base64.getEncoder().encodeToString(userSalt),
                    Base64.getEncoder().encodeToString(hUser),
                    OffsetDateTime.now(),
                    userWrappedDek,
                    adminWrappedDek);

            List<User> users = new ArrayList<>(meta.getUsers());
            users.add(newUser);

            // 10. .vault-meta.json 저장 (atomic write)
            vaultMetaRepository.save(meta.withUsers(users));

            // 11. data/logs/{userId}/ 디렉토리 생성
            createUserLogsDir(userId);

            log.info("User registered: {}", userId);
        } finally {
            // 12. 모든 민감 바이트 폐기
            Arrays.fill(dekUser, (byte) 0);
            Arrays.fill(userSalt, (byte) 0);
            if (derivation != null) Arrays.fill(derivation, (byte) 0);
            if (kUser != null) Arrays.fill(kUser, (byte) 0);
            if (hUser != null) Arrays.fill(hUser, (byte) 0);
            if (kAdmin != null) Arrays.fill(kAdmin, (byte) 0);
        }
    }

    private void createUserLogsDir(String userId) {
        try {
            Files.createDirectories(logsRoot.resolve(userId));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to create user logs directory for: " + userId, e);
        }
    }
}
