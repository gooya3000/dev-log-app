package com.example.devlogapp.service;

import com.example.devlogapp.config.AdminProperties;
import com.example.devlogapp.config.JacksonConfig;
import com.example.devlogapp.domain.User;
import com.example.devlogapp.storage.VaultMetaRepository;
import com.example.devlogapp.vault.KeyWrapper;
import com.example.devlogapp.vault.PassphraseKdf;
import com.example.devlogapp.vault.Vault;
import com.example.devlogapp.vault.VaultMeta;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ② 셀프 가입 두 사본: userWrappedDek + adminWrappedDek 둘 다 같은 DEK_user 반환
 * ③ user login round-trip: 가입 후 unlock 성공, 잘못된 passphrase 실패
 * ④ admin reset: 옛 passphrase 불가, 새 passphrase 가능, DEK_user 동일, adminWrappedDek 동일
 * ⑤ 사용자 삭제: users[] 엔트리 + data/logs/{userId}/ 디렉토리 사라짐
 * ⑦ adminWrappedDek 변조 시 reset 실패
 */
class UserAdminServiceTest {

    private static final String ADMIN_PASSPHRASE = "test-admin-passphrase-2024";
    private static final String USER_ID = "alice";

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();
    private VaultMetaRepository vaultMetaRepository;
    private AdminProperties adminProperties;
    private UserAdminService userAdminService;
    private UserRegistrationService userRegistrationService;
    private UserAuthService userAuthService;
    private Vault vault;
    private Path tempDir;
    private Path logsRoot;

    @BeforeEach
    void setup(@TempDir Path tempDir) throws IOException {
        this.tempDir = tempDir;
        adminProperties = new AdminProperties();
        adminProperties.setPassphrase(ADMIN_PASSPHRASE);

        logsRoot = tempDir.resolve("logs");
        Files.createDirectories(logsRoot);

        vaultMetaRepository = new VaultMetaRepository(objectMapper, tempDir);
        vault = new Vault();

        // bootstrap
        VaultBootstrapService bootstrap = new VaultBootstrapService(adminProperties, vaultMetaRepository, logsRoot);
        bootstrap.bootstrap();

        userRegistrationService = new UserRegistrationService(adminProperties, vaultMetaRepository, logsRoot);
        userAdminService = new UserAdminService(adminProperties, vaultMetaRepository, logsRoot);
        userAuthService = new UserAuthService(vaultMetaRepository, vault);
    }

    @Test
    void register_addsTwoWrappedDekCopies() {
        // ② 셀프 가입 → users[] 에 alice 엔트리, userWrappedDek + adminWrappedDek 둘 다 존재
        userRegistrationService.register(USER_ID, "pass1");

        VaultMeta meta = vaultMetaRepository.load().orElseThrow();
        assertThat(meta.getUsers()).hasSize(1);

        User user = meta.getUsers().get(0);
        assertThat(user.getId()).isEqualTo(USER_ID);
        assertThat(user.getSalt()).isNotBlank();
        assertThat(user.getPassphraseHash()).isNotBlank();
        assertThat(user.getUserWrappedDek()).isNotNull();
        assertThat(user.getUserWrappedDek().getCt()).isNotBlank();
        assertThat(user.getAdminWrappedDek()).isNotNull();
        assertThat(user.getAdminWrappedDek().getCt()).isNotBlank();
    }

    @Test
    void register_userAndAdminCanUnwrapSameDek() {
        // ② 각자 자기 키로 unwrap 했을 때 같은 32바이트 DEK_user 나오는지 검증
        String passphrase = "pass1";
        userRegistrationService.register(USER_ID, passphrase);

        VaultMeta meta = vaultMetaRepository.load().orElseThrow();
        User user = meta.getUsers().get(0);

        // userWrappedDek unwrap (K_user 로)
        byte[] userSalt = Base64.getDecoder().decode(user.getSalt());
        byte[] derivation = PassphraseKdf.deriveUser(passphrase.toCharArray(), userSalt);
        byte[] kUser = Arrays.copyOfRange(derivation, 0, 32);
        Arrays.fill(derivation, (byte) 0);

        byte[] dekFromUser = KeyWrapper.unwrap(kUser, user.getUserWrappedDek().getNonce(), user.getUserWrappedDek().getCt());
        Arrays.fill(kUser, (byte) 0);

        // adminWrappedDek unwrap (K_admin 으로)
        byte[] adminSaltBytes = Base64.getDecoder().decode(meta.getAdminSalt());
        byte[] kAdmin = PassphraseKdf.deriveAdmin(ADMIN_PASSPHRASE.toCharArray(), adminSaltBytes);
        byte[] dekFromAdmin = KeyWrapper.unwrap(kAdmin, user.getAdminWrappedDek().getNonce(), user.getAdminWrappedDek().getCt());
        Arrays.fill(kAdmin, (byte) 0);

        // 두 사본이 같은 DEK_user
        assertThat(dekFromUser).isEqualTo(dekFromAdmin);
        assertThat(dekFromUser).hasSize(32);

        Arrays.fill(dekFromUser, (byte) 0);
        Arrays.fill(dekFromAdmin, (byte) 0);
    }

    @Test
    void register_userDirCreated() {
        // ② 가입 후 data/logs/{userId}/ 디렉토리 생성
        userRegistrationService.register(USER_ID, "pass1");
        assertThat(logsRoot.resolve(USER_ID)).exists().isDirectory();
    }

