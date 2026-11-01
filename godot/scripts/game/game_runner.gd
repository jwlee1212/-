class_name GameRunner
extends RefCounted
## 한 경기 진행. 타석을 하나씩 처리한다: 내 차례면 "my_turn" 을 돌려주고(화면이 타석 화면을 띄운다),
## 아니면 AutoPa(타자·투수 능력치 + 타구 물리)로 결과를 정해 문자 중계 한 줄을 만든다.
## 양 팀 모두 9명 타순과 투수진이 있고, 박스스코어(타자·투수 기록)를 쌓는다. 투구 수가 차면 투수를 바꾼다.

var state: GameState
var cfg: CareerConfig
var bc: BattingConfig
var my_side: int
var opponent: School
## 내 선수가 이 경기에 나오는가 (부상이면 결장)
var me_playing := true
## 내 선수의 타순 자리 (0~8). 타순에 없으면 -1 (결장, 또는 벤치에서 대타를 기다리는 중)
var my_slot := -1
## 벤치에서 시작해 대타를 기다리는 중인가 (이벤트의 pinchHit 효과)
var on_bench := false
## 이 경기에 대타로 들어갔는가
var pinch_hit := false
## 대타를 승부처와 상관없이 pinchHit.forceInning 회부터만 부른다 (타순 0 = 벤치 선수를 이야기가 부르는 경기)
var pinch_late := false
## 내 선수가 안 나온 이유 (결과 화면·변화 설명)
var absent_text := "부상으로 결장"
## 결과를 고정한 경기인가 (이벤트의 matchOverride — 타석 없이 이닝별 점수만 흘러간다, 박스스코어는 빈다)
var fixed := false
var teams: Array[Team] = []
var box: BoxScore
## 팀별 지금 던지는 투수 번호
var pitcher_index := [0, 0]
## 이번 경기 내 기록 (커리어가 성장·기록에 쓴다)
var my_line := PlayerData.SeasonStats.new()
## 이번 경기 내 타석들 (기록실 분할 기록용): [{"outcome", "rbi", "sac_fly", "pitcher": 투구 성향 id,
##   "balls", "strikes": 결정구 때 카운트(모르면 -1), "batted": SwingJudge.BattedBall, "spray", "ev"}]
var my_pas: Array[Dictionary] = []
## 문자 중계 (최근 것이 끝)
var log: Array[String] = []
## 마지막 자동 타석 (하이라이트용)
var last_auto: Dictionary = {}
## 마지막 플레이 (주자 궤적·송구) — 하이라이트가 그린다
var last_play: PlaySimulator.PlayResult = null

## 연출 장면이 있는 경기인가 (이벤트의 stage): 장면 직전까지는 이닝별 점수만 흐르고, 정해진 상황(회·아웃·주자·점수)의
## 내 타석에서 진짜 경기가 시작된다. 그 앞 이닝의 박스스코어는 빈다
var staged := false
## 장면의 내 타석이 어떤 순간인가 (그 이벤트의 moment)
var stage_moment := ""
## 대본이 만든 연습경기인가 (stage.practice): 순위·시즌 기록에 들어가지 않는다
var exhibition := false
## 경기 대본이 있는 경기인가 (이벤트 효과 gamePlan → set_plan): 장면(scenes)마다 정해진 회·아웃·주자·점수에서
## 내 타석(pa)이나 내 수비(field)를 직접 하고, 장면 사이 이닝은 대본의 이닝별 점수(line)만 흐른다.
## 마지막 장면 뒤는 after: fixed(남은 이닝도 대본 점수만 — 결과가 대본대로) | play(그 상황에서 실제 경기로 이어진다)
var planned := false
## 장면 결과 [{"var", "value", "kind"}] — 장면이 끝나는 순간 선수 vars 에도 바로 들어간다
var scene_results: Array = []
## 대본 조건 검사 (장면 결과 map 의 if — 커리어가 EventBook.test 로 넣어 준다)
var cond_test: Callable
## 결과 고정 경기에서 라이벌의 그날 기록 (matchOverride.rivalLine, {} 이면 자동 타석으로 만든다)
var rival_line := {}

## 주루 설정 (RunControl.AUTO | ASSIST | MANUAL). 자동이 아니면 내 주자의 주루 판단("my_run")과 도루 기회("my_steal")를
## step() 이 돌려준다. 자동(기본)이면 내 주자도 AI 가 뛰고 도루는 없다 — 화면 없이 돌릴 때(테스트·빨리 감기·튜닝 도구)도 자동
var running_mode := RunControl.AUTO
## 내 수비(유격수)를 직접 하는가. 켜면 경기마다 정해 둔 상대 타석 1~2번에서 step() 이 "my_field" 를 돌려준다
## ({"seed", "batter": 타자 이름, "runner_speed": 타자 주력, "fielding": 내 수비 능력치(경기용) — 판정에 쓴다}).
## 끄면(기본) 그 타석도 자동 — 화면 없이 돌릴 때(테스트·빨리 감기·튜닝 도구)는 꺼져 있어 경기 결과가 예전과 같다
var fielding := false
## 이번 경기 내 수비 기록 [FieldPlay.Kind, …] (결과 화면·기록용)
var my_fields: Array[int] = []

