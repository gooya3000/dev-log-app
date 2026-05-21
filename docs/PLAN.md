# DevLog 프로젝트 계획

개인용 개발 회고 앱. 하루 회고를 구조화해 JSON으로 보관하고, 나중에 LLM을 통해 기술 블로그 초안으로 재활용한다.

---

## 1. MVP 기능

핵심 기능을 좁게 잡고, "회고 → 블로그 초안"이라는 한 줄짜리 사용자 가치만 끝까지 동작하게 한다.

### 1.1 필수 (MVP)

| 기능 | 설명 |
|---|---|
| 시스템 첫 부팅 | `application-local.properties` 의 admin passphrase fail-fast 검증 + `adminSalt` 발급 (§4.5.6). DEK 는 부팅 시 만들지 않음 — 사용자 가입 시점에 사용자별로 생성 |
| 관리자 로그인 | `/vault/login` 에서 admin passphrase 입력 → Spring Security ROLE_ADMIN 세션 (§5.7) |
| **사용자 셀프 가입** | `/register` 에서 userId + passphrase 입력 → `DEK_user` 랜덤 발급 → `userWrappedDek` (K_user) + `adminWrappedDek` (K_admin) 두 사본 저장 → `users[]` 엔트리 추가 (§4.5.6) |
| 사용자 로그인 | `/unlock` 에서 사용자 passphrase 입력 → `H_user` 검증 → `userWrappedDek` unwrap 으로 `DEK_user` 획득 → ROLE_USER 세션 (§5.6) |
| 사용자 passphrase 재설정 | 관리자 모드에서 `/vault/users/{id}/reset-passphrase` → `adminWrappedDek` 로 `DEK_user` 회수 → 새 passphrase 로 `userWrappedDek` 만 재발급. `DEK_user` 그대로 (§4.5.6) |
| 사용자 삭제 | 관리자 모드에서 `users[]` 엔트리 + `data/logs/{userId}/` 디렉토리 일괄 제거 |
| 회고 작성 | 폼에서 날짜·제목·본문 항목들을 입력 → 평문 JSON 직렬화 → AES-256-GCM 암호화 → 파일 1개로 저장 |
| 회고 목록 | 날짜 내림차순, 제목·태그·요약 노출. 매 페이지 로드 시 메모리 마스터키로 전체 복호화 후 in-memory 정렬·필터 |
| 회고 상세 | 파일 1개 복호화 → 렌더링 |
| 회고 수정 | 동일 파일 덮어쓰기, `updatedAt` 갱신. 새 nonce로 재암호화 |
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

- 사용자 본인의 자율 passphrase 변경 (옛 passphrase 알고 새 passphrase 로 회전) — 잊으면 admin reset 으로 대체
- 가입 차단/초대 코드 (오픈 가입). 학습용 로컬 가정.
- 이메일 인증/비밀번호 복구 메일
- 검색 인덱스, 풀텍스트 검색 (목록 페이지의 단순 필터까지만)
- 이미지 첨부
- 자동 동기화, 클라우드 백업
- 마크다운 WYSIWYG 에디터 (textarea + 미리보기 정도)

---

## 2. 화면 구성

Spring Security `SecurityFilterChain` 으로 URL 별 권한이 결정된다. 권한 미달 시 자동 리다이렉트 (`/logs/**` 미인증 → `/unlock`, `/vault/**` 미인증 → `/vault/login`).

| URL | 메서드 | 권한 | 화면 / 동작 |
|---|---|---|---|
| `GET /` | GET | ROLE_USER | `/logs` 리다이렉트 |
| `GET /register` | GET | permitAll | 셀프 가입 폼 (userId + passphrase + passphrase 확인) |
| `POST /register` | POST | permitAll | userId 중복 확인 → `DEK_user` 발급 → `userWrappedDek` + `adminWrappedDek` 두 사본 저장 → `users[]` 추가 → `/unlock?registered` (가입 완료, 로그인 안내) |
| `GET /unlock` | GET | permitAll | 사용자 passphrase 입력 폼 (`?registered` 쿼리 시 안내 노출) |
| `POST /unlock` | POST | permitAll | `H_user` 검증 → `K_user` 로 `userWrappedDek` unwrap → `DEK_user` 메모리 적재 (+ userId 컨텍스트) → ROLE_USER 세션 → `/logs` |
| `POST /logout` | POST | ROLE_USER | 세션 종료, `DEK_user` 메모리에서 제거 → `/unlock` |
| `GET /logs` | GET | ROLE_USER | 본인 회고 목록 (`data/logs/{userId}/` 만 스캔) |
| `GET /logs/new` | GET | ROLE_USER | 신규 작성 폼 |
| `POST /logs` | POST | ROLE_USER | 저장 후 `/logs/{id}` |
| `GET /logs/{id}` | GET | ROLE_USER | 상세 + "블로그 초안 생성" 버튼 |
| `GET /logs/{id}/edit` | GET | ROLE_USER | 수정 폼 |
| `POST /logs/{id}` | POST | ROLE_USER | 수정 저장 |
| `POST /logs/{id}/delete` | POST | ROLE_USER | 삭제 |
| `POST /logs/{id}/blog-draft` | POST | ROLE_USER | 폼에 `apiKey` 포함, LLM 호출 후 결과 표시. 키는 1회 사용 후 폐기 |
| `GET /vault/login` | GET | permitAll | 관리자 passphrase 입력 폼 |
| `POST /vault/login` | POST | permitAll | properties 의 admin passphrase 와 constant-time 비교 → ROLE_ADMIN 세션 → `/vault/users` |
| `POST /vault/logout` | POST | ROLE_ADMIN | 관리자 세션 종료 → `/vault/login` |
| `GET /vault/users` | GET | ROLE_ADMIN | 사용자 목록 (id, 가입 시각 메타만 표시 — passphraseHash·wrappedDek 노출 X) |
| `GET /vault/users/{id}/reset-passphrase` | GET | ROLE_ADMIN | 재설정 확인 화면 |
| `POST /vault/users/{id}/reset-passphrase` | POST | ROLE_ADMIN | `adminWrappedDek` 로 `DEK_user` 회수 → 새 임시 passphrase 발급 → `userWrappedDek` + salt + passphraseHash 만 교체. `DEK_user`·`adminWrappedDek` 그대로 → `/vault/users/{id}/reset-result` (1회 노출) |
| `GET /vault/users/{id}/reset-result` | GET | ROLE_ADMIN | 새 임시 passphrase 1회 표시 |
| `POST /vault/users/{id}/delete` | POST | ROLE_ADMIN | `users[]` 엔트리 제거 + `data/logs/{userId}/` 디렉토리 통째 삭제 |

