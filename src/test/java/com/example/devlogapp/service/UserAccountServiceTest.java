package com.example.devlogapp.service;

import com.example.devlogapp.config.AdminProperties;
import com.example.devlogapp.config.JacksonConfig;
import com.example.devlogapp.domain.User;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.example.devlogapp.vault.KeyWrapper;
import com.example.devlogapp.vault.PassphraseKdf;
import com.example.devlogapp.vault.Vault;
import com.example.devlogapp.vault.VaultMeta;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserAccountService 단위 테스트.
 * PLAN.md §6.2 R-4 Done 기준 ②~⑦, ⑩ 충족.
 */
class UserAccountServiceTest {

    private static final String ADMIN_PASSPHRASE = "test-admin-account-service";
    private static final String USER_ID = "bob";
    private static final String OLD_PASSPHRASE = "old-passphrase-for-bob-test";
    private static final String NEW_PASSPHRASE = "new-passphrase-for-bob-test";

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();
    private VaultMetaRepository vaultMetaRepository;
    private UserRegistrationService userRegistrationService;
    private UserAuthService userAuthService;
    private UserAccountService userAccountService;
    private Vault vault;

    @BeforeEach
    void setup(@TempDir Path tempDir) throws IOException {
        AdminProperties adminProperties = new AdminProperties();
        adminProperties.setPassphrase(ADMIN_PASSPHRASE);

        Path logsRoot = tempDir.resolve("logs");
        Files.createDirectories(logsRoot);

        vaultMetaRepository = new VaultMetaRepository(objectMapper, tempDir);
        vault = new Vault();

        VaultBootstrapService bootstrap = new VaultBootstrapService(adminProperties, vaultMetaRepository, logsRoot);
        bootstrap.bootstrap();

        userRegistrationService = new UserRegistrationService(adminProperties, vaultMetaRepository, logsRoot);
        userRegistrationService.register(USER_ID, OLD_PASSPHRASE);

        userAuthService = new UserAuthService(vaultMetaRepository, vault);
        userAuthService.unlock(USER_ID, OLD_PASSPHRASE);  // Vault 에 DEK 적재

        userAccountService = new UserAccountService(vaultMetaRepository, vault);
    }

    // ② POST 옛/새/확인 일치 → users[] salt·passphraseHash·userWrappedDek 교체, adminWrappedDek 동일
    @Test
    void changeOwnPassphrase_success_updates_only_user_wrapped_fields() {
        VaultMeta before = vaultMetaRepository.load().orElseThrow();
        User userBefore = before.getUsers().stream()
                .filter(u -> u.getId().equals(USER_ID)).findFirst().orElseThrow();
        String adminWrappedDekCtBefore = userBefore.getAdminWrappedDek().getCt();
        String oldSalt = userBefore.getSalt();
        String oldPassphraseHash = userBefore.getPassphraseHash();
        String oldUserWrappedDekCt = userBefore.getUserWrappedDek().getCt();

        userAccountService.changeOwnPassphrase(USER_ID, OLD_PASSPHRASE, NEW_PASSPHRASE);

        VaultMeta after = vaultMetaRepository.load().orElseThrow();
        User userAfter = after.getUsers().stream()
                .filter(u -> u.getId().equals(USER_ID)).findFirst().orElseThrow();

        // salt, passphraseHash, userWrappedDek 은 교체됨
        assertThat(userAfter.getSalt()).isNotEqualTo(oldSalt);
        assertThat(userAfter.getPassphraseHash()).isNotEqualTo(oldPassphraseHash);
        assertThat(userAfter.getUserWrappedDek().getCt()).isNotEqualTo(oldUserWrappedDekCt);

        // adminWrappedDek 는 절대 건드리지 않음 (PLAN §4.5.6)
        assertThat(userAfter.getAdminWrappedDek().getCt()).isEqualTo(adminWrappedDekCtBefore);
    }

    // ③ 변경 후 같은 세션으로 Vault DEK_user 동일 (세션 유지)
    @Test
    void changeOwnPassphrase_vaultDekUnchanged() {
        byte[] dekBefore = vault.copyDek();
        try {
            userAccountService.changeOwnPassphrase(USER_ID, OLD_PASSPHRASE, NEW_PASSPHRASE);

            assertThat(vault.isLocked()).isFalse();
            assertThat(vault.getUserId()).isEqualTo(USER_ID);
            byte[] dekAfter = vault.copyDek();
            try {
                assertThat(dekAfter).isEqualTo(dekBefore);
            } finally {
                Arrays.fill(dekAfter, (byte) 0);
            }
        } finally {
            Arrays.fill(dekBefore, (byte) 0);
        }
    }

