# CLAUDE.md — 야구 단장 시뮬레이션 (가칭)

Claude Code가 이 저장소에서 작업할 때 가장 먼저 읽는 파일이다. 기획 세부 사항은 `docs/`에 있고, 이 파일은 프로젝트 요약, 불변 원칙, 작업 방식, 개발 순서를 담는다.

---

## 1. 프로젝트 한 줄 요약

2026년 한국 프로야구와 같은 구조의 **가상 리그**에서 신임 단장이 되어 스카우트·드래프트·FA·트레이드·구단 경영으로 팀을 운영하는 **텍스트 기반 모바일 단장 시뮬레이션**. 경기는 타석 단위로 자동 시뮬레이션되고, 유저는 주 단위로 결정한다.

- 실제 구단명·선수명·로고·마스코트·팀 컬러는 **절대 사용하지 않는다** (상표·초상권)
- 리그 제도(144경기, 5팀 포스트시즌, FA 등급, 샐러리캡, 군 복무 등)는 현실 KBO를 따른다
- 최신 규정 변화(ABS, 피치클락, 수비 시프트 제한 등)는 시뮬레이션에 반영하지 않는다

## 2. 기술 스택

| 항목 | 선택 |
|---|---|
| 언어 | Kotlin |
| 구조 | Kotlin Multiplatform (엔진은 `commonMain`) |
| 타깃 | JVM(콘솔 프로토타입, 도구, 테스트) → Android + iOS |
| UI | Compose Multiplatform (M10부터) |
| 직렬화 | kotlinx.serialization (JSON) |
| 테스트 | kotlin.test |
| 빌드 | Gradle Kotlin DSL |

## 3. 작업 방식: 위임 모드

유저는 **위임 모드**를 선택했다. Claude Code가 설계·구현·테스트를 주도하고, 유저는 리뷰하며 이해한다. 그래서 아래를 반드시 지킨다.

1. **마일스톤 단위로 작업한다.** 한 번에 여러 마일스톤을 섞지 않는다 (§6).
2. **마일스톤이 끝날 때마다 `docs/dev-log.md`에 기록한다.**
   - 무엇을 만들었는지 (파일·클래스 목록)
   - 핵심 로직이 **왜 그렇게 동작하는지** 한국어로 설명 (유저가 읽고 이해할 수 있는 수준)
   - 테스트 결과와 캘리브레이션 수치
   - 기획과 다르게 구현한 부분과 이유
3. **기획 문서와 충돌하면 임의로 결정하지 말고 유저에게 묻는다.** 기획에 없는 세부 사항은 가장 단순한 방식으로 구현하고 dev-log에 "임시 결정"으로 남긴다.
4. **"확인 필요" 표시가 있는 현실 규정 수치**(§8)는 `config/balance.json`에 임시값을 넣고 `TODO(verify)` 주석과 함께 dev-log에 목록으로 남긴다. 추측한 값을 확정값처럼 쓰지 않는다.
5. 코드 주석과 문서는 **한국어**, 식별자(클래스·함수·변수명)는 영어.

## 4. 불변 원칙 (어기면 안 됨)

1. **엔진은 UI와 완전히 분리한다.** 엔진 코드(`commonMain`)에는 `println`, Android/iOS API, Compose 코드가 없다. 엔진은 상태와 `GameEvent` 목록만 만들고, 화면(콘솔·모바일)이 그것을 소비한다.
2. **모든 랜덤은 시드를 받은 `kotlin.random.Random` 인스턴스로만 발생시킨다.** 전역 랜덤, 시스템 시간 기반 랜덤 금지. 주차마다 시드를 세이브에 저장해 "같은 결정 → 같은 결과"를 보장한다 (세이브 스컴 방지, 버그 재현).
3. **밸런스 수치는 코드에 하드코딩하지 않는다.** 모든 확률·배율·임계값은 `config/balance.json`에서 읽는다.
4. **숨김 수치는 UI 계층이 직접 읽을 수 없다.** 선수의 진짜 값(`HiddenTraits`, 타 팀 선수의 실제 능력치)은 `ScoutingView`를 거쳐 정확도에 따른 범위·등급으로만 노출한다. 가시성 제한(internal 등)으로 구조적으로 막는다.
5. **관전용 시뮬레이션을 따로 만들지 않는다.** 시뮬레이터는 항상 같은 이벤트 스트림을 만들고, 문자 중계는 그 스트림을 렌더링할 뿐이다.
6. **검증 테스트가 통과해야 다음 마일스톤으로 간다** (§7).