    @Test
    void register_duplicateUserId_fails() {
        // userId 중복 → 실패
        userRegistrationService.register(USER_ID, "pass1");
        assertThatThrownBy(() -> userRegistrationService.register(USER_ID, "pass2"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void login_correctPassphrase_vaultLoaded() {
        // ③ 가입 후 unlock 성공
        userRegistrationService.register(USER_ID, "test-pass");
        userAuthService.unlock(USER_ID, "test-pass");
        assertThat(vault.isLocked()).isFalse();
        assertThat(vault.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void login_wrongPassphrase_fails() {
        // ③ 잘못된 passphrase → 인증 실패
        userRegistrationService.register(USER_ID, "test-pass");
        assertThatThrownBy(() -> userAuthService.unlock(USER_ID, "wrong-pass"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetPassphrase_oldFails_newSucceeds_dekSame() {
        // ④ reset 후 옛 passphrase 불가, 새 passphrase 가능, DEK_user 동일
        String oldPass = "old-passphrase";

        userRegistrationService.register(USER_ID, oldPass);

        // 가입 시점 DEK 캡처
        userAuthService.unlock(USER_ID, oldPass);
        byte[] dekBefore = vault.copyDek();
        vault.lock();

        // passphrase 재설정 — 새 passphrase 는 서비스가 SecureRandom 으로 생성하여 반환
        String newPass = userAdminService.resetPassphrase(USER_ID);

        // 반환된 passphrase: 정확히 16자, 영숫자만
        assertThat(newPass).hasSize(16);
        assertThat(newPass).matches("[A-Za-z0-9]+");

        // 옛 passphrase 로는 실패
        assertThatThrownBy(() -> userAuthService.unlock(USER_ID, oldPass))
                .isInstanceOf(IllegalArgumentException.class);

        // 새 passphrase 로 unlock 성공
        userAuthService.unlock(USER_ID, newPass);
        assertThat(vault.isLocked()).isFalse();

        byte[] dekAfter = vault.copyDek();

        // DEK 자체는 동일
        assertThat(dekAfter).isEqualTo(dekBefore);

        Arrays.fill(dekBefore, (byte) 0);
        Arrays.fill(dekAfter, (byte) 0);
    }

    @Test
    void resetPassphrase_returnsDifferentValueEachTime() {
        // 두 번 호출 시 각각 다른 passphrase 반환 (랜덤 검증)
        userRegistrationService.register(USER_ID, "initial-pass");
        String first = userAdminService.resetPassphrase(USER_ID);
        String second = userAdminService.resetPassphrase(USER_ID);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void resetPassphrase_adminWrappedDekUnchanged() {
        // ④ reset 후 adminWrappedDek 는 그대로
        userRegistrationService.register(USER_ID, "old-pass");
        VaultMeta before = vaultMetaRepository.load().orElseThrow();
        String adminCtBefore = before.getUsers().get(0).getAdminWrappedDek().getCt();

        userAdminService.resetPassphrase(USER_ID);
        VaultMeta after = vaultMetaRepository.load().orElseThrow();
        String adminCtAfter = after.getUsers().get(0).getAdminWrappedDek().getCt();

        assertThat(adminCtAfter).isEqualTo(adminCtBefore);
    }

    @Test
    void deleteUser_removesEntryAndDirectory() throws IOException {
        // ⑤ 사용자 삭제 → users[] 엔트리 + data/logs/{userId}/ 디렉토리 둘 다 사라짐
        userRegistrationService.register(USER_ID, "pass1");
        userAuthService.unlock(USER_ID, "pass1");
        Path userDir = logsRoot.resolve(USER_ID);
        // 회고 파일 1개 만들기
        Files.writeString(userDir.resolve("2026-05-21_dummy.json"), "{\"test\":1}");
        vault.lock();

        userAdminService.deleteUser(USER_ID);

        VaultMeta meta = vaultMetaRepository.load().orElseThrow();
        assertThat(meta.getUsers()).isEmpty();
        assertThat(userDir).doesNotExist();
    }

    @Test
    void adminWrappedDekTampered_resetFails() throws IOException {
        // ⑦ adminWrappedDek.ct 바이트 한 개 flip → resetPassphrase 호출 시 unwrap 실패
        userRegistrationService.register(USER_ID, "pass1");

        VaultMeta meta = vaultMetaRepository.load().orElseThrow();
        User user = meta.getUsers().get(0);

        // adminWrappedDek.ct 를 변조
        byte[] ctBytes = Base64.getDecoder().decode(user.getAdminWrappedDek().getCt());
        ctBytes[0] ^= 0xFF;  // 첫 바이트 flip
        String tamperedCt = Base64.getEncoder().encodeToString(ctBytes);

        User.WrappedDek tamperedAdminWDek = new User.WrappedDek(
                user.getAdminWrappedDek().getAlg(),
                user.getAdminWrappedDek().getNonce(),
                tamperedCt);

        User tamperedUser = new User(
                user.getId(), user.getSalt(), user.getPassphraseHash(),
                user.getCreatedAt(), user.getUserWrappedDek(), tamperedAdminWDek);

        vaultMetaRepository.save(meta.withUsers(java.util.List.of(tamperedUser)));

        // reset 시 unwrap 실패
        assertThatThrownBy(() -> userAdminService.resetPassphrase(USER_ID))
                .isInstanceOf(IllegalStateException.class);
    }
}
