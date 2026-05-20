# DevLog 프로젝트 계획

개인용 개발 회고 앱. 하루 회고를 구조화해 JSON으로 보관하고, 나중에 LLM을 통해 기술 블로그 초안으로 재활용한다.

---

## 1. MVP 기능

핵심 기능을 좁게 잡고, "회고 → 블로그 초안"이라는 한 줄짜리 사용자 가치만 끝까지 동작하게 한다.

### 1.1 필수 (MVP)

| 기능 | 설명 |
|---|---|
| 시스템 첫 부팅 | `application-local.properties` 의 admin passphrase 로 자동 bootstrap — DEK 생성, `adminWrappedDek` 저장 (§4.5.6) |
| 관리자 로그인 | `/vault/login` 에서 admin passphrase 입력 → Spring Security ROLE_ADMIN 세션 (§5.7) |
| 사용자 생성 | 관리자 모드에서 `/vault/users` → userId + 초기 passphrase 발급 → `users[]` 엔트리 추가 (§4.5.6) |
| 사용자 로그인 | `/unlock` 에서 사용자 passphrase 입력 → `H_user` 검증 → DEK unwrap → ROLE_USER 세션 (§5.6) |
| 사용자 passphrase 재설정 | 관리자 모드에서 `/vault/users/{id}/reset-passphrase` → 새 임시 passphrase 발급, 매핑 엔트리 교체. DEK 그대로 (§4.5.6) |
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

- 사용자 인증, 다중 사용자
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
| `GET /unlock` | GET | permitAll | 사용자 passphrase 입력 폼 |
| `POST /unlock` | POST | permitAll | passphrase 검증 (H_user 비교) → K_user 로 wrappedDek unwrap → DEK 메모리 적재 → ROLE_USER 세션 → `/logs` |
| `POST /logout` | POST | ROLE_USER | 세션 종료, DEK 메모리에서 제거 → `/unlock` |
| `GET /logs` | GET | ROLE_USER | 회고 목록 (날짜 내림차순, 태그 필터) |
| `GET /logs/new` | GET | ROLE_USER | 신규 작성 폼 |
| `POST /logs` | POST | ROLE_USER | 저장 후 `/logs/{id}` |
| `GET /logs/{id}` | GET | ROLE_USER | 상세 + "블로그 초안 생성" 버튼 |
| `GET /logs/{id}/edit` | GET | ROLE_USER | 수정 폼 |
| `POST /logs/{id}` | POST | ROLE_USER | 수정 저장 |
| `POST /logs/{id}/delete` | POST | ROLE_USER | 삭제 |
| `POST /logs/{id}/blog-draft` | POST | ROLE_USER | 폼에 `apiKey` 포함, LLM 호출 후 결과 표시. 키는 1회 사용 후 폐기 |
| `GET /vault/login` | GET | permitAll | 관리자 passphrase 입력 폼 |
| `POST /vault/login` | POST | permitAll | properties 의 admin passphrase 와 constant-time 비교 + adminWrappedDek sanity unwrap → ROLE_ADMIN 세션 → `/vault/users` |
| `POST /vault/logout` | POST | ROLE_ADMIN | 관리자 세션 종료 → `/vault/login` |
| `GET /vault/users` | GET | ROLE_ADMIN | 사용자 목록 (id, salt 메타만 표시 — passphraseHash는 노출 X) |
| `GET /vault/users/new` | GET | ROLE_ADMIN | 사용자 생성 폼 (userId + 초기 passphrase) |
| `POST /vault/users` | POST | ROLE_ADMIN | 사용자 생성 → `/vault/users/{id}/created` (1회 노출) |
| `GET /vault/users/{id}/created` | GET | ROLE_ADMIN | 방금 생성된 사용자의 초기 passphrase 1회 표시 |
| `GET /vault/users/{id}/reset-passphrase` | GET | ROLE_ADMIN | 재설정 확인 화면 |
| `POST /vault/users/{id}/reset-passphrase` | POST | ROLE_ADMIN | 새 임시 passphrase 발급 + users[] 엔트리 교체 → `/vault/users/{id}/reset-result` (1회 노출) |
| `GET /vault/users/{id}/reset-result` | GET | ROLE_ADMIN | 새 임시 passphrase 1회 표시 |
| `POST /vault/users/{id}/delete` | POST | ROLE_ADMIN | 사용자 매핑 엔트리 제거 (해당 사용자는 더 이상 데이터 접근 불가) |

