class_name HsSim
extends RefCounted
## 고교 경기 시뮬레이션 (로직 — 화면은 홈의 [이 경기 시뮬레이션] · [달력]).
## 고교는 한 주에 경기 하나(토요일, GameCalendar.hs_game_day). 모든 경기를 직접 하거나 시뮬레이션할 수 있다 —
## 단 이야기가 정한 경기(경기 대본 · 연출 장면 · 한 번뿐인 경기 중 이야기)는 직접 한다 (고르는 순간이 그 경기 안에 있어서).
## 시뮬레이션해도 내 타석은 내 능력치로 자동 타석(GameRunner.sim_to_end) — 성장·기록·스카우트 관심은 똑같이 쌓인다.
## "날짜까지 시뮬레이션"은 이야기 카드 · 이야기 경기 · 미니게임 · 시즌 끝에서는 늘 멈추고, stops 에서 켠 일(새 부상)에도 멈춘다.

const STOP_TEXT := {"date": "정한 날짜에 도착했다", "story": "이야기가 생겨 멈췄다", "story_game": "이야기가 있는 경기라 직접 한다",
	"minigame": "할 일이 생겨 멈췄다", "injury": "부상으로 멈췄다", "plan": "평일 스케줄을 먼저 채워야 한다", "season_end": "시즌이 끝났다",
	"guard": "멈췄다"}


## 이번 주 경기를 시뮬레이션해도 되나 (이야기가 정한 경기·캠프·이미 한 주는 안 된다)
static func can_sim(career: CareerState) -> bool:
	if career.pro != null or career.game_done or career.is_season_over() or career.in_camp():
		return false
	if not career.game_plan.is_empty() or career.pending_minigame != "" or not career.pending_drill().is_empty():
		return false
	if not career.book.staged_story(career, career.next_role()).is_empty():
		return false
	for m: String in ["first_pa", "pinch_pa", "clutch_pa"]:
		if career.book.has_game_story(career, m):
			return false
	return true


## 이번 주 경기를 시뮬레이션한다 (평일 스케줄을 아직 안 돌렸으면 지금 계획대로). 돌려주는 값: {"game", "notes"}
static func sim_game(career: CareerState) -> Dictionary:
	assert(can_sim(career), "이 경기는 시뮬레이션할 수 없다")
	if career.week_training == "":
		if career.plan_ready():
			career.run_schedule()
		else:
			career.train("rest")
	var game := career.new_game()
	if game.me_playing and not game.fixed:
		game.sim_to_end(career.rng_for("sim-game").seed)
	else:
		while not game.state.over:
			game.step()
	var notes := career.finish_game(game)
	return {"game": game, "notes": notes}


## 시뮬레이션 결과 한 줄 ("vs 청운고 5:3 승 · 4타수 2안타")
static func result_text(game: GameRunner) -> String:
	var r := game.my_result()
	var line := game.my_line.line_text() if game.me_playing else game.absent_text
	return "vs %s %d:%d %s · %s" % [game.opponent.name, game.state.score[game.my_side], game.state.score[1 - game.my_side],
		["패", "무", "승"][r + 1], line]


## target 날짜(그날 경기 전)까지 시뮬레이션. 돌려주는 값: {"stop", "text", "notes", "games": [결과 한 줄…]}
static func sim_until(career: CareerState, target: int, stops: Dictionary = {}) -> Dictionary:
	var notes: Array[String] = []
	var games: Array[String] = []
	for guard in 200:
		if career.in_camp() or career.is_season_over():
			return _stop("season_end", notes, games)
		if career.game_done:
			if not career.events("after").is_empty():
				return _stop("story", notes, games)
			if GameCalendar.hs_game_day(career.cfg, career.player.grade, career.week + 1) > target:
				return _stop("date", notes, games)
			career.advance_week()
			if career.is_season_over():
				return _stop("season_end", notes, games)
			if not career.events("start").is_empty():
				return _stop("story", notes, games)
			continue
		if GameCalendar.hs_today(career) >= target:
			return _stop("date", notes, games)
		if career.pending_minigame != "" or not career.pending_drill().is_empty():
			return _stop("minigame", notes, games)
		if not career.events("start").is_empty():
			return _stop("story", notes, games)
		if career.week_training == "" and not career.plan_ready():
			return _stop("plan", notes, games)
		if not can_sim(career):
			return _stop("story_game", notes, games)
		var hurt := career.player.is_injured()
		var r := sim_game(career)
		notes.append_array(r["notes"])
		games.append(result_text(r["game"]))
		if career.player.is_injured() and not hurt and bool(stops.get("injury", true)):
			return _stop("injury", notes, games)
	return _stop("guard", notes, games)


static func _stop(reason: String, notes: Array[String], games: Array[String]) -> Dictionary:
	return {"stop": reason, "text": STOP_TEXT.get(reason, ""), "notes": notes, "games": games}


## y 년 m 월 고교 달력 칸 {날짜: {"title", "sub", "state", "pick", "me"}} — 지난 경기(기록실) · 이번 주 경기 · 남은 토요일
static func calendar_cells(career: CareerState, y: int, m: int) -> Dictionary:
	var cfg := career.cfg
	var first := GameCalendar.days_from_civil(y, m, 1)
	var last := first + GameCalendar.days_in_month(y, m) - 1
	var now := GameCalendar.hs_today(career)
	var cells := {}
	for e: CareerRecords.GameEntry in career.records.games:
		var z := GameCalendar.hs_game_day(cfg, e.grade, e.week)
		if z < first or z > last:
			continue
		cells[z] = {"title": e.opponent.substr(0, 3), "sub": e.score_text(), "state": ["loss", "draw", "win"][e.result + 1], "pick": false,
			"me": e.line_text()}
	if not career.in_camp() and not career.is_season_over():
		# 이번 주 경기 · 남은 토요일 (대진은 아직 모른다)
		for w in range(career.week, career.week + 30):
			var z := GameCalendar.hs_game_day(cfg, career.player.grade, w)
			if z > last:
				break
			if z < first or cells.has(z):
				continue
			if w == career.week and not career.game_done:
				var opp := career.opponent()
				cells[z] = {"title": opp.short, "sub": career.phase_name(), "state": "today", "pick": false, "me": ""}
			else:
				cells[z] = {"title": "경기", "sub": "", "state": "future", "pick": true, "me": ""}
	if now >= first and now <= last and cells.has(now):
		cells[now]["pick"] = false
	return cells
