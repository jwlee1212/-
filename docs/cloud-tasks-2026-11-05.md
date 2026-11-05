# 클라우드 세션 작업 (2026-11-05 밤)

> 남은 크레딧 약 50달러. 세션 셋을 **동시에** 돌린다 (+ 남으면 넷째).
> **전제: 통합 먼저.** 지금 일이 브랜치 넷에 흩어져 있다 — `claude/g8-batter-rig`(기준) · `claude/ep05-seongho-cutscene-redraw-kwykjs`(컷신 + 로컬 다양화·Gemini 정지 컷) · `claude/soyul-pitch-hint` · `claude/y2-story-cards`(2학년 EP09~13).
> 세 브랜치를 `claude/g8-batter-rig` 에 합친 뒤(로컬에서 Claude 가 한다), 아래 세션은 모두 **합친 `claude/g8-batter-rig`** 에서 시작한다.
> 셋 다 `config/content.json` 을 건드리니 서로의 영역을 지킨다. 충돌은 `python3 scripts/build_scenario.py` 를 다시 돌려 푼다.

## 세션 1 — 이야기: 2학년 나머지 + 3학년 (가장 큼)

```text
저장소의 claude/g8-batter-rig 브랜치에서 시작해. 밤새 혼자 진행하는 작업이야. 유저는 아침에 결과를 보고 고칠 거야.

먼저 읽어: CLAUDE.md, docs/00-concept.md, docs/worldview.md, docs/scenario/ 의 모든 문서(특히 scenario_y1_ep01-08.md, scenario_y2_ep09-13.md, scenario_y2y3_outline.md, card-plan.md, cutscene-plan.md), scripts/build_scenario.py, docs/dev-log.md 의 마지막 항목들.

## 할 일
1. 2학년 EP14~16 과 3학년 EP17~24 를 카드 형식으로 써. 2학년 EP09~13 은 이미 있어 (scenario_y2_ep09-13.md, 클라우드가 쓴 것). scenario_y2_ep09.md 는 그 전에 쓴 초안이니까, 겹치면 ep09-13 을 따르고 초안 파일은 지워.
   - 줄거리는 scenario_y2y3_outline.md 의 표를 따라.
   - 형식은 1학년과 같아: 장면은 카드 한 장, 대사는 1~4줄, 선택지에는 반드시 "| 힌트" 를 달고, 고르면 반드시 무언가(능력치, 관계, 진로)가 바뀌어야 해. 경기 밖 결정은 한 주 0~3개.
2. 사이드 스토리 문자 (content.json messages, m_y2_*·m_y3_*): 하린·소율·서지안의 데이트 → 고백 갈래(연애는 한 명만), 3학년 응원 문자, 결과 날 문자.
3. 드래프트(EP22)는 worldview.md 5-3 의 표대로 관심도 등급 → 구단. 미지명이면 육성선수 / 대학 진학을 고르게 해.
4. 컷신을 넣을 자리에는 CUTSCENE 줄을 쓰지 말고 "※ 컷신 후보: <id> — 한 줄" 주석만 달아. 그리고 docs/scenario/cutscene-gemini-prompts.md 에 2·3학년 컷신의 장면 프롬프트를 1학년과 같은 형식으로 덧붙여 (공통 문구와 인물 기준 그림 첨부 규칙은 그대로, 주인공 얼굴은 안 보이게).
5. build_scenario.py 가 새 문서도 읽게 하고, 테스트(3년 자동 진행에서 모든 장면이 나오는지, 선택지 힌트, 카드 길이)를 추가해.

## 유저에게 물을 것
개요 문서 맨 아래 "유저 확인 필요" 6개는 아직 답이 없어. 개요에 적힌 임시 결정을 따르고, PR 설명 맨 위에 다시 모아 적어. 새로 생긴 큰 결정도 같은 목록에 넣어.

## 건드리지 마
컷신 코드와 그림(godot/scripts/cutscene/, cutscene_view.gd, make_cutscene_art.py, cutscene_grids.py, content.json 의 cutscenes), 밸런스 수치(balance.json)는 다른 세션이 맡고 있어.

## 환경과 확인
Godot 4.7.2 리눅스 판 (https://github.com/godotengine/godot/releases 의 4.7.2-stable). 설치가 안 되면 대본은 계속 쓰고 테스트를 못 돌렸다고 보고해. 헤드리스 run_tests.gd 가 전부 통과해야 해.

## 마무리
새 브랜치에서 작업하고, 커밋은 에피소드 묶음마다 나눠. 푸시한 다음 claude/g8-batter-rig 를 대상으로 PR을 열어. docs/dev-log.md 와 CLAUDE.md(문서 목록, 테스트 목록)도 고쳐. content.json 충돌은 build_scenario.py 를 다시 돌려 풀어.
```

## 세션 2 — 컷신: 그림만 넣으면 붙는 구조 + 음악

