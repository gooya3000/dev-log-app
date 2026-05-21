# DevLog — 핸드오프 노트

PLAN.md 가 1차 출처. 이 문서는 **현재 위치를 빠르게 잡기 위한 진행 노트**.
마지막 갱신: 2026-05-21.

---

## Phase 상태 (PLAN §6.1 DAG)

| Phase | 내용 | 대표 커밋 | 상태 |
|---|---|---|---|
| 0 | 빌드/설정/Vault 설계 | `2a37f13`, `5aa478c`, `dee299c` | ✅ |
| 1 | 도메인 + 저장소 + vault + security | `489481a` | ✅ |
| 2-A | 웹 CRUD + Security UI | `4dace47` | ✅ |
| 2-B | AI 어댑터 + 프롬프트 빌더 | `da1f64f`, `07947a8` | ✅ |
| 2-C | 저장소 테스트 보강 | `e6719de` | ✅ |
| 3 (코드) | 블로그 초안 화면 통합 | `1c0a078` | ✅ |
| 3 (리뷰) | `code-reviewer` 통합 검토 | — | ⏳ 미실시 |
| 4 | E2E 수동 검수 | — | 🟡 일부 (블로그 초안 동작 확인 완료, 체크리스트 미명문화) |

---

## 최근 결정 / 변경

- **2026-05-21 — AI 어댑터 OpenAI → Gemini 로 교체** (`57b6ad7`). 무료 티어 사용이 목적.
  - 모델: `gemini-2.5-flash`. `gemini-2.0-flash` 는 본 프로젝트 키의 free tier 가 `limit:0` 으로 잡혀 사용 불가.
  - 키 발급처: [Google AI Studio](https://aistudio.google.com/apikey).
- PLAN.md §3 패키지 트리·§5.2/§5.5/§6.2 의 OpenAI/Claude 언급 → Gemini 단일화.

---

## 다음 액션

1. **Vault 재셋업** — 학습용 약한 passphrase 흔적을 지우려 `data/` 삭제 후 새 부트스트랩 완료. 본인이 `/vault/login` → `/vault/users/new` 에서 강한 passphrase (20자+ 랜덤 또는 4단어+) 로 사용자 생성 → `/unlock` → 회고 1~2건 작성.
2. **`data/` 커밋** — 1번이 끝나면 `data/` 통째로 staged · 커밋·푸시. CLAUDE.md 정책상 ciphertext 라 공개 리포 commit OK.
3. **`code-reviewer` 1회 실행** — Phase 1~3 누적 커밋(`489481a..HEAD`) 대상으로 통합 검토. main 직푸시 워크플로우라 "머지 전" 트리거는 없고, 단계 마무리 시점에 한 번 돌리는 용도.
4. **Phase 4 체크리스트 명문화** — PLAN.md §6.2 의 "수동 체크리스트" 항목을 실제 항목으로 채워두기 (로그인 흐름, 회고 CRUD, 블로그 초안, 권한 분리 등).

---

## 알려진 주의사항

- **Passphrase 강도가 보안 경계의 전부.** 공개 리포 + PBKDF2 600k iters. 약한 passphrase 면 오프라인 brute-force 로 DEK 노출 가능. CLAUDE.md 의 "공개 리포 안전" 전제는 **강한 passphrase** 가 받쳐줘야 성립.
- **Admin passphrase** 는 `application-local.properties` 평문 보관 (PLAN §5.7). 이 파일은 `.gitignore` — 절대 커밋 금지. `application-local.properties.example` 만 리포에 둠.
- **Gemini 호출 본문은 Google 서버에 일정 기간 잔존 가능** (무료 티어는 모델 개선 용도 활용 명시). 회고에 민감 정보 넣을 때 유의.
- **DEK 는 메모리에만**. 디스크엔 wrap 된 형태로만. 어떤 경로로도 평문 DEK 가 파일에 떨어지면 설계 위반.

---

## 빠른 명령

- 실행: `./gradlew bootRun`  → http://localhost:8080
- 테스트 전체: `./gradlew test`
- 진입점: `/vault/login` (admin), `/unlock` (user)
