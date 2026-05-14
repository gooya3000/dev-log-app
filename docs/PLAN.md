# DevLog 프로젝트 계획

개인용 개발 회고 앱. 하루 회고를 구조화해 JSON으로 보관하고, 나중에 LLM을 통해 기술 블로그 초안으로 재활용한다.

---

## 1. MVP 기능

핵심 기능을 좁게 잡고, "회고 → 블로그 초안"이라는 한 줄짜리 사용자 가치만 끝까지 동작하게 한다.

### 1.1 필수 (MVP)

| 기능 | 설명 |
|---|---|
| 회고 작성 | 폼에서 날짜·제목·본문 항목들을 입력해 JSON 1개로 저장 |
| 회고 목록 | 날짜 내림차순, 제목·태그·요약 노출 |
| 회고 상세 | 저장된 JSON을 그대로 렌더링 |
| 회고 수정 | 동일 파일 덮어쓰기, `updatedAt` 갱신 |
| 회고 삭제 | 파일 단위 삭제 (휴지통 X) |
| 블로그 초안 생성 | 회고 1건을 프롬프트에 실어 LLM에 요청 → Markdown 결과 화면에 표시. **API 키는 매 요청 시 폼으로 입력**, 서버는 1회 사용 후 즉시 폐기 |

### 1.2 회고 데이터 항목

- `id` (ULID 또는 UUID)
- `date` (YYYY-MM-DD) — 회고 일자
- `title`
- `tags` (List\<String\>)
- `whatIDid` — 오늘 한 일
- `whatILearned` — 배운 것
- `problems` — 막힌 부분/이슈
- `tomorrow` — 내일 할 일
- `mood` (선택, GOOD/SOSO/BAD 같은 enum)
- `createdAt`, `updatedAt`

### 1.3 NON-GOAL (MVP에서 의도적으로 제외)

- 사용자 인증, 다중 사용자
- 검색 인덱스, 풀텍스트 검색 (목록 페이지의 단순 필터까지만)
- 이미지 첨부
- 자동 동기화, 클라우드 백업
- 마크다운 WYSIWYG 에디터 (textarea + 미리보기 정도)

---

## 2. 화면 구성

Thymeleaf 5개 화면이면 MVP는 충분하다.

| URL | 메서드 | 화면 / 동작 |
|---|---|---|
| `GET /` | GET | 목록 페이지로 리다이렉트 |
| `GET /logs` | GET | 회고 목록 (날짜 내림차순, 태그 필터) |
| `GET /logs/new` | GET | 신규 작성 폼 |
| `POST /logs` | POST | 저장 후 `/logs/{id}`로 리다이렉트 |
| `GET /logs/{id}` | GET | 상세 보기 + "블로그 초안 생성" 버튼 |
| `GET /logs/{id}/edit` | GET | 수정 폼 |
| `POST /logs/{id}` | POST | 수정 저장 |
| `POST /logs/{id}/delete` | POST | 삭제 |
| `POST /logs/{id}/blog-draft` | POST | 폼 데이터에 `apiKey`(password 필드) 포함, LLM 호출 후 `blog-draft.html`로 결과 표시. 응답 직후 키 변수는 메서드 스코프 종료와 함께 폐기 |

### 템플릿 파일

```
src/main/resources/templates/
├── layout.html          // 공통 헤더/푸터 fragment
├── logs/
│   ├── list.html
│   ├── form.html        // new/edit 공용
│   ├── detail.html
│   └── blog-draft.html  // 생성된 마크다운 + 복사 버튼
└── error.html
```

---

## 3. 패키지 구조

`com.example.devlogapp` 하위에 레이어드 + 기능별로 얕게 나눈다. 개인 프로젝트라 과한 모듈화는 피한다.

