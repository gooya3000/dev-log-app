package com.example.devlogapp.storage;

import com.example.devlogapp.domain.DevLog;
import com.example.devlogapp.domain.DevLogId;

import java.util.List;
import java.util.Optional;

/**
 * 회고 저장소 인터페이스.
 * PLAN.md §4.2, §4.3 참조.
 */
public interface DevLogRepository {

    /** 저장 또는 업데이트 (atomic write). */
    void save(DevLog devLog);

    /** ID 로 조회. */
    Optional<DevLog> findById(DevLogId id);

    /** 전체 목록 (날짜 내림차순). */
    List<DevLog> findAll();

    /** 삭제. 파일 없으면 무시. */
    void deleteById(DevLogId id);
}
