package com.example.devlogapp.vault;

import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 메모리에 DEK + unlock 한 userId 컨텍스트를 보관하는 빈.
 * DEK 는 평문으로 디스크에 기록되지 않는다.
 * 앱 종료 또는 명시적 lock() 시 DEK 폐기.
 * PLAN.md §4.5.4 참조.
 */
@Component
public class Vault {

    private byte[] dek;       // 32바이트 DEK (null = locked)
    private String userId;    // unlock 한 사용자 ID

    /** DEK 를 메모리에 적재하고 userId 컨텍스트 설정. */
    public synchronized void unlock(byte[] dek, String userId) {
        // 기존 DEK 폐기 후 교체
        wipeInternalDek();
        this.dek = Arrays.copyOf(dek, dek.length);
        this.userId = userId;
    }

    /** DEK 와 userId 컨텍스트를 메모리에서 제거. */
    public synchronized void lock() {
        wipeInternalDek();
        this.userId = null;
    }

    /** 잠김 여부 확인. */
    public synchronized boolean isLocked() {
        return dek == null;
    }

    /** 현재 unlock 한 userId 반환. */
    public synchronized String getUserId() {
        return userId;
    }

    /**
     * DEK 복사본 반환. 호출자가 사용 후 Arrays.fill(copy, (byte)0) 폐기.
     * @throws VaultLockedException DEK 미로드 상태
     */
    public synchronized byte[] copyDek() {
        if (dek == null) {
            throw new VaultLockedException("Vault is locked. Please unlock first.");
        }
        return Arrays.copyOf(dek, dek.length);
    }

    private void wipeInternalDek() {
        if (this.dek != null) {
            Arrays.fill(this.dek, (byte) 0);
            this.dek = null;
        }
    }
}