```
com.example.devlogapp
├── DevLogAppApplication
│
├── config
│   ├── JacksonConfig            // ObjectMapper 빈 (JavaTimeModule 등)
│   └── StorageProperties        // @ConfigurationProperties("devlog.storage")
│
├── domain
│   ├── DevLog                   // 회고 도메인 모델 (불변에 가깝게)
│   ├── DevLogId                 // value object (선택)
│   └── Mood                     // enum
│
├── storage                      // 파일 I/O 책임
│   ├── DevLogRepository         // 인터페이스
│   └── JsonDevLogRepository     // 구현 (디렉토리 스캔 + atomic write)
│
├── service
│   ├── DevLogService            // 도메인 유스케이스
│   └── BlogDraftService         // LLM 호출 오케스트레이션
│
├── ai
│   ├── AiClient                 // 인터페이스 — generate(prompt, apiKey): String
│   ├── ClaudeAiClient           // 어댑터 (Claude). 키는 호출 인자로만 받음
│   ├── OpenAiAiClient           // 어댑터 (OpenAI). 둘 중 하나만 우선 구현
│   └── prompt
│       └── BlogPromptBuilder    // DevLog → 프롬프트 텍스트
│
├── web
│   ├── LogController            // CRUD 화면
│   ├── BlogDraftController      // 블로그 변환
│   └── form
│       ├── DevLogForm           // 폼 바인딩 + @Valid
│       └── BlogDraftForm
│
└── util
    └── Ids                      // ULID/UUID 발급
```

설계 노트:
- `storage`는 인터페이스로 분리해서 나중에 다른 백엔드(SQLite 등)로 교체할 여지를 둔다.
- `ai` 모듈은 어댑터 패턴. **API 키는 빈/필드/설정 어디에도 두지 않는다.** 컨트롤러가 폼에서 `String apiKey`를 받아 서비스 → 어댑터에 인자로 전달하는 stateless 흐름. 자세한 정책은 §5 참조.

---

## 4. JSON 저장 정책

### 4.1 저장 위치

- 기본 경로: `./data/logs/`
- `application.properties`에서 변경 가능
  ```properties
  devlog.storage.root=./data/logs
  ```
- 앱 시작 시 디렉토리 자동 생성. `.gitignore`에 `data/` 추가.

### 4.2 파일 1개 = 회고 1개

- 파일명: `{date}_{id}.json` — 예: `2026-05-14_01HXXXXXXXXXXXXXXXXXXXXXX.json`
  - 디렉토리 정렬만으로 자연스럽게 최신순 노출.
  - `id`를 파일명에 포함해 같은 날짜 여러 건도 충돌하지 않음.
- 파일 내부 스키마 (예시):
  ```json
  {
    "id": "01HXXXXXXXXXXXXXXXXXXXXXX",
    "date": "2026-05-14",
    "title": "Spring Boot에서 JSON 파일 저장소 만들기",
    "tags": ["spring", "jackson", "io"],
    "whatIDid": "...",
    "whatILearned": "...",
    "problems": "...",
    "tomorrow": "...",
    "mood": "GOOD",
    "createdAt": "2026-05-14T22:10:33+09:00",
    "updatedAt": "2026-05-14T22:10:33+09:00",
    "schemaVersion": 1
  }
  ```
- `schemaVersion`을 처음부터 둬서 나중에 마이그레이션 여지를 둔다.

### 4.3 동시성·안정성

- **atomic write**: `Files.writeString(tmp, ...)` 후 `Files.move(tmp, target, ATOMIC_MOVE)`. 쓰는 도중 크래시가 나도 기존 파일이 깨지지 않는다.
- **읽기**: 매번 디렉토리 스캔 + 파일 파싱. 개인용이라 수백~수천 건까지는 충분히 빠르다. 느려지면 메모리 캐시(`Map<Id, DevLog>`) 추가.
- **잠금**: 단일 프로세스 가정. 파일 락은 도입하지 않는다.
- **인코딩**: UTF-8 고정.
- **Jackson 설정**: `JavaTimeModule` + `WRITE_DATES_AS_TIMESTAMPS=false` + `SerializationFeature.INDENT_OUTPUT=true` (사람이 직접 열어볼 수 있게 pretty-print).

### 4.4 ID 발급

