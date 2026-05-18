package com.example.devlogapp.storage;

import com.example.devlogapp.vault.VaultMeta;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * .vault-meta.json 읽기/쓰기.
 * atomic write (tmp → move).
 * PLAN.md §4.5.3 참조.
 */
@Repository
public class VaultMetaRepository {

    private static final String META_FILENAME = ".vault-meta.json";

    private final ObjectMapper objectMapper;
    private final Path metaRoot;  // data/ 디렉토리 (logs 의 부모)

    @Autowired
    public VaultMetaRepository(ObjectMapper objectMapper,
                                com.example.devlogapp.config.StorageProperties storageProperties) {
        this.objectMapper = objectMapper;
        // data/logs → data/ 로 올라감
        Path logsPath = Path.of(storageProperties.getRoot());
        this.metaRoot = logsPath.getParent() != null ? logsPath.getParent() : logsPath;
    }

    /** .vault-meta.json 경로를 외부에서 주입하는 생성자 (테스트용). */
    public VaultMetaRepository(ObjectMapper objectMapper, Path metaRoot) {
        this.objectMapper = objectMapper;
        this.metaRoot = metaRoot;
    }

    public Optional<VaultMeta> load() {
        Path target = metaRoot.resolve(META_FILENAME);
        if (!Files.exists(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(target.toFile(), VaultMeta.class));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read .vault-meta.json", e);
        }
    }

    public void save(VaultMeta meta) {
        try {
            Files.createDirectories(metaRoot);
            Path target = metaRoot.resolve(META_FILENAME);
            Path tmp = metaRoot.resolve(META_FILENAME + ".tmp");
            objectMapper.writeValue(tmp.toFile(), meta);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write .vault-meta.json", e);
        }
    }

    public Path getMetaRoot() {
        return metaRoot;
    }
}