## 5. 모듈(패키지) 구조

```
engine (commonMain)
├─ model/        Player, Batter, Pitcher, HiddenTraits, Contract, MilitaryStatus,
│                Team, ParentCompany, Manager, Coach, MedicalStaff, GeneralManager
├─ league/       League, Schedule, Standings, LeagueEnvironment, Park
├─ sim/          Log5, RatingTables, PlateAppearanceSim, BaseRunning, GameSimulator,
│                GameState, BaseState, GameEvent, GameRules(정규/포스트시즌)
├─ tactics/      DirectiveSheet, DirectivePreset, ManagerAI, ManagerTendency
├─ stats/        StatsRecorder, StatLine(좌우 분리), BoxScore, BoxScoreValidator,
│                Sabermetrics, LeagueConstants, War
├─ season/       Calendar, WeekLoop, Inbox, AutoAdvance, Roster(엔트리), FuturesLeague(2군 추정)
├─ condition/    Fatigue, Injury, Form
├─ development/  Growth, AgingCurves, Awakening, Retirement
├─ scouting/     ScoutingView, ScoutingBudget, ScoutReport
├─ market/       Valuation, PositionNeed, Trade, TradeAI, FreeAgency, Bidding,
│                Draft, DraftPickValue, Waivers, SalaryCap, ForeignPlayers
├─ management/   Finance, OwnerTrust, FanSentiment, StaffMarket, Career, Achievements
├─ events/       InternationalTournament, MilitaryService, LeagueEnvironmentEvent
└─ io/           LeagueLoader, SaveGame, BalanceConfig

tools (jvmMain)  LeagueGenerator, NameGenerator, TeamTemplate, Calibrator, BalanceSimulator
cli   (jvmMain)  콘솔 프로토타입 화면
app   (M10~)     Compose Multiplatform 화면
```

## 6. 개발 순서 (마일스톤)

상세 완료 기준은 `docs/15-dev-plan.md`.

**프로토타입 — 목표: 콘솔에서 여러 시즌이 굴러간다**

| 단계 | 내용 | 관련 문서 |
|---|---|---|
| M0 | 프로젝트 세팅 (KMP, kotlinx.serialization, kotlin.test) | — |
| M1 | 선수·팀 모델 + 리그 생성기 → `data/league_2026.json` | 02, 03 |
| M2 | 타석·경기 시뮬레이션 + 박스스코어 검증기 + 1단계 기록 | 04, 05 |
| M3 | 사전 지시 규칙표 + 감독 AI | 06 |
| M4 | 주간 루프 + 컨디션 + 캘리브레이션 | 07, 08 |
| M5 | 성장·노화·은퇴 + 30시즌 밸런스 테스트 | 09 |

**정식 버전**

| 단계 | 내용 | 관련 문서 |
|---|---|---|
| M6 | 스카우트·드래프트·지명권 트레이드 | 10 |
| M7 | FA(역제안·경쟁 입찰)·트레이드·소프트캡·2단계 세이버 지표·WAR | 11, 04 |
| M8 | 외국인·군 입대·국제대회 | 12 |
| M9 | 구단 경영·스태프·커리어 모드·업적 | 13, 14 |
| M10 | 모바일 화면 (Compose Multiplatform) | 15 |
| M11 | 세이브·난이도·아이언맨·최종 밸런싱 | 14 |

## 7. 필수 테스트