    // ④ 변경 후 새 passphrase 로 unlock 가능, 옛 passphrase 로 unlock 실패
    @Test
    void changeOwnPassphrase_newPassphraseWorks_oldPassphraseFails() {
        userAccountService.changeOwnPassphrase(USER_ID, OLD_PASSPHRASE, NEW_PASSPHRASE);

        vault.lock();

        // 새 passphrase 로 unlock 가능
        userAuthService.unlock(USER_ID, NEW_PASSPHRASE);
        assertThat(vault.isLocked()).isFalse();
        vault.lock();

        // 옛 passphrase 로 unlock 실패
        assertThatThrownBy(() -> userAuthService.unlock(USER_ID, OLD_PASSPHRASE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ⑤ admin 의 adminWrappedDek 로 DEK_user 회수 시 변경 전과 동일한 32바이트 (DEK_user 불변 증명)
    @Test
    void changeOwnPassphrase_adminWrappedDekStillRecoversSameDek() {
        byte[] dekBeforeChange = vault.copyDek();
        try {
            userAccountService.changeOwnPassphrase(USER_ID, OLD_PASSPHRASE, NEW_PASSPHRASE);

            VaultMeta meta = vaultMetaRepository.load().orElseThrow();
            User user = meta.getUsers().stream()
                    .filter(u -> u.getId().equals(USER_ID)).findFirst().orElseThrow();

            byte[] adminSaltBytes = Base64.getDecoder().decode(meta.getAdminSalt());
            byte[] kAdmin = PassphraseKdf.deriveAdmin(ADMIN_PASSPHRASE.toCharArray(), adminSaltBytes);
            try {
                User.WrappedDek awd = user.getAdminWrappedDek();
                byte[] dekFromAdmin = KeyWrapper.unwrap(kAdmin, awd.getNonce(), awd.getCt());
                try {
                    assertThat(dekFromAdmin).isEqualTo(dekBeforeChange);
                } finally {
                    Arrays.fill(dekFromAdmin, (byte) 0);
                }
            } finally {
                Arrays.fill(kAdmin, (byte) 0);
            }
        } finally {
            Arrays.fill(dekBeforeChange, (byte) 0);
        }
    }

    // ⑥ 옛 passphrase 불일치 → PassphraseMismatchException, users[] 무변경
    @Test
    void changeOwnPassphrase_wrongOldPassphrase_throwsAndNoChange() {
        VaultMeta before = vaultMetaRepository.load().orElseThrow();
        User userBefore = before.getUsers().stream()
                .filter(u -> u.getId().equals(USER_ID)).findFirst().orElseThrow();
        String saltBefore = userBefore.getSalt();

        assertThatThrownBy(() ->
                userAccountService.changeOwnPassphrase(USER_ID, "wrong-passphrase", NEW_PASSPHRASE))
                .isInstanceOf(PassphraseMismatchException.class);

        // vault-meta 변경 없음
        VaultMeta after = vaultMetaRepository.load().orElseThrow();
        User userAfter = after.getUsers().stream()
                .filter(u -> u.getId().equals(USER_ID)).findFirst().orElseThrow();
        assertThat(userAfter.getSalt()).isEqualTo(saltBefore);
    }

    // ⑩ 로그·예외 메시지에 passphrase/DEK/K_user 원문 노출 없음 확인
    //    - 예외 메시지에 passphrase 원문 포함 여부 assert
    @Test
    void changeOwnPassphrase_exceptionMessage_doesNotContainPassphrase() {
        String wrongPass = "secret-wrong-password-12345";
        try {
            userAccountService.changeOwnPassphrase(USER_ID, wrongPass, NEW_PASSPHRASE);
        } catch (PassphraseMismatchException e) {
            assertThat(e.getMessage()).doesNotContain(wrongPass);
            assertThat(e.getMessage()).doesNotContain(NEW_PASSPHRASE);
        }
    }
}