### 템플릿 파일

```
src/main/resources/templates/
├── layout.html                       // 공통 fragment (sec:authorize 태그 활용)
├── register.html                     // 셀프 가입 폼
├── unlock.html                       // 사용자 로그인 (?registered 시 안내 노출)
├── logs/
│   ├── list.html
│   ├── form.html                     // new/edit 공용
│   ├── detail.html
│   └── blog-draft.html               // 생성된 마크다운 + 복사 버튼
├── vault/
│   ├── login.html                    // 관리자 로그인
│   └── users/
│       ├── list.html                 // 사용자 목록 (id, 가입 시각만)
│       ├── confirm-reset.html        // 재설정 확인
│       └── reset-result.html         // 새 임시 passphrase 1회 표시
└── error.html
```

> **제거됨** (셀프 가입 모델로 전환): `vault/users/form.html` (관리자가 사용자 생성하는 폼), `vault/users/created.html` (관리자가 본 초기 passphrase).

> **Thymeleaf-Spring Security 통합**: `layout.html` 헤더에 `<div sec:authorize="hasRole('USER')">`, `<div sec:authorize="hasRole('ADMIN')">` 식으로 권한별 메뉴 노출. `thymeleaf-extras-springsecurity6` 의존성 포함.

---

## 3. 패키지 구조

`com.example.devlogapp` 하위에 레이어드 + 기능별로 얕게 나눈다. 개인 프로젝트라 과한 모듈화는 피한다.

```
com.example.devlogapp
├── DevLogAppApplication
│
├── config
│   ├── JacksonConfig            // ObjectMapper 빈 (JavaTimeModule 등)
│   ├── StorageProperties        // @ConfigurationProperties("devlog.storage")
│   └── AdminProperties          // @ConfigurationProperties("devlog.admin") — passphrase, @ToString.Exclude
│
├── security                     // Spring Security 통합 (§5.7)
│   ├── SecurityConfig           // SecurityFilterChain — /vault/** = ADMIN, /logs/** = USER
│   │                            //   Phase 1: 단일 FilterChain + admin formLogin 만 구성 (임시)
│   │                            //   Phase 2-A TODO: ① admin/user 두 FilterChain 분리,
│   │                            //                   ② /unlock CSRF ignoring 제거 (Thymeleaf _csrf 토큰)
│   ├── AdminAuthenticationProvider  // admin passphrase 검증 + adminWrappedDek sanity unwrap
│   ├── UserAuthenticationProvider   // user passphrase 검증 + DEK unwrap → Vault 적재
│   └── CurrentSession           // 현재 세션의 권한·userId 컨텍스트 헬퍼
│
├── domain
│   ├── DevLog                   // 회고 도메인 모델 (불변에 가깝게)
│   ├── DevLogId                 // value object (선택)
│   ├── Mood                     // enum
│   ├── UserId                   // 사용자 식별자
│   └── User                     // 사용자 메타 (id, salt, passphraseHash, userWrappedDek, adminWrappedDek)
│
├── storage                      // 파일 I/O 책임
│   ├── DevLogRepository         // 인터페이스 (사용자 컨텍스트 인식 — data/logs/{userId}/ 스캔)
│   ├── JsonDevLogRepository     // 구현 (사용자 디렉토리 스캔 + atomic write + VaultCipher 통과)
│   └── VaultMetaRepository      // .vault-meta.json 읽기/쓰기 (adminSalt + users[])
│
├── vault                        // 암호화 핵심 (§4.5)
│   ├── Vault                    // 메모리에 DEK + 컨텍스트(userId) 보관 (스코프: 세션 or 애플리케이션)
│   ├── VaultCipher              // AES-256-GCM encrypt/decrypt (파일 envelope용)
│   ├── KeyWrapper               // DEK wrap/unwrap (AES-GCM)
│   ├── PassphraseKdf            // PBKDF2-HMAC-SHA256 (admin: 32바이트 / user: 64바이트로 K + H 분리)
│   ├── EnvelopeV1               // 파일 envelope 직렬화 포맷 (v/alg/nonce/ct)
│   ├── VaultMeta                // .vault-meta.json 도메인 모델
│   ├── VaultLockedException
│   └── UserNotFoundException
│
├── service
│   ├── DevLogService            // 도메인 유스케이스 (사용자 컨텍스트 인식)
│   ├── VaultBootstrapService    // 첫 부팅 시 admin passphrase fail-fast + adminSalt 발급 (DEK 만들지 않음)
│   ├── UserRegistrationService  // 셀프 가입 — DEK_user 생성 + userWrappedDek + adminWrappedDek 두 사본 저장
│   ├── UserAdminService         // 사용자 reset/delete (ROLE_ADMIN 전용). 생성 책임은 UserRegistrationService 로 이관
│   ├── UserAuthService          // 사용자 로그인 (passphrase 검증 + userWrappedDek unwrap)
│   └── BlogDraftService         // LLM 호출 오케스트레이션
│
├── ai
│   ├── AiClient                 // 인터페이스 — generate(prompt, apiKey): String
│   ├── GeminiAiClient           // 어댑터 (Google Gemini generateContent). 키는 호출 인자로만 받음
│   └── prompt
│       └── BlogPromptBuilder    // DevLog → 프롬프트 텍스트
│
├── web
│   ├── LogController            // /logs/**
│   ├── BlogDraftController      // /logs/{id}/blog-draft
│   ├── AuthController           // /unlock, /logout (사용자 로그인)
│   ├── RegisterController       // /register (셀프 가입)
│   ├── AdminAuthController      // /vault/login, /vault/logout (관리자 로그인)
│   ├── AdminUserController      // /vault/users/** (목록·reset·delete 만)
│   └── form
│       ├── DevLogForm           // @Valid 폼
│       ├── UserUnlockForm       // passphrase 1개 (@ToString.Exclude)
│       ├── AdminLoginForm       // admin passphrase 1개 (@ToString.Exclude)
│       ├── UserRegisterForm     // userId + passphrase + passphraseConfirm (@ToString.Exclude)
│       ├── ResetPassphraseForm  // 새 임시 passphrase (@ToString.Exclude)
│       └── BlogDraftForm        // apiKey 포함 (@ToString.Exclude)
│
└── util
    └── Ids                      // ULID/UUID 발급
```

