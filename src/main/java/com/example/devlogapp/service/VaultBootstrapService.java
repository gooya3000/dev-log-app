package com.example.devlogapp.service;

import com.example.devlogapp.config.AdminProperties;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.example.devlogapp.vault.KeyWrapper;
import com.example.devlogapp.vault.PassphraseKdf;
import com.example.devlogapp.vault.VaultMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 첫 부팅 시 .vault-meta.json 없으면 adminWrappedDek 초기화.
 * admin passphrase 누락/default 이면 fail-fast (예외 발생).
 * PLAN.md §4.5.6 첫 부팅 자동 bootstrap 참조.
 */
@Service
public class VaultBootstrapService implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(VaultBootstrapService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminProperties adminProperties;
    private final VaultMetaRepository vaultMetaRepository;

    public VaultBootstrapService(AdminProperties adminProperties,
                                  VaultMetaRepository vaultMetaRepository) {
        this.adminProperties = adminProperties;
        this.vaultMetaRepository = vaultMetaRepository;
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
            return;
        }

        log.info("Vault not found. Initializing...");

        byte[] dek = new byte[32];
        byte[] adminSalt = new byte[16];
        byte[] kAdmin = null;

        try {
            RANDOM.nextBytes(dek);
            RANDOM.nextBytes(adminSalt);

            kAdmin = PassphraseKdf.deriveAdmin(
                    adminProperties.getPassphrase().toCharArray(), adminSalt);

            KeyWrapper.WrapResult wrapped = KeyWrapper.wrap(kAdmin, dek);

            VaultMeta.AdminWrappedDek adminWrappedDek = new VaultMeta.AdminWrappedDek(
                    Base64.getEncoder().encodeToString(adminSalt),
                    "AES-256-GCM",
                    wrapped.nonceB64(),
                    wrapped.ctB64());

            VaultMeta meta = new VaultMeta(
                    1,
                    "PBKDF2-HMAC-SHA256",
                    PassphraseKdf.KDF_ITERATIONS,
                    adminWrappedDek,
                    List.of());

            vaultMetaRepository.save(meta);
            log.info("Vault bootstrap complete.");
        } finally {
            Arrays.fill(dek, (byte) 0);
            if (kAdmin != null) Arrays.fill(kAdmin, (byte) 0);
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