var _player: PlayerData
var _rng: RandomNumberGenerator
## 유저의 주루 판단을 기다리는 자동 타석 {"pa", "half", "control": RunControl}
var _pending_run := {}
## 도루 기회를 이미 물어본 타석 (같은 타자 때 두 번 묻지 않는다)
var _steal_asked := ""
var _stage := {}
## 장면에 도착했나
var _stage_set := false
## 결과 고정 경기의 이닝별 점수 [팀][이닝-1]
var _fixed_line := [[], []]
## 수비 기회: 상대 타석 몇 번째에 오는가 (경기 시작 때 시드로 정한다). 경기 난수(_rng)와 따로 굴려서 수비를 켜고 꺼도 다른 타석은 같다
var _field_rng: RandomNumberGenerator
var _field_at: Array[int] = []
var _plan := {}
var _scene_i := 0
var _scene_ready := false
## 대본이 끝나 실제 경기로 이어지는 중인가 (after: play)
var _plan_free := false
## 대본 이닝별 점수 [팀][이닝-1], 이미 점수를 넣은 반 이닝 (반 이닝 번호 -> true)
var _plan_line := [[], []]
var _plan_added := {}
## 지금까지 끝난 상대 타석 수
var _def_pa := 0
var _pending_field := false


## 두 학교의 고정 명단으로 경기를 만든다. playing = false 면 내 선수는 결장 (그 자리는 원래 명단 선수).
## bench = true 면 벤치에서 시작해 승부처에 대타로 들어간다 (그때까지 타순은 원래 명단 그대로)
func _init(p_cfg: CareerConfig, p_bc: BattingConfig, player: PlayerData, my_school: School, p_opponent: School, home: bool,
		seed_value: int, playing: bool = true, bench: bool = false) -> void:
	cfg = p_cfg
	bc = p_bc
	_player = player
	opponent = p_opponent
	me_playing = playing
	my_side = GameState.HOME if home else GameState.AWAY
	state = GameState.new(cfg.innings, cfg.running, bc.ball_physics)
	_rng = RandomNumberGenerator.new()
	_rng.seed = seed_value
	on_bench = playing and bench
	var starting := playing and not on_bench
	if starting:
		my_slot = player.lineup_slot - 1
	var mine := my_school.team.for_game(player if starting else null, player.skills_for_game(cfg))
	var theirs := opponent.team.for_game(null, null)
	teams.assign([mine, theirs] if my_side == GameState.AWAY else [theirs, mine])
	box = BoxScore.new(teams[0].pitchers.size(), teams[1].pitchers.size())
	_field_rng = RandomNumberGenerator.new()
	_field_rng.seed = hash("field%d" % seed_value)
	var fc: Dictionary = cfg.fielding
	var chances := _field_rng.randi_range(int(fc["chancesMin"]), int(fc["chancesMax"]))
	while _field_at.size() < chances:
		var at := _field_rng.randi_range(int(fc["firstPa"]), int(fc["lastPa"]))
		if at not in _field_at:
			_field_at.append(at)
	log.append("%s vs %s — 경기 시작!" % [team_name(GameState.AWAY), team_name(GameState.HOME)])


func team_name(side: int) -> String:
	return teams[side].name


func team_short(side: int) -> String:
	return teams[side].short


func is_my_turn() -> bool:
	return my_slot >= 0 and not state.over and state.batting_side() == my_side and state.batter_index[my_side] == my_slot


## 승부처인가: clutch.minInning 회 이후 · clutch.maxDiff 점 차 이내 · 득점권 주자 (경기 중 대화와 대타가 같이 쓴다)
func is_clutch() -> bool:
	var c: Dictionary = cfg.story["clutch"]
	var close: bool = absi(state.score[0] - state.score[1]) <= int(c["maxDiff"])
	return state.inning >= int(c["minInning"]) and close and (state.occupied(1) or state.occupied(2))


## 벤치에 있는 내 선수를 지금 대타로 부를 때인가: 우리 공격의 승부처, 또는 pinchHit.forceInning 회부터는 아무 타석이나
## (승부처가 끝까지 안 와도 한 번은 나간다. 홈 팀이 앞서면 마지막 회 말이 없으므로 마지막 회 전까지로 당긴다)
func _pinch_moment() -> bool:
	if not on_bench or state.batting_side() != my_side:
		return false
	if staged:
		return _stage_set
	var force := mini(int(cfg.story["pinchHit"]["forceInning"]), state.innings - 1)
	return state.inning >= force or (is_clutch() and not pinch_late)


## 대타: 지금 타석에 설 타자를 내 선수로 바꾼다. 내 선수는 그 타순에 남아 경기 끝까지 뛴다
func _enter_as_pinch_hitter() -> void:
	var idx: int = state.batter_index[my_side]
	var out := teams[my_side].lineup[idx]
	teams[my_side].lineup[idx] = Team.my_batter(_player, _player.skills_for_game(cfg))
	box.substitute(my_side, idx, out.name)
	my_slot = idx
	on_bench = false
	pinch_hit = true
	log.append("— %s 대타: %d번 %s → ★ %s —" % [team_short(my_side), idx + 1, out.name, _player.name])


func current_batter() -> Team.Batter:
	var side := state.batting_side()
	return teams[side].lineup[state.batter_index[side]]


func current_batter_name() -> String:
	return current_batter().name


## 내 타석에서 상대하는 투구 성향 id. 연출 장면의 타석은 대본이 정한 성향(stage.pitcher)을 쓴다
func my_pitcher_profile() -> String:
	if planned and not _plan_free and current_scene().has("pitcher"):
		return current_scene()["pitcher"]
	if staged and my_line.pa == 0 and _stage.has("pitcher"):
		return _stage["pitcher"]
	return current_pitcher().profile


## 타순 한 자리를 다른 선수로 바꾼다 (경기 시작 전 — 대본상 다쳐서 빠진 선수의 자리)
func replace_starter(idx: int, batter: Team.Batter) -> void:
	if idx != my_slot:
		teams[my_side].lineup[idx] = batter


