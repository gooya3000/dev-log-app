package com.example.devlogapp.service;

import com.example.devlogapp.config.AdminProperties;
import com.example.devlogapp.config.JacksonConfig;
import com.example.devlogapp.domain.DevLog;
import com.example.devlogapp.domain.Mood;
import com.example.devlogapp.storage.JsonDevLogRepository;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.example.devlogapp.vault.Vault;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ⑥ 두 사용자 격리: alice 와 bob 가입, 각자 회고 1건.
 *    alice 의 DEK 로 bob 의 envelope 복호화 시도 → GCM 실패(AEADBadTagException).
 */
class UserIsolationTest {

    private static final String ADMIN_PASSPHRASE = "test-admin-isolation";

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();
    private VaultMetaRepository vaultMetaRepository;
    private AdminProperties adminProperties;
    private Path logsRoot;

    @BeforeEach
    void setup(@TempDir Path tempDir) throws IOException {
        adminProperties = new AdminProperties();
        adminProperties.setPassphrase(ADMIN_PASSPHRASE);

        logsRoot = tempDir.resolve("logs");
        Files.createDirectories(logsRoot);

        vaultMetaRepository = new VaultMetaRepository(objectMapper, tempDir);

        VaultBootstrapService bootstrap = new VaultBootstrapService(adminProperties, vaultMetaRepository, logsRoot);
        bootstrap.bootstrap();
    }

    @Test
    void twoUsers_cannotDecryptEachOthersLogs() throws IOException {
        UserRegistrationService registration = new UserRegistrationService(adminProperties, vaultMetaRepository, logsRoot);
        registration.register("alice", "alice-pass");
        registration.register("bob", "bob-pass");

        // alice 회고 저장
        Vault aliceVault = new Vault();
        UserAuthService aliceAuth = new UserAuthService(vaultMetaRepository, aliceVault);
        aliceAuth.unlock("alice", "alice-pass");

        JsonDevLogRepository aliceRepo = new JsonDevLogRepository(objectMapper, aliceVault, logsRoot);
        DevLogService aliceService = new DevLogService(aliceRepo);
        aliceService.create("2026-05-21", "앨리스 회고", List.of(), "했음", "배움", "막힘", "내일", Mood.GOOD);
        aliceVault.lock();

        // bob 회고 저장
        Vault bobVault = new Vault();
        UserAuthService bobAuth = new UserAuthService(vaultMetaRepository, bobVault);
        bobAuth.unlock("bob", "bob-pass");

        JsonDevLogRepository bobRepo = new JsonDevLogRepository(objectMapper, bobVault, logsRoot);
        DevLogService bobService = new DevLogService(bobRepo);
        bobService.create("2026-05-21", "밥 회고", List.of(), "했음", "배움", "막힘", "내일", Mood.SOSO);
        bobVault.lock();

        // alice 의 DEK 로 bob 의 파일을 복호화 시도 → 실패
        aliceAuth.unlock("alice", "alice-pass");
        Path bobDir = logsRoot.resolve("bob");

        // alice Vault 로 bob 파일 직접 복호화 시도
        try (Stream<Path> stream = Files.list(bobDir)) {
            Path bobFile = stream.filter(p -> p.getFileName().toString().endsWith(".json")).findFirst().orElseThrow();

            // alice 의 Vault(DEK) 로 bob 파일 복호화 시도
            com.example.devlogapp.vault.EnvelopeV1 envelope =
                    objectMapper.readValue(bobFile.toFile(), com.example.devlogapp.vault.EnvelopeV1.class);

            byte[] aliceDek = aliceVault.copyDek();
            try {
                assertThatThrownBy(() ->
                    com.example.devlogapp.vault.VaultCipher.decrypt(envelope, aliceDek)
                ).isInstanceOf(IllegalStateException.class);  // GCM AEADBadTagException wrapping
            } finally {
                java.util.Arrays.fill(aliceDek, (byte) 0);
            }
        }
    }

    @Test
    void twoUsers_eachSeesOnlyOwnLogs() throws IOException {
        // 각자 자기 디렉토리만 스캔
        UserRegistrationService registration = new UserRegistrationService(adminProperties, vaultMetaRepository, logsRoot);
        registration.register("alice", "alice-pass");
        registration.register("bob", "bob-pass");

        Vault aliceVault = new Vault();
        UserAuthService aliceAuth = new UserAuthService(vaultMetaRepository, aliceVault);
        aliceAuth.unlock("alice", "alice-pass");

        JsonDevLogRepository aliceRepo = new JsonDevLogRepository(objectMapper, aliceVault, logsRoot);
        DevLogService aliceService = new DevLogService(aliceRepo);
        aliceService.create("2026-05-21", "앨리스 회고", List.of(), "했음", "배움", "막힘", "내일", Mood.GOOD);

        List<DevLog> aliceLogs = aliceService.findAll();
        assertThat(aliceLogs).hasSize(1);
        assertThat(aliceLogs.get(0).getTitle()).isEqualTo("앨리스 회고");
        aliceVault.lock();

        Vault bobVault = new Vault();
        UserAuthService bobAuth = new UserAuthService(vaultMetaRepository, bobVault);
        bobAuth.unlock("bob", "bob-pass");

        JsonDevLogRepository bobRepo = new JsonDevLogRepository(objectMapper, bobVault, logsRoot);
        DevLogService bobService = new DevLogService(bobRepo);
        // bob 은 아무 회고도 없음
        assertThat(bobService.findAll()).isEmpty();
        bobVault.lock();
    }
}