설계 노트:
- `storage` 와 `vault` 는 의도적으로 분리. `JsonDevLogRepository` 는 항상 `VaultCipher` 통과 → ciphertext 만 디스크에 기록. 평문은 메서드 스코프 안에서만 존재.
- `security` 패키지는 Spring Security 통합 영역. 인증 흐름(passphrase → KDF → DEK unwrap → SecurityContext 채우기)이 여기 있어 vault 와 web 사이의 경계 역할.
- `ai` 모듈은 어댑터 패턴. **API 키·passphrase·복구 관련 모든 비밀**은 빈/필드/설정 어디에도 두지 않는다(admin passphrase 만 예외 — `AdminProperties` 빈에 평문 상주, §5.7). 컨트롤러가 폼에서 받아 서비스 → 도메인까지 메서드 인자로만 전달하는 stateless 흐름. 자세한 정책은 §5.

---

## 4. JSON 저장 정책

### 4.1 저장 위치

- 기본 루트: `./data/logs/`
- **사용자별 서브디렉토리**: `./data/logs/{userId}/` — 각 사용자의 회고가 이 안에 모임
- `application.properties`에서 루트 변경 가능
  ```properties
  devlog.storage.root=./data/logs
  ```
- 앱 시작 시 루트 디렉토리 자동 생성. 사용자 서브디렉토리는 셀프 가입(`/register`) 시점에 생성.
- **`data/`는 리포에 커밋된다.** 안에 들어가는 모든 파일은 §4.5에 따라 AEAD 암호화된 ciphertext이므로 공개 리포에 올라가도 안전. 평문 회고가 절대 디스크에 떨어지지 않게 하는 것이 핵심 invariant.
- 사용자 삭제 시 `data/logs/{userId}/` 디렉토리 통째 제거 (§4.5.6 사용자 삭제 흐름).

### 4.2 파일 1개 = 회고 1개

- 경로: `data/logs/{userId}/{date}_{id}.json` — 예: `data/logs/alice/2026-05-14_01HXXXXXXXXXXXXXXXXXXXXXX.json`
  - 사용자 서브디렉토리 안에서 디렉토리 정렬만으로 최신순 노출.
  - `id`를 파일명에 포함해 같은 날짜 여러 건도 충돌하지 않음.
  - 사용자별 격리로 DEK 가 다른 다른 사용자 파일을 잘못 복호화 시도할 일 자체가 없음.
- 파일에 실제로 들어가는 것은 §4.5의 암호화 envelope이다. **암호화 전(평문)** DevLog JSON 스키마는 다음과 같다 — 이 형태로 직렬화한 뒤 AES-256-GCM으로 암호화해 envelope의 `ct` 필드에 base64로 인코딩해 넣는다.
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

### 4.5 암호화 저장 (Vault) — 사용자별 DEK + admin 마스터키 wrap

평문 회고가 디스크나 리포에 노출되지 않도록 모든 회고 파일은 AEAD로 암호화한다. 시스템에는 두 역할이 분리되어 있다:

- **관리자 (admin)**: `application-local.properties` 의 평문 passphrase 로 인증. `/vault/**` 접근 권한. **사용자별 `DEK_user` 의 admin 사본 보유** → passphrase 재설정·삭제 가능. 사용자 회고 평문 복호화도 기술적으로는 가능 (신뢰 가정).
- **사용자 (user)**: 본인 passphrase 로 인증. `/logs/**` 접근 권한. 본인 회고 CRUD. **다른 사용자 회고는 DEK 가 달라 접근 불가.**

> **세 종류의 비밀, 각자 책임**:
> - **Admin passphrase** — 관리자(=본인)가 `application-local.properties` 에 평문 하드코딩. `.gitignore` 보호. 분실 시 reset 기능 영구 상실 (단 사용자 본인은 자기 데이터 접근 계속 가능, §4.5.7).
> - **User passphrase** — 사용자가 머릿속/비번 매니저에 보관. 분실 시 관리자가 새 임시 passphrase 발급 (`adminWrappedDek` 우회로).
> - **DEK_user (Data Encryption Key)** — **사용자마다 1개**, 가입 시점에 `SecureRandom` 으로 발급. 메모리(`Vault` 빈)에 unlock 동안만 평문 존재. 디스크엔 항상 두 사본 (`userWrappedDek`, `adminWrappedDek`) 으로 wrap 되어 저장.

#### 4.5.1 결정사항 요약

| 항목 | 결정 |
|---|---|
| 키 구조 | **사용자별 DEK + 두 사본 wrap** — 각 사용자가 자기 `DEK_user`. `userWrappedDek` (K_user 로 wrap) + `adminWrappedDek` (K_admin 으로 wrap) 두 사본을 `users[]` 엔트리에 함께 저장 |
| 권한 분리 | Spring Security `SecurityFilterChain` — `/vault/**` = ROLE_ADMIN, `/logs/**` = ROLE_USER, `/register` = permitAll |
| 가입 경로 | 셀프 가입 (`POST /register`, permitAll) — 사용자가 직접 userId + passphrase 등록, 서버가 K_admin 자동 도출해서 `adminWrappedDek` 함께 발급 |
| Unlock 경로 | ① user passphrase (`/unlock`) → ROLE_USER + DEK_user 메모리 적재 / ② admin passphrase (`/vault/login`) → ROLE_ADMIN (DEK 적재 X) |
| User passphrase 검증 | PBKDF2 출력 64바이트 → 전반 32 = `K_user` (`userWrappedDek` unwrap 용), 후반 32 = `H_user` (저장된 hash 와 동등 비교) |
| Admin passphrase 보관 | `application-local.properties` 평문 하드코딩 (.gitignored). 환경변수 placeholder도 안 씀 |
| 잠금 단위 | DEK = 사용자 1명 (사용자 격리). 다른 사용자 회고는 같은 admin 으로도 별도 wrap 사본을 풀어야 봄 |
| 회고 저장 경로 | `data/logs/{userId}/{date}_{id}.json` — 사용자별 서브디렉토리 분리 |
| 암호화 범위 | **파일 내용만** (파일명·디렉토리명 평문 유지) |
| 세션 만료 | 명시 만료 없음 — 앱 종료 시 메모리 해제 |
| 헤더 메타 | **포함** — version / alg / kdf 식별자 |

