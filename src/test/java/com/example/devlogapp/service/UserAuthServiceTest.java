package com.example.devlogapp.service;

import com.example.devlogapp.config.AdminProperties;
import com.example.devlogapp.config.JacksonConfig;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.example.devlogapp.vault.Vault;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ③ UserAuthService.unlock 라운드트립 — H_user 검증 통과 → DEK unwrap → Vault 에 DEK 적재
 */
class UserAuthServiceTest {

    private static final String ADMIN_PASSPHRASE = "test-admin-pass-auth";
    private static final String USER_ID = "alice";
    private static final String USER_PASSPHRASE = "user-secret-passphrase";

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();
    private VaultMetaRepository vaultMetaRepository;
    private UserRegistrationService userRegistrationService;
    private UserAuthService userAuthService;
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
        userRegistrationService.register(USER_ID, USER_PASSPHRASE);

        userAuthService = new UserAuthService(vaultMetaRepository, vault);
    }

    @Test
    void unlock_correctPassphrase_vaultLoaded() {
        // ③ 올바른 passphrase → DEK unwrap → Vault 에 DEK 적재
        assertThat(vault.isLocked()).isTrue();

        userAuthService.unlock(USER_ID, USER_PASSPHRASE);

        assertThat(vault.isLocked()).isFalse();
        assertThat(vault.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void unlock_wrongPassphrase_fails() {
        assertThatThrownBy(() -> userAuthService.unlock(USER_ID, "wrong-passphrase"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Authentication failed");
    }

    @Test
    void unlock_unknownUser_fails() {
        assertThatThrownBy(() -> userAuthService.unlock("nonexistent", USER_PASSPHRASE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lock_wipesVault() {
        userAuthService.unlock(USER_ID, USER_PASSPHRASE);
        assertThat(vault.isLocked()).isFalse();

        vault.lock();
        assertThat(vault.isLocked()).isTrue();
        assertThat(vault.getUserId()).isNull();
    }

    @Test
    void unlock_dekIsConsistent() {
        // 두 번 unlock 해도 같은 DEK 반환
        userAuthService.unlock(USER_ID, USER_PASSPHRASE);
        byte[] dek1 = vault.copyDek();
        vault.lock();

        userAuthService.unlock(USER_ID, USER_PASSPHRASE);
        byte[] dek2 = vault.copyDek();
        vault.lock();

        assertThat(dek1).isEqualTo(dek2);

        java.util.Arrays.fill(dek1, (byte) 0);
        java.util.Arrays.fill(dek2, (byte) 0);
    }
}