## 지금 공을 던지는 (수비 팀) 투수
func current_pitcher() -> Team.Pitcher:
	var def := 1 - state.batting_side()
	return teams[def].pitchers[pitcher_index[def]]


## 결과를 고정한다: 우리 my_runs 점, 상대 opp_runs 점으로 p_innings 회에 끝난다 (정규 이닝보다 짧으면 콜드게임).
## 점수는 시드 난수로 이닝에 나눠 담는다. 이긴 홈 팀은 마지막 회 말 공격이 없다
## line: [[우리 이닝별 점수…], [상대 이닝별 점수…]] 을 주면 난수 대신 그대로 쓴다
func fix_result(my_runs: int, opp_runs: int, p_innings: int, line: Array = []) -> void:
	fixed = true
	state.innings = p_innings
	if not line.is_empty():
		_fixed_line[my_side] = (line[0] as Array).map(func(v: Variant) -> int: return int(v))
		_fixed_line[1 - my_side] = (line[1] as Array).map(func(v: Variant) -> int: return int(v))
		return
	var runs := [0, 0]
	runs[my_side] = my_runs
	runs[1 - my_side] = opp_runs
	for side in 2:
		var n := p_innings - 1 if side == GameState.HOME and runs[GameState.HOME] > runs[GameState.AWAY] and p_innings > 1 else p_innings
		var per: Array = []
		per.resize(n)
		per.fill(0)
		for i in int(runs[side]):
			per[_rng.randi_range(0, n - 1)] += 1
		_fixed_line[side] = per


## 박스스코어가 경기 전체를 담고 있나 (결과 고정·연출 경기는 타석 없이 흘러간 이닝이 있다)
func has_full_box() -> bool:
	return not fixed and not staged and not planned


## 연출 장면을 건다 (경기 시작 전에). scene = 이벤트의 stage: {"inning", "half": "top"|"bottom", "outs", "bases": [루…], "score": [우리, 상대],
##   "slot"?: 대타로 들어갈 타순, "runnerSlots"?: 주자들의 타순, "reached"?: 주자들이 나간 방법("walk"|"single"|"double"|"triple")}.
## 우리 공격이어야 하므로 half 가 bottom 이면 홈, top 이면 원정 경기여야 한다 (CareerState.new_game 이 맞춘다)
func set_stage(scene: Dictionary, moment: String) -> void:
	assert((scene["half"] == "bottom") == (my_side == GameState.HOME), "연출 장면은 우리 공격이어야 한다")
	staged = true
	stage_moment = moment
	_stage = scene
	var k := int(scene["inning"])
	var runs := [0, 0]
	runs[my_side] = int(scene["score"][0])
	runs[1 - my_side] = int(scene["score"][1])
	# 장면 전에 끝난 공격 횟수: 말 장면이면 원정 k 번·홈 k-1 번, 초 장면이면 둘 다 k-1 번
	for side in 2:
		var n := k if side == GameState.AWAY and my_side == GameState.HOME else k - 1
		var per: Array = []
		per.resize(n)
		per.fill(0)
		for i in int(runs[side]):
			per[_rng.randi_range(0, n - 1)] += 1
		_fixed_line[side] = per


## 연출 경기의 장면 앞부분: 한 회씩 점수만 올리다가, 장면의 회가 되면 상황을 차려 놓는다
func _step_stage() -> Dictionary:
	var st := state
	var k := int(_stage["inning"])
	var i := st.inning if st.line[GameState.HOME].is_empty() else st.inning + 1
	if i < k:
		st.inning = i
		st.top = false
		var got := [0, 0]
		for side in 2:
			got[side] = int(_fixed_line[side][i - 1])
			st.line[side] = (_fixed_line[side] as Array).slice(0, i)
			st.score[side] += got[side]
		var text := "[%d회] %s %d점 · %s %d점" % [i, team_short(GameState.AWAY), got[0], team_short(GameState.HOME), got[1]]
		log.append(text)
		return {"type": "auto", "text": text, "highlight": null, "side": GameState.AWAY}
	# 장면의 회: (말 장면이면 상대의 초 공격 점수까지 올리고) 아웃·주자·타순을 차린다
	st.inning = k
	st.top = my_side == GameState.AWAY
	st.line[GameState.AWAY] = (_fixed_line[GameState.AWAY] as Array).slice(0, k)
	st.line[GameState.HOME] = (_fixed_line[GameState.HOME] as Array).slice(0, k - 1)
	st.line[my_side].append(0)
	if my_side == GameState.HOME:
		var top_runs := int(_fixed_line[GameState.AWAY][k - 1])
		st.score[GameState.AWAY] += top_runs
		log.append("[%d회초] %s %d점" % [k, team_short(GameState.AWAY), top_runs])
	log.append("— %s (%s %d : %d %s) —" % [st.half_text(), team_short(GameState.AWAY), st.score[0], st.score[1], team_short(GameState.HOME)])
	var me := (int(_stage["slot"]) - 1) if on_bench and _stage.has("slot") else (my_slot if my_slot >= 0 else 8)
	var outs := int(_stage.get("outs", 0))
	var bases: Array = _stage.get("bases", [])
	var slots: Array = _stage.get("runnerSlots", [])
	var reached: Array = _stage.get("reached", [])
	# 주자: 정해 준 타순, 없으면 아웃된 타자들 앞 타순부터 거슬러. 나간 방법은 정해 준 대로, 없으면 있는 루에 맞춰 (1루 볼넷·2루 2루타·3루 3루타)
	var by_base := {1: SwingJudge.Outcome.WALK, 2: SwingJudge.Outcome.DOUBLE, 3: SwingJudge.Outcome.TRIPLE}
	var by_name := {"walk": SwingJudge.Outcome.WALK, "single": SwingJudge.Outcome.SINGLE, "double": SwingJudge.Outcome.DOUBLE, "triple": SwingJudge.Outcome.TRIPLE}
	for j in bases.size():
		var idx: int = (int(slots[j]) - 1) if j < slots.size() else posmod(me - outs - 1 - j, 9)
		_stage_credit(idx, by_name[reached[j]] if j < reached.size() else by_base[int(bases[j])])
		st.bases[int(bases[j]) - 1] = idx
	# 아웃: 내 바로 앞 타자들 (마지막 타자는 삼진, 그 앞은 땅볼)
	for j in outs:
		_stage_credit(posmod(me - outs + j, 9), SwingJudge.Outcome.STRIKEOUT if j == outs - 1 else SwingJudge.Outcome.GROUND_OUT)
	st.outs = outs
	st.batter_index[my_side] = me
	_stage_set = true
	var text := "— %s %s %s —" % [st.half_text(), "%d사" % outs if outs > 0 else "무사", st.runners_text()]
	log.append(text)
	return {"type": "auto", "text": text, "highlight": null, "side": my_side}