### 템플릿 파일

```
src/main/resources/templates/
├── layout.html                       // 공통 fragment (sec:authorize 태그 활용)
├── unlock.html                       // 사용자 로그인
├── logs/
│   ├── list.html
│   ├── form.html                     // new/edit 공용
│   ├── detail.html
│   └── blog-draft.html               // 생성된 마크다운 + 복사 버튼
├── vault/
│   ├── login.html                    // 관리자 로그인
│   └── users/
│       ├── list.html                 // 사용자 목록
│       ├── form.html                 // 사용자 생성 폼
│       ├── created.html              // 생성 직후 초기 passphrase 1회 표시
│       ├── confirm-reset.html        // 재설정 확인
│       └── reset-result.html         // 새 임시 passphrase 1회 표시
└── error.html
```

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
│   └── User                     // 사용자 메타 (id, salt, passphraseHash, wrappedDek)
│
├── storage                      // 파일 I/O 책임
│   ├── DevLogRepository         // 인터페이스
│   ├── JsonDevLogRepository     // 구현 (디렉토리 스캔 + atomic write + VaultCipher 통과)
│   └── VaultMetaRepository      // .vault-meta.json 읽기/쓰기 (adminWrappedDek + users[])
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
│   ├── VaultBootstrapService    // 첫 부팅 시 adminWrappedDek 초기화 (`ApplicationRunner`)
│   ├── UserAdminService         // 사용자 생성/삭제/passphrase 재설정 (ROLE_ADMIN 전용)
│   ├── UserAuthService          // 사용자 로그인 (passphrase 검증 + DEK unwrap)
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
│   ├── AdminAuthController      // /vault/login, /vault/logout (관리자 로그인)
│   ├── AdminUserController      // /vault/users/**
│   └── form
│       ├── DevLogForm           // @Valid 폼
│       ├── UserUnlockForm       // passphrase 1개 (@ToString.Exclude)
│       ├── AdminLoginForm       // admin passphrase 1개 (@ToString.Exclude)
│       ├── UserCreateForm       // userId + 초기 passphrase (@ToString.Exclude)
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

- 기본 경로: `./data/logs/`
- `application.properties`에서 변경 가능
  ```properties
  devlog.storage.root=./data/logs
  ```
- 앱 시작 시 디렉토리 자동 생성.
- **`data/`는 리포에 커밋된다.** 안에 들어가는 모든 파일은 §4.5에 따라 AEAD 암호화된 ciphertext이므로 공개 리포에 올라가도 안전. 평문 회고가 절대 디스크에 떨어지지 않게 하는 것이 핵심 invariant.

### 4.2 파일 1개 = 회고 1개

- 파일명: `{date}_{id}.json` — 예: `2026-05-14_01HXXXXXXXXXXXXXXXXXXXXXX.json`
  - 디렉토리 정렬만으로 자연스럽게 최신순 노출.
  - `id`를 파일명에 포함해 같은 날짜 여러 건도 충돌하지 않음.
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

### 4.5 암호화 저장 (Vault) — 역할 분리

평문 회고가 디스크나 리포에 노출되지 않도록 모든 회고 파일은 AEAD로 암호화한다. 시스템에는 두 역할이 분리되어 있다:

- **관리자 (admin)**: `application-local.properties` 의 평문 passphrase 로 인증. `/vault/**` 접근 권한. 사용자 매핑 관리 + 사용자 passphrase 재설정.
- **사용자 (user)**: 본인 passphrase 로 인증. `/logs/**` 접근 권한. 회고 CRUD.