#### 4.5.2 키 계층

```
admin passphrase                              user passphrase
(application-local.properties)                (폼 입력)
       │                                            │
       ▼ PBKDF2(adminSalt, 600k, 32)               ▼ PBKDF2(userSalt, 600k, 64)
   K_admin (256-bit)                                ├─ [0..32]  = K_user
       │                                            └─ [32..64] = H_user
       │                                                  │
       │ ┌────────────────────────────────────┐           │ ① H_user vs 저장된 passphraseHash
       │ │                                    │           │ ② 일치 시 K_user 로 unwrap
       ▼ ▼ AES-GCM unwrap                                 ▼ AES-GCM unwrap
   adminWrappedDek                                    userWrappedDek
       │                                                  │
       └──────────────┐                  ┌────────────────┘
                      ▼                  ▼
                   ┌─────────────────────────┐
                   │       DEK_user          │ ← 사용자마다 1개, 메모리에만
                   │       (32 bytes)        │   `userWrappedDek` 와 `adminWrappedDek`
                   └──────────┬──────────────┘   둘 다 unwrap 하면 같은 바이트열
                              │ AES-GCM (per-file 12바이트 nonce)
                              ▼
                  data/logs/{userId}/{date}_{id}.json (회고 ciphertext)
```

- **DEK_user** 는 셀프 가입 시 단 1회 발급. 그 사용자가 사는 동안 동일. **사용자마다 다름.**
- **passphrase 변경/재설정 시 DEK_user 자체는 그대로** — 해당 사용자의 `userWrappedDek` 만 새 K_user 로 다시 wrap. 회고 파일 재암호화 불필요.
- **DEK_user 는 가입 시점의 짧은 메모리 시간을 제외하면 평문으로 어디에도 안 떨어진다.** 이후엔 unlock 동안 `Vault` 빈에만.
- `adminWrappedDek` 와 `userWrappedDek` 는 **같은 DEK_user 의 두 ciphertext 사본**. 서로 다른 키·nonce 로 wrap 되어 ciphertext 자체는 완전히 다른 바이트열이지만, 각자 자기 키로 풀면 같은 32바이트 DEK_user 가 나옴.

#### 4.5.3 파일 구조

**`data/.vault-meta.json`** (평문 JSON 래퍼, 내부 ct 는 ciphertext, 리포에 커밋):
```json
{
  "v": 1,
  "kdf": "PBKDF2-HMAC-SHA256",
  "kdfIter": 600000,
  "adminSalt": "<base64 16바이트>",
  "users": [
    {
      "id": "alice",
      "salt": "<base64 16바이트>",
      "passphraseHash": "<base64 32바이트, PBKDF2 출력 후반부>",
      "createdAt": "2026-05-21T10:00:00+09:00",
      "userWrappedDek": {
        "alg": "AES-256-GCM",
        "nonce": "<base64 12바이트>",
        "ct": "<DEK_user 를 K_user 로 AES-GCM 암호화 + tag, base64>"
      },
      "adminWrappedDek": {
        "alg": "AES-256-GCM",
        "nonce": "<base64 12바이트>",
        "ct": "<같은 DEK_user 를 K_admin 으로 AES-GCM 암호화 + tag, base64>"
      }
    }
  ]
}
```

- `adminSalt` 는 top-level — admin passphrase 에서 `K_admin` 을 도출할 때 쓰는 단일 salt. 사용자마다 다르지 않음 (admin passphrase 가 1개니까).
- `users[]` 의 각 엔트리는 **자기 sault, 자기 hash, 자기 DEK_user 의 두 wrap 사본**을 들고 있음.
- userId 는 `users[]` 내에서 unique. 가입 시 중복 검사.

**`data/logs/{userId}/{date}_{id}.json`** (회고 envelope):
```json
{
  "v": 1,
  "alg": "AES-256-GCM",
  "nonce": "<base64 12바이트>",
  "ct": "<평문 DevLog JSON → DEK_user 로 AES-GCM 암호화 + tag, base64>"
}
```

#### 4.5.4 KDF · 키 처리

**Admin path:**
- `adminSalt` 는 첫 부팅 시 발급, `.vault-meta.json` top-level 에 보관
- `K_admin = PBKDF2-HMAC-SHA256(admin passphrase, adminSalt, 600,000, 32 bytes)`
- 다음 시점에 도출 → 즉시 사용 → 폐기:
  - 셀프 가입 (`adminWrappedDek` 생성용)
  - 사용자 passphrase 재설정 (`adminWrappedDek` unwrap → `DEK_user` 회수)
- admin 로그인 자체는 K_admin 도출 없이 `application-local.properties` 값과 constant-time 비교만 (DEK 작업이 없는 단순 권한 부여)

**User path:**
- `userSalt` 는 사용자 가입·재설정 시마다 새 발급
- PBKDF2 출력 **64바이트**:
  - `K_user = derivation[0..32]` (`userWrappedDek` wrap/unwrap 용 AES 키)
  - `H_user = derivation[32..64]` (인증용, 저장된 `passphraseHash` 와 비교)
- 로그인: `H_user` 비교(constant-time)로 1차 검증 → 일치 시 `K_user` 로 `userWrappedDek` unwrap → `DEK_user` 획득