## 장면을 차리며 앞 타자의 타석을 박스스코어·중계에 적는다 (주자·아웃은 _step_stage 가 직접 놓는다)
func _stage_credit(idx: int, outcome: SwingJudge.Outcome) -> void:
	var def := 1 - my_side
	var bl: BoxScore.BatterLine = box.batters[my_side][idx]
	bl.add(outcome, 0, false)
	var pl: BoxScore.PitcherLine = box.pitchers[def][pitcher_index[def]]
	pl.bf += 1
	match outcome:
		SwingJudge.Outcome.WALK: pl.bb += 1
		SwingJudge.Outcome.STRIKEOUT: pl.so += 1
	if SwingJudge.is_hit(outcome):
		pl.h += 1
		box.hits[my_side] += 1
	elif outcome != SwingJudge.Outcome.WALK:
		pl.outs += 1
	log.append("[%s] %s %d번 %s — %s" % [state.half_text(), team_short(my_side), idx + 1, teams[my_side].lineup[idx].name, outcome_label(outcome)])


# ---------- 경기 대본 (gamePlan) ----------

## 경기 대본을 건다 (경기 시작 전에). plan: {"innings"?, "role"?: start|sub, "line"?: [[우리 이닝별 점수…], [상대…]],
##   "scenes": [{"inning", "half", "outs", "bases", "score"?: [우리, 상대](장면 시작 때 점수를 이 값으로 맞춘다), "slot"?,
##   "kind": pa|field, "event"?, "var"?, "map"?, "pitcher"?, "boost"?, "doublePlay"?: [판정…]}], "after"?: fixed|play}
## 대본 이닝별 점수는 장면에서 내가 낸 점수와 따로 더해진다. 무작위 수비 기회는 없다 (수비는 장면에서만)
func set_plan(plan: Dictionary) -> void:
	planned = true
	_plan = plan
	state.innings = int(plan.get("innings", cfg.innings))
	var line: Array = plan.get("line", [[], []])
	for i in 2:
		var side := my_side if i == 0 else 1 - my_side
		var per: Array = []
		for k in state.innings:
			per.append(int(line[i][k]) if k < (line[i] as Array).size() else 0)
		_plan_line[side] = per
	_field_at.clear()


## 지금 장면 (대본이 없거나 끝났으면 {})
func current_scene() -> Dictionary:
	if not planned or _plan_free or _scene_i >= (_plan.get("scenes", []) as Array).size():
		return {}
	return _plan["scenes"][_scene_i]


## 지금 장면에 도착해 내 차례(타석·수비)를 기다리는 중인가
func in_scene() -> bool:
	return planned and _scene_ready and not current_scene().is_empty()


func _half_index() -> int:
	return (state.inning - 1) * 2 + (0 if state.top else 1)


## 대본 경기의 한 걸음: 장면까지 반 이닝씩 대본 점수만 흘리고, 장면에 오면 상황을 차리고, 내 차례를 돌려준다
func _step_plan() -> Dictionary:
	_add_plan_runs()
	var sc := current_scene()
	if sc.is_empty():
		# 마지막 장면 뒤 (after: fixed): 남은 반 이닝을 대본 점수로
		return _close_plan_half()
	var target := (int(sc["inning"]) - 1) * 2 + (0 if sc["half"] == "top" else 1)
	if not _scene_ready:
		if _half_index() < target:
			return _close_plan_half()
		_setup_scene(sc)
		var text := "— %s %s %s —" % [state.half_text(), "%d사" % state.outs if state.outs > 0 else "무사", state.runners_text()]
		log.append(text)
		return {"type": "auto", "text": text, "highlight": null, "side": state.batting_side()}
	if sc["kind"] == "pa":
		return {"type": "my_turn"}
	var side := state.batting_side()
	var b := teams[side].lineup[state.batter_index[side]]
	if fielding:
		_pending_field = true
		return {"type": "my_field", "seed": _field_rng.randi(), "batter": b.name, "runner_speed": b.skills.speed,
			"fielding": _player.fielding_for_game(cfg), "side": side}
	# 화면 없이 돌릴 때 (테스트·빨리 감기): 수비 능력치로 판정을 굴린다
	_pending_field = true
	var text := apply_field(auto_field_kind())
	return {"type": "auto", "text": text, "highlight": null, "side": side}