| 테스트 | 기준 | 도입 |
|---|---|---|
| 박스스코어 검증 | 모든 경기에서 등식 성립 (`docs/05`) | M2 |
| 대량 경기 | 1,000시즌 시뮬레이션 중 검증 실패 0건 | M2~M4 |
| 캘리브레이션 | 100시즌 평균이 목표 범위 안 (`docs/04`) | M4 |
| 장기 밸런스 | 30시즌 후 주전 평균 능력치 60±3, 90+ 선수 수 안정 | M5 |
| 재현성 | 같은 시드·같은 결정 → 완전히 같은 결과 | M1부터 |
| 정보 은닉 | UI 계층에서 진짜 값 접근 불가 (컴파일 수준) | M6 |

## 8. 확인 필요 규정 (임시값으로 구현 후 유저 확인)

현실 KBO 규정 중 정확한 수치를 확인하지 못한 항목이다. 임시값을 쓰고 `TODO(verify)`로 표시한다.

- 1군 엔트리 등록·출전 인원 (임시: 등록 28명)
- 말소 후 재등록 제한 기간 (임시: 10일 → 게임에선 "다음 주 불가, 그다음 주 가능")
- FA 자격 연수 (임시: 고졸 8시즌, 대졸 7시즌, 재자격 4시즌) 및 1시즌 인정 기준
- FA 등급(A/B/C) 기준과 보상선수·보상금 규정
- 경쟁균형세(샐러리캡) 금액과 초과 제재 비율
- 최저 연봉
- 신인 드래프트 라운드 수와 라운드별 순번 방식 (임시: 11라운드, 매 라운드 전년도 역순)
- 외국인 선수 보유·출전 규정, 신규 계약 연봉 상한, 시즌 중 교체 횟수와 마감일
- 병역 특례 기준 (아시안게임 금메달, 올림픽 메달), 대표팀 연령 제한과 와일드카드 인원
- 입대 연기 가능 나이 (임시: 만 28세 전후)

확인된 규정: 정규시즌 연장 11회 제한 + 무승부(승률 계산 제외), 포스트시즌 연장 15회 + 무승부 시 추가 경기, 와일드카드 4위 1승 어드밴티지(4위 홈, 4위는 1승 또는 1무로 진출).

## 9. 문서 목록

| 파일 | 내용 |
|---|---|
| `docs/01-league.md` | 리그 구조, 10개 구단, 전력·예산·지명권 |
| `docs/02-player-model.md` | 능력치, 숨김 수치, 정보 정확도, 좌우 상성 |
| `docs/03-league-generation.md` | 고정 리그 생성 절차와 팀 템플릿 |
| `docs/04-plate-appearance.md` | Log5 타석 시뮬레이션, 보정, 캘리브레이션, 기록 지표 |
| `docs/05-game-logic.md` | 경기 진행, 주루, 자책점, 투수 기록, 박스스코어 검증 |
| `docs/06-directives.md` | 사전 지시 규칙표, 감독 캐릭터·AI |
| `docs/07-weekly-loop.md` | 캘린더, 주간 루프, 엔트리, 자동 진행, 문자 중계 |
| `docs/08-condition.md` | 피로, 부상, 폼 |
| `docs/09-development.md` | 성장·노화, 코치, 은퇴 |
| `docs/10-scouting-draft.md` | 스카우트, 9월 드래프트, 지명권 트레이드 |
| `docs/11-fa-trade.md` | 가치 평가, FA, 트레이드, 소프트캡 |
| `docs/12-foreign-military.md` | 외국인 선수, 군 입대, 국제대회 |
| `docs/13-management.md` | 재정, 구단주, 팬심, 스태프, 커리어 |
| `docs/14-game-flow.md` | 목표, 모드, 난이도, 포스트시즌, 세이브 |
| `docs/15-dev-plan.md` | 마일스톤 상세, 화면 구성, 백로그 |
| `data/teams.json` | 10개 구단 초기값·생성 파라미터 |
| `config/balance.json` | 밸런스 수치 초안 |