**DEK_user:**
- 셀프 가입 시 `SecureRandom.nextBytes(32)` 로 발급
- 가입 직후 두 사본 (`userWrappedDek`, `adminWrappedDek`) 만들고 평문은 즉시 폐기
- 사용자 unlock 동안 `Vault` 빈에 `(userId, DEK_user)` 로 적재
- `POST /logout` 또는 앱 종료 시 메모리 zero-fill 후 해제

#### 4.5.5 암호화/복호화 (AEAD)

- 알고리즘: **AES-256-GCM** (`Cipher.getInstance("AES/GCM/NoPadding")`)
- nonce: 매 암호화마다 새 12바이트 random. **재사용 절대 금지.**
- tag: 16바이트 인증 태그, GCM 자동 부착. 잘못된 키 / 변조 시 복호화 실패.
- 회고 파일: 평문 DevLog JSON → UTF-8 bytes → `DEK_user` 로 GCM 암호화 → base64 → envelope `ct`
- DEK 래핑: 같은 `DEK_user` 32바이트 → `K_admin` 으로 한 번, `K_user` 로 한 번 → 각각 다른 nonce 로 GCM 암호화 → base64

#### 4.5.6 흐름

**첫 부팅 자동 bootstrap** (`.vault-meta.json` 없을 때):
```
앱 시작
  1. application-local.properties 의 devlog.admin.passphrase 읽기
     없거나 "CHANGE-ME-..." default 면 → 에러 로그 + 시작 중단 (fail-fast)
  2. adminSalt = SecureRandom.nextBytes(16)
  3. .vault-meta.json 저장 {v:1, kdf:"PBKDF2-HMAC-SHA256", kdfIter:600000,
                            adminSalt: base64(adminSalt), users: []}
  4. data/logs/ 루트 디렉토리 생성 (없으면)
  → 부팅 완료. users 가 비어 있어 admin login 또는 /register 만 가능.
```
> **이전 모델과 차이**: bootstrap 단계에서 DEK 를 만들지 않는다. DEK_user 는 사용자가 가입할 때 사용자별로 발급된다.

**관리자 로그인** (`POST /vault/login`):
```
  1. 폼에서 admin passphrase 입력
  2. application-local.properties 값과 constant-time 비교
       불일치 → 지연(~500ms) + 일반 에러
  3. Spring Security 컨텍스트에 ROLE_ADMIN 부여 → /vault/users 로
  4. 입력 passphrase 폐기
     (K_admin 은 이 시점엔 도출하지 않음 — 사용자 reset 등 DEK 작업이 필요할 때만 그 메서드 안에서 잠깐 도출 후 폐기)
```

**셀프 가입** (`POST /register`, permitAll):
```
  1. 폼: userId + passphrase + passphraseConfirm
     - passphrase != passphraseConfirm → 검증 에러
     - userId 형식 검증 (영문/숫자/밑줄, 길이 제한)
  2. users[] 에서 userId 중복 확인 → 중복이면 일반화된 "이미 사용 중" 에러
  3. DEK_user = SecureRandom.nextBytes(32)
  4. userSalt = SecureRandom.nextBytes(16)
  5. derivation = PBKDF2(passphrase, userSalt, 600_000, 64)
     K_user = derivation[0..32], H_user = derivation[32..64]
  6. userWrappedDek = AES-GCM(DEK_user, K_user, randomNonce1)
  7. adminSalt 읽기 → K_admin = PBKDF2(adminPassphrase, adminSalt, 600_000, 32)
  8. adminWrappedDek = AES-GCM(DEK_user, K_admin, randomNonce2)
  9. users[] 에 추가:
       {id, salt: userSalt, passphraseHash: H_user, createdAt,
        userWrappedDek, adminWrappedDek}
  10. .vault-meta.json 저장 (atomic write)
  11. data/logs/{userId}/ 디렉토리 생성
  12. DEK_user, K_user, K_admin, H_user, derivation, passphrase 모두 폐기
  → /unlock?registered (가입 완료, 로그인 안내)
```

**사용자 로그인** (`POST /unlock`):
```
  1. 폼: userId + passphrase
  2. users[] 에서 해당 id 조회 (없으면 지연 + 일반 에러 — userId 존재 여부 누출 차단)
  3. derivation = PBKDF2(passphrase, 엔트리의 userSalt, 600_000, 64)
  4. constant-time compare(derivation[32..64], 저장된 passphraseHash)
       불일치 → 지연 + 일반 에러
  5. K_user = derivation[0..32] → userWrappedDek unwrap → DEK_user 획득
  6. Vault 빈에 (userId, DEK_user) 적재 + Spring Security 에 ROLE_USER + userId 저장
  → /logs 로
  7. derivation, K_user, passphrase 폐기 (DEK_user 만 Vault 빈에 남음)
```

**사용자 passphrase 재설정** (`POST /vault/users/{id}/reset-passphrase`, ROLE_ADMIN 필요):
```
  1. ROLE_ADMIN 검증 (Spring Security)
  2. users[] 에서 대상 userId 엔트리 조회 (없으면 404)
  3. K_admin = PBKDF2(adminPassphrase, adminSalt, 600_000, 32)
  4. 해당 사용자의 adminWrappedDek unwrap → DEK_user 회수
  5. 새 임시 passphrase 결정 (admin 입력 또는 시스템 random 16자)
  6. newUserSalt = SecureRandom.nextBytes(16)
  7. newDerivation = PBKDF2(새 passphrase, newUserSalt, 600_000, 64)
  8. newUserWrappedDek = AES-GCM(DEK_user, newDerivation[0..32], newNonce)
  9. users[id] 엔트리에서 salt, passphraseHash, userWrappedDek 만 교체
       (adminWrappedDek 는 손 안 댐 — DEK_user 그대로니 admin 사본도 유효)
  10. .vault-meta.json 저장
  → /vault/users/{id}/reset-result (1회 노출) — 새 임시 passphrase 표시
  11. DEK_user, K_admin, newDerivation, 새 passphrase 모두 폐기
```
> **핵심**: 옛 K_user 를 사용하지 않는다 (옛 passphrase 를 모르니까). admin 마스터키가 우회로 역할.