```text
저장소의 claude/g8-batter-rig 브랜치에서 시작해. 밤새 혼자 진행하는 작업이야.

먼저 읽어: CLAUDE.md (원칙 6의 컷신 규칙, 외부 그림 정지 컷), docs/scenario/cutscene-gemini-prompts.md, docs/dev-log.md 의 컷신 항목들, godot/scripts/cutscene/cutscene_plan.gd, godot/scripts/screens/cutscene_view.gd, scripts/import_cutscene_stills.py, config/content.json 의 cutscenes.

## 할 일
1. 대신 트는 컷 (fallback): 정지 그림 컷(still)의 그림 파일이 아직 없으면, 지금 있는 도트 판 컷으로 대신 틀게 해. 예: {"still": "ep05_seongho_5", "fallback": {도트 컷 정의}}. CutscenePlan.validate 와 DialogueView 의 건너뛰기 검사도 이에 맞춰 고쳐.
2. 1학년 컷신 9개(ep01_join 은 이미 됨)의 정의를 정지 그림 컷으로 미리 짜. 파일 이름은 cutscene-gemini-prompts.md 와 같게 하고, 지금 도트 컷은 fallback 으로 남겨. 그림이 들어오면 import_cutscene_stills.py 한 번만 돌리면 바뀌어야 해. 컷마다 pan, zoom, hold, flash, shake, sfx, 자막을 영화처럼 짜. 대사는 scenario_y1_ep01-08.md 에서 가져와.
3. 컷신 음악 한 곡: scripts/make_bgm.py 에 "cutscene" 곡(잔잔한 패드 + 피아노, 루프)을 추가하고, MusicPlayer TRACKS 와 balance.json presentation.bgm 에도 넣어. 컷신이 시작되면 이 곡으로 크로스페이드하고, 끝나면 원래 곡으로 돌아가게 해. 컷신 정의에 "music": false 를 쓰면 음악 없이 (성호 컷신처럼 정적이 중요한 장면).
4. 정지 그림 크기: 29장 기준 웹 빌드가 얼마나 커지는지 재고, 화질 차이가 거의 없는 압축(Godot 가져오기 설정 등)을 찾아 적용해.
5. 위 기능들의 테스트를 run_tests.gd 에 추가해.

## 지켜
원칙 2~5 (시드로 정해진 난수, 시계 하나로 잰 시간, 수치는 balance.json, 색은 Tokens). 주인공 얼굴은 보이지 않게.
이야기 문서(docs/scenario/*.md 대본, build_scenario.py 의 2·3학년 부분)와 messages 는 다른 세션이 맡고 있으니 건드리지 마.

## 환경과 확인
pip install numpy pillow. Godot 4.7.2 리눅스 판. 헤드리스 테스트 전부 통과. 스크린샷은 xvfb-run -a 로 res://tests/shots_cutscene.tscn 을 먼저 시도하고, 안 되면 파이썬 미리보기로 대신해. 무엇으로 확인했는지 적어.

## 마무리
새 브랜치, 기능마다 커밋. 푸시하고 claude/g8-batter-rig 대상 PR. dev-log·CLAUDE.md 갱신. content.json 충돌은 build_scenario.py 를 다시 돌려 풀어.
```

## 세션 3 — 밸런스 점검 리포트 (코드는 거의 안 건드린다)

```text
저장소의 claude/g8-batter-rig 브랜치에서 시작해. 리포트를 쓰는 작업이야. 수치는 바꾸지 말고 제안만 해 (손맛과 밸런스 결정은 유저가 한다).

먼저 읽어: CLAUDE.md, docs/dev-log.md, godot/tests/calibrate.gd, godot/tests/calibrate_career.gd, config/balance.json.

## 할 일
1. calibrate.gd 와 calibrate_career.gd 를 여러 시드(20개 이상)로 돌려서 표로 정리해: 타율, 삼진율, 홈런 수, 고교 3년 능력치 분포, 스카우트 관심도 등급 분포, 드래프트 예상 분포(worldview.md 5-3 기준 S/A/B/C). 한국 고교·프로 기록 수준과 비교해.
2. 1학년 카드판 이후 달라진 것: 폼 개조를 바꿨을 때와 지켰을 때의 2학년 시작 능력치 차이, 겨울 훈련 방향별 차이, 사이드 스토리 문자가 3년 동안 실제로 몇 통 오고 언제 몰리는지(한 주 최대 2통), 호감 보상 문자(관계 30·35·40·45)에 언제 닿는지.
3. 이상한 점(한 선택이 너무 세다, 아무도 못 받는 보상, 문자가 한 주에 몰린다 등)을 찾아서, 수치를 바꿀 제안을 "이걸 바꾸면 체감이 이렇게 달라진다" 형식으로 써.
4. 결과는 docs/reports/balance-2026-11.md 로. 측정용 스크립트를 새로 만들면 godot/tests/ 에 두고 CLAUDE.md 명령 목록에 적어.

## 환경
Godot 4.7.2 리눅스 판 (설치가 안 되면 멈추고 보고해 — 이 작업은 실행이 전부야).

## 마무리
새 브랜치, 커밋, 푸시, claude/g8-batter-rig 대상 PR. balance.json·content.json 은 바꾸지 마.
```

## (선택) 세션 4 — 프로 편 설계 문서 (크레딧이 남으면)

```text
저장소의 claude/g8-batter-rig 브랜치에서 시작해. 설계 문서만 쓰는 작업이야 (코드 없음).

docs/worldview.md 의 5장(KBA 프로리그)과 7장(연표), docs/00-concept.md, docs/01-dev-plan.md 의 G7 을 읽고,
"드래프트 → 2군 → 1군 → 신인왕 → 병역 → FA(메인 엔딩)" 까지의 게임 구조를 docs/pro-design-draft.md 로 써.
한 주 루프가 고교와 어떻게 달라지는지, 경기 수(144경기)를 어떻게 줄여 보여 줄지(하이라이트·자동), 경기 밖 결정이 한 주 0~3개를 넘지 않게 하는 방법,
메인 스토리 카드 목록(연차별), 컷신 후보, 사이드 스토리를 정리해. 큰 결정마다 선택지 2~3개와 추천을 적어서 유저가 고를 수 있게 해.
새 브랜치에 커밋, 푸시, claude/g8-batter-rig 대상 PR.
```