## 화면 없이 하는 내 수비 판정 (balance.json game.fielding.auto): 아웃 확률 = base + 수비 × perPoint (max 까지),
## 아웃 중 bonusShare 는 레이저 송구, 아웃이 아니면 반은 포구 실책 · 반은 내야안타
func auto_field_kind() -> FieldPlay.Kind:
	var a: Dictionary = cfg.fielding["auto"]
	var p := minf(float(a["base"]) + _player.fielding_for_game(cfg) * float(a["perPoint"]), float(a["max"]))
	var roll := _field_rng.randf()
	if roll < p:
		return FieldPlay.Kind.BONUS if roll < p * float(a["bonusShare"]) else FieldPlay.Kind.NORMAL
	return FieldPlay.Kind.MISS if _field_rng.randf() < 0.5 else FieldPlay.Kind.LATE


## 지금 반 이닝의 대본 점수를 (아직 안 넣었으면) 넣는다
func _add_plan_runs() -> void:
	var h := _half_index()
	if _plan_added.has(h) or state.over:
		return
	_plan_added[h] = true
	var side := state.batting_side()
	var runs := int(_plan_line[side][state.inning - 1]) if state.inning <= (_plan_line[side] as Array).size() else 0
	if runs > 0:
		state.score[side] += runs
		state.line[side][state.inning - 1] += runs


## 지금 반 이닝을 끝내고 다음으로 (대본 경기). 마지막 회면 경기를 끝낸다
func _close_plan_half() -> Dictionary:
	var st := state
	var side := st.batting_side()
	var got: int = st.line[side][st.inning - 1]
	var text := "[%s] %s %d점" % [st.half_text(), team_short(side), got]
	log.append(text)
	st.outs = 0
	st.bases = [GameState.EMPTY, GameState.EMPTY, GameState.EMPTY]
	if st.top:
		# 마지막 회 초에 홈 팀이 앞서면 끝 — 단 대본에 그 회 말 장면이 남아 있으면 한다 (연습경기에서 마지막 수비를 하는 것처럼)
		if st.inning >= st.innings and st.score[GameState.HOME] > st.score[GameState.AWAY] and current_scene().is_empty():
			st.over = true
		else:
			st.top = false
			st.line[GameState.HOME].append(0)
	elif st.inning >= st.innings:
		st.over = true
	else:
		st.inning += 1
		st.top = true
		st.line[GameState.AWAY].append(0)
	if st.over:
		if st.innings < cfg.innings:
			log.append("— %d회 콜드게임 —" % st.innings)
		log.append("경기 종료! %s %d : %d %s" % [team_short(GameState.AWAY), st.score[0], st.score[1], team_short(GameState.HOME)])
	return {"type": "auto", "text": text, "highlight": null, "side": side}


## 점수를 value 로 맞춘다 (이닝별 점수의 마지막 칸부터 고쳐서 합을 맞춘다)
func _force_score(side: int, value: int) -> void:
	var diff := value - int(state.score[side])
	var per: Array = state.line[side]
	var i := per.size() - 1
	while diff != 0 and i >= 0:
		var v := maxi(0, int(per[i]) + diff)
		diff -= v - int(per[i])
		per[i] = v
		i -= 1
	state.score[side] = value - diff


## 장면을 차린다: 점수·아웃·주자, 내 선수를 타순(또는 수비)에 넣고, 장면 보정을 건다
func _setup_scene(sc: Dictionary) -> void:
	var st := state
	if sc.has("score"):
		_force_score(my_side, int(sc["score"][0]))
		_force_score(1 - my_side, int(sc["score"][1]))
	st.outs = int(sc.get("outs", 0))
	var side := st.batting_side()
	if my_slot < 0:
		# 벤치에서 들어간다: 대타(내 타석 장면) 또는 대수비(수비 장면) — 타순 slot(기본 9번) 자리
		var idx := int(sc.get("slot", 9)) - 1
		var out := teams[my_side].lineup[idx]
		teams[my_side].lineup[idx] = Team.my_batter(_player, _player.skills_for_game(cfg))
		box.substitute(my_side, idx, out.name)
		my_slot = idx
		on_bench = false
		pinch_hit = sc["kind"] == "pa"
		log.append("— %s %s: %d번 %s → ★ %s —" % [team_short(my_side), "대타" if pinch_hit else "대수비", idx + 1, out.name, _player.name])
	if sc["kind"] == "pa":
		st.batter_index[my_side] = my_slot
	var bases: Array = sc.get("bases", [])
	st.bases = [GameState.EMPTY, GameState.EMPTY, GameState.EMPTY]
	for j in bases.size():
		# 주자: 지금 타자 앞 타순부터 거슬러 (누가 나갔는지는 장면에 중요하지 않다)
		st.bases[int(bases[j]) - 1] = posmod(st.batter_index[side] - 1 - j, 9)
	for k: String in sc.get("boost", {}):
		_player.game_boost[k] = int(_player.game_boost.get(k, 0)) + int(sc["boost"][k])
	_scene_ready = true


## 장면이 끝났다 (내 타석 결과 · 수비 판정 이름): 결과 변수를 세우고 다음 장면으로
func _finish_scene(kind: String) -> void:
	var sc := current_scene()
	var value: Variant = kind.to_lower()
	if sc.has("var"):
		var m: Dictionary = sc.get("map", {})
		var v: Variant = m.get(kind.to_lower(), m.get("_", kind.to_lower()))
		if v is Dictionary:
			v = v["then"] if cond_test.is_valid() and cond_test.call(v.get("if", {})) else v["else"]
		value = v
		_player.vars[sc["var"]] = value
	scene_results.append({"var": sc.get("var", ""), "value": value, "kind": kind.to_lower()})
	for k: String in sc.get("boost", {}):
		_player.game_boost[k] = int(_player.game_boost.get(k, 0)) - int(sc["boost"][k])
	_scene_i += 1
	_scene_ready = false
	if _scene_i >= (_plan["scenes"] as Array).size() and _plan.get("after", "fixed") == "play":
		_plan_free = true