> **세 종류의 비밀, 각자 책임**:
> - **Admin passphrase** — 관리자(=본인)가 `application-local.properties` 에 평문 하드코딩. `.gitignore` 보호. 분실 시 admin 매핑 재발급 필요 (사용자 unlock 상태에서만 가능, §4.5.7).
> - **User passphrase** — 사용자(=본인)가 머릿속/비번 매니저에 보관. 분실 시 관리자가 새 임시 passphrase 발급.
> - **DEK (Data Encryption Key)** — 시스템 생성. 메모리(`Vault` 빈)에만 평문 존재. 디스크엔 항상 wrap 된 형태.
>
> 둘(admin 또는 본인 user passphrase) 중 하나만 살아 있으면 데이터 접근 가능. 두 비밀 모두 분실 시에만 영구 손실.

#### 4.5.1 결정사항 요약

| 항목 | 결정 |
|---|---|
| 키 구조 | **DEK 래핑** — 파일은 랜덤 DEK 로 암호화, DEK 는 admin / user 별로 따로 wrap |
| 권한 분리 | Spring Security `SecurityFilterChain` — `/vault/**` = ROLE_ADMIN, `/logs/**` = ROLE_USER (사용자 unlock 상태) |
| Unlock 경로 | ① user passphrase (`/unlock`) → ROLE_USER / ② admin passphrase (`/vault/login`) → ROLE_ADMIN |
| User passphrase 검증 | PBKDF2 출력 64바이트 → 전반 32 = `K_user` (wrap용), 후반 32 = `H_user` (저장된 hash 와 동등 비교) |
| Admin passphrase 보관 | `application-local.properties` 평문 하드코딩 (.gitignored). 환경변수 placeholder도 안 씀 |
| 잠금 단위 | DEK 한 개 = vault 전체 (사용자 N명이어도 같은 DEK 공유) |
| 암호화 범위 | **파일 내용만** (파일명 `{date}_{id}.json` 평문 유지) |
| 세션 만료 | 명시 만료 없음 — 앱 종료 시 메모리 해제 |
| 헤더 메타 | **포함** — version / alg / kdf 식별자 |

#### 4.5.2 키 계층

```
   [Admin 영역]                              [User 영역]

   admin passphrase                          user passphrase
   (application-local.properties)            (폼 입력)
        │                                          │
        ▼ PBKDF2(adminSalt, 600k, 32)             ▼ PBKDF2(userSalt, 600k, 64)
        K_admin (256-bit)                          ├─ 전반 32바이트 = K_user
        │                                          └─ 후반 32바이트 = H_user
        │                                                │
        │ AES-GCM unwrap                                 │ ① H_user 가 저장된 passphraseHash 와 일치?
        ▼                                                ▼ ② 일치 시 K_user 로 AES-GCM unwrap
                       ┌─────────────────┐
                       │     DEK         │ ← 메모리(`Vault` 빈)에만, 디스크엔 항상 wrap
                       │   (32 bytes)    │
                       └────────┬────────┘
                                │ AES-GCM (per-file 12바이트 nonce)
                                ▼
                       data/{date}_{id}.json (회고 ciphertext)
```

- **DEK** 는 시스템 첫 부팅 시 단 1회 생성. 평생 동일.
- **admin / user passphrase 변경 시 DEK 자체는 그대로** — `adminWrappedDek` 또는 해당 사용자의 `wrappedDek` 만 새로 발급. 회고 파일 재암호화 불필요.
- DEK 가 평문으로 디스크에 노출되는 경로가 없어야 함.

#### 4.5.3 파일 구조

