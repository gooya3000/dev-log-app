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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void save_utf8_koreanAndEmoji_roundtrip() {
        // UTF-8 본문(한글 + 이모지)이 ciphertext 라운드트립 후에도 정확히 복원되는지
        String body = "오늘은 🚀 새 기능을 배포했다. 이슈는 없었음 😅";
        OffsetDateTime now = OffsetDateTime.now();
        DevLog log = DevLog.builder()
                .id("utf8-id")
                .date("2026-05-18")
                .title("UTF-8 라운드트립 🇰🇷")
                .tags(List.of("한글-태그", "emoji-🔥"))
                .whatIDid(body)
                .whatILearned("UTF-8 직렬화는 Jackson 이 알아서 처리")
                .problems("")
                .tomorrow("내일도 화이팅 💪")
                .mood(Mood.GOOD)
                .createdAt(now)
                .updatedAt(now)
                .schemaVersion(1)
                .build();

        repository.save(log);
        DevLog loaded = repository.findById(new DevLogId("utf8-id")).orElseThrow();

        assertThat(loaded.getTitle()).isEqualTo("UTF-8 라운드트립 🇰🇷");
        assertThat(loaded.getWhatIDid()).isEqualTo(body);
        assertThat(loaded.getTomorrow()).isEqualTo("내일도 화이팅 💪");
        assertThat(loaded.getTags()).containsExactly("한글-태그", "emoji-🔥");
    }

    @Test
    void findAll_sameDate_multipleEntriesCoexist() {
        // {date}_{id}.json 규칙 덕분에 같은 날짜에 N건 저장해도 충돌 없음
        repository.save(sampleLog("dup-1", "2026-05-18", "오전 회고"));
        repository.save(sampleLog("dup-2", "2026-05-18", "오후 회고"));
        repository.save(sampleLog("dup-3", "2026-05-18", "저녁 회고"));

        List<DevLog> all = repository.findAll();
        assertThat(all).hasSize(3);
        assertThat(all).extracting(DevLog::getId)
                .containsExactlyInAnyOrder("dup-1", "dup-2", "dup-3");

        assertThat(repository.findById(new DevLogId("dup-2")).orElseThrow().getTitle())
                .isEqualTo("오후 회고");
    }

    @Test
    void findAll_ignoresLeftoverTmpFiles() throws IOException {
        // 크래시로 남은 .tmp 파일이 디렉토리에 있어도 findAll 결과에 안 섞임
        repository.save(sampleLog("real-id", "2026-05-18", "정상 회고"));
        Files.writeString(logsDir.resolve("2026-05-18_orphan.json.tmp"), "garbage-not-an-envelope");

        List<DevLog> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getId()).isEqualTo("real-id");
    }

    @Test
    void findById_corruptedEnvelope_throws() throws IOException {
        // 손상된 envelope 파일을 읽으면 IllegalStateException — 조용한 데이터 손실 차단
        Path corrupted = logsDir.resolve("2026-05-18_corrupt-id.json");
        Files.writeString(corrupted, "{\"v\":1,\"alg\":\"AES-256-GCM\",\"nonce\":\"AAAA\",\"ct\":\"AAAA\"}",
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> repository.findById(new DevLogId("corrupt-id")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void save_preservesSchemaVersionAndTimestamps() {
        // schemaVersion + ISO-8601 offset timestamp 가 라운드트립 후에도 보존
        OffsetDateTime created = OffsetDateTime.of(2026, 5, 18, 10, 0, 0, 0, ZoneOffset.ofHours(9));
        OffsetDateTime updated = created.plusHours(2);
        DevLog log = DevLog.builder()
                .id("meta-id")
                .date("2026-05-18")
                .title("메타데이터 보존")
                .tags(List.of())
                .whatIDid("x").whatILearned("x").problems("x").tomorrow("x")
                .mood(Mood.SOSO)
                .createdAt(created)
                .updatedAt(updated)
                .schemaVersion(1)
                .build();

        repository.save(log);
        DevLog loaded = repository.findById(new DevLogId("meta-id")).orElseThrow();

        assertThat(loaded.getSchemaVersion()).isEqualTo(1);
        assertThat(loaded.getCreatedAt().toInstant()).isEqualTo(created.toInstant());
        assertThat(loaded.getUpdatedAt().toInstant()).isEqualTo(updated.toInstant());
    }
}
