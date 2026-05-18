package com.example.devlogapp.vault;

/**
 * Vault 가 잠긴 상태(DEK 미로드)에서 암호화/복호화 시도 시 발생.
 */
public class VaultLockedException extends RuntimeException {

    public VaultLockedException(String message) {
        super(message);
    }
}
