# AGENTS.md — 두 AI 의 협업 규칙

이 저장소는 **Claude (Claude Code)** 와 **ChatGPT (Codex·ChatGPT)** 가 함께 작업한다. 유저는 두 AI 에게 일을 맡기고 결과를 리뷰한다 (위임 모드).
이 파일은 **누가 무엇을 하고, 브랜치를 어떻게 나눠 충돌을 막는지**의 원본이다. 2026-11-09 정함.

> **ChatGPT 는 작업 전에 `CLAUDE.md` 를 끝까지 읽는다.** 프로젝트 요약·기술 스택·불변 원칙(§4)·폴더 구조·명령어가 거기 있고, 두 AI 모두 똑같이 지킨다.
> 이름은 CLAUDE.md 지만 Claude 전용 규칙이 아니라 이 프로젝트의 규칙이다.
> 유저에게는 **한국어**로 답한다. 코드 주석·문서는 한국어, 식별자는 영어.

---

## 1. 업무 분담

**기준: 파일(폴더) 단위로 주인을 나눈다.** 같은 파일을 두 AI 가 동시에 고치지 않는 것이 충돌을 막는 가장 확실한 방법이다.

### Claude — 주 개발 (코드·테스트·통합)

| 맡는 일 | 파일 |
|---|---|
| 게임 로직·화면 코드 전부 (R1 루프·UI 개편 구현 포함) | `godot/scripts/**`, `godot/scenes/**`, `godot/project.godot`, `godot/export_presets.cfg` |
| 테스트·스크린샷 도구, 테스트 개수 기록 | `godot/tests/**` |
| 밸런스·손맛 수치 | `config/balance.json` |
| 콘텐츠 **생성 결과물**과 생성 스크립트 | `config/content.json`, `scripts/*.py` (아래 `pro_feed_text.py` 제외), `godot/art/pixel/**`, `*_meta.gd` |
| 그림을 게임에 넣기 (칸 정리·메타 만들기·화면 연결) | `scripts/tidy_sheet.py` 등 → `godot/art/**` |
| 기획·규칙 문서, 마일스톤 기록 | `CLAUDE.md`, `AGENTS.md`, `docs/00-concept.md`, `docs/01-dev-plan.md`, `docs/02-design.md`, `docs/dev-log.md` |
| **main 합치기** (유저가 시킬 때, 테스트 통과 후) | — |

### ChatGPT — 그림·글·리뷰

| 맡는 일 | 파일 |
|---|---|
| **그림 생성**: GPT 이미지로 인게임 도트·일러스트·컷신 그림을 뽑는다 (유저와 함께). 프롬프트와 원본을 남긴다 | `docs/refs/sprites/**`, `docs/refs/cutscene/**`, `docs/refs/**` 의 새 파일 |
| 그림·소리 출처 기록 (쓴 도구·상업 이용 여부) | `licenses/assets.md` (맨 아래에 줄 추가만) |
| **이야기 글**: 이야기 카드·시나리오 초안 (R1 의 프로 2~8년 차 이야기 카드, 돌발 이벤트, 국가대표·FA 장면 등) | `docs/scenario/**` |
| 뉴스·SNS·어그로 기사 문장 | `scripts/pro_feed_text.py` |
| 세계관 보강 (새 인물·구단·대회 — 실명 금지) | `docs/worldview.md` |
| **코드 리뷰**: Claude 브랜치의 PR 을 읽고 버그·개선점을 코멘트로 남긴다 (코드는 고치지 않는다) | — |

### 그 밖의 경우

- 표에 없는 파일, 또는 상대 쪽 파일을 고쳐야 하면 **먼저 유저에게 묻는다.** 유저가 "이번엔 ChatGPT 가 이 화면 코드 해" 처럼 명시하면 그 작업만 예외다 — 그때는 브랜치 이름과 PR 설명에 고친 파일 범위를 적는다.
- 상대 영역에서 고칠 점을 발견하면 직접 고치지 말고 PR 설명이나 `docs/handoff/` 메모(§4)로 넘긴다.
- ChatGPT 일반 채팅(저장소 접근 없음)으로 만든 결과물은 유저가 파일로 넣거나 Claude 에게 넘긴다. 그때도 위 폴더 규칙을 따른다.

---

## 2. 브랜치 규칙

