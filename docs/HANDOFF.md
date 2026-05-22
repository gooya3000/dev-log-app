# DevLog — 핸드오프 노트

PLAN.md 가 1차 출처. 이 문서는 **현재 위치를 빠르게 잡기 위한 진행 노트**.
마지막 갱신: 2026-05-22 (R-4 종료).

---

## Phase 상태 (PLAN §6.1 DAG)

| Phase | 내용 | 대표 커밋 | 상태 |
|---|---|---|---|
| 0 | 빌드/설정/Vault 설계 | `2a37f13`, `5aa478c`, `dee299c` | ✅ |
| 1 | 도메인 + 저장소 + vault + security (공유 DEK 모델) | `489481a` | ✅ → R-1 에서 재구축 |
| 2-A | 웹 CRUD + Security UI (관리자 사용자 생성 모델) | `4dace47` | ✅ → R-2 에서 재구축 |
| 2-B | AI 어댑터 + 프롬프트 빌더 | `da1f64f`, `07947a8` | ✅ |
| 2-C | 저장소 테스트 보강 | `e6719de` | ✅ |
| 3 (코드) | 블로그 초안 화면 통합 | `1c0a078` | ✅ |
| 3 (리뷰) | `code-reviewer` 통합 검토 | `7679b1c` | ✅ (R-3 리뷰와 묶임) |
| 4 | E2E 수동 검수 | — | 🟡 일부 (구버전 모델 기준 — R-3 E2E 후 재실행 필요) |
| **R-PLAN** | PLAN.md vault 모델 전환 (공유 DEK → 사용자별 DEK + admin wrap) | `436d826` | ✅ |
| **R-1** | vault 모델 재구축 (도메인·vault·storage·service·security) | `a07f614` | ✅ |
| **R-2** | 웹 화면 재구축 (`/register` 추가, `/vault/users/new` 제거) | `b78fd06` | ✅ |
| **R-3 (리뷰)** | `code-reviewer` 통합 리뷰 + M1(reset SecureRandom) 보강 | `7679b1c` | ✅ |
| **R-3 (E2E)** | 본인 `bootRun` 수동 검수 — 가입~admin reset 까지 동작 확인. `data/` 산출물 보존 (R-4 입력) | (이번 커밋) | ✅ |
| **R-4 (PLAN)** | §1.3 NON-GOAL 결정 뒤집기 + §1.1/§2/§4.5.6/§5.6/§6 본인 변경 흐름 신설 | `b0423bb` | ✅ |
| **R-4 (구현 + 리뷰)** | ProfileController + UserAccountService + PassphraseChangeForm + 템플릿 + 단위/슬라이스 13개. code-reviewer Blocker 0 / Major 0 / Minor 5 (모두 후속) | (이번 커밋) | ✅ |

---

## 최근 결정 / 변경

- **2026-05-22 — Phase R-4 완료** (이번 커밋). `spring-backend` 위임으로 `/logs/profile/passphrase` GET/POST 구현. PLAN §4.5.6 본인 변경 11단계(옛 검증 → DEK_user sanity check → userWrappedDek 만 rewrap → adminWrappedDek 무변경 → 세션 유지) 그대로. `./gradlew test` 109개 통과 (R-3 96 + 신규 13). code-reviewer 통합 리뷰 결과 **Blocker 0 / Major 0 / Minor 5**:
  - r1: `PassphraseChangeForm` 에 클래스 `@ToString` 없어 `@ToString.Exclude` 무력 (실효 노출 X, 형제 폼들과 패턴 불일치 / PLAN §5.6 보증 약함)
  - r2: `currentSession.getUserId() == null` 가드 누락 → 500 노출 (PLAN §4.5.6 단계 3 "세션 무효 → /unlock 강제" 와 어긋남)
  - r3: `VaultMetaRepository.save` 실패가 500 그대로 (PLAN §4.5.6 단계 10 "일반 에러" 와 어긋남)
  - r4: MockMvc 슬라이스 `any()` 매처로 userId null 흐름 통과 → `ArgumentCaptor` 보강 권장
  - r5: 단계 7 직전 `vault.copyDek()` `VaultLockedException` 500 가능성 (정상 흐름 영향 X)
  - 5건 모두 R-4 외 별도 후속. R-3 의 m1~m6 와 함께 묶음 처리 후보.
