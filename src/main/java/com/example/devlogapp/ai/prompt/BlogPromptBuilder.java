package com.example.devlogapp.ai.prompt;

import com.example.devlogapp.domain.DevLog;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DevLog 도메인 객체를 기술 블로그 초안 작성용 프롬프트 텍스트로 변환한다.
 * PLAN.md §3 (ai.prompt 패키지), §6.2 Phase 2-B 참조.
 *
 * 필드 값을 요약/패러프레이즈 없이 그대로 삽입하므로 결정론적 단위 테스트가 가능하다.
 */
@Component
public class BlogPromptBuilder {

    /**
     * DevLog 1건을 받아 한국어 기술 블로그 초안 작성 프롬프트를 반환한다.
     *
     * @param log 회고 도메인 객체
     * @return 프롬프트 텍스트
     */
    public String build(DevLog log) {
        StringBuilder sb = new StringBuilder();

        sb.append("당신은 개발자의 기술 블로그 작성을 돕는 어시스턴트입니다.\n");
        sb.append("아래 개발 회고 내용을 바탕으로 한국어 기술 블로그 포스트 초안을 작성해 주세요.\n\n");

        sb.append("## 출력 형식 지시\n");
        sb.append("- Markdown 으로 작성할 것\n");
        sb.append("- 최상위 제목은 `# 제목` 형식 사용\n");
        sb.append("- 섹션은 `## 섹션명` 형식 사용\n");
        sb.append("- 회고 1건 → 블로그 1편 분량으로 작성\n");
        sb.append("- 기술적 내용은 독자가 이해하기 쉽게 설명\n\n");

        sb.append("## 회고 원문\n\n");

        sb.append("**날짜:** ").append(nullSafe(log.getDate())).append("\n");
        sb.append("**제목:** ").append(nullSafe(log.getTitle())).append("\n");

        List<String> tags = log.getTags();
        if (tags != null && !tags.isEmpty()) {
            sb.append("**태그:** ").append(String.join(", ", tags)).append("\n");
        }

        if (log.getMood() != null) {
            sb.append("**오늘의 기분:** ").append(log.getMood().name()).append("\n");
        }

        sb.append("\n### 오늘 한 일\n");
        sb.append(nullSafe(log.getWhatIDid())).append("\n");

        sb.append("\n### 배운 것\n");
        sb.append(nullSafe(log.getWhatILearned())).append("\n");

        sb.append("\n### 막힌 부분 / 이슈\n");
        sb.append(nullSafe(log.getProblems())).append("\n");

        sb.append("\n### 내일 할 일\n");
        sb.append(nullSafe(log.getTomorrow())).append("\n");

        sb.append("\n---\n");
        sb.append("위 회고를 바탕으로 기술 블로그 포스트 초안을 Markdown 형식으로 작성해 주세요.\n");
        sb.append("독자는 개발자이며, 실제로 도움이 될 만한 기술 내용을 풍부하게 담아 주세요.\n");

        return sb.toString();
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
