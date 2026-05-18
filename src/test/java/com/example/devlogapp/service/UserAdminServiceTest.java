package com.example.devlogapp.service;

import com.example.devlogapp.config.AdminProperties;
import com.example.devlogapp.config.JacksonConfig;
import com.example.devlogapp.domain.User;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.example.devlogapp.vault.Vault;
import com.example.devlogapp.vault.VaultMeta;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ③ UserAdminService.createUser — 호출 후 users[] 엔트리 추가
 * ⑤ UserAdminService.resetPassphrase — 옛 passphrase 로 unlock 실패, 새 passphrase 로 unlock 성공, DEK 동일
 */
class UserAdminServiceTest {

    private static final String ADMIN_PASSPHRASE = "test-admin-passphrase-2024";
    private static final String USER_ID = "self";

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();
    private VaultMetaRepository vaultMetaRepository;
    private AdminProperties adminProperties;
    private UserAdminService userAdminService;
    private UserAuthService userAuthService;
    private Vault vault;

    @BeforeEach
    void setup(@TempDir Path tempDir) {
        adminProperties = new AdminProperties();
        adminProperties.setPassphrase(ADMIN_PASSPHRASE);

        vaultMetaRepository = new VaultMetaRepository(objectMapper, tempDir);
        vault = new Vault();

        // bootstrap
        VaultBootstrapService bootstrap = new VaultBootstrapService(adminProperties, vaultMetaRepository);
        bootstrap.bootstrap();

        userAdminService = new UserAdminService(adminProperties, vaultMetaRepository);
        userAuthService = new UserAuthService(vaultMetaRepository, vault);
    }

    @Test
    void createUser_addsEntry() {
        // ③ 호출 후 users[] 엔트리 추가
        userAdminService.createUser(USER_ID, "initial-passphrase");

        VaultMeta meta = vaultMetaRepository.load().orElseThrow();
        assertThat(meta.getUsers()).hasSize(1);

        User user = meta.getUsers().get(0);
        assertThat(user.getId()).isEqualTo(USER_ID);
        assertThat(user.getSalt()).isNotBlank();
        assertThat(user.getPassphraseHash()).isNotBlank();
        assertThat(user.getWrappedDek()).isNotNull();
        assertThat(user.getWrappedDek().getCt()).isNotBlank();
    }

    @Test
    void createUser_canUnlock() {
        // 생성 후 해당 passphrase 로 unlock 가능
        String passphrase = "my-initial-passphrase";
        userAdminService.createUser(USER_ID, passphrase);

        userAuthService.unlock(USER_ID, passphrase);
        assertThat(vault.isLocked()).isFalse();
        assertThat(vault.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void resetPassphrase_oldFails_newSucceeds_dekSame() {
        // ⑤ reset 후 옛 passphrase 로 unlock 실패, 새 passphrase 로 unlock 성공
        String oldPassphrase = "old-passphrase";
        String newPassphrase = "new-passphrase-2024";

        userAdminService.createUser(USER_ID, oldPassphrase);

        // unlock 하여 DEK 캡처
        userAuthService.unlock(USER_ID, oldPassphrase);
        byte[] dekBefore = vault.copyDek();
        vault.lock();

        // passphrase 재설정
        userAdminService.resetPassphrase(USER_ID, newPassphrase);

        // 옛 passphrase 로는 실패
        assertThatThrownBy(() -> userAuthService.unlock(USER_ID, oldPassphrase))
                .isInstanceOf(IllegalArgumentException.class);

        // 새 passphrase 로 unlock 성공
        userAuthService.unlock(USER_ID, newPassphrase);
        assertThat(vault.isLocked()).isFalse();

        byte[] dekAfter = vault.copyDek();

        // DEK 자체는 동일
        assertThat(dekAfter).isEqualTo(dekBefore);

        java.util.Arrays.fill(dekBefore, (byte) 0);
        java.util.Arrays.fill(dekAfter, (byte) 0);
    }
}