**사용자 삭제** (`POST /vault/users/{id}/delete`, ROLE_ADMIN 필요):
```
  1. ROLE_ADMIN 검증
  2. users[] 에서 해당 엔트리 제거 → .vault-meta.json 저장
  3. data/logs/{userId}/ 디렉토리 안의 모든 파일 삭제 + 디렉토리 삭제
     (구현: Files.walkFileTree + visitFile 에서 delete, postVisitDirectory 에서 delete)
  → /vault/users (목록으로)
```

**`SecurityConfig`**: Spring Security `SecurityFilterChain` 빈으로 URL→권한 매핑:
- `/vault/login`, `/register`, `/unlock`, static → permitAll
- `/vault/**` → `hasRole("ADMIN")`
- `/logs/**`, `/` → `hasRole("USER")`
- CSRF 활성화 (관리자·일반 POST 모두 보호)
- 폼 로그인 커스텀 인증 핸들러 (passphrase → `userWrappedDek` unwrap → 인증 성공/실패)

#### 4.5.7 백업 · 복구 정책

- **본인 user passphrase + admin passphrase 모두 분실** → **해당 사용자만** 영구 손실. 다른 사용자엔 영향 없음. (사용자별 DEK 격리의 장점)
- **본인 user passphrase 만 분실** → ROLE_ADMIN 으로 들어가 재설정 (§4.5.6의 reset 흐름). 가장 흔한 케이스.
- **admin passphrase 만 분실** → user 본인 passphrase 만 알면 데이터 접근은 계속 가능. 다만 **reset 기능 영구 상실** + 신규 가입 시 `adminWrappedDek` 발급 불가 → 신규 가입도 사실상 막힘.
  - 회전 화면은 NON-GOAL. admin passphrase 잃지 않도록 비번 매니저 보관 강력 권장.
- `data/` 백업 = vault 백업. admin/user passphrase 는 별도 보관 필수.
- README 에 위 정책을 그대로 명시.

---

## 5. 시크릿 정책 (LLM API 키 + User Passphrase + Admin Passphrase)

이 리포는 **GitHub 공개 예정**이다. 어떤 시크릿도 리포에 들어가지 않는다.

### 5.1 키는 매 요청마다 폼으로 받는다

- 블로그 초안 생성 화면(`detail.html`)에 `<input type="password" name="apiKey">` 필드를 둔다.
- `POST /logs/{id}/blog-draft` 요청 시 폼 데이터로 함께 전송된다.
- 컨트롤러 → 서비스 → 어댑터까지 메서드 인자로만 전달된다. 필드, 빈, 캐시, 세션, 로그 어디에도 저장하지 않는다.
- 어댑터는 외부 LLM 호출에 헤더로 실어 보내고, 메서드가 끝나면 GC 대상이 된다.

### 5.2 application.properties에 두지 않을 것

- `gemini.api-key=...` / `anthropic.api-key=...` 같은 줄을 **절대 만들지 않는다.**
- `application.properties`에는 키 관련 환경변수 placeholder조차 두지 않는다 — 두면 "환경변수에 키 있으면 자동으로 쓰는" 동작이 추가돼서 이번 단계 정책(매 요청 입력)이 깨진다.

### 5.3 코드 / 로그에서 키 다루는 원칙

- DTO/Form 클래스의 `apiKey` 필드는 `@ToString.Exclude` (Lombok) 또는 직접 `toString` override로 로그에 찍히지 않게 한다.
- 컨트롤러·서비스 입출력 로깅에서 `apiKey`를 명시적으로 마스킹/제외.
- 예외 메시지에 키 원문이 포함되지 않게 — 외부 HTTP 호출 실패 시 응답 본문을 그대로 던지지 말고, 상태 코드와 짧은 메시지만 노출.

### 5.4 `.gitignore`에 미리 넣을 항목

```
.env
.env.local
**/application-local.properties
.claude/settings.local.json
```

**주의**: `data/`는 ignore 대상이 **아니다.** §4.5에 따라 ciphertext만 디스크에 떨어지므로 공개 리포에 안전하게 커밋된다.

### 5.5 사용자 안내 (README에 적을 것)

**최초 설정 (한 번):**
1. `application-local.properties.example` → `application-local.properties` 복사
2. `devlog.admin.passphrase` 를 강한 값으로 교체 (비밀번호 매니저에 보관)
3. 첫 부팅 시 시스템이 `adminSalt` 발급 후 `.vault-meta.json` 생성

**사용자 가입 (각자):**
1. `/register` 에서 userId + passphrase 입력 → 가입 완료
2. 시스템이 자동으로 `DEK_user` 발급 + 사용자/admin 두 사본 wrap 해서 저장
3. 가입 직후 `/unlock` 에서 본인 passphrase 로 로그인

**일상 사용:**
- 회고 작성: `/unlock` → 본인 passphrase → 정상 사용
- passphrase 잊었을 때: 관리자에게 요청 → `/vault/login` → 관리자가 `/vault/users/{id}/reset-passphrase` 실행 → 새 임시 passphrase 안내받고 즉시 로그인 후 변경 권장

**분실 시:**
- 본인 passphrase 만 분실 → 관리자 reset (가장 흔한 시나리오, 본인 회고 그대로 복구)
- 관리자 passphrase 만 분실 → 본인 로그인은 계속 가능하나 reset 기능·신규 가입 발급 불가 — admin 분실 안 하도록 유의
- 둘 다 분실 → **해당 사용자만** 영구 손실 (다른 사용자엔 영향 없음)

