package com.example.devlogapp.vault;

import com.example.devlogapp.domain.User;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * .vault-meta.json 도메인 모델 (새 모델).
 * adminSalt top-level + users[] (각 엔트리에 userWrappedDek + adminWrappedDek).
 * 공유 DEK 제거 — DEK 는 사용자 가입 시 사용자별로 발급.
 * PLAN.md §4.5.3 참조.
 */
public final class VaultMeta {

    private final int v;
    private final String kdf;
    private final int kdfIter;
    private final String adminSalt;  // base64 16바이트 — K_admin 도출용
    private final List<User> users;

    @JsonCreator
    public VaultMeta(
            @JsonProperty("v") int v,
            @JsonProperty("kdf") String kdf,
            @JsonProperty("kdfIter") int kdfIter,
            @JsonProperty("adminSalt") String adminSalt,
            @JsonProperty("users") List<User> users) {
        this.v = v;
        this.kdf = kdf;
        this.kdfIter = kdfIter;
        this.adminSalt = adminSalt;
        this.users = users == null ? List.of() : List.copyOf(users);
    }

    public int getV() { return v; }
    public String getKdf() { return kdf; }
    public int getKdfIter() { return kdfIter; }
    public String getAdminSalt() { return adminSalt; }
    public List<User> getUsers() { return users; }

    /** users 목록만 교체한 새 VaultMeta 반환. */
    public VaultMeta withUsers(List<User> newUsers) {
        return new VaultMeta(v, kdf, kdfIter, adminSalt, newUsers);
    }
}
