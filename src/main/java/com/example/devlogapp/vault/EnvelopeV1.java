package com.example.devlogapp.vault;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 회고 파일 암호화 envelope 포맷 (v1).
 * PLAN.md §4.5.3 참조.
 *
 * {
 *   "v": 1,
 *   "alg": "AES-256-GCM",
 *   "nonce": "<base64 12바이트>",
 *   "ct": "<base64 ciphertext+tag>"
 * }
 */
public final class EnvelopeV1 {

    private final int v;
    private final String alg;
    private final String nonce;
    private final String ct;

    @JsonCreator
    public EnvelopeV1(
            @JsonProperty("v") int v,
            @JsonProperty("alg") String alg,
            @JsonProperty("nonce") String nonce,
            @JsonProperty("ct") String ct) {
        this.v = v;
        this.alg = alg;
        this.nonce = nonce;
        this.ct = ct;
    }

    public int getV() { return v; }
    public String getAlg() { return alg; }
    public String getNonce() { return nonce; }
    public String getCt() { return ct; }
}