## 결과 고정 경기의 한 회 (초·말을 한꺼번에)
func _step_fixed() -> Dictionary:
	var st := state
	var k := st.inning if st.line[GameState.HOME].is_empty() else st.inning + 1
	st.inning = k
	st.top = false
	var got := [0, 0]
	for side in 2:
		st.line[side] = (_fixed_line[side] as Array).slice(0, k)
		got[side] = int(_fixed_line[side][k - 1]) if k <= (_fixed_line[side] as Array).size() else 0
		st.score[side] += got[side]
	var text := "[%d회] %s %d점 · %s %d점" % [k, team_short(GameState.AWAY), got[0], team_short(GameState.HOME), got[1]]
	log.append(text)
	if k >= st.innings:
		st.over = true
		if st.innings < cfg.innings:
			log.append("— %d회 콜드게임 —" % st.innings)
		log.append("경기 종료! %s %d : %d %s" % [team_short(GameState.AWAY), st.score[0], st.score[1], team_short(GameState.HOME)])
	return {"type": "auto", "text": text, "highlight": null, "side": GameState.AWAY}


## 내 타석이 없는 경기(벤치·결장)를 끝까지 한 번에 돌린다 — 화면에서 지켜볼 게 없을 때 쓴다
func run_to_end() -> void:
	assert(my_slot < 0 and not on_bench, "내 타석이 있는 경기는 한 번에 돌릴 수 없다")
	while not state.over:
		step()


## 유저가 내 주자를 직접 조작하는가 (주루 설정이 보조·수동)
func controls_running() -> bool:
	return running_mode != RunControl.AUTO


## 내 선수가 지금 도루할 수 있는 베이스에 있으면 그 베이스 (1·2), 아니면 0: 우리 공격, 다음 베이스가 비어 있다
func steal_base() -> int:
	if my_slot < 0 or state.over or state.batting_side() != my_side:
		return 0
	for i in 2:
		if state.bases[i] == my_slot and state.bases[i + 1] == GameState.EMPTY:
			return i + 1
	return 0


## 도루 결과를 경기에 반영한다 (안 뛰었으면 아무 일도 없다). 돌려주는 값: 중계 한 줄 ("" = 안 뛰었다)
func apply_steal(res: Steal.Result) -> String:
	if not res.attempted():
		return ""
	var def := 1 - my_side
	var half := state.half_text()
	var safe := res.kind == Steal.Kind.SAFE
	var base_name := "%d루" % res.to
	var bl: BoxScore.BatterLine = box.batters[my_side][my_slot]
	var half_over := state.apply_steal(res.from, safe)
	var text: String
	if safe:
		my_line.steals += 1
		bl.sb += 1
		text = "[%s] ★ %s — %s 도루 성공!" % [half, _player.name, base_name]
	else:
		my_line.caught_stealing += 1
		bl.cs += 1
		(box.pitchers[def][pitcher_index[def]] as BoxScore.PitcherLine).outs += 1
		text = "[%s] ★ %s — %s" % [half, _player.name, "견제사" if res.kind == Steal.Kind.PICKED_OFF else base_name + " 도루 실패"]
	log.append(text)
	if half_over:
		_after_pa({"half_over": true})
	return text


## 유저의 주루 판단이 끝났다 ("my_run" 뒤에 화면이 부른다): 그 플레이를 경기에 반영한다. 돌려주는 값은 step() 과 같다
func finish_run() -> Dictionary:
	assert(not _pending_run.is_empty(), "기다리는 주루 판단이 없다")
	var p := _pending_run
	_pending_run = {}
	var control: RunControl = p["control"]
	control.finish()
	var r := _finish_auto(p["pa"], p["half"], control.play)
	r["highlight"] = null  # 방금 화면에서 봤다
	return r