- ULID 권장 (시간순 정렬 + 충돌 회피). 외부 라이브러리 추가가 싫으면 UUIDv7 또는 `System.currentTimeMillis() + 랜덤` 조합.

---

## 5. API 키 / 시크릿 정책

이 리포는 **GitHub 공개 예정**이다. 어떤 시크릿도 리포에 들어가지 않는다.

### 5.1 키는 매 요청마다 폼으로 받는다

- 블로그 초안 생성 화면(`detail.html`)에 `<input type="password" name="apiKey">` 필드를 둔다.
- `POST /logs/{id}/blog-draft` 요청 시 폼 데이터로 함께 전송된다.
- 컨트롤러 → 서비스 → 어댑터까지 메서드 인자로만 전달된다. 필드, 빈, 캐시, 세션, 로그 어디에도 저장하지 않는다.
- 어댑터는 외부 LLM 호출에 헤더로 실어 보내고, 메서드가 끝나면 GC 대상이 된다.

### 5.2 application.properties에 두지 않을 것

- `anthropic.api-key=...` 같은 줄을 **절대 만들지 않는다.**
- `application.properties`에는 키 관련 환경변수 placeholder조차 두지 않는다 — 두면 "환경변수에 키 있으면 자동으로 쓰는" 동작이 추가돼서 이번 단계 정책(매 요청 입력)이 깨진다.

### 5.3 코드 / 로그에서 키 다루는 원칙

- DTO/Form 클래스의 `apiKey` 필드는 `@ToString.Exclude` (Lombok) 또는 직접 `toString` override로 로그에 찍히지 않게 한다.
- 컨트롤러·서비스 입출력 로깅에서 `apiKey`를 명시적으로 마스킹/제외.
- 예외 메시지에 키 원문이 포함되지 않게 — 외부 HTTP 호출 실패 시 응답 본문을 그대로 던지지 말고, 상태 코드와 짧은 메시지만 노출.

### 5.4 `.gitignore`에 미리 넣을 항목

```
data/
.env
.env.local
**/application-local.properties
```

### 5.5 사용자 안내 (README에 적을 것)

- 블로그 초안 생성 시 본인의 Claude/OpenAI API 키를 폼에 직접 입력해야 한다.
- 입력한 키는 브라우저 → 서버 → LLM 으로 1회 전달만 되고, 서버는 저장하지 않는다.
- 로컬에서만 띄워서 사용할 것을 권장 (HTTPS 없이 외부 노출하면 키가 평문으로 흐를 수 있음).

---

## 6. 서브에이전트 작업 분리 계획

서브에이전트는 **컨텍스트가 좁고, 산출물이 독립적이며, 검증 기준이 명확한** 작업에 강하다. 그 기준으로 작업을 쪼갠다.

### 6.1 의존 관계 (DAG)

```
[Phase 0] 빌드/설정 정비
        │
        ▼
[Phase 1] 도메인 + 저장소  ──┐
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
[Phase 2-A] 웹 CRUD     [Phase 2-B] AI 클라이언트   [Phase 2-C] 저장소 테스트
        │                     │                     │
        └─────────────────────┼─────────────────────┘
                              ▼
                  [Phase 3] 블로그 변환 화면 통합
                              │
                              ▼
                       [Phase 4] E2E 점검
```

### 6.2 단계별 서브에이전트 위임 표

