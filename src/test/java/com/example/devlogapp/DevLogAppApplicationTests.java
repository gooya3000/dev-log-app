package com.example.devlogapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 기본 컨텍스트 로드 테스트.
 * admin passphrase 를 테스트 전용 값으로 설정해 bootstrap 통과.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "devlog.admin.passphrase=test-admin-passphrase-for-ci",
    "devlog.storage.root=${java.io.tmpdir}/devlog-test-context/logs"
})
class DevLogAppApplicationTests {

    @Test
    void contextLoads() {
    }

}
