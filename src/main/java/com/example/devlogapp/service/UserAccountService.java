package com.example.devlogapp.service;

import com.example.devlogapp.domain.User;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.example.devlogapp.vault.KeyWrapper;
import com.example.devlogapp.vault.PassphraseKdf;
import com.example.devlogapp.vault.Vault;
import com.example.devlogapp.vault.VaultMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 본인 passphrase 변경.
 * PLAN.md §4.5.6 "사용자 본인 passphrase 변경" 11단계 흐름.
 * PLAN.md §5.6: 로그·예외에 passphrase/K_user/H_user/DEK 노출 금지.
 */
@Service
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VaultMetaRepository vaultMetaRepository;
    private final Vault vault;

    public UserAccountService(VaultMetaRepository vaultMetaRepository, Vault vault) {
        this.vaultMetaRepository = vaultMetaRepository;
        this.vault = vault;
    }

    /**
     * 본인 passphrase 변경 (PLAN §4.5.6 11단계).
     * - 단계 1: 컨트롤러가 ROLE_USER 검증 + userId 넘김
     * - 단계 2: 폼 검증은 컨트롤러
     *
     * @param userId        현재 세션 userId
     * @param oldPassphrase 옛 passphrase (검증 후 GC 대상)
     * @param newPassphrase 새 passphrase (검증 후 GC 대상)
     * @throws PassphraseMismatchException 옛 passphrase 불일치 또는 DEK unwrap 실패
     * @throws IllegalStateException       세션 무효 또는 vault 상태 불일치
     */
    public void changeOwnPassphrase(String userId, String oldPassphrase, String newPassphrase) {
        // 단계 3: users[] 에서 세션 userId 엔트리 조회
        VaultMeta meta = vaultMetaRepository.load()
                .orElseThrow(() -> new IllegalStateException("Vault not initialized"));

        User target = meta.getUsers().stream()
                .filter(u -> u.getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "세션 userId가 vault에 없습니다. 세션이 무효합니다."));

        byte[] oldDerivation = null;
        byte[] kUserOld = null;
        byte[] newDerivation = null;
        byte[] kUserNew = null;
        byte[] hUserNew = null;
        byte[] dekUser = null;
        byte[] newSalt = new byte[16];

        try {
            // 단계 4: PBKDF2(oldPassphrase, 엔트리 salt, 600_000, 64)
            byte[] saltBytes = Base64.getDecoder().decode(target.getSalt());
            oldDerivation = PassphraseKdf.deriveUser(oldPassphrase.toCharArray(), saltBytes);

            // 단계 5: constant-time 비교 (oldDerivation[32..64] vs passphraseHash)
            byte[] storedHash = Base64.getDecoder().decode(target.getPassphraseHash());
            byte[] derivedHash = Arrays.copyOfRange(oldDerivation, 32, 64);
            boolean hashMatches = MessageDigest.isEqual(derivedHash, storedHash);
            Arrays.fill(derivedHash, (byte) 0);

            if (!hashMatches) {
                delayOnFailure();
                throw new PassphraseMismatchException("현재 비밀번호가 일치하지 않습니다.");
            }

            // 단계 6: K_user_old = oldDerivation[0..32] → userWrappedDek unwrap → DEK_user 회수
            kUserOld = Arrays.copyOfRange(oldDerivation, 0, 32);
            User.WrappedDek uwd = target.getUserWrappedDek();
            try {
                dekUser = KeyWrapper.unwrap(kUserOld, uwd.getNonce(), uwd.getCt());
            } catch (Exception e) {
                delayOnFailure();
                throw new PassphraseMismatchException("현재 비밀번호가 일치하지 않습니다.", e);
            }

            // 단계 7: sanity check — 회수한 DEK_user == Vault 의 DEK_user
            byte[] sessionDek = vault.copyDek();
            boolean dekMatches = MessageDigest.isEqual(dekUser, sessionDek);
            Arrays.fill(sessionDek, (byte) 0);

            if (!dekMatches) {
                throw new IllegalStateException("세션과 vault 상태가 불일치합니다.");
            }

            // 단계 8: newSalt
            RANDOM.nextBytes(newSalt);

            // 단계 9: newDerivation = PBKDF2(newPassphrase, newSalt, 64)
            newDerivation = PassphraseKdf.deriveUser(newPassphrase.toCharArray(), newSalt);
            kUserNew = Arrays.copyOfRange(newDerivation, 0, 32);
            hUserNew = Arrays.copyOfRange(newDerivation, 32, 64);

            // 단계 10: newUserWrappedDek = AES-GCM(DEK_user, K_user_new, newNonce)
            KeyWrapper.WrapResult wrapped = KeyWrapper.wrap(kUserNew, dekUser);
            User.WrappedDek newUserWrappedDek = new User.WrappedDek(
                    "AES-256-GCM", wrapped.nonceB64(), wrapped.ctB64());

            // 단계 11: users[id] 엔트리에서 salt, passphraseHash, userWrappedDek 만 교체
            //           adminWrappedDek 는 절대 건드리지 않음
            User updated = target.withNewPassphrase(
                    Base64.getEncoder().encodeToString(newSalt),
                    Base64.getEncoder().encodeToString(hUserNew),
                    newUserWrappedDek);

            List<User> updatedUsers = meta.getUsers().stream()
                    .map(u -> u.getId().equals(userId) ? updated : u)
                    .toList();

            // atomic write (VaultMetaRepository.save 는 이미 atomic)
            vaultMetaRepository.save(meta.withUsers(updatedUsers));

            // 세션 유지 — Vault 의 DEK_user 는 그대로 (단계 11 참조)
            log.info("Passphrase changed for user: {}", userId);

        } finally {
            // 단계 12: byte 배열 zero-fill 폐기
            if (oldDerivation != null) Arrays.fill(oldDerivation, (byte) 0);
            if (kUserOld != null) Arrays.fill(kUserOld, (byte) 0);
            if (newDerivation != null) Arrays.fill(newDerivation, (byte) 0);
            if (kUserNew != null) Arrays.fill(kUserNew, (byte) 0);
            if (hUserNew != null) Arrays.fill(hUserNew, (byte) 0);
            if (dekUser != null) Arrays.fill(dekUser, (byte) 0);
            Arrays.fill(newSalt, (byte) 0);
            // String passphrase 는 GC 에 맡김 (PLAN §4.5.6 step 12)
        }
    }

    private static void delayOnFailure() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
