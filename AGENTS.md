# AGENTS.md — 두 AI 의 협업 규칙

이 저장소는 **Claude (Claude Code)** 와 **ChatGPT (Codex·ChatGPT)** 가 함께 작업한다. 유저는 두 AI 에게 일을 맡기고 결과를 리뷰한다 (위임 모드).
이 파일은 **누가 무엇을 하는지, 충돌을 어떻게 막는지**의 원본이다. 2026-11-09 정함, **2026-11-10 개정 — Codex 는 이미지 생성만**.

> **Codex 는 작업 전에 `CLAUDE.md` 를 읽는다.** 특히 그림 규칙(§4-6: 실명·실제 로고 금지, 사진 같은 AI 얼굴 금지, 출처 기록)과 세계관 이름(`docs/worldview.md`)을 지킨다.
> 유저에게는 **한국어**로 답한다.

---

## 1. Codex 에게 — 이미지만 만들어서 폴더에 넣어 둔다 (2026-11-10 유저 지시)

### 하는 일

1. **이미지 생성**: 유저가 요청한 그림(인게임 2D SD 캐릭터·배경·UI 그림·일러스트·컷신 장면·엠블럼 등)을 GPT 이미지로 만든다. 사양은 요청 메모(`docs/handoff/`)나 `docs/02-design.md` §5-3 의 그림 목록을 따른다
2. **폴더에 넣기**: 원본 그림과 그 그림을 만든 프롬프트를 같이 넣는다
   - 캐릭터·인게임 그림 → `docs/refs/sprites/<이름>_v<번호>.png` + `<이름>_v<번호>_prompt.md`
   - 컷신 장면 → `docs/refs/cutscene/`
   - 대화 인물 → `docs/refs/portraits/`
   - 화면(UI) 시안·배경 → `docs/mockups/`
   - 이미 있는 파일은 덮어쓰지 않는다 — 새 버전 번호로 새 파일
3. **출처 기록**: `licenses/assets.md` **맨 끝에** 줄을 덧붙인다 (파일 경로·쓴 도구·상업 이용 여부). 중간 수정·표 재배치 금지
4. **인게임 화면 캡처** (그림이 게임에서 어떻게 보이는지 확인용): 스크린샷 도구만 돌린다
   ```
   ~/Applications/Godot.app/Contents/MacOS/Godot --always-on-top --path godot res://tests/shots.tscn
   ```
   (특정 화면은 `res://tests/shots_<이름>.tscn`, 결과는 `godot/build/shots/` — 커밋하지 않는다). 남겨 둘 캡처는 `docs/refs/screens/<날짜>-<내용>.png` 로 복사한다
5. **알리기**: 끝나면 `docs/handoff/<날짜>-gpt-<주제>.md` 새 파일에 만든 그림 목록·크기·칸 배치(애니메이션이면 칸 순서·칸 크기)·기준 그림을 적는다. 게임에 넣는 일은 Claude 가 한다

### 하지 않는 일

- **테스트를 돌리지 않는다** (`run_tests.gd`, `calibrate*.gd`, `check_content.py` 등). 스크린샷 도구(`shots*.tscn`)만 예외
- **코드·설정·문서를 고치지 않는다**: `godot/` 아래 전부(스크립트·장면·`godot/art/`·`project.godot`), `scripts/`, `config/`, `CLAUDE.md`·`AGENTS.md`·`docs/` 의 기존 문서
- 그림을 게임 폴더(`godot/art/**`)에 직접 넣거나, 칸 정리·변환 스크립트(`tidy_sheet.py` 등)를 돌리지 않는다
- 이야기·대사·뉴스 문장·세계관을 쓰지 않는다 (유저가 따로 시킨 경우만)

---

## 2. Claude — 나머지 전부

| 맡는 일 | 파일 |
|---|---|
| 게임 로직·화면(UI)·테스트 | `godot/**` 전부 |
| Codex 가 넣은 그림을 게임에 붙이기 (칸 정리·메타·화면 연결·`godot/art/` 로 옮기기) | `scripts/tidy_sheet.py` 등 → `godot/art/**`, `godot/scripts/characters/**` |
| 밸런스·콘텐츠·이야기 | `config/**`, `docs/scenario/**`, `scripts/**` |
| 기획·규칙 문서, 마일스톤 기록 | `CLAUDE.md`, `AGENTS.md`, `docs/**` (Codex 그림 폴더 제외) |
| 테스트 실행, **main 합치기** (유저가 시킬 때, 테스트 통과 후) | — |

---

## 3. 브랜치·충돌 규칙

| 항목 | 규칙 |
|---|---|
| 브랜치 이름 | Claude: `claude/<작업>` · Codex: `codex/<작업>`. 작업 이름은 짧은 영어 |
| 시작 | **항상 최신 main 에서 딴다**: `git fetch origin && git switch -c codex/<작업> origin/main`. 유저의 메인 폴더(`~/Downloads/baseball-gm`)에서 브랜치를 바꾸지 않는다 — Codex 는 자기 worktree 에서 작업한다 |
| main | **main 에 직접 커밋·푸시하지 않는다.** 강제 푸시 금지 |
| Codex 커밋 | 새 그림·프롬프트·`docs/refs/screens/` 캡처·`docs/handoff/` 메모·`licenses/assets.md` 끝 줄만. 그 밖의 파일이 바뀌어 있으면 커밋하지 말고 되돌린다 |
| 합치기 | Claude 가 유저 요청을 받아 main 에 합친다. 커밋 제목 `합치기: <무엇> (<브랜치>), 테스트 N건 통과` |
| 커밋 메시지 | 한국어, 무엇을 왜. 커밋·푸시는 유저가 시킬 때 |
| 기록 파일 | `docs/dev-log.md`·`licenses/assets.md` 는 맨 끝에 덧붙이기만 (`.gitattributes` 의 `merge=union`) |
| 공통 | 저장소 전체 포맷터·줄바꿈 변경·일괄 이름 바꾸기·폴더 이동 금지 |
