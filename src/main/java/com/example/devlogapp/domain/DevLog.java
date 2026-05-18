package com.example.devlogapp.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 회고 도메인 모델. 가급적 불변 — 수정 시 withUpdated() 사용.
 * PLAN.md §1.2, §4.2 참조.
 */
public final class DevLog {

    private final String id;
    private final String date;          // YYYY-MM-DD
    private final String title;
    private final List<String> tags;
    private final String whatIDid;
    private final String whatILearned;
    private final String problems;
    private final String tomorrow;
    private final Mood mood;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final int schemaVersion;

    @JsonCreator
    public DevLog(
            @JsonProperty("id") String id,
            @JsonProperty("date") String date,
            @JsonProperty("title") String title,
            @JsonProperty("tags") List<String> tags,
            @JsonProperty("whatIDid") String whatIDid,
            @JsonProperty("whatILearned") String whatILearned,
            @JsonProperty("problems") String problems,
            @JsonProperty("tomorrow") String tomorrow,
            @JsonProperty("mood") Mood mood,
            @JsonProperty("createdAt") OffsetDateTime createdAt,
            @JsonProperty("updatedAt") OffsetDateTime updatedAt,
            @JsonProperty("schemaVersion") int schemaVersion) {
        this.id = id;
        this.date = date;
        this.title = title;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
        this.whatIDid = whatIDid;
        this.whatILearned = whatILearned;
        this.problems = problems;
        this.tomorrow = tomorrow;
        this.mood = mood;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.schemaVersion = schemaVersion;
    }

    public String getId() { return id; }
    public String getDate() { return date; }
    public String getTitle() { return title; }
    public List<String> getTags() { return tags; }
    public String getWhatIDid() { return whatIDid; }
    public String getWhatILearned() { return whatILearned; }
    public String getProblems() { return problems; }
    public String getTomorrow() { return tomorrow; }
    public Mood getMood() { return mood; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public int getSchemaVersion() { return schemaVersion; }

    /** updatedAt 갱신 후 새 인스턴스 반환. */
    public DevLog withUpdated(String title, List<String> tags, String whatIDid,
                               String whatILearned, String problems, String tomorrow,
                               Mood mood, OffsetDateTime updatedAt) {
        return new DevLog(id, date, title, tags, whatIDid, whatILearned,
                problems, tomorrow, mood, createdAt, updatedAt, schemaVersion);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String date;
        private String title;
        private List<String> tags;
        private String whatIDid;
        private String whatILearned;
        private String problems;
        private String tomorrow;
        private Mood mood;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
        private int schemaVersion = 1;

        public Builder id(String id) { this.id = id; return this; }
        public Builder date(String date) { this.date = date; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder tags(List<String> tags) { this.tags = tags; return this; }
        public Builder whatIDid(String v) { this.whatIDid = v; return this; }
        public Builder whatILearned(String v) { this.whatILearned = v; return this; }
        public Builder problems(String v) { this.problems = v; return this; }
        public Builder tomorrow(String v) { this.tomorrow = v; return this; }
        public Builder mood(Mood mood) { this.mood = mood; return this; }
        public Builder createdAt(OffsetDateTime v) { this.createdAt = v; return this; }
        public Builder updatedAt(OffsetDateTime v) { this.updatedAt = v; return this; }
        public Builder schemaVersion(int v) { this.schemaVersion = v; return this; }

        public DevLog build() {
            return new DevLog(id, date, title, tags, whatIDid, whatILearned,
                    problems, tomorrow, mood, createdAt, updatedAt, schemaVersion);
        }
    }
}
