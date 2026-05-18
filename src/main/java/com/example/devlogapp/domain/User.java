package com.example.devlogapp.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 사용자 메타 도메인 모델 (불변).
 * id, salt, passphraseHash, wrappedDek 보관.
 * PLAN.md §4.5.3 참조.
 */
public final class User {

    private final String id;
    private final String salt;           // base64 16바이트
    private final String passphraseHash; // base64 32바이트 (H_user)
    private final WrappedDek wrappedDek;

    @JsonCreator
    public User(
            @JsonProperty("id") String id,
            @JsonProperty("salt") String salt,
            @JsonProperty("passphraseHash") String passphraseHash,
            @JsonProperty("wrappedDek") WrappedDek wrappedDek) {
        this.id = id;
        this.salt = salt;
        this.passphraseHash = passphraseHash;
        this.wrappedDek = wrappedDek;
    }

    public String getId() { return id; }
    public String getSalt() { return salt; }
    public String getPassphraseHash() { return passphraseHash; }
    public WrappedDek getWrappedDek() { return wrappedDek; }

    /**
     * wrappedDek 포맷 (alg/nonce/ct — salt는 User 레벨에 있음).
     */
    public static final class WrappedDek {
        private final String alg;
        private final String nonce;
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
