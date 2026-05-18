package com.example.devlogapp.vault;

import com.example.devlogapp.domain.User;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * .vault-meta.json 도메인 모델.
 * PLAN.md §4.5.3 참조.
 */
public final class VaultMeta {

    private final int v;
    private final String kdf;
    private final int kdfIter;
    private final AdminWrappedDek adminWrappedDek;
    private final List<User> users;

    @JsonCreator
    public VaultMeta(
            @JsonProperty("v") int v,
            @JsonProperty("kdf") String kdf,
            @JsonProperty("kdfIter") int kdfIter,
            @JsonProperty("adminWrappedDek") AdminWrappedDek adminWrappedDek,
            @JsonProperty("users") List<User> users) {
        this.v = v;
        this.kdf = kdf;
        this.kdfIter = kdfIter;
        this.adminWrappedDek = adminWrappedDek;
        this.users = users == null ? List.of() : List.copyOf(users);
    }

    public int getV() { return v; }
    public String getKdf() { return kdf; }
    public int getKdfIter() { return kdfIter; }
    public AdminWrappedDek getAdminWrappedDek() { return adminWrappedDek; }
    public List<User> getUsers() { return users; }

    /** users 목록만 교체한 새 VaultMeta 반환. */
    public VaultMeta withUsers(List<User> newUsers) {
        return new VaultMeta(v, kdf, kdfIter, adminWrappedDek, newUsers);
    }

    /**
     * adminWrappedDek 포맷 (salt/alg/nonce/ct).
     * PLAN.md §4.5.3 참조.
     */
    public static final class AdminWrappedDek {
        private final String salt;
        private final String alg;
        private final String nonce;
        private final String ct;

        @JsonCreator
        public AdminWrappedDek(
                @JsonProperty("salt") String salt,
                @JsonProperty("alg") String alg,
                @JsonProperty("nonce") String nonce,
                @JsonProperty("ct") String ct) {
            this.salt = salt;
            this.alg = alg;
            this.nonce = nonce;
            this.ct = ct;
        }

        public String getSalt() { return salt; }
        public String getAlg() { return alg; }
        public String getNonce() { return nonce; }
        public String getCt() { return ct; }
    }
}
