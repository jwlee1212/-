# 옛 Kotlin 엔진 문서 (참고용)

단장 시뮬레이션 시절 Kotlin 엔진을 설명하는 문서다. **유저 시점 서술(단장이 하는 결정, 화면 등)은 지금 게임과 맞지 않는다.**
지금은 Godot로 옮겨 왔고, 이 문서는 나중에 다시 만들 부분을 설계할 때 참고한다:

- `04-plate-appearance.md`, `05-game-logic.md` — 타석 단위 자동 시뮬레이션(Log5), 주루·기록 (G4)
- `08-condition.md` — 피로·부상·폼 (G5)
- `09-development.md` — 성장·노화 곡선 (G5, G7)
- `03-league-generation.md`, `10-scouting-draft.md`, `11-fa-trade.md`, `12-foreign-military.md` — 리그·드래프트·FA·군 복무 (G7)

Kotlin 코드와 캘리브레이션된 수치(옛 `config/balance.json`)는 git 태그 `pre-godot`에 있다.
새 기획은 `docs/00-concept.md`부터 읽는다.
