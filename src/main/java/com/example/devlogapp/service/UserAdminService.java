package com.example.devlogapp.service;

import com.example.devlogapp.config.AdminProperties;
import com.example.devlogapp.config.StorageProperties;
import com.example.devlogapp.domain.User;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.example.devlogapp.vault.KeyWrapper;
import com.example.devlogapp.vault.PassphraseKdf;
import com.example.devlogapp.vault.UserNotFoundException;
import com.example.devlogapp.vault.VaultMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 사용자 passphrase 재설정 / 삭제 (ROLE_ADMIN 전용).
 * 새 모델: createUser 없음 — 셀프 가입은 UserRegistrationService.
 * resetPassphrase 는 adminWrappedDek 로 DEK_user 회수 후 userWrappedDek 만 교체.
 * PLAN.md §4.5.6 참조.
 */
@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminProperties adminProperties;
    private final VaultMetaRepository vaultMetaRepository;
    private final Path logsRoot;

    @Autowired
    public UserAdminService(AdminProperties adminProperties,
                             VaultMetaRepository vaultMetaRepository,
                             StorageProperties storageProperties) {
        this.adminProperties = adminProperties;
        this.vaultMetaRepository = vaultMetaRepository;
        this.logsRoot = Path.of(storageProperties.getRoot());
    }

    /** 테스트용 — logsRoot 직접 주입. */
    public UserAdminService(AdminProperties adminProperties,
                             VaultMetaRepository vaultMetaRepository,
                             Path logsRoot) {
        this.adminProperties = adminProperties;
        this.vaultMetaRepository = vaultMetaRepository;
        this.logsRoot = logsRoot;
    }

    /**
     * 사용자 passphrase 재설정.
     * adminWrappedDek 로 DEK_user 회수 → 새 passphrase 로 userWrappedDek 만 재발급.
     * DEK_user · adminWrappedDek · 회고 파일은 그대로.
     * PLAN.md §4.5.6 사용자 passphrase 재설정 흐름 참조.
     *
     * @param userId        대상 사용자 ID
     * @param newPassphrase 새 임시 passphrase
     */
    public void resetPassphrase(String userId, String newPassphrase) {
        VaultMeta meta = loadMeta();

        User target = meta.getUsers().stream()
                .filter(u -> u.getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new UserNotFoundException(userId));

        byte[] kAdmin = null;
        byte[] dekUser = null;
        byte[] newDerivation = null;
        byte[] newKUser = null;
        byte[] newHUser = null;
        byte[] newUserSalt = new byte[16];

        try {
            // 3. K_admin = PBKDF2(adminPassphrase, adminSalt, 32)
            byte[] adminSaltBytes = Base64.getDecoder().decode(meta.getAdminSalt());
            kAdmin = PassphraseKdf.deriveAdmin(
                    adminProperties.getPassphrase().toCharArray(), adminSaltBytes);

            // 4. adminWrappedDek unwrap → DEK_user 회수
            User.WrappedDek awd = target.getAdminWrappedDek();
            dekUser = KeyWrapper.unwrap(kAdmin, awd.getNonce(), awd.getCt());

            // 5-6. 새 salt
            RANDOM.nextBytes(newUserSalt);

            // 7. newDerivation = PBKDF2(새 passphrase, newUserSalt, 64)
            newDerivation = PassphraseKdf.deriveUser(newPassphrase.toCharArray(), newUserSalt);
            newKUser = Arrays.copyOfRange(newDerivation, 0, 32);
            newHUser = Arrays.copyOfRange(newDerivation, 32, 64);
            Arrays.fill(newDerivation, (byte) 0);
            newDerivation = null;

            // 8. newUserWrappedDek = AES-GCM(DEK_user, newKUser, newNonce)
            KeyWrapper.WrapResult wrapped = KeyWrapper.wrap(newKUser, dekUser);
            User.WrappedDek newUserWrappedDek = new User.WrappedDek(
                    "AES-256-GCM", wrapped.nonceB64(), wrapped.ctB64());

            // 9. users[id] 엔트리에서 salt, passphraseHash, userWrappedDek 만 교체
            User updated = target.withNewPassphrase(
                    Base64.getEncoder().encodeToString(newUserSalt),
                    Base64.getEncoder().encodeToString(newHUser),
                    newUserWrappedDek);

            List<User> users = meta.getUsers().stream()
                    .map(u -> u.getId().equals(userId) ? updated : u)
                    .toList();

            // 10. .vault-meta.json 저장
            vaultMetaRepository.save(meta.withUsers(users));
            log.info("Passphrase reset for user: {}", userId);
        } finally {
            // 11. 폐기
            if (kAdmin != null) Arrays.fill(kAdmin, (byte) 0);
            if (dekUser != null) Arrays.fill(dekUser, (byte) 0);
            if (newDerivation != null) Arrays.fill(newDerivation, (byte) 0);
            if (newKUser != null) Arrays.fill(newKUser, (byte) 0);
            if (newHUser != null) Arrays.fill(newHUser, (byte) 0);
            Arrays.fill(newUserSalt, (byte) 0);
        }
    }

    /**
     * 사용자 삭제.
     * users[] 엔트리 제거 + data/logs/{userId}/ 디렉토리 통째 제거.
     * PLAN.md §4.5.6 사용자 삭제 흐름 참조.
     */
    public void deleteUser(String userId) {
        VaultMeta meta = loadMeta();

        List<User> users = meta.getUsers().stream()
                .filter(u -> !u.getId().equals(userId))
                .toList();

        vaultMetaRepository.save(meta.withUsers(users));

        // data/logs/{userId}/ 디렉토리 통째 삭제
        Path userDir = logsRoot.resolve(userId);
        if (Files.exists(userDir)) {
            try {
                Files.walkFileTree(userDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        if (exc != null) throw exc;
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new IllegalStateException("Failed to delete user directory: " + userDir, e);
            }
        }

        log.info("User deleted: {}", userId);
    }

    private VaultMeta loadMeta() {
        return vaultMetaRepository.load()
                .orElseThrow(() -> new IllegalStateException("Vault not initialized"));
    }
}