| 항목 | 규칙 |
|---|---|
| 브랜치 이름 | Claude: `claude/<작업>` · ChatGPT: `codex/<작업>` (또는 `gpt/<작업>`). 작업 이름은 짧은 영어 (`codex/pro-y2-cards`) |
| 시작 | **항상 최신 main 에서 딴다**: `git fetch origin && git switch -c codex/<작업> origin/main` |
| main | **main 에 직접 커밋·푸시하지 않는다.** 강제 푸시(`--force`)는 어느 공유 브랜치에도 금지 (자기 브랜치는 `--force-with-lease` 만) |
| 크기 | 한 브랜치 = 한 가지 일. 오래 끌지 않는다 (가능하면 하루~이틀 안에 합치기) |
| 합치기 전 | 최신 main 을 자기 브랜치에 먼저 합쳐(`git merge origin/main`) 충돌을 자기 브랜치에서 푼다 → 테스트·점검 통과 확인 |
| 합치기 | Claude 가 유저 요청을 받아 main 에 합친다. 커밋 제목 `합치기: <무엇> (<브랜치>), 테스트 N건 통과` |
| 커밋 메시지 | 한국어, 무엇을 왜 바꿨는지. 커밋·푸시는 유저가 시킬 때 |

---

## 3. 충돌이 잘 나는 파일 — 특별 규칙

| 파일 | 규칙 |
|---|---|
| `config/content.json` | **생성 파일.** 손으로 고치지 않는다. ChatGPT 는 원본(`docs/scenario/*.md`, `scripts/pro_feed_text.py`)만 커밋하고, 확인용으로 `python3 scripts/build_scenario.py` · `python3 scripts/check_content.py` 를 돌렸다면 **커밋 전에 `git checkout config/content.json` 으로 되돌린다.** 합칠 때 Claude 가 다시 생성한다 |
| `godot/art/pixel/**`, `*_meta.gd`, `godot/data/` | 생성 파일. Claude 만 스크립트로 만든다 |
| `docs/dev-log.md`, `licenses/assets.md` | **맨 끝에 덧붙이기만** 한다 (중간 수정·정렬·표 재배치 금지). `.gitattributes` 의 `merge=union` 으로 둘 다 끝에 덧붙인 경우 자동으로 합쳐진다. ChatGPT 가 dev-log 에 쓸 때는 제목에 `[ChatGPT]` 를 붙인다 |
| `CLAUDE.md`, `AGENTS.md` | Claude 만 고친다. ChatGPT 가 규칙 변경을 원하면 PR 설명이나 `docs/handoff/` 메모로 제안한다 |
| `config/balance.json`, `godot/project.godot` | Claude 만 |
| 공통 | 저장소 전체 포맷터 실행·줄바꿈(CRLF) 변경·파일 일괄 이름 바꾸기·폴더 이동 금지. 고친 줄만 바뀌게 한다 |

---

## 4. 서로 넘겨주기

- **PR 설명**에 적는다: 무엇을 했나 / 고친 파일 / 확인한 방법(돌린 테스트·점검) / 상대에게 넘길 일.
- 긴 인수인계는 `docs/handoff/<날짜>-<보내는쪽>-<주제>.md` 새 파일로 쓴다 (예: `docs/handoff/2026-11-10-gpt-pro-cards.md`). **새 파일이라 충돌이 나지 않는다.** 처리가 끝나면 받는 쪽이 지운다.
- 이야기 카드 초안은 지금 `docs/scenario/` 문서 형식(대사·선택지·효과 표기)을 그대로 따른다. 형식이 엔진에 없는 것을 요구하면 메모로 Claude 에게 넘긴다 (엔진 쪽 추가는 Claude).
- 그림을 넣었으면 메모에 파일 경로·칸 크기·칸 순서·기준 그림을 적어 준다. Claude 가 `tidy_sheet.py` 로 정리해 게임에 붙인다.

---

## 5. 확인 방법

| 바꾼 것 | 확인 |
|---|---|
| 코드 | `~/Applications/Godot.app/Contents/MacOS/Godot --headless --path godot --script res://tests/run_tests.gd` 전부 통과 |
| 시나리오·문자·뉴스 문장 | `python3 scripts/build_scenario.py` (또는 `pro_content.py`) → `python3 scripts/check_content.py` 통과 (그다음 `content.json` 되돌리기, §3) |
| 그림 | 원본과 프롬프트가 `docs/refs/` 에, 출처가 `licenses/assets.md` 에 있는지 |

Godot 이 없는 환경(Codex 클라우드 등)에서는 테스트를 못 돌렸다고 PR 에 적는다. 합칠 때 Claude 가 돌린다.
