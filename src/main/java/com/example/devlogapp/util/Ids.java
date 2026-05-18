package com.example.devlogapp.util;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * UUIDv7 스타일 ID 발급 유틸리티.
 * 외부 라이브러리 없이 timestamp(ms) + random 으로 시간순 정렬 가능한 ID 생성.
 */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {}

    /**
     * UUIDv7 형식 ID 반환.
     * 상위 48비트 = 현재 unix_ts_ms, 이후 bits = version/variant + random.
     */
    public static String newId() {
        long ts = Instant.now().toEpochMilli();

        // upper 64 bits: 48-bit timestamp | 4-bit version(7) | 12-bit random
        long rand80 = RANDOM.nextLong();
        long upper = (ts << 16) | (0x7000L) | (rand80 & 0x0FFFL);

        // lower 64 bits: 2-bit variant(10) | 62-bit random
        long lower = (rand80 >>> 2) | (0x8000000000000000L);

        return String.format("%016x-%04x-%04x-%04x-%012x",
                (upper >>> 32) & 0xFFFFFFFFL,
                (upper >>> 16) & 0xFFFFL,
                upper & 0xFFFFL,
                (lower >>> 48) & 0xFFFFL,
                lower & 0xFFFFFFFFFFFFL);
    }
}
