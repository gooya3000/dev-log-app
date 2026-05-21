package com.example.devlogapp.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.ToString;

import java.time.OffsetDateTime;

/**
 * 사용자 메타 도메인 모델 (불변).
 * 새 모델: userWrappedDek + adminWrappedDek 두 사본 보유.
 * PLAN.md §4.5.3 참조.
 */
public final class User {

    private final String id;
    private final String salt;             // base64 16바이트 (userSalt)
    @ToString.Exclude
    private final String passphraseHash;   // base64 32바이트 (H_user)
    private final OffsetDateTime createdAt;
    @ToString.Exclude
    private final WrappedDek userWrappedDek;
    @ToString.Exclude
    private final WrappedDek adminWrappedDek;

    @JsonCreator
    public User(
            @JsonProperty("id") String id,
            @JsonProperty("salt") String salt,
            @JsonProperty("passphraseHash") String passphraseHash,
            @JsonProperty("createdAt") OffsetDateTime createdAt,
            @JsonProperty("userWrappedDek") WrappedDek userWrappedDek,
            @JsonProperty("adminWrappedDek") WrappedDek adminWrappedDek) {
        this.id = id;
        this.salt = salt;
        this.passphraseHash = passphraseHash;
        this.createdAt = createdAt;
        this.userWrappedDek = userWrappedDek;
        this.adminWrappedDek = adminWrappedDek;
    }

    public String getId() { return id; }
    public String getSalt() { return salt; }
    public String getPassphraseHash() { return passphraseHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public WrappedDek getUserWrappedDek() { return userWrappedDek; }
    public WrappedDek getAdminWrappedDek() { return adminWrappedDek; }

    /**
     * salt / passphraseHash / userWrappedDek 교체한 새 User 반환 (passphrase 재설정용).
     * adminWrappedDek 와 createdAt 은 그대로.
     */
    public User withNewPassphrase(String newSalt, String newPassphraseHash, WrappedDek newUserWrappedDek) {
        return new User(id, newSalt, newPassphraseHash, createdAt, newUserWrappedDek, adminWrappedDek);
    }

    /**
     * DEK wrap 포맷 (alg/nonce/ct).
     * PLAN.md §4.5.3 참조.
     */
    public static final class WrappedDek {
        private final String alg;
        private final String nonce;
        @ToString.Exclude
        private final String ct;

        @JsonCreator
        public WrappedDek(
                @JsonProperty("alg") String alg,
                @JsonProperty("nonce") String nonce,
                @JsonProperty("ct") String ct) {
            this.alg = alg;
            this.nonce = nonce;
            this.ct = ct;
        }

        public String getAlg() { return alg; }
        public String getNonce() { return nonce; }
        public String getCt() { return ct; }
    }
}