**`data/.vault-meta.json`** (평문 JSON 래퍼, 내부 ct 는 ciphertext, 리포에 커밋):
```json
{
  "v": 1,
  "kdf": "PBKDF2-HMAC-SHA256",
  "kdfIter": 600000,
  "adminWrappedDek": {
    "salt": "<base64 16바이트>",
    "alg": "AES-256-GCM",
    "nonce": "<base64 12바이트>",
    "ct": "<DEK 를 K_admin 으로 AES-GCM 암호화 + tag, base64>"
  },
  "users": [
    {
      "id": "self",
      "salt": "<base64 16바이트>",
      "passphraseHash": "<base64 32바이트, PBKDF2 출력 후반부>",
      "wrappedDek": {
        "alg": "AES-256-GCM",
        "nonce": "<base64 12바이트>",
        "ct": "<DEK 를 K_user 로 AES-GCM 암호화 + tag, base64>"
      }
    }
  ]
}
```

`users` 배열은 MVP 에서 1명 (`id: "self"`)만 채우면 충분하지만 구조는 N명 확장 가능하게 둔다.

**`data/{date}_{id}.json`** (회고 envelope):
```json
{
  "v": 1,
  "alg": "AES-256-GCM",
  "nonce": "<base64 12바이트>",
  "ct": "<평문 DevLog JSON → DEK 로 AES-GCM 암호화 + tag, base64>"
}
```

#### 4.5.4 KDF · 키 처리

**Admin path:**
- `adminSalt` 는 첫 부팅 시 생성, `adminWrappedDek.salt` 에 보관
- `K_admin = PBKDF2-HMAC-SHA256(admin passphrase, adminSalt, 600,000, 32 bytes)`
- 부팅 시 또는 `/vault/login` 시도 시 즉시 도출 후 `adminWrappedDek` unwrap → DEK 획득 → `K_admin` 폐기

**User path:**
- `userSalt` 는 사용자 생성·재설정 시마다 새 발급
- PBKDF2 출력 **64바이트**:
  - `K_user = derivation[0..32]` (DEK wrapping 용 AES 키)
  - `H_user = derivation[32..64]` (인증용, 저장된 `passphraseHash` 와 비교)
- 로그인: `H_user` 비교(constant-time)로 1차 검증 → 일치 시 `K_user` 로 `wrappedDek` unwrap → DEK 획득

**DEK:**
- 첫 부팅 bootstrap 시 `SecureRandom.nextBytes(32)` 로 생성
- `Vault` 빈에 메모리 보관 + 어느 사용자가 unlock 했는지 컨텍스트 추적
- 평문 디스크 기록 절대 금지

#### 4.5.5 암호화/복호화 (AEAD)

- 알고리즘: **AES-256-GCM** (`Cipher.getInstance("AES/GCM/NoPadding")`)
- nonce: 매 암호화마다 새 12바이트 random. **재사용 절대 금지.**
- tag: 16바이트 인증 태그, GCM 자동 부착. 잘못된 키 / 변조 시 복호화 실패.
- 회고 파일: 평문 DevLog JSON → UTF-8 bytes → DEK 로 GCM 암호화 → base64 → envelope `ct`
- DEK 래핑: DEK 32바이트 → K_admin 또는 K_user 로 GCM 암호화 → base64

#### 4.5.6 흐름

**첫 부팅 자동 bootstrap** (`.vault-meta.json` 없을 때):
```
앱 시작
  1. application-local.properties 의 devlog.admin.passphrase 읽기
     없거나 "CHANGE-ME-..." default 면 → 에러 로그 + 시작 중단 (fail-fast)
  2. DEK = SecureRandom.nextBytes(32)
  3. adminSalt = SecureRandom.nextBytes(16)
  4. K_admin = PBKDF2(adminPassphrase, adminSalt, 600_000, 32)
  5. adminWrappedDek = AES-GCM(DEK, K_admin, randomNonce)
  6. .vault-meta.json 저장 (users: [])
  7. DEK, K_admin 변수 폐기 (admin 이 로그인할 때 다시 도출)
  → 부팅 완료. users 가 비어 있어 사용자 unlock 은 불가, admin login 만 가능.
```