- **2026-05-22 — R-3 (E2E) 완료** (`19b9eb2`). 본인이 `bootRun` 으로 가입~admin reset 흐름까지 수동 검수. 검수 중 16자 임시 passphrase 의 UX 위화감을 발견해 R-4 신설로 이어짐. 검수 산출물 `data/.vault-meta.json` + `data/logs/test/2026-05-22_*.json` 1건은 R-4 구현·검증 입력으로 그대로 커밋 (PLAN §5.4: ciphertext 형태로 공개 리포 정상 커밋).
- **2026-05-22 — R-4 신설 결정**. 사용자 본인 자율 passphrase 변경 메뉴를 PLAN §1.3 NON-GOAL 에서 빼서 R-4 로 신설. 이유: admin reset 으로 받은 SecureRandom 16자가 "임시" 라는 이름과 달리 영구 passphrase 가 되는 UX 위화감. R-3 E2E 통과 후 PLAN.md §1.3/§4.5.6/§6.2 갱신 → 구현.
- **2026-05-22 — Phase R-3 (리뷰) 완료** (`7679b1c`). `code-reviewer` 통합 리뷰 결과:
  - **Blocker 0, Major 1 (M1), Minor 6 (m1~m6)**. M1 즉시 보강:
    - 문제: `POST /vault/users/{id}/reset-passphrase` 가 admin 입력 passphrase 를 그대로 사용 → PLAN §4.5.6 step 5 "새 임시 passphrase = SecureRandom 16자 (admin 직접 입력 경로 없음)" 명문 위반. R-1 산출물 결함.
    - 보강: `UserAdminService.resetPassphrase(userId)` 시그니처 변경, 내부 `SecureRandom` 으로 영숫자 16자 (≈ 95.3 bits) 생성 후 반환. 컨트롤러/템플릿/Form 정리. `./gradlew test` 95 → 96 통과.
  - Minor 6건 (m1 `@ToString.Exclude` 무력, m2 `unlock.html value="self"` 잔재, m3 `list.html` createdAt 미표시, m4 `AdminUserController.list` User 도메인 직접 노출, m5 `UserAuthService` 죽은 코드, m6 `application-local.properties.example` 구버전 주석) — R-3 외 별도 후속.
- **2026-05-21 — Phase R-2 완료** (`b78fd06`). `spring-backend` 위임으로 웹 화면 재구축.
  - 신규: `web/RegisterController`, `web/form/UserRegisterForm`, `templates/register.html`, `test/RegisterControllerTest` (5 케이스).
  - 수정: `SecurityConfig` `/register` permitAll, `unlock.html` `?registered` 안내, `vault/users/list.html` 생성 버튼 제거.
  - 제거: `UserCreateForm`, `vault/users/form.html`, `vault/users/created.html`.
  - `./gradlew test` 95개 통과 / 0 실패 (R-1 의 90개 + 새 5개).
  - 판단 메모: ① `IllegalArgumentException` 은 200 + 폼 재렌더 + 일반화 메시지("가입할 수 없습니다.") 로 처리 — userId 존재 여부 누출 차단(PLAN §5.6). ② ⑤번 ROLE_USER 차단 케이스는 `SecurityAccessTest.roleUser_cannot_access_vaultUsers` 에 이미 존재해 중복 추가 생략.
- **2026-05-21 — Phase R-1 완료** (`a07f614`). `spring-backend` 위임으로 vault 모델 재구축.
  - 도메인·service·storage·security 17개 파일 변경/신규. `./gradlew test` 90개 통과 / 0 실패.
  - PLAN.md §6.2 R-1 의 9가지 Done 기준 (bootstrap·두 wrap 사본·login round-trip·reset 시 DEK 동일·사용자 격리·adminWrappedDek 변조 탐지 등) 모두 커버.
  - 신규: `UserRegistrationService` (셀프 가입), `UserIsolationTest` (사용자 격리).
  - 회고 저장 경로 `data/logs/{userId}/{date}_{id}.json` 으로 사용자별 서브디렉토리 분리 적용.
