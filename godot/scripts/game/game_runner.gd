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
## 결과 고정 경기에서 라이벌의 그날 기록 (matchOverride.rivalLine, {} 이면 자동 타석으로 만든다)
var rival_line := {}

var _player: PlayerData
var _rng: RandomNumberGenerator
var _stage := {}
## 장면에 도착했나
var _stage_set := false
## 결과 고정 경기의 이닝별 점수 [팀][이닝-1]
var _fixed_line := [[], []]


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
	return not fixed and not staged


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


## 다음 타석. 돌려주는 값: {"type": "my_turn" | "auto" | "over", "text": 중계 한 줄,
##   "highlight": 하이라이트로 보여 줄 플레이(PlayResult, 없으면 null), "side": 공격한 팀}
func step() -> Dictionary:
	if state.over:
		return {"type": "over"}
	if fixed:
		return _step_fixed()
	if staged and not _stage_set:
		return _step_stage()
	if _pinch_moment():
		_enter_as_pinch_hitter()
	if is_my_turn():
		return {"type": "my_turn"}
	var side := state.batting_side()
	var idx: int = state.batter_index[side]
	var batter := teams[side].lineup[idx]
	var pitcher := current_pitcher()
	var pa := AutoPa.simulate(cfg.auto_pa, bc, batter.skills, pitcher, _rng)
	var half := state.half_text()
	var r := _apply(pa.outcome, pa.ball, pa.pitches, false, null)
	var text := "[%s] %s %d번 %s — %s%s" % [half, team_short(side), idx + 1, batter.name, outcome_label(r.outcome), _runs_text(r)]
	log.append(text)
	_after_pa(r)
	# 하이라이트: 점수가 난 안타·홈런
	var highlight = null
	if pa.ball != null and (r.outcome == SwingJudge.Outcome.HOME_RUN or r.runs > 0 or r.note == "병살"):
		highlight = last_play
	last_auto = {"text": text, "ball": pa.ball, "outcome": r.outcome}
	return {"type": "auto", "text": text, "highlight": highlight, "side": side}


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
	_maybe_change_pitcher(def)
	return r


## 타순 번호 → 주력 (그 팀 타자)
func speed_of(side: int) -> Callable:
	var lineup := teams[side].lineup
	return func(i: int) -> int: return lineup[i].skills.speed


## 지금 공격 팀의 주자 상황 (내 타석 화면이 플레이를 미리 진행할 때 쓴다)
func situation() -> Dictionary:
	var side := state.batting_side()
	return {"bases": state.bases.duplicate(), "outs": state.outs, "batter": state.batter_index[side], "speed_of": speed_of(side)}


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