**관리자 로그인** (`POST /vault/login`):
```
  1. 폼에서 admin passphrase 입력
  2. application-local.properties 값과 constant-time 비교
       불일치 → 지연(500ms) + 일반 에러
  3. K_admin = PBKDF2(입력 passphrase, adminSalt, 600_000, 32)
  4. adminWrappedDek 시험 unwrap (sanity check; 평문 비교가 통과해도 wrap 검증을 한 번 더)
  5. Spring Security 컨텍스트에 ROLE_ADMIN 부여 → /vault/users 로
  6. K_admin, 입력 passphrase 폐기
     (단, "사용자 재설정" 요청 시 다시 K_admin 도출. DEK 자체는 admin 메모리 영구 보관 안 함 — 매 admin 작업마다 짧게 사용 후 폐기)
```

**사용자 생성** (`POST /vault/users`, ROLE_ADMIN 필요):
```
  1. 폼: userId + 초기 passphrase (관리자가 정함 또는 시스템 random 생성 후 화면 표시)
  2. K_admin 재도출 → adminWrappedDek unwrap → DEK 임시 메모리 획득
  3. userSalt = SecureRandom.nextBytes(16)
  4. derivation = PBKDF2(초기 passphrase, userSalt, 600_000, 64)
  5. K_user = derivation[0..32], H_user = derivation[32..64]
  6. wrappedDek_user = AES-GCM(DEK, K_user, randomNonce)
  7. users[] 에 {id, userSalt, passphraseHash: H_user, wrappedDek: wrappedDek_user} 추가
  8. .vault-meta.json 저장
  → /vault/users/created (1회 노출 화면) — 초기 passphrase 표시
  9. DEK, K_admin, K_user, H_user, derivation, 입력 passphrase 모두 폐기
```

**사용자 로그인** (`POST /unlock`):
```
  1. 폼: (userId) + passphrase. 단일 사용자 모드면 userId 자동으로 "self"
  2. users[] 에서 해당 id 의 엔트리 조회 (없으면 지연 + 일반 에러)
  3. derivation = PBKDF2(passphrase, 엔트리의 userSalt, 600_000, 64)
  4. constant-time compare(derivation[32..64], 저장된 passphraseHash)
       불일치 → 지연 + 일반 에러
  5. K_user = derivation[0..32] → wrappedDek unwrap → DEK 획득
  6. Vault 빈에 DEK 적재 + Spring Security 에 ROLE_USER + userId 저장
  → /logs 로
  7. derivation, K_user, 입력 passphrase 폐기
```

**사용자 passphrase 재설정** (`POST /vault/users/{id}/reset-passphrase`, ROLE_ADMIN 필요):
```
  1. ROLE_ADMIN 검증 (Spring Security)
  2. K_admin 재도출 → adminWrappedDek unwrap → DEK 임시 메모리
  3. 새 임시 passphrase 결정 (admin 입력 또는 시스템 random)
  4. newUserSalt = SecureRandom.nextBytes(16)
  5. newDerivation = PBKDF2(새 passphrase, newUserSalt, 600_000, 64)
  6. users[id] 엔트리 통째로 교체:
       {id, newUserSalt, passphraseHash: newDerivation[32..64],
        wrappedDek: AES-GCM(DEK, newDerivation[0..32], newNonce)}
  7. .vault-meta.json 저장
  → /vault/users/{id}/reset-result (1회 노출 화면) — 새 임시 passphrase 표시
  8. DEK, K_admin, newDerivation, 새 passphrase 모두 폐기
```

**`SecurityConfig`**: Spring Security `SecurityFilterChain` 빈으로 URL→권한 매핑:
- `/vault/login`, `/unlock`, static → permitAll
- `/vault/**` → `hasRole("ADMIN")`
- `/logs/**`, `/` → `hasRole("USER")`
- CSRF 활성화 (관리자 작업 보호 + 일반 POST 보호)
- 폼 로그인 대신 커스텀 인증 핸들러 (passphrase → DEK unwrap → 인증 성공/실패)

#### 4.5.7 백업 · 복구 정책