| Phase | 서브에이전트 역할 | 인풋 (브리핑에 포함할 것) | 아웃풋 / Done 기준 |
|---|---|---|---|
| 0 | **빌드 정비** | 현 `build.gradle`, 추가할 의존성(`spring-boot-starter-validation`), JSON 저장 경로 | `build.gradle` 수정, `application.properties`에 `devlog.storage.root` 추가, `.gitignore`에 `data/` 추가 |
| 1 | **도메인+저장소 구현** | 본 문서 §1.2, §3, §4 전체 | `DevLog`, `DevLogRepository`, `JsonDevLogRepository`. `JsonDevLogRepositoryTest`에서 save → findAll → findById → update → delete round-trip 통과 |
| 2-A | **웹 레이어 구현** | 본 문서 §2, 그리고 §3의 web 패키지 구조 | 5개 Thymeleaf 템플릿 + 컨트롤러 + 폼 검증. `MockMvc` 슬라이스 테스트로 200/302 확인 |
| 2-B | **AI 어댑터 구현** | §3의 ai 패키지 + §5 키 정책 (필드/빈 저장 금지, 인자 전달만) + "DevLog 1건 → 블로그용 마크다운" 변환 사양 | `AiClient` 인터페이스(`generate(prompt, apiKey)`), Claude **또는** OpenAI 어댑터 1개, `BlogPromptBuilder`. 어댑터 단위 테스트는 WireMock 또는 fake. **키가 로그/예외에 노출되지 않는지 확인하는 테스트 포함** |
| 2-C | **저장소 보강 테스트** | atomic write·동시 read·UTF-8·`schemaVersion` 누락 케이스 | 추가 단위 테스트들 |
| 3 | **블로그 변환 화면 통합** | Phase 2-A, 2-B 산출물 | `BlogDraftController` + `blog-draft.html`, "복사" 버튼, 통합 테스트 1개 |
| 4 | **E2E 점검** | (사람이 직접) `./gradlew bootRun` 후 브라우저로 검증 | 수동 체크리스트 통과 |

### 6.3 서브에이전트에 브리핑할 때 지킬 규칙

- **한 번에 한 Phase만 위임한다.** 여러 단계를 통째로 맡기면 중간 의사결정이 묻힌다.
- **Phase 2-A / 2-B / 2-C는 병렬로 위임 가능하다** (인터페이스 합의가 Phase 1에서 끝나 있기 때문). 의존성이 없는 작업은 같은 메시지에서 동시에 띄운다.
- 각 위임에 반드시 포함할 것:
  1. 이 문서의 어느 절(§)을 따르는지
  2. 변경해도 되는 파일 / 건드리지 말아야 할 파일
  3. "Done" 판정 기준 (어떤 테스트가 통과해야 하는가)
  4. 응답 길이 제한 (긴 설명 대신 변경 요약)
- **이해는 위임하지 않는다.** "알아서 잘 해줘"가 아니라 "이 인터페이스, 이 시그니처, 이 테스트를 통과시켜줘"로 좁힌다.
- **검증은 메인 세션에서.** 서브에이전트의 "완료했습니다"는 의도이지 사실이 아니므로, 메인에서 diff와 테스트 결과를 직접 본다.

### 6.4 어떤 에이전트 타입을 쓸까

- 코드 탐색·"어디 정의돼 있나?" 류 → `Explore`
- 설계 결정·트레이드오프 정리 → `Plan`
- 실제 구현·테스트 작성 → `general-purpose`
- 보안/취약점 점검 (후반) → `/security-review` 스킬

---

## 7. 다음 액션 (제안)

1. 본 문서 §1~§5에 대한 최종 사용자 컨펌. (AI 키 처리 = 옵션 A: 매 요청 폼 입력으로 확정됨.)
2. **Phase 0 (빌드 정비)** — 메인 세션에서 직접 처리. 변경:
   - `build.gradle`에 `spring-boot-starter-validation` 추가
   - `application.properties`에 `devlog.storage.root=./data/logs` 추가
   - `.gitignore`에 `data/`, `.env`, `application-local.properties` 추가
3. **Phase 1 (도메인 + 저장소)** — 서브에이전트에 위임. 인터페이스가 후속 모든 작업의 계약이라서 단독으로 먼저 끝낸다.
4. Phase 1 머지 후, **Phase 2-A · 2-B · 2-C 병렬 위임.** 한 메시지에서 동시에 띄운다.
5. **Phase 3**: 메인 세션에서 통합 (어댑터 + 컨트롤러 와이어링은 의사결정이 섞여 있어 서브에이전트에 맡기지 말 것).
6. **Phase 4**: 메인 세션이 `./gradlew bootRun` 후 직접 브라우저 검수.
