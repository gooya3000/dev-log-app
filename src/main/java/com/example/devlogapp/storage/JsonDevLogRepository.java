package com.example.devlogapp.storage;

import com.example.devlogapp.config.StorageProperties;
import com.example.devlogapp.domain.DevLog;
import com.example.devlogapp.domain.DevLogId;
import com.example.devlogapp.vault.EnvelopeV1;
import com.example.devlogapp.vault.Vault;
import com.example.devlogapp.vault.VaultCipher;
import com.example.devlogapp.vault.VaultLockedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * JSON 파일 기반 회고 저장소 — 사용자별 서브디렉토리.
 * 파일 경로: data/logs/{userId}/{date}_{id}.json
 * 파일 내용은 항상 VaultCipher 통과 → ciphertext(EnvelopeV1)만 디스크에 기록.
 * Vault 에서 현재 userId 를 읽어 해당 사용자 디렉토리만 스캔.
 * PLAN.md §4.1, §4.2, §4.3, §4.5 참조.
 */
@Repository
public class JsonDevLogRepository implements DevLogRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonDevLogRepository.class);

    private final ObjectMapper objectMapper;
    private final Vault vault;
    private final Path logsRoot;  // data/logs/ (사용자 서브디렉토리의 부모)

    @Autowired
    public JsonDevLogRepository(ObjectMapper objectMapper,
                                 Vault vault,
                                 StorageProperties storageProperties) {
        this.objectMapper = objectMapper;
        this.vault = vault;
        this.logsRoot = Path.of(storageProperties.getRoot());
        try {
            Files.createDirectories(logsRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create logs root directory: " + logsRoot, e);
        }
    }

    /** 테스트용 — logsRoot 직접 주입. */
    public JsonDevLogRepository(ObjectMapper objectMapper, Vault vault, Path logsRoot) {
        this.objectMapper = objectMapper;
        this.vault = vault;
        this.logsRoot = logsRoot;
        try {
            Files.createDirectories(logsRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create logs root directory: " + logsRoot, e);
        }
    }

    @Override
    public void save(DevLog devLog) {
        byte[] dek = vault.copyDek();
        try {
            String plainJson = objectMapper.writeValueAsString(devLog);
            EnvelopeV1 envelope = VaultCipher.encrypt(plainJson, dek);

            Path userDir = userDir();
            Files.createDirectories(userDir);

            Path target = userDir.resolve(filename(devLog));
            Path tmp = userDir.resolve(filename(devLog) + ".tmp");
            objectMapper.writeValue(tmp.toFile(), envelope);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save DevLog: " + devLog.getId(), e);
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    @Override
    public Optional<DevLog> findById(DevLogId id) {
        String suffix = "_" + id.getValue() + ".json";
        try (Stream<Path> stream = Files.list(userDir())) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(suffix))
                    .findFirst()
                    .map(this::decryptFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list user logs directory", e);
        }
    }

    @Override
    public List<DevLog> findAll() {
        try (Stream<Path> stream = Files.list(userDir())) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().endsWith(".tmp"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString(),
                            Comparator.reverseOrder()))
                    .map(this::decryptFile)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list user logs directory", e);
        }
    }

    @Override
    public void deleteById(DevLogId id) {
        String suffix = "_" + id.getValue() + ".json";
        try (Stream<Path> stream = Files.list(userDir())) {
            stream
                    .filter(p -> p.getFileName().toString().endsWith(suffix))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            log.warn("Failed to delete file: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list user logs directory for delete", e);
        }
    }

    private DevLog decryptFile(Path path) {
        byte[] dek = vault.copyDek();
        try {
            EnvelopeV1 envelope = objectMapper.readValue(path.toFile(), EnvelopeV1.class);
            String plainJson = VaultCipher.decrypt(envelope, dek);
            return objectMapper.readValue(plainJson, DevLog.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decrypt/parse DevLog file: " + path, e);
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    /** 현재 unlock 사용자의 로그 디렉토리. Vault 가 locked 이면 VaultLockedException. */
    private Path userDir() {
        String userId = vault.getUserId();
        if (userId == null) {
            throw new VaultLockedException("Vault is locked. Cannot determine user directory.");
        }
        return logsRoot.resolve(userId);
    }

    /** 파일명: {date}_{id}.json */
    private static String filename(DevLog devLog) {
        return devLog.getDate() + "_" + devLog.getId() + ".json";
    }
}
