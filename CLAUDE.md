# DevLog

개인용 개발 회고 앱. Spring Boot + Thymeleaf, DB 없이 JSON 파일 1개 = 회고 1건. 회고를 LLM에 보내 기술 블로그 마크다운 초안을 생성한다.

이 리포는 **GitHub에 공개로 푸시**된다. 어떤 시크릿도 리포에 들어가지 않는다.

---

## 설계 참조: `docs/PLAN.md`

모든 설계 결정은 `docs/PLAN.md`를 1차 출처로 삼는다. 기능 범위, 화면, 패키지 구조, JSON 저장 정책, 키 정책, 서브에이전트 분리 계획이 모두 거기 있다.

- 코드를 짜기 전, 변경이 PLAN.md의 어느 절(§)에 해당하는지 먼저 확인한다.
- PLAN.md와 실제 코드가 충돌하면, **결정을 다시 묻고 PLAN.md를 갱신한 뒤** 코드를 바꾼다. 침묵 드리프트 금지.
  - "묻는 주체"는 상황에 따라 다르다: **서브에이전트는 메인 세션에**, **메인 세션은 사용자에게**. 서브에이전트가 PLAN 을 직접 고치지 않는다 (`.claude/agents/*.md` 의 "지시 문서 자가 수정 금지" 규칙).

## 절대 지킬 것 (보안)

세부 규칙은 `docs/PLAN.md` §5 단일 출처. 아래는 코드 짤 때 단 1초도 잊으면 안 되는 핀카드.

- **시크릿 제로 커밋**: API 키·user passphrase 는 폼으로만 받아 메서드 인자로만 흐르게 한다 (DTO `@ToString.Exclude` 필수). admin passphrase 만 `application-local.properties` 평문, 그 외 모든 시크릿은 stateless. 회고 평문은 디스크에 닿지 않는다 — `VaultCipher` 통과 AES-256-GCM ciphertext 만 기록.
- **DEK 래핑 구조 유지**: 파일은 DEK 로 암호화, DEK 는 admin 키 / 각 user 키로 별도 wrap. DEK 가 평문 디스크에 노출되는 경로를 만들지 말 것.
- **권한 분리 유지**: Spring Security 로 `/vault/**` = ROLE_ADMIN, `/logs/**` = ROLE_USER. 한 컨트롤러가 두 권한 다 다루지 말 것.
- **`.gitignore` 단일 출처**: PLAN.md §5.4. **주의**: `data/` 는 ignore 대상 아님 — ciphertext 형태로 리포에 커밋된다.

## 기술 스택

- Java 17, Spring Boot 3.x, Gradle
- Thymeleaf, Jackson, `spring-boot-starter-validation`, Lombok
- 테스트: JUnit 5 (+ MockMvc, WireMock as needed)
- 저장: 로컬 JSON 파일 (`./data/logs/{userId}/{date}_{id}.json` — 사용자별 서브디렉토리, PLAN.md §4.5)
- AI 어댑터: **Gemini** (`GeminiAiClient`, `gemini-2.5-flash`). 키 발급·정책은 PLAN.md §5 / `docs/HANDOFF.md`

## 자주 쓰는 명령

- 실행: `./gradlew bootRun`
- 테스트: `./gradlew test`
- 빌드: `./gradlew build`

## 코드 관습

- 기본 패키지: `com.example.devlogapp` (서브 구조는 PLAN.md §3)
- 도메인 모델은 가능한 한 불변. `@Data` 남발하지 말고, 수정 필요한 곳에만 setter.
- Jackson 시간 직렬화: ISO-8601 (`WRITE_DATES_AS_TIMESTAMPS=false`), pretty-print on.
- JSON 파일 쓰기는 **atomic**: tmp 파일에 쓴 뒤 `Files.move(..., ATOMIC_MOVE)`.
- 파일명 규칙: `{yyyy-MM-dd}_{id}.json`. `id`는 UUIDv7 (`util/Ids.java`).
- `schemaVersion` 필드를 첫날부터 둔다.
- 컨트롤러는 폼 검증(`@Valid`)을 거치고, 도메인 변환은 서비스에서. 컨트롤러에 비즈니스 로직 금지.

## 테스트 정책

- 저장소(`JsonDevLogRepository`)는 임시 디렉토리 기반 round-trip 테스트가 기본.
- AI 어댑터(Gemini)는 WireMock/fake로 외부 호출 없이 테스트. **실제 키로 테스트 돌리지 말 것.**
- 컨트롤러는 MockMvc 슬라이스로 200/302/유효성 에러 흐름 확인.

## 서브에이전트 작업 시

이 리포는 `.claude/agents/`에 **코드 작업용 4개 + 메타 1개**의 커스텀 에이전트를 정의해 두었다. Phase별 매핑은 PLAN.md §6.4 표 (코드용) / §6.5 (메타) 참조.

코드 작업용 (PLAN.md §6.4):
- `spring-backend` (sonnet, 풀권한) — Phase 1, 2-A, **R-1/R-2** (vault 모델 재구축)
- `ai-integration` (sonnet, 풀권한) — Phase 2-B 및 Gemini 어댑터 후속. 시스템 프롬프트에 §5 키 정책이 박혀 있음
- `test-engineer` (haiku, 프로덕션 코드 수정 금지 규칙) — Phase 2-C, 테스트 보강
- `code-reviewer` (opus, **읽기 전용**) — 각 Phase 작업 종료 직후, 커밋·푸시 직전 통합 리뷰. Edit/Write 도구 없음

메타 (PLAN.md §6.5):
- `instruction-auditor` (opus, **읽기 전용**, Read/Grep/Glob 만) — 지시 문서(`*.md`, `.claude/agents/*`) 자체의 모순·모호성·중복·역할 혼재 점검. 코드는 안 봄. 사용자가 직접 호출하며 판단 모호 시 메인 세션에 확인 질문을 던지는 형태

위임 시 규칙:
- 브리핑에 **PLAN.md의 어느 절/Phase인지** 항상 명시.
- "이해를 위임하지 않는다." 구체적인 파일/시그니처/통과시켜야 할 테스트로 좁혀서 위임.
- Phase 1(도메인+저장소)이 후속 작업의 계약이라 단독으로 먼저 끝낸다. Phase 2-A/2-B/2-C는 병렬. **현재 상태는 HANDOFF.md 참조** — Phase 1–3 종료, R-1/R-2 종료, R-3(통합 리뷰+E2E)이 다음.
- 서브에이전트의 "완료했습니다"는 의도일 뿐. 메인 세션이 diff·테스트로 직접 확인. 각 Phase 작업 종료 직후, 커밋·푸시 직전 `code-reviewer` 한 번 더 돌릴 것.

## 커뮤니케이션

- 응답은 한국어로, 짧게. 결정과 결과 위주.
- 코드 변경 후 길게 요약하지 말 것 — diff가 이미 보임.