- **admin passphrase ∧ 모든 user passphrase 분실 = 영구 손실.** 복구 불가.
- **user passphrase 만 분실** → ROLE_ADMIN 으로 들어가 재설정 (§4.5.6의 reset 흐름). 가장 흔한 케이스.
- **admin passphrase 만 분실** → user 가 본인 passphrase 만 알면 데이터 접근은 계속 가능. 다만 다른 사용자 추가·재설정 등 admin 작업이 불가.
  - 해결: user 로 unlock 한 상태(DEK 메모리 보유)에서 admin passphrase 회전 화면 제공 — **Phase 1 이후 NON-GOAL**. MVP에서는 admin passphrase 잃지 않도록 유의.
  - 회전 흐름 (참고): user unlock → DEK 메모리 → 새 admin passphrase + 새 adminSalt → `adminWrappedDek` 재발급 → `application-local.properties` 의 값도 새 값으로 수동 갱신.
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

**최초 설정:**
1. `application-local.properties.example` → `application-local.properties` 복사
2. `devlog.admin.passphrase` 를 강한 값으로 교체 (비밀번호 매니저에 보관)
3. 첫 부팅 시 시스템이 자동으로 admin vault 초기화
4. `/vault/login` → admin 로그인 → `/vault/users` → 첫 사용자 생성 (초기 passphrase 발급)
5. 사용자 초기 passphrase 화면에서 1회 노출되니 즉시 비번 매니저로 옮길 것

**일상 사용:**
- 회고 작성: `/unlock` → 사용자 passphrase 입력 → 정상 사용
- passphrase 잊었을 때: `/vault/login` → 관리자 로그인 → 사용자 재설정

**분실 시:**
- 사용자 passphrase 만 분실 → 관리자 모드로 재설정 가능 (가장 흔한 시나리오)
- 관리자 passphrase 만 분실 → 사용자 로그인은 계속 가능하나 관리자 작업 불가. 회전 화면은 Phase 1 NON-GOAL — admin 분실 안 하도록 유의
- 둘 다 분실 → 영구 손실, 복구 불가

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

