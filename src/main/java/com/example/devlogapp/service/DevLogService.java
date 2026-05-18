package com.example.devlogapp.service;

import com.example.devlogapp.domain.DevLog;
import com.example.devlogapp.domain.DevLogId;
import com.example.devlogapp.domain.Mood;
import com.example.devlogapp.storage.DevLogRepository;
import com.example.devlogapp.util.Ids;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 회고 도메인 유스케이스.
 * Vault unlock 상태(ROLE_USER)에서만 사용.
 * PLAN.md §1.1 참조.
 */
@Service
public class DevLogService {

    private final DevLogRepository repository;

    public DevLogService(DevLogRepository repository) {
        this.repository = repository;
    }

    /** 신규 회고 저장. */
    public DevLog create(String date, String title, List<String> tags,
                          String whatIDid, String whatILearned, String problems,
                          String tomorrow, Mood mood) {
        String id = Ids.newId();
        OffsetDateTime now = OffsetDateTime.now();
        DevLog devLog = DevLog.builder()
                .id(id)
                .date(date)
                .title(title)
                .tags(tags)
                .whatIDid(whatIDid)
                .whatILearned(whatILearned)
                .problems(problems)
                .tomorrow(tomorrow)
                .mood(mood)
                .createdAt(now)
                .updatedAt(now)
                .schemaVersion(1)
                .build();
        repository.save(devLog);
        return devLog;
    }

    /** ID 로 조회. */
    public Optional<DevLog> findById(String id) {
        return repository.findById(new DevLogId(id));
    }

    /** 전체 목록 (날짜 내림차순). */
    public List<DevLog> findAll() {
        return repository.findAll();
    }

    /** 수정 저장. */
    public DevLog update(String id, String date, String title, List<String> tags,
                          String whatIDid, String whatILearned, String problems,
                          String tomorrow, Mood mood) {
        DevLog existing = repository.findById(new DevLogId(id))
                .orElseThrow(() -> new IllegalArgumentException("DevLog not found: " + id));

        DevLog updated = existing.withUpdated(
                title, tags, whatIDid, whatILearned, problems, tomorrow, mood,
                OffsetDateTime.now());
        repository.save(updated);
        return updated;
    }

    /** 삭제. */
    public void delete(String id) {
        repository.deleteById(new DevLogId(id));
    }
}
