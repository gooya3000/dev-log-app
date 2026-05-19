package com.example.devlogapp.ai;

import com.example.devlogapp.ai.prompt.BlogPromptBuilder;
import com.example.devlogapp.domain.DevLog;
import com.example.devlogapp.domain.Mood;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BlogPromptBuilder 단위 테스트.
 * DevLog 필드 값이 프롬프트에 그대로 포함되는지 결정론적으로 검증.
 */
class BlogPromptBuilderTest {

    private BlogPromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new BlogPromptBuilder();
    }

    private DevLog sampleLog() {
        return DevLog.builder()
                .id("01HXZ1234")
                .date("2026-05-19")
                .title("Spring Boot 에서 WireMock 으로 외부 API 테스트하기")
                .tags(List.of("spring", "wiremock", "test"))
                .whatIDid("WireMock 을 설정하고 OpenAI API 어댑터를 테스트했다.")
                .whatILearned("WireMock 의 stub 과 verify 를 함께 쓰면 요청/응답 양방향 검증이 가능하다.")
                .problems("WireMock standalone jar 버전이 JUnit 5 와 충돌해 의존성 조정이 필요했다.")
                .tomorrow("BlogDraftController 를 구현한다.")
                .mood(Mood.GOOD)
                .createdAt(OffsetDateTime.parse("2026-05-19T22:00:00+09:00"))
                .updatedAt(OffsetDateTime.parse("2026-05-19T22:00:00+09:00"))
                .schemaVersion(1)
                .build();
    }

    @Test
    void prompt_containsKoreanAndMarkdownInstruction() {
        String prompt = builder.build(sampleLog());

        assertThat(prompt).contains("Markdown");
        assertThat(prompt).contains("한국어");
        assertThat(prompt).contains("# ");
        assertThat(prompt).contains("## ");
    }

    @Test
    void prompt_containsTitleFieldVerbatim() {
        DevLog log = sampleLog();
        String prompt = builder.build(log);

        assertThat(prompt).contains(log.getTitle());
    }

    @Test
    void prompt_containsDateFieldVerbatim() {
        DevLog log = sampleLog();
        String prompt = builder.build(log);

        assertThat(prompt).contains(log.getDate());
    }

    @Test
    void prompt_containsTagsVerbatim() {
        DevLog log = sampleLog();
        String prompt = builder.build(log);

        for (String tag : log.getTags()) {
            assertThat(prompt).contains(tag);
        }
    }

    @Test
    void prompt_containsWhatIDidVerbatim() {
        DevLog log = sampleLog();
        String prompt = builder.build(log);

        assertThat(prompt).contains(log.getWhatIDid());
    }

    @Test
    void prompt_containsWhatILearnedVerbatim() {
        DevLog log = sampleLog();
        String prompt = builder.build(log);

        assertThat(prompt).contains(log.getWhatILearned());
    }

    @Test
    void prompt_containsProblemsVerbatim() {
        DevLog log = sampleLog();
        String prompt = builder.build(log);

        assertThat(prompt).contains(log.getProblems());
    }

    @Test
    void prompt_containsTomorrowVerbatim() {
        DevLog log = sampleLog();
        String prompt = builder.build(log);

        assertThat(prompt).contains(log.getTomorrow());
    }

    @Test
    void prompt_containsMoodVerbatim() {
        DevLog log = sampleLog();
        String prompt = builder.build(log);

        assertThat(prompt).contains("GOOD");
    }

    @Test
    void prompt_isDeterministic() {
        DevLog log = sampleLog();

        String first = builder.build(log);
        String second = builder.build(log);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void prompt_withNullFields_doesNotThrow() {
        DevLog log = DevLog.builder()
                .id("01HXZ0000")
                .date("2026-05-19")
                .title("제목")
                .tags(null)
                .whatIDid(null)
                .whatILearned(null)
                .problems(null)
                .tomorrow(null)
                .mood(null)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .schemaVersion(1)
                .build();

        // NullPointerException 없이 실행되어야 함
        String prompt = builder.build(log);
        assertThat(prompt).contains("제목");
    }
}
