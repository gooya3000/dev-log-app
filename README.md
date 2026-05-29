# DevLog — Claude Code로 만드는 개발 회고 앱

> **"Claude Code로 개인 개발 회고 앱 만들기: 서브에이전트와 문서 주도 개발 실습기"**

이 리포는 Claude Code의 **서브에이전트 분업 워크플로우**와 **문서 주도 개발(PLAN.md를 단일 설계 출처로 삼는 방식)**을 실제 프로젝트에서 어떻게 적용하는지 보여주는 실습 기록이다.

앱 자체는 개인용 개발 회고 도구다. 하루 회고를 구조화해 암호화된 JSON 파일로 보관하고, Gemini LLM으로 기술 블로그 초안을 자동 생성한다.

---

## 이 프로젝트에서 실습한 것

### 문서 주도 개발
- `docs/PLAN.md`를 **단일 설계 출처**로 유지 — 기능 범위, 화면, 패키지 구조, 암호화 정책, 키 정책이 모두 여기서 결정된다
- 코드와 PLAN.md가 충돌하면 코드가 아니라 PLAN.md를 먼저 갱신한다
- `README.md`는 GitHub 표시용, `CLAUDE.md`는 Claude Code 행동 지침용으로 역할을 분리

### 서브에이전트 분업
단일 LLM 세션에 모든 역할을 맡기는 대신, 역할별 전문 에이전트를 분리해 파이프라인으로 연결했다.

```
contract-designer  →  [사용자 확인]
    ├─ contract-implementer   (본문 구현)   ─┐ 병렬
    └─ test-engineer          (테스트 작성) ─┘ isolation=worktree
                              ↓ [사용자 확인]
                        test-verifier  (실행 + 실패 분류)
                              ↓ [Phase 묶음 종료 시]
                         code-reviewer (보안·설계 적합성 감사)
```

각 에이전트는 `.claude/agents/*.md`에 역할·권한·트리거를 명세한다. 에이전트가 설계 문서를 자가 수정하지 못하도록 역할 경계를 명확히 한다.

### 보안 설계를 코드에 강제하기
단순 CRUD가 아니라 **시크릿 제로 커밋**, **DEK 래핑**, **권한 분리**를 실제로 구현하면서 "LLM이 보안 규칙을 얼마나 일관성 있게 지키는가"를 검증한다.

---

## 앱 기능

| 기능 | 설명 |
|---|---|
| 셀프 가입 | userId + passphrase → 사용자별 DEK 발급, user/admin 두 사본 저장 |
| 회고 작성·수정·삭제 | 날짜·제목·태그·오늘 한 일·배운 것·이슈·내일 할 일·기분 |
| 회고 목록 | 날짜 내림차순, 제목·태그·요약 표시 |
| 블로그 초안 생성 | 회고 1건 → Gemini 2.5 Flash → Markdown. API 키는 폼 입력, 1회 사용 후 폐기 |
| 본인 passphrase 변경 | 옛 passphrase 검증 → `userWrappedDek`만 재발급, DEK·세션 유지 |
| 관리자 passphrase 재설정 | `adminWrappedDek`으로 DEK 회수 → 임시 passphrase 발급, DEK 불변 |

---

## 기술 스택

- **Java 21**, Spring Boot 3.x, Gradle
- Thymeleaf, Bootstrap 5, Spring Security 6
- Jackson, Lombok, spring-boot-starter-validation
- AI: `GeminiAiClient` (gemini-2.5-flash)
- 저장: 로컬 JSON 파일 (`data/logs/{userId}/{date}_{id}.json`)
- 테스트: JUnit 5, MockMvc, WireMock

---

## 보안 설계

- **AES-256-GCM**: 회고 평문은 디스크에 닿지 않음. `VaultCipher` 통과 ciphertext만 저장
- **DEK 래핑 구조**: DEK는 user 키 / admin 키로 각각 wrap해 두 사본 보관
- **KDF**: PBKDF2-HMAC-SHA256 (user: K+H 64바이트 분리, admin: 32바이트)
- **stateless 시크릿**: API 키·passphrase는 폼으로만 흐름, 저장소에 기록 없음
- **권한 분리**: `/vault/**` = ROLE_ADMIN, `/logs/**` = ROLE_USER (두 개 FilterChain)
- **ciphertext 커밋**: `data/`는 `.gitignore` 대상이 아님 — 암호화된 채로 리포에 포함

---

## 시작하기

### 설정

`src/main/resources/application-local.properties` 파일 생성:

```properties
devlog.admin.passphrase=여기에_관리자_passphrase_입력
```

### 실행

```bash
./gradlew bootRun
```

`http://localhost:8080` 접속 후 `/register` → `/unlock` → `/logs/new` 순서로 시작. 관리자 기능은 `/vault/login`.

### 테스트

```bash
./gradlew test
```

AI 어댑터는 WireMock stub으로 실제 API 호출 없이 테스트.