**보안 주의:**
- 블로그 초안 생성 시 본인의 Gemini API 키를 폼에 직접 입력. 입력한 키는 1회 사용 후 폐기. (키는 [Google AI Studio](https://aistudio.google.com/apikey) 에서 무료 발급)
- `application-local.properties` 는 `.gitignore` — 절대 커밋되지 않음을 매번 확인.
- 로컬에서만 띄워 사용 권장 (HTTPS 없이 외부 노출 금지).

### 5.6 User passphrase 처리

사용자 로그인 passphrase 는 §5.1~5.3 stateless 원칙을 그대로 따른다.

- 폼으로만 받음 (`POST /unlock`, `POST /vault/users` 초기 발급 시, `POST /vault/users/{id}/reset-passphrase` 재설정 시).
- 컨트롤러 → 서비스 → `PassphraseKdf` 까지 메서드 인자(`String passphrase`)로만 흐름.
- PBKDF2 출력 64바이트는 `K_user` (전반 32, wrap용) + `H_user` (후반 32, 인증용) 로 분리해서 **둘 다 사용 후 즉시 폐기**. 메모리에 남는 것은 DEK 만.
- `UserUnlockForm` / `UserCreateForm` / `ResetPassphraseForm` 의 `passphrase` 필드는 `@ToString.Exclude` 필수.
- 로그·예외 메시지에 passphrase · K_user · H_user · DEK 노출 금지.
- 잘못된 passphrase 응답은 일정 시간 (~500ms) 인위적 지연 + 일반화 메시지("로그인 실패")로 타이밍/존재 여부 누출 완화.
- **세션 만료 없음** (결정사항): unlock 후 앱 종료까지 DEK 메모리 유지. 사용자가 명시적 `POST /logout` 시 즉시 해제.

### 5.7 Admin passphrase 처리

관리자 passphrase 는 user passphrase 와 달리 **시스템에 평문으로 상주**한다 (`application-local.properties`). 이는 명시적으로 선택한 trade-off다.

**보관 위치:**
- `application-local.properties` (`.gitignore`)에 평문 하드코딩.
- 리포에는 `application-local.properties.example` (placeholder만) 만 커밋.
- `application.properties` (committed)에는 admin passphrase 관련 **항목 절대 없음** — 환경변수 placeholder 도 안 씀 (§5.2 원칙 유지).

**런타임 노출:**
- Spring 의 `@ConfigurationProperties("devlog.admin")` 로 `AdminProperties` 빈에 String 으로 로드.
- 이 빈은 **로그·toString·JSON 직렬화 금지** — `@ToString.Exclude` + Jackson `@JsonIgnore` 또는 직렬화기 제외.
- 평문 비교는 `MessageDigest.isEqual(a.getBytes(UTF_8), b.getBytes(UTF_8))` 같은 constant-time 비교.

**시작 시 검증 (fail-fast):**
- `devlog.admin.passphrase` 가 비어 있거나 `CHANGE-ME-...` default 값이면 **앱 시작 중단** (에러 로그 + `SpringApplication.exit(...)`).
- README 첫 줄에 "application-local.properties 만들고 admin passphrase 설정 후 실행" 안내.

**메모리 라이프타임:**
- 부팅부터 종료까지 `AdminProperties` 빈 안에 상주.
- 메모리 dump 시 노출 risk 존재 — 머신 보안에 의존하는 trade-off.
- 학습용 + 개인 머신 한정 가정 하에서 수용.

**금지 사항:**
- application.properties (committed) 에 절대 두지 않을 것.
- 로그·예외·HTTP 응답·세션·git diff 에 노출 금지. `git log -p` 에서 절대 보이면 안 됨.
- DTO/Form 의 admin passphrase 필드 (`AdminLoginForm.passphrase`) 는 `@ToString.Exclude` 필수.
- 잘못된 admin passphrase 응답은 user passphrase 와 같은 ~500ms 지연 + 일반화 메시지.

**위협 모델 한계:**
- 머신 통제권 잃으면 admin passphrase 즉시 노출 → 모든 사용자 매핑 재설정 가능.
- 다만 user passphrase 는 여전히 안전 → 데이터 직접 복호화는 별도 단계 필요.
- 학습용 개인 머신에 한정된 가정 하에서 acceptable.

---

## 6. 서브에이전트 작업 분리 계획

서브에이전트는 **컨텍스트가 좁고, 산출물이 독립적이며, 검증 기준이 명확한** 작업에 강하다. 그 기준으로 작업을 쪼갠다.

### 6.1 의존 관계 (DAG)

Phase 0~3 (구버전 — 공유 DEK 모델) 는 모두 머지된 상태. 사용자가 셀프 가입 모델로 방향을 틀면서 **재구축 Phase R 시리즈** 가 추가됨.

```
[Phase 0] 빌드/설정 정비                                  ✓ 완료
        │
        ▼
[Phase 1] 도메인 + 저장소 + vault + security (공유 DEK)  ✓ 완료 → 재구축 대상
        │
        ▼
[Phase 2-A/B/C] 웹 + AI + 저장소 테스트                  ✓ 완료
        │
        ▼
[Phase 3] 블로그 변환 화면 통합                          ✓ 완료
        │
        ▼
─────────── 여기서 모델 전환 (공유 DEK → 사용자별 DEK + admin wrap) ───────────
        │
        ▼
[Phase R-PLAN] PLAN.md 갱신                              ← 현재 작업
        │
        ▼
[Phase R-1] vault 모델 재구축                            (spring-backend)
   - domain/User (WrappedDek 2개), vault/VaultMeta (adminSalt + users[])
   - VaultBootstrapService (DEK 안 만듦), UserRegistrationService (신규)
   - UserAdminService (createUser 제거, reset/delete 재작성)
   - UserAuthService, Vault, JsonDevLogRepository (사용자별 경로)
   - 기존 vault/storage/service 테스트 새 모델로 마이그레이션
        │
        ▼
[Phase R-2] 웹 화면 재구축                                (spring-backend)
   - RegisterController + register.html (신규)
   - AdminUserController: /vault/users/new 제거, list/reset/delete 만
   - SecurityConfig: /register permitAll
   - 사용자 안내 (unlock?registered 등)
        │
        ▼
[Phase R-3] 통합 점검                                     (code-reviewer + 메인)
   - code-reviewer 리포트
   - 메인 세션: bootRun → 가입 → 로그인 → 회고 작성 → admin reset E2E
```

### 6.2 단계별 서브에이전트 위임 표

| Phase | 사용 에이전트 | 인풋 (브리핑에 포함할 것) | 아웃풋 / Done 기준 |
|---|---|---|---|
| 0 | (메인 세션 직접) | 현 `build.gradle`, 추가할 의존성, JSON 저장 경로 | ✓ 완료 |
| 1 | `spring-backend` | 본 문서 §1.2, §3, §4 (구버전 공유 DEK 모델), §5.6/§5.7 | ✓ 완료 (현재 재구축 대상) |
| 2-A | `spring-backend` | §2 (구버전 URL 표), §3 web, §5.6/§5.7 | ✓ 완료 (현재 재구축 대상) |
| 2-B | `ai-integration` | §3 ai 패키지 + §5 키 정책 | ✓ 완료 |
| 2-C | `test-engineer` | atomic write·동시 read·UTF-8·`schemaVersion` | ✓ 완료 |
| 3 | 메인 + `code-reviewer` | Phase 2-A/B 산출물 | ✓ 완료 |
| **R-1** | `spring-backend` | 본 문서 §1.1, §3, §4 **전체(§4.5 사용자별 DEK + admin wrap)**, §5.6/§5.7. **건드릴 파일 명시**: `domain/User`, `vault/VaultMeta`, `storage/VaultMetaRepository`, `storage/JsonDevLogRepository`, `service/VaultBootstrapService`, 새로 만들 `service/UserRegistrationService`, `service/UserAdminService` (createUser 제거), `service/UserAuthService`, `vault/Vault`, `security/UserAuthenticationProvider`. **건드리지 말 파일**: `ai/**`, `web/BlogDraftController`, Phase 3 산출물 | 테스트: ① bootstrap → adminSalt 만 발급, DEK 안 만듦 ② 셀프 가입 → users[] 엔트리에 userWrappedDek + adminWrappedDek 두 사본 동시 저장, 같은 DEK_user 디코드 ③ user login round-trip(H_user 비교 → userWrappedDek unwrap → DEK_user) ④ admin reset → 옛 passphrase 불가, 새 passphrase 가능, **DEK_user·adminWrappedDek 동일**, 회고 파일 재암호화 X ⑤ 사용자 삭제 → users[] 엔트리 + data/logs/{userId}/ 디렉토리 둘 다 사라짐 ⑥ 두 사용자 회고 격리 — userA 의 Vault 로 userB 파일 복호화 시 GCM 실패 ⑦ adminWrappedDek 변조 시 reset 실패 ⑧ admin passphrase 누락/default 시 fail-fast 유지 ⑨ admin passphrase 로그 노출 없음 |
| **R-2** | `spring-backend` | 본 문서 §2 (URL 표 + 템플릿 목록), §3 web 패키지, §5.6/§5.7. **신규 파일**: `web/RegisterController`, `web/form/UserRegisterForm`, `templates/register.html`. **수정 파일**: `web/AdminUserController` (생성 액션 제거), `templates/vault/users/list.html`, `templates/unlock.html` (?registered 안내), `security/SecurityConfig` (/register permitAll). **제거 파일**: `web/form/UserCreateForm`, `templates/vault/users/form.html`, `templates/vault/users/created.html` | MockMvc 슬라이스 테스트: ① `GET /register` 200 ② `POST /register` 유효한 폼 → 302 → `/unlock?registered` + users[] 에 새 엔트리 ③ `POST /register` userId 중복 → 4xx + 일반화 메시지 ④ `POST /register` passphrase != confirm → 4xx ⑤ ROLE_USER 가 `/vault/users` 진입 시 차단 ⑥ /vault/users/new 경로는 더 이상 매핑되지 않음 (404) |
| **R-3** | `code-reviewer` + 메인 | R-1, R-2 diff 전체 + §4.5 정책 + §5 시크릿 정책 | code-reviewer 리포트 PASS. 메인 세션이 `./gradlew bootRun` 으로 ① 가입 ② 로그인 ③ 회고 작성 ④ 로그아웃 ⑤ admin reset ⑥ 새 임시 passphrase 로 재로그인 — 회고 그대로 보임 ⑦ admin 사용자 삭제 → 디렉토리 사라짐 까지 통과 |

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

이 리포는 **프로젝트 레벨 커스텀 에이전트 4개를 정의**했다 (`.claude/agents/`). 각자 모델·권한·책임이 다르게 설계되어 있다.

| 에이전트 | 모델 | 권한 | 어디에 쓰나 |
|---|---|---|---|
| `spring-backend` | sonnet | 풀권한 | Phase 1 (도메인+저장소), Phase 2-A (웹 CRUD) |
| `ai-integration` | sonnet | 풀권한 | Phase 2-B (AI 어댑터) — §5 키 정책을 시스템 프롬프트에 박아둠 |
| `test-engineer` | haiku | 풀권한 (단, 프로덕션 코드 수정 금지를 본문 규칙으로) | Phase 2-C, 그리고 다른 Phase의 테스트 보강 |
| `code-reviewer` | opus | **읽기 전용** (Edit/Write 없음) | 각 Phase 마무리 시점, Phase 3 통합 직후 (main 직푸시 워크플로우라 "머지 전" 트리거는 없음 — 단계 종료마다 한 번씩) |

빌트인 에이전트는 보조 용도로:
- 코드 탐색·"어디 정의돼 있나?" 류 → `Explore`
- 설계 결정·트레이드오프 정리 → `Plan`
- 그 외 범용 한 번짜리 → `general-purpose`
- 보안/취약점 점검 (후반) → `/security-review` 스킬

---

## 7. 다음 액션 (제안)

1. ~~본 문서 §1~§5 컨펌~~ ✓
2. ~~**Phase 0~3** (공유 DEK 모델 기반 1차 구현)~~ ✓
3. **모델 전환 결정** ✓ — 셀프 가입 + 사용자별 DEK + admin 마스터키 wrap. PLAN.md §4.5 전면 재작성 완료.
4. **Phase R-1 (vault 모델 재구축)** — `spring-backend` 위임. §6.2 R-1 행 그대로. 가장 큰 변경이라 단계 마무리에 `code-reviewer` 한 번.
5. **Phase R-2 (웹 화면 재구축)** — `spring-backend` 위임. R-1 완료 후. `/register` 추가 + `/vault/users/new` 제거 + 템플릿 정리.
6. **Phase R-3 (통합 점검)** — `code-reviewer` 리포트 + 메인 세션 `bootRun` 브라우저 E2E 점검. 가입 → 로그인 → 회고 → admin reset → 재로그인 까지.
7. (이후) Phase 4 본래 계획대로 — README 갱신 + 수동 검수 체크리스트 정리.
