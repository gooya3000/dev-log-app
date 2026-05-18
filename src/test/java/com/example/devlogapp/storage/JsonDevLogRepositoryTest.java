package com.example.devlogapp.storage;

import com.example.devlogapp.config.AdminProperties;
import com.example.devlogapp.config.JacksonConfig;
import com.example.devlogapp.domain.DevLog;
import com.example.devlogapp.domain.DevLogId;
import com.example.devlogapp.domain.Mood;
import com.example.devlogapp.service.UserAdminService;
import com.example.devlogapp.service.UserAuthService;
import com.example.devlogapp.service.VaultBootstrapService;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ⑩ JsonDevLogRepository 표준 CRUD 라운드트립
 * save → load by id → list (날짜 내림차순) → update → delete
 * Vault unlock 상태에서 평문 DevLog ↔ ciphertext 파일 라운드트립
 */
class JsonDevLogRepositoryTest {

    private static final String ADMIN_PASSPHRASE = "test-admin-pass-repo";
    private static final String USER_ID = "self";
    private static final String USER_PASSPHRASE = "user-pass-repo";

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();
    private JsonDevLogRepository repository;
    private Vault vault;
    private Path logsDir;

    @BeforeEach
    void setup(@TempDir Path tempDir) throws IOException {
        AdminProperties adminProperties = new AdminProperties();
        adminProperties.setPassphrase(ADMIN_PASSPHRASE);

        logsDir = tempDir.resolve("logs");
        Files.createDirectories(logsDir);

        VaultMetaRepository vaultMetaRepository = new VaultMetaRepository(objectMapper, tempDir);
        vault = new Vault();

        // bootstrap + user 생성 + unlock
        VaultBootstrapService bootstrap = new VaultBootstrapService(adminProperties, vaultMetaRepository);
        bootstrap.bootstrap();

        UserAdminService userAdminService = new UserAdminService(adminProperties, vaultMetaRepository);
        userAdminService.createUser(USER_ID, USER_PASSPHRASE);

        UserAuthService userAuthService = new UserAuthService(vaultMetaRepository, vault);
        userAuthService.unlock(USER_ID, USER_PASSPHRASE);

        repository = new JsonDevLogRepository(objectMapper, vault, logsDir);
    }

    private DevLog sampleLog(String id, String date, String title) {
        OffsetDateTime now = OffsetDateTime.now();
        return DevLog.builder()
                .id(id)
                .date(date)
                .title(title)
                .tags(List.of("spring", "test"))
                .whatIDid("테스트했습니다")
                .whatILearned("JUnit5 배웠습니다")
                .problems("없었습니다")
                .tomorrow("더 테스트")
                .mood(Mood.GOOD)
                .createdAt(now)
                .updatedAt(now)
                .schemaVersion(1)
                .build();
    }

    @Test
    void save_findById_roundtrip() {
        // ⑩ save → load by id 라운드트립
        DevLog log = sampleLog("test-id-001", "2026-05-18", "첫 번째 회고");
        repository.save(log);

        DevLog loaded = repository.findById(new DevLogId("test-id-001")).orElseThrow();
        assertThat(loaded.getId()).isEqualTo("test-id-001");
        assertThat(loaded.getTitle()).isEqualTo("첫 번째 회고");
        assertThat(loaded.getTags()).containsExactly("spring", "test");
        assertThat(loaded.getMood()).isEqualTo(Mood.GOOD);
        assertThat(loaded.getSchemaVersion()).isEqualTo(1);
    }

    @Test
    void save_encryptedOnDisk() throws IOException {
        // ⑩ 파일이 ciphertext (평문 JSON 아님) 로 저장됨
        DevLog log = sampleLog("enc-id-001", "2026-05-18", "암호화 테스트");
        repository.save(log);

        // 파일 내용이 envelope JSON (v/alg/nonce/ct) 형태인지 확인
        Path file = Files.list(logsDir)
                .filter(p -> p.getFileName().toString().contains("enc-id-001"))
                .findFirst()
                .orElseThrow();

        String raw = Files.readString(file);
        // 평문 회고 내용이 파일에 없어야 함
        assertThat(raw).doesNotContain("암호화 테스트");
        // envelope 구조 확인
        assertThat(raw).contains("\"alg\"");
        assertThat(raw).contains("\"nonce\"");
        assertThat(raw).contains("\"ct\"");
    }

    @Test
    void findAll_dateDescOrder() {
        // ⑩ list 날짜 내림차순
        repository.save(sampleLog("id-a", "2026-05-16", "이전 회고"));
        repository.save(sampleLog("id-b", "2026-05-18", "최신 회고"));
        repository.save(sampleLog("id-c", "2026-05-17", "중간 회고"));

        List<DevLog> all = repository.findAll();
        assertThat(all).hasSize(3);
        assertThat(all.get(0).getDate()).isEqualTo("2026-05-18");
        assertThat(all.get(1).getDate()).isEqualTo("2026-05-17");
        assertThat(all.get(2).getDate()).isEqualTo("2026-05-16");
    }

    @Test
    void update_overwrites() {
        // ⑩ update
        OffsetDateTime now = OffsetDateTime.now();
        DevLog original = sampleLog("update-id", "2026-05-18", "원래 제목");
        repository.save(original);

        DevLog updated = original.withUpdated(
                "수정된 제목", List.of("updated"), "수정됨", "수정2", "수정3", "수정4",
                Mood.SOSO, now.plusMinutes(5));
        repository.save(updated);

        DevLog loaded = repository.findById(new DevLogId("update-id")).orElseThrow();
        assertThat(loaded.getTitle()).isEqualTo("수정된 제목");
        assertThat(loaded.getMood()).isEqualTo(Mood.SOSO);
    }

    @Test
    void delete_removesFile() {
        DevLog log = sampleLog("del-id", "2026-05-18", "삭제할 회고");
        repository.save(log);
        assertThat(repository.findById(new DevLogId("del-id"))).isPresent();

        repository.deleteById(new DevLogId("del-id"));
        assertThat(repository.findById(new DevLogId("del-id"))).isEmpty();
    }

    @Test
    void findById_notFound_returnsEmpty() {
        assertThat(repository.findById(new DevLogId("nonexistent-id"))).isEmpty();
    }
}