## 다음 타석. 돌려주는 값: {"type": "my_turn" | "auto" | "over" | "my_steal" | "my_run", "text": 중계 한 줄,
##   "highlight": 하이라이트로 보여 줄 플레이(PlayResult, 없으면 null), "side": 공격한 팀}
## 주루 설정이 자동이 아닐 때만: "my_steal" {"from": 내 베이스} — 화면이 도루 화면을 띄우고 apply_steal() 뒤 다시 step(),
##   "my_run" {"control": RunControl, "batter": 타자 이름} — 화면이 플레이를 보여 주며 판단을 받고 finish_run()
func step() -> Dictionary:
	if state.over:
		return {"type": "over"}
	if fixed:
		return _step_fixed()
	if planned and not _plan_free:
		return _step_plan()
	if staged and not _stage_set:
		return _step_stage()
	if _pinch_moment():
		_enter_as_pinch_hitter()
	if is_my_turn():
		return {"type": "my_turn"}
	assert(_pending_run.is_empty(), "주루 판단을 기다리는 중이다 (finish_run 을 먼저)")
	assert(not _pending_field, "수비 결과를 기다리는 중이다 (apply_field 를 먼저)")
	var side := state.batting_side()
	var idx: int = state.batter_index[side]
	if side != my_side:
		if fielding and my_slot >= 0 and _def_pa in _field_at:
			_pending_field = true
			var b := teams[side].lineup[idx]
			return {"type": "my_field", "seed": _field_rng.randi(), "batter": b.name, "runner_speed": b.skills.speed,
				"fielding": _player.fielding_for_game(cfg), "side": side}
		_def_pa += 1
	if controls_running() and steal_base() > 0:
		var key := "%d:%d:%d" % [state.inning, my_line.pa, idx]
		if _steal_asked != key:
			_steal_asked = key
			return {"type": "my_steal", "from": steal_base(), "side": side}
	var batter := teams[side].lineup[idx]
	var pitcher := current_pitcher()
	var pa := AutoPa.simulate(cfg.auto_pa, bc, batter.skills, pitcher, _rng)
	var half := state.half_text()
	var play: PlaySimulator.PlayResult = null
	if controls_running() and pa.ball != null and side == my_side and my_slot >= 0 and my_slot in state.bases:
		# 내가 루상에 있다: 내 주자의 판단을 유저가 고를 수 있게 플레이를 먼저 돌려 본다.
		# 다시 돌릴 때마다 난수를 같은 자리로 되돌리므로, 판단 전까지는 언제나 똑같이 흘러간다
		var rng_state := _rng.state
		var ball: BattedBallSim.Result = pa.ball
		var resim := func(choices: Array) -> PlaySimulator.PlayResult:
			_rng.state = rng_state
			return state.make_play(ball, idx, speed_of(side), _rng, my_slot, choices)
		var control := RunControl.new(resim.call([]), resim, running_mode == RunControl.MANUAL)
		if control.has_choices():
			_pending_run = {"pa": pa, "half": half, "control": control}
			return {"type": "my_run", "control": control, "batter": batter.name, "side": side}
		play = control.play
	return _finish_auto(pa, half, play)


## 자동 타석 하나를 경기에 반영하고 중계 한 줄을 만든다. play: 이미 진행한 플레이 (없으면 여기서 진행한다)
func _finish_auto(pa: Dictionary, half: String, play: PlaySimulator.PlayResult) -> Dictionary:
	var side := state.batting_side()
	var idx: int = state.batter_index[side]
	var batter := teams[side].lineup[idx]
	var r := _apply(pa.outcome, pa.ball, pa.pitches, false, play)
	var text := "[%s] %s %d번 %s — %s%s" % [half, team_short(side), idx + 1, batter.name, outcome_label(r.outcome), _runs_text(r)]
	log.append(text)
	_after_pa(r)
	# 하이라이트: 점수가 난 안타·홈런
	var highlight = null
	if pa.ball != null and (r.outcome == SwingJudge.Outcome.HOME_RUN or r.runs > 0 or r.note == "병살"):
		highlight = last_play
	last_auto = {"text": text, "ball": pa.ball, "outcome": r.outcome}
	return {"type": "auto", "text": text, "highlight": highlight, "side": side}


## 내 수비 결과를 경기에 반영한다 ("my_field" 뒤에 화면이 부른다). 돌려주는 값: 중계 한 줄
func apply_field(kind: FieldPlay.Kind) -> String:
	assert(_pending_field, "기다리는 수비가 없다")
	_pending_field = false
	_def_pa += 1
	my_fields.append(kind)
	# 내 수비 기록: 1루에서 잡은 아웃 = 보살, 포구 실책·악송구 = 실책 (늦은 송구 내야안타는 둘 다 아니다)
	if kind == FieldPlay.Kind.BONUS or kind == FieldPlay.Kind.NORMAL:
		my_line.assists += 1
	elif kind == FieldPlay.Kind.MISS or kind == FieldPlay.Kind.WILD:
		my_line.errors += 1
	var side := state.batting_side()
	var idx: int = state.batter_index[side]
	var half := state.half_text()
	var batter := teams[side].lineup[idx]
	var sc := current_scene() if planned and _scene_ready else {}
	var applied := kind
	var dp := false
	# 대본의 병살 장면: 이 판정이면 1루 주자도 2루에서 잡는다 (6-4-3) — 타자는 레이저 송구처럼 아웃, 주자는 안 나간다
	if (sc.get("doublePlay", []) as Array).has(FieldPlay.Kind.keys()[kind]) and state.bases[0] != GameState.EMPTY and state.outs < 2:
		state.bases[0] = GameState.EMPTY
		state.outs += 1
		applied = FieldPlay.Kind.BONUS
		dp = true
	var r := state.apply_shortstop(applied, idx)
	if dp:
		r.note = "병살"
	_credit(side, idx, r, int(cfg.fielding["pitchesPerPlay"]))
	_maybe_change_pitcher(1 - side)
	var what := {FieldPlay.Kind.BONUS: "유격수 땅볼 — 레이저 송구!",
		FieldPlay.Kind.NORMAL: "유격수 땅볼 아웃", FieldPlay.Kind.WILD: "유격수 악송구로 2루까지",
		FieldPlay.Kind.MISS: "유격수 포구 실책으로 출루", FieldPlay.Kind.LATE: "유격수 내야안타"}
	var runs := " (%d점)" % r.runs if r.runs > 0 else ""
	var text := "[%s] %s %d번 %s — ★ %s%s%s" % [half, team_short(side), idx + 1, batter.name, what[kind], " · 병살!" if dp else "", runs]
	log.append(text)
	_after_pa(r)
	if not sc.is_empty():
		_finish_scene(FieldPlay.Kind.keys()[kind])
	return text