| Phase | 사용 에이전트 | 인풋 (브리핑에 포함할 것) | 아웃풋 / Done 기준 |
|---|---|---|---|
| 0 | (메인 세션 직접) | 현 `build.gradle`, 추가할 의존성(`spring-boot-starter-validation`), JSON 저장 경로 | `build.gradle` 수정, `application.properties`에 `devlog.storage.root` 추가, `.gitignore`에 `data/` 추가 |
| 1 | `spring-backend` | 본 문서 §1.2, §3, §4 **전체(§4.5 DEK 래핑 + admin/user 분리 포함)**, §5.6 user passphrase 정책, §5.7 admin passphrase 정책 | `DevLog`·`User`·`UserId`·`DevLogRepository`·`JsonDevLogRepository`·`VaultMetaRepository`, **`vault` 패키지 전체**(`Vault`, `VaultCipher`, `KeyWrapper`, `PassphraseKdf`, `EnvelopeV1`, `VaultMeta`), **`security` 패키지**(`SecurityConfig`, `AdminAuthenticationProvider`, `UserAuthenticationProvider`, `CurrentSession`), **service 4종**(`VaultBootstrapService`, `UserAdminService`, `UserAuthService`, `DevLogService`), `AdminProperties` 빈. 테스트: ① bootstrap → adminWrappedDek 생성·저장, ② admin login → ROLE_ADMIN 부여, ③ admin이 user 생성 → users[] 엔트리·초기 passphrase 검증, ④ user login round-trip(passphrase → H_user 비교 → DEK unwrap), ⑤ admin reset → 옛 passphrase 불가·새 passphrase 가능·DEK 그대로, ⑥ DEK 변경 없이 user passphrase 회전 시 회고 파일 재암호화 X 검증, ⑦ envelope 변조 GCM 거부, ⑧ admin passphrase 누락/default 시 fail-fast, ⑨ admin passphrase 로그 노출 없음 (로그 캡처 테스트), ⑩ 표준 CRUD 라운드트립 |
| 2-A | `spring-backend` | 본 문서 §2(URL 표 + 템플릿 목록), §3의 web 패키지, §5.6/§5.7 폼 처리 정책 | 11개 Thymeleaf 템플릿 (layout + unlock + logs 4종 + vault/login + vault/users 5종 + error), Spring Security 통합 (`thymeleaf-extras-springsecurity6` 의 `sec:authorize`), 컨트롤러 4종(`AuthController`, `LogController`, `AdminAuthController`, `AdminUserController`), 폼 검증. `MockMvc` + `@WithMockUser` 슬라이스 테스트로 ① 미인증 시 적절한 로그인 페이지 리다이렉트, ② ROLE_USER 만 `/logs` 진입, ③ ROLE_ADMIN 만 `/vault/users` 진입, ④ CSRF 토큰 동작 확인 |
| 2-B | `ai-integration` | §3의 ai 패키지 + §5 키 정책 + "DevLog 1건 → 블로그용 마크다운" 변환 사양 + 어댑터 대상 (현재: Gemini generateContent) | `AiClient` 인터페이스(`generate(prompt, apiKey)`), 어댑터 1개, `BlogPromptBuilder`. WireMock 테스트 + **키 노출 검증 테스트 필수** |
| 2-C | `test-engineer` | atomic write·동시 read·UTF-8·`schemaVersion` 누락 케이스 | 저장소 보강 단위 테스트들 |
| 3 (코드) | (메인 세션) | Phase 2-A, 2-B 산출물 | `BlogDraftController` + `blog-draft.html`, "복사" 버튼, 통합 테스트 1개 |
| 3 (리뷰) | `code-reviewer` | Phase 1~3 변경 diff 전체 | 체크리스트 기반 PASS/FAIL 리포트 |
| 4 | (사람이 직접) | `./gradlew bootRun` 후 브라우저로 검증 | 수동 체크리스트 통과 |

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
| `code-reviewer` | opus | **읽기 전용** (Edit/Write 없음) | 각 Phase 머지 직전, Phase 3 통합 전 |

빌트인 에이전트는 보조 용도로:
- 코드 탐색·"어디 정의돼 있나?" 류 → `Explore`
- 설계 결정·트레이드오프 정리 → `Plan`
- 그 외 범용 한 번짜리 → `general-purpose`
- 보안/취약점 점검 (후반) → `/security-review` 스킬

---

## 7. 다음 액션 (제안)

1. ~~본 문서 §1~§5 컨펌~~ ✓
   - AI 키: 매 요청 폼 입력
   - Vault: DEK 래핑, admin/user 권한 분리, Spring Security 통합
   - Admin passphrase: application-local.properties 평문 하드코딩 (.gitignored)
2. ~~**Phase 0 (빌드/설정 정비)**~~ ✓ — 본 커밋으로 마무리
   - `2a37f13`: validation, storage root, .gitignore 기본
   - `5aa478c`: vault 암호화 정책 1차 (이번에 admin/user 모델로 재정비)
   - 본 커밋: Spring Security 의존성 + admin passphrase 정책 + DEK 래핑 + admin/user 분리 확정 + `application-local.properties.example`
3. **Phase 1 (도메인 + 저장소 + vault + security)** — `spring-backend` 서브에이전트에 위임. §6.2 Phase 1 행 그대로. 범위가 매우 커졌으니 머지 직전 `code-reviewer` 필수.
4. Phase 1 머지 후, **Phase 2-A · 2-B · 2-C 병렬 위임.** 2-A 는 Spring Security 통합 UI 까지 포함.
5. **Phase 3**: 메인 세션에서 통합.
6. **Phase 4**: 메인 세션이 `./gradlew bootRun` 후 직접 브라우저 검수 — 첫 실행이면 `/vault/login` 으로 가서 admin 로그인 → 사용자 생성 → 사용자 로그인 → 회고 작성 흐름까지.
