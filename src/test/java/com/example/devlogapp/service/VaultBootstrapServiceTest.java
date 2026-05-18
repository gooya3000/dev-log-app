package com.example.devlogapp.service;

import com.example.devlogapp.config.AdminProperties;
import com.example.devlogapp.config.JacksonConfig;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.example.devlogapp.vault.VaultMeta;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ① VaultBootstrapService — .vault-meta.json 없는 임시 디렉토리에서 부팅 시 adminWrappedDek 생성·저장
 * ⑧ VaultBootstrapService — admin passphrase 비었거나 CHANGE-ME-로 시작하면 명확한 예외/fail-fast
 */
class VaultBootstrapServiceTest {

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();

    private VaultBootstrapService service(String passphrase, Path metaRoot) {
        AdminProperties props = new AdminProperties();
        props.setPassphrase(passphrase);
        VaultMetaRepository repo = new VaultMetaRepository(objectMapper, metaRoot);
        return new VaultBootstrapService(props, repo);
    }

    @Test
    void bootstrap_createsAdminWrappedDek(@TempDir Path tempDir) {
        // ① .vault-meta.json 없는 상태에서 bootstrap → adminWrappedDek 생성·저장
        VaultBootstrapService svc = service("strong-admin-passphrase-test", tempDir);
        svc.bootstrap();

        VaultMetaRepository repo = new VaultMetaRepository(objectMapper, tempDir);
        VaultMeta meta = repo.load().orElseThrow();

        assertThat(meta.getV()).isEqualTo(1);
        assertThat(meta.getAdminWrappedDek()).isNotNull();
        assertThat(meta.getAdminWrappedDek().getSalt()).isNotBlank();
        assertThat(meta.getAdminWrappedDek().getNonce()).isNotBlank();
        assertThat(meta.getAdminWrappedDek().getCt()).isNotBlank();
        assertThat(meta.getUsers()).isEmpty();
    }

    @Test
    void bootstrap_alreadyInitialized_skips(@TempDir Path tempDir) {
        // 두 번 호출해도 두 번째는 skip (덮어쓰기 안 함)
        VaultBootstrapService svc = service("strong-admin-passphrase-test", tempDir);
        svc.bootstrap();

        VaultMetaRepository repo = new VaultMetaRepository(objectMapper, tempDir);
        String firstCt = repo.load().orElseThrow().getAdminWrappedDek().getCt();

        svc.bootstrap();
        String secondCt = repo.load().orElseThrow().getAdminWrappedDek().getCt();

        assertThat(firstCt).isEqualTo(secondCt);
    }

    @Test
    void bootstrap_emptyPassphrase_failFast(@TempDir Path tempDir) {
        // ⑧ admin passphrase 비어 있으면 예외
        VaultBootstrapService svc = service("", tempDir);

        assertThatThrownBy(svc::bootstrap)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("devlog.admin.passphrase");
    }

    @Test
    void bootstrap_defaultPassphrase_failFast(@TempDir Path tempDir) {
        // ⑧ CHANGE-ME-... 로 시작하면 예외
        VaultBootstrapService svc = service("CHANGE-ME-TO-A-STRONG-ADMIN-PASSPHRASE", tempDir);

        assertThatThrownBy(svc::bootstrap)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    void bootstrap_changeMePrefix_failFast(@TempDir Path tempDir) {
        // CHANGE-ME- 접두어만 확인
        VaultBootstrapService svc = service("CHANGE-ME-anything", tempDir);

        assertThatThrownBy(svc::bootstrap)
                .isInstanceOf(IllegalStateException.class);
    }
}
