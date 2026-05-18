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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ⑤ passphrase reset 전후 DEK 동일 검증
 * ⑥ user passphrase 회전 시 회고 파일 재암호화 없음 — ciphertext 비트 단위로 동일
 */
class PassphraseResetNoCiphertextChangeTest {

    private static final String ADMIN_PASSPHRASE = "test-admin-pass-reset";
    private static final String USER_ID = "self";
    private static final String OLD_PASSPHRASE = "old-user-pass";
    private static final String NEW_PASSPHRASE = "new-user-pass-2024";

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();
    private VaultMetaRepository vaultMetaRepository;
    private UserAdminService userAdminService;
    private UserAuthService userAuthService;
    private Vault vault;
    private Path logsDir;

    @BeforeEach
    void setup(@TempDir Path tempDir) throws IOException {
        AdminProperties adminProperties = new AdminProperties();
        adminProperties.setPassphrase(ADMIN_PASSPHRASE);

        logsDir = tempDir.resolve("logs");
        Files.createDirectories(logsDir);

        vaultMetaRepository = new VaultMetaRepository(objectMapper, tempDir);
        vault = new Vault();

        VaultBootstrapService bootstrap = new VaultBootstrapService(adminProperties, vaultMetaRepository);
        bootstrap.bootstrap();

        userAdminService = new UserAdminService(adminProperties, vaultMetaRepository);
        userAdminService.createUser(USER_ID, OLD_PASSPHRASE);
        userAuthService = new UserAuthService(vaultMetaRepository, vault);
    }

    @Test
    void resetPassphrase_logFileCiphertextUnchanged() throws IOException {
        // ⑥ passphrase 회전 전 회고 파일 저장
        userAuthService.unlock(USER_ID, OLD_PASSPHRASE);

        JsonDevLogRepository repo = new JsonDevLogRepository(objectMapper, vault, logsDir);
        DevLogService logService = new DevLogService(repo);

        logService.create("2026-05-18", "테스트 회고", List.of("test"),
                "했습니다", "배웠습니다", "막혔습니다", "내일할일", Mood.GOOD);

        // 파일 내용 캡처 (before)
        Map<String, byte[]> before = captureFiles(logsDir);
        vault.lock();

        // passphrase 재설정
        userAdminService.resetPassphrase(USER_ID, NEW_PASSPHRASE);

        // 파일 내용 캡처 (after)
        Map<String, byte[]> after = captureFiles(logsDir);

        // ⑥ 파일 이름/내용 비트 단위로 동일
        assertThat(after.keySet()).isEqualTo(before.keySet());
        for (String filename : before.keySet()) {
            assertThat(after.get(filename))
                    .as("ciphertext of %s should be unchanged after passphrase reset", filename)
                    .isEqualTo(before.get(filename));
        }

        // 새 passphrase 로 복호화 가능
        userAuthService.unlock(USER_ID, NEW_PASSPHRASE);
        List<DevLog> logs = logService.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getTitle()).isEqualTo("테스트 회고");
    }

    private Map<String, byte[]> captureFiles(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .collect(Collectors.toMap(
                            p -> p.getFileName().toString(),
                            p -> {
                                try {
                                    return Files.readAllBytes(p);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }));
        }
    }
}