## 내 타석 결과를 경기에 반영한다
func apply_my_result(result: AtBat.Result) -> String:
	var half := state.half_text()
	var ball: BattedBallSim.Result = result.contact.ball if result.contact != null else null
	var pitcher_profile := my_pitcher_profile()
	var r := _apply(result.outcome, ball, result.pitches, result.bunt, result.play)
	my_line.add(r.outcome, r.rbi, r.sac_fly)
	my_pas.append({"outcome": r.outcome, "rbi": r.rbi, "sac_fly": r.sac_fly, "pitcher": pitcher_profile,
		"balls": result.balls, "strikes": result.strikes,
		"batted": ball.batted_ball if ball != null else SwingJudge.BattedBall.NONE,
		"spray": ball.spray_deg if ball != null else 0.0, "ev": ball.ev_kmh if ball != null else 0.0})
	var text := "[%s] ★ %s — %s%s" % [half, _player.name, outcome_label(r.outcome), _runs_text(r)]
	log.append(text)
	_after_pa(r)
	if planned and _scene_ready and current_scene().get("kind", "") == "pa":
		_finish_scene(SwingJudge.Outcome.keys()[r.outcome])
	return text


## 우리 팀 기준 승패: 1 승, 0 무, -1 패
func my_result() -> int:
	var mine: int = state.score[my_side]
	var theirs: int = state.score[1 - my_side]
	return signi(mine - theirs)


## 타석 하나를 경기·박스스코어에 반영. play: 이미 진행한 플레이(내 타석 화면에서 본 것)가 있으면 그대로 쓴다
func _apply(outcome: SwingJudge.Outcome, ball: BattedBallSim.Result, pitches: int, bunt: bool, play: PlaySimulator.PlayResult) -> Dictionary:
	var side := state.batting_side()
	var def := 1 - side
	var idx: int = state.batter_index[side]
	var r: Dictionary
	last_play = null
	if play == null and ball != null:
		play = state.make_play(ball, idx, speed_of(side), _rng)
	if play != null:
		last_play = play
		r = state.apply_play(play, bunt)
	else:
		r = state.apply(outcome, _rng, bunt, null, idx)
	_credit(side, idx, r, pitches)
	_maybe_change_pitcher(def)
	return r


## 타석 하나를 박스스코어(타자·투수)에 적는다
func _credit(side: int, idx: int, r: Dictionary, pitches: int) -> void:
	var def := 1 - side
	var bl: BoxScore.BatterLine = box.batters[side][idx]
	bl.add(r.outcome, r.rbi, r.sac_fly)
	for scorer: int in r.scorers:
		box.batters[side][scorer].r += 1
	var pl: BoxScore.PitcherLine = box.pitchers[def][pitcher_index[def]]
	pl.bf += 1
	pl.outs += r.outs
	pl.r += r.runs
	pl.pitches += pitches
	match r.outcome:
		SwingJudge.Outcome.WALK: pl.bb += 1
		SwingJudge.Outcome.STRIKEOUT: pl.so += 1
		SwingJudge.Outcome.HOME_RUN: pl.hr += 1
	if SwingJudge.is_hit(r.outcome):
		pl.h += 1
		box.hits[side] += 1


## 타순 번호 → 주력 (그 팀 타자)
func speed_of(side: int) -> Callable:
	var lineup := teams[side].lineup
	return func(i: int) -> int: return lineup[i].skills.speed


## 지금 공격 팀의 주자 상황 (내 타석 화면이 플레이를 미리 진행할 때 쓴다)
func situation() -> Dictionary:
	var side := state.batting_side()
	return {"bases": state.bases.duplicate(), "outs": state.outs, "batter": state.batter_index[side], "speed_of": speed_of(side),
		"control": running_mode if controls_running() else ""}


## 투구 수가 차거나 많이 맞으면 다음 투수
func _maybe_change_pitcher(def: int) -> void:
	var i: int = pitcher_index[def]
	var p := teams[def].pitchers[i]
	var pl: BoxScore.PitcherLine = box.pitchers[def][i]
	var tired := pl.pitches >= p.max_pitches or (i == 0 and pl.r >= int(cfg.roster["pullAfterRuns"]))
	if tired and i + 1 < teams[def].pitchers.size() and not state.over:
		pitcher_index[def] = i + 1
		log.append("— %s 투수 교체: %s → %s —" % [team_short(def), p.name, teams[def].pitchers[i + 1].name])


func _after_pa(r: Dictionary) -> void:
	if state.over:
		log.append("경기 종료! %s %d : %d %s" % [team_short(GameState.AWAY), state.score[0], state.score[1], team_short(GameState.HOME)])
	elif r.half_over:
		log.append("— 공수 교대 (%s %d : %d %s) —" % [team_short(GameState.AWAY), state.score[0], state.score[1], team_short(GameState.HOME)])


func _runs_text(r: Dictionary) -> String:
	var parts := []
	if r.note != "" and r.note != "희생번트":
		parts.append(r.note)
	if r.runs > 0:
		parts.append("%d점!" % r.runs)
	return "" if parts.is_empty() else " (" + ", ".join(parts) + ")"


static func outcome_label(o: SwingJudge.Outcome) -> String:
	match o:
		SwingJudge.Outcome.SINGLE: return "안타"
		SwingJudge.Outcome.DOUBLE: return "2루타"
		SwingJudge.Outcome.TRIPLE: return "3루타"
		SwingJudge.Outcome.HOME_RUN: return "홈런!"
		SwingJudge.Outcome.WALK: return "볼넷"
		SwingJudge.Outcome.STRIKEOUT: return "삼진"
		SwingJudge.Outcome.GROUND_OUT: return "땅볼 아웃"
		SwingJudge.Outcome.FLY_OUT: return "뜬공 아웃"
		SwingJudge.Outcome.LINE_OUT: return "직선타 아웃"
		SwingJudge.Outcome.SAC_BUNT: return "희생번트"
	return "?"