- **2026-05-21 — Vault 모델 전환** (`436d826`). 공유 DEK + 관리자 사용자 생성 → **사용자별 DEK + 셀프 가입 + admin 마스터키 wrap**.
  - 셀프 가입(`POST /register`, permitAll). `/vault/users/new` 제거.
  - 사용자마다 `DEK_user` 1개, `userWrappedDek` + `adminWrappedDek` 두 사본을 `users[]` 엔트리에 저장.
  - 회고 저장 경로 `data/logs/{userId}/{date}_{id}.json` 으로 사용자별 분리.
  - 관리자 reset: `adminWrappedDek` 우회로로 `DEK_user` 회수 → `userWrappedDek` 만 재발급. `DEK_user`·`adminWrappedDek` 그대로.
  - 사용자 삭제 시 `data/logs/{userId}/` 디렉토리 통째 제거.
  - 부수 결정: 가입은 **오픈** (초대 코드 X), 본인 자율 passphrase 변경은 **NON-GOAL**.
- **2026-05-21 — AI 어댑터 OpenAI → Gemini 로 교체** (`57b6ad7`). 무료 티어 사용이 목적.
  - 모델: `gemini-2.5-flash`. `gemini-2.0-flash` 는 본 프로젝트 키의 free tier 가 `limit:0` 으로 잡혀 사용 불가.
  - 키 발급처: [Google AI Studio](https://aistudio.google.com/apikey).

---

## 다음 액션

1. **R-4 본인 검수 (bootRun E2E)** — `./gradlew bootRun` 으로 직접 띄워 ① 로그인 ② `/logs/profile/passphrase` 로 본인 변경 ③ 안내 배너 + 세션 유지 ④ 변경 후 회고 목록 정상 노출 ⑤ 로그아웃 후 새 passphrase 로 재로그인 ⑥ 옛 passphrase 로는 실패 까지 확인. 통과하면 R-4 종결.
2. **Minor 후속 묶음** — R-3 m1~m6 + R-4 r1~r5 우선순위 골라 묶음 처리. 본인 결정.
3. **Vault 재셋업** — Minor 정리 후 `data/` 삭제하고 새 부트스트랩 → `/register` 로 본인 사용자 가입 (강한 passphrase, PLAN §5.5) → 회고 1~2건 작성 → `data/` 커밋·푸시.
4. **Phase 4 체크리스트 명문화** — PLAN §6.2 "수동 체크리스트" 를 실제 항목으로 채우기.

---

## 알려진 주의사항

- **모델 전환 진행 중 — 코드와 PLAN 불일치 상태.** PLAN.md §4.5 는 새 모델 (사용자별 DEK + admin wrap), 코드는 아직 구버전 (공유 DEK). R-1 ~ R-3 완료까지 이 갭이 유지된다. 새 코드 작성은 **반드시 PLAN.md 기준**.
- **Passphrase 강도가 보안 경계의 전부.** 공개 리포 + PBKDF2 600k iters. 약한 passphrase 면 오프라인 brute-force 로 DEK 노출 가능. CLAUDE.md 의 "공개 리포 안전" 전제는 **강한 passphrase** 가 받쳐줘야 성립.
- **Admin passphrase** 는 `application-local.properties` 평문 보관 (PLAN §5.7). 이 파일은 `.gitignore` — 절대 커밋 금지. `application-local.properties.example` 만 리포에 둠. admin 분실 시 reset 기능 영구 상실 + 신규 가입 시 `adminWrappedDek` 발급 불가 → 신규 가입 사실상 막힘.
- **Gemini 호출 본문은 Google 서버에 일정 기간 잔존 가능** (무료 티어는 모델 개선 용도 활용 명시). 회고에 민감 정보 넣을 때 유의.
- **DEK_user 는 메모리에만**. 디스크엔 wrap 된 형태로만 (사용자/admin 두 사본). 어떤 경로로도 평문 DEK 가 파일에 떨어지면 설계 위반.
- **`data/` 는 `.gitignore` 가 아니다.** AES-256-GCM ciphertext 형태로 공개 리포에 그대로 커밋된다 (PLAN §5.4). 평문 회고가 디스크에 닿지 않는 것이 이 invariant 의 전제 — `VaultCipher` 우회로가 생기면 즉시 설계 위반.

---

## 빠른 명령

- 실행: `./gradlew bootRun`  → http://localhost:8080
- 테스트 전체: `./gradlew test`
- 진입점 (R-2 완료 후 기준):
  - `/register` — 사용자 셀프 가입 (permitAll)
  - `/unlock` — 사용자 로그인
  - `/vault/login` — 관리자 로그인 (사용자 reset/delete 용)
