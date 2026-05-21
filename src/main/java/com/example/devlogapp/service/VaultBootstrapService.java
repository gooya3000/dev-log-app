package com.example.devlogapp.service;

import com.example.devlogapp.config.AdminProperties;
import com.example.devlogapp.config.StorageProperties;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.example.devlogapp.vault.VaultMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * 첫 부팅 시 .vault-meta.json 없으면 adminSalt 초기화 + data/logs/ 루트 생성.
 * 새 모델: bootstrap 에서 DEK 만들지 않음 — DEK 는 사용자 가입 시 생성.
 * admin passphrase 누락/default 이면 fail-fast.
 * PLAN.md §4.5.6 첫 부팅 자동 bootstrap 참조.
 */
@Service
public class VaultBootstrapService implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(VaultBootstrapService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminProperties adminProperties;
    private final VaultMetaRepository vaultMetaRepository;
    private final Path logsRoot;

    @Autowired
    public VaultBootstrapService(AdminProperties adminProperties,
                                  VaultMetaRepository vaultMetaRepository,
                                  StorageProperties storageProperties) {
        this.adminProperties = adminProperties;
        this.vaultMetaRepository = vaultMetaRepository;
        this.logsRoot = Path.of(storageProperties.getRoot());
    }

    /** 테스트용 — logsRoot 직접 주입. */
    public VaultBootstrapService(AdminProperties adminProperties,
                                  VaultMetaRepository vaultMetaRepository,
                                  Path logsRoot) {
        this.adminProperties = adminProperties;
        this.vaultMetaRepository = vaultMetaRepository;
        this.logsRoot = logsRoot;
    }

    /** 테스트 호환용 — logsRoot 없이 (기존 테스트 시그니처 유지). metaRoot 기반으로 logsRoot 추정. */
    public VaultBootstrapService(AdminProperties adminProperties,
                                  VaultMetaRepository vaultMetaRepository) {
        this.adminProperties = adminProperties;
        this.vaultMetaRepository = vaultMetaRepository;
        this.logsRoot = vaultMetaRepository.getMetaRoot().resolve("logs");
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        bootstrap();
    }

    /**
     * bootstrap 로직 (테스트에서 직접 호출 가능).
     * @throws IllegalStateException admin passphrase 미설정 또는 default 값
     */
    public void bootstrap() {
        validateAdminPassphrase();

        if (vaultMetaRepository.load().isPresent()) {
            log.info("Vault already initialized. Skipping bootstrap.");
            ensureLogsRoot();
            return;
        }

        log.info("Vault not found. Initializing...");

        byte[] adminSalt = new byte[16];
        RANDOM.nextBytes(adminSalt);

        VaultMeta meta = new VaultMeta(
                1,
                "PBKDF2-HMAC-SHA256",
                600_000,
                Base64.getEncoder().encodeToString(adminSalt),
                List.of());

        vaultMetaRepository.save(meta);
        ensureLogsRoot();
        log.info("Vault bootstrap complete. adminSalt generated, users: [].");
    }

    private void ensureLogsRoot() {
        try {
            Files.createDirectories(logsRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create logs root directory: " + logsRoot, e);
        }
    }

    private void validateAdminPassphrase() {
        String passphrase = adminProperties.getPassphrase();
        if (passphrase == null || passphrase.isBlank()) {
            throw new IllegalStateException(
                    "devlog.admin.passphrase is not set. " +
                    "Create application-local.properties from the example file and set a strong passphrase.");
        }
        if (passphrase.startsWith("CHANGE-ME-")) {
            throw new IllegalStateException(
                    "devlog.admin.passphrase is still the default placeholder. " +
                    "Please set a strong passphrase in application-local.properties.");
        }
    }
}
