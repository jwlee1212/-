class_name GameState
extends RefCounted
## 한 경기의 상태: 이닝·초말·아웃·주자(누구인지)·점수. 타석 결과를 받아 주루와 득점을 처리한다.
##
## 맞은 공(타구 물리 결과)이 있으면 주루는 PlaySimulator(주자 FSM + 수비 송구)가 정한다 — make_play() → apply_play().
## 타구 물리 결과가 없으면(볼넷·삼진, 또는 시험용) 정해진 진루와 예비 확률을 쓴다 (apply).
## 주자 = 타순 번호(0~8), 빈 베이스 = -1

const AWAY := 0
const HOME := 1
const EMPTY := -1
const HOME_PLATE := 4

var innings: int
var inning := 1
var top := true
var outs := 0
var bases := [EMPTY, EMPTY, EMPTY]  # 1루, 2루, 3루에 있는 주자의 타순 번호
var score := [0, 0]
## 팀별 다음 타자 (0~8)
var batter_index := [0, 0]
var over := false
## 이닝별 점수 (점수판용) [팀][이닝-1]
var line := [[0], []]

var _running: Dictionary
var _phys: BattedBallSim.Config


func _init(p_innings: int, running: Dictionary, phys: BattedBallSim.Config = null) -> void:
	innings = p_innings
	_running = running
	_phys = phys


func batting_side() -> int:
	return AWAY if top else HOME


func occupied(base_index: int) -> bool:
	return bases[base_index] != EMPTY


func has_runner() -> bool:
	return occupied(0) or occupied(1) or occupied(2)


func runners_text() -> String:
	var names := []
	for i in 3:
		if occupied(i):
			names.append("%d" % (i + 1))
	if names.is_empty():
		return "주자 없음"
	if names.size() == 3:
		return "만루"
	return "·".join(names) + "루"


func half_text() -> String:
	return "%d회%s" % [inning, "초" if top else "말"]


## 맞은 공으로 플레이를 진행한다 (아직 상태는 바꾸지 않는다). speed_of: 타순 번호 → 주력(0~100)
func make_play(ball: BattedBallSim.Result, batter: int, speed_of: Callable, rng: RandomNumberGenerator) -> PlaySimulator.PlayResult:
	return PlaySimulator.new(_phys, ball, batter, bases.duplicate(), speed_of, outs, rng).run()


## 플레이 결과를 상태에 반영한다. bunt: 번트였는가 (주자를 보내고 타자만 아웃이면 희생번트)
## 돌려주는 값은 apply() 와 같은 모양
func apply_play(play: PlaySimulator.PlayResult, bunt: bool = false) -> Dictionary:
	var side := batting_side()
	var had_runner := has_runner()
	var outcome := play.batter_outcome
	var runner_outs := 0
	for r in play.runners:
		if r.state == RunnerAI.State.OUT and not r.is_batter():
			runner_outs += 1
	var note := play.note
	if bunt and outcome == SwingJudge.Outcome.GROUND_OUT and had_runner and runner_outs == 0 and outs < 2:
		outcome = SwingJudge.Outcome.SAC_BUNT
		note = "희생번트"
	var res := {"outcome": outcome, "runs": play.scorers.size(), "rbi": play.rbi, "scorers": play.scorers.duplicate(),
		"outs": play.outs, "note": note, "half_over": false, "sac_fly": play.sac_fly}
	outs += play.outs
	bases = play.bases.duplicate()
	_add_runs(side, res.runs)
	batter_index[side] = (batter_index[side] + 1) % 9
	if outs >= 3:
		res.half_over = true
		_end_half()
	elif not top and inning >= innings and score[HOME] > score[AWAY]:
		over = true  # 끝내기
	return res


## 타석 결과 적용.
## batter: 타자의 타순 번호, ball: 타구 물리 결과(없으면 null), run_mps: Callable(타순 번호) -> 주력(0~100)
## 돌려주는 값: {"outcome", "runs", "rbi", "scorers": [득점한 타순 번호], "outs": 이 타석에서 잡은 아웃 수,
##              "note": "병살"·"희생플라이" 등, "half_over", "sac_fly"}
func apply(outcome: SwingJudge.Outcome, rng: RandomNumberGenerator, bunt: bool = false,
		ball: BattedBallSim.Result = null, batter: int = 0, run_mps: Callable = Callable()) -> Dictionary:
	var side := batting_side()
	var outs_before := outs
	var res := {"outcome": outcome, "runs": 0, "rbi": 0, "scorers": [], "outs": 0, "note": "", "half_over": false, "sac_fly": false}
	if bunt and outcome == SwingJudge.Outcome.GROUND_OUT and has_runner() and outs_before < 2:
		outcome = SwingJudge.Outcome.SAC_BUNT
		res.outcome = outcome
	if ball != null and _phys != null:
		# 맞은 공: 주자 FSM 이 진행한다 (run_mps 대신 주력 함수가 필요하다 — speed_of 로 넘어온다)
		var speed_of := run_mps if run_mps.is_valid() else func(_i: int) -> int: return 50
		return apply_play(make_play(ball, batter, speed_of, rng), bunt)
	var speed := func(_idx: int) -> float:
		return 7.5

	match outcome:
		SwingJudge.Outcome.STRIKEOUT, SwingJudge.Outcome.LINE_OUT:
			_out(res)
		SwingJudge.Outcome.SAC_BUNT:
			# 타자는 아웃, 주자는 한 베이스씩 (3루 주자는 홈인 = 스퀴즈)
			_out(res)
			res.note = "희생번트"
			if outs < 3:
				_score(res, bases[2], true)
				bases = [EMPTY, bases[0], bases[1]]
		SwingJudge.Outcome.FLY_OUT:
			_out(res)
			if outs < 3:
				_tag_up(res, ball, rng, speed)
		SwingJudge.Outcome.GROUND_OUT:
			_ground_out(res, ball, rng, batter, outs_before, speed)
		SwingJudge.Outcome.WALK:
			if occupied(0):
				if occupied(1):
					if occupied(2):
						_score(res, bases[2], true)
					bases[2] = bases[1]
				bases[1] = bases[0]
			bases[0] = batter
		SwingJudge.Outcome.HOME_RUN:
			for i in [2, 1, 0]:
				_score(res, bases[i], true)
			_score(res, batter, true)
			bases = [EMPTY, EMPTY, EMPTY]
		SwingJudge.Outcome.SINGLE, SwingJudge.Outcome.DOUBLE, SwingJudge.Outcome.TRIPLE:
			_hit(res, outcome, ball, rng, batter, speed)

	_add_runs(side, res.runs)
	batter_index[side] = (batter_index[side] + 1) % 9
	if outs >= 3:
		res.half_over = true
		_end_half()
	elif not top and inning >= innings and score[HOME] > score[AWAY]:
		over = true  # 끝내기
	return res


# ---------- 상황별 주루 ----------

func _out(res: Dictionary) -> void:
	outs += 1
	res.outs += 1


func _score(res: Dictionary, runner: int, rbi: bool) -> void:
	if runner == EMPTY:
		return
	res.runs += 1
	res.scorers.append(runner)
	if rbi:
		res.rbi += 1


## 안타: 최소 진루 + 시간이 되면 한 베이스 더
func _hit(res: Dictionary, outcome: SwingJudge.Outcome, ball: BattedBallSim.Result, rng: RandomNumberGenerator, batter: int, speed: Callable) -> void:
	var h := 1
	if outcome == SwingJudge.Outcome.DOUBLE:
		h = 2
	elif outcome == SwingJudge.Outcome.TRIPLE:
		h = 3
	var infield_hit := ball != null and ball.is_first_base_play()
	# 2사면 맞는 순간 뛴다 (투아웃 스타트)
	var jump: float = float(_running["leadStartS"]) + (float(_running["twoOutJumpS"]) if outs == 2 else 0.0)
	var new_bases := [EMPTY, EMPTY, EMPTY]
	var blocked := HOME_PLATE + 1  # 앞 주자가 멈춘 베이스 (뒤 주자는 그 앞까지만)
	for i: int in [2, 1, 0]:
		var runner: int = bases[i]
		if runner == EMPTY:
			continue
		var from: int = i + 1
		var target: int = from + h
		if infield_hit:
			# 내야안타: 밀려나는 주자만 한 베이스
			target = from + (1 if _forced(i) else 0)
		elif target < HOME_PLATE:
			if ball != null and _phys != null and ball.fielder != "":
				if _runner_beats_throw(ball, from, target + 1, float(speed.call(runner)), jump):
					target += 1
			elif _fallback_extra(outcome, from, rng):
				target += 1
		if target < HOME_PLATE:
			target = mini(target, blocked - 1)
		if target >= HOME_PLATE:
			_score(res, runner, true)
		else:
			new_bases[target - 1] = runner
			blocked = target
	new_bases[h - 1] = batter
	bases = new_bases


## 내야 땅볼 아웃: 병살(2루 포스 → 1루) 시도, 아니면 타자만 아웃·주자 진루
func _ground_out(res: Dictionary, ball: BattedBallSim.Result, rng: RandomNumberGenerator, batter: int, outs_before: int, speed: Callable) -> void:
	if occupied(0) and outs_before < 2:
		var force_out := false
		var relay_out := false
		if ball != null and _phys != null and ball.fielder != "":
			var lead: float = _running["leadStartS"]
			var t_force := ball.duration() + _phys.transfer_s + ball.fielder_to.distance_to(_phys.base_pos(2)) / _phys.throw_mps
			var runner_t: float = -lead + _phys.base_distance / float(speed.call(bases[0]))
			force_out = t_force < runner_t
			if force_out:
				var t_relay := t_force + float(_running["pivotS"]) + _phys.base_pos(2).distance_to(_phys.base_pos(1)) / _phys.throw_mps
				relay_out = t_relay < ball.runner_first
		else:
			force_out = true
			relay_out = rng.randf() < float(_running["doublePlayChance"])
		if force_out:
			var old := bases.duplicate()
			_out(res)  # 2루에서 1루 주자 포스 아웃
			if relay_out:
				_out(res)  # 1루에서 타자도 아웃
				res.note = "병살"
				bases = [EMPTY, EMPTY, EMPTY]
			else:
				res.note = "야수선택"
				bases = [batter, EMPTY, EMPTY]
			if outs < 3:
				# 밀려난 주자들은 한 베이스씩 (3루 주자는 병살이 아니고 무사·1사였으면 홈인)
				if old[2] != EMPTY:
					if old[1] != EMPTY or not relay_out:
						_score(res, old[2], not relay_out)
					else:
						bases[2] = old[2]
				if old[1] != EMPTY:
					bases[2] = old[1]
			return
	# 타자만 1루에서 아웃, 주자는 한 베이스씩 (3루 주자는 무사·1사일 때 홈인)
	_out(res)
	if outs < 3:
		_advance_others(res, outs_before < 2)


## 땅볼 아웃 뒤 남은 주자 진루
func _advance_others(res: Dictionary, third_scores: bool) -> void:
	var new_bases := [EMPTY, EMPTY, EMPTY]
	if bases[2] != EMPTY:
		if third_scores:
			_score(res, bases[2], true)
		else:
			new_bases[2] = bases[2]
	if bases[1] != EMPTY:
		if new_bases[2] == EMPTY:
			new_bases[2] = bases[1]
		else:
			new_bases[1] = bases[1]
	if bases[0] != EMPTY:
		if new_bases[1] == EMPTY:
			new_bases[1] = bases[0]
		else:
			new_bases[0] = bases[0]
	bases = new_bases


## 뜬공 태그업: 잡은 순간 출발해 송구보다 빠르면 진루
func _tag_up(res: Dictionary, ball: BattedBallSim.Result, rng: RandomNumberGenerator, speed: Callable) -> void:
	if ball != null and _phys != null and ball.fielder != "":
		if ball.fielder in _phys.infield:
			return  # 내야 뜬공은 태그업하지 않는다
		var catch_t := ball.duration()
		var tag: float = _running["tagUpS"]
		if occupied(2):
			var runner_t: float = catch_t + tag + _phys.base_distance / float(speed.call(bases[2]))
			var throw_t := catch_t + _phys.transfer_s * 0.5 + BattedBallSim.outfield_to_base_s(_phys, ball.fielder_to.length()) + float(_running["tagS"])
			if runner_t < throw_t:
				_score(res, bases[2], true)
				bases[2] = EMPTY
				res.note = "희생플라이"
				res.sac_fly = true
		if occupied(1) and not occupied(2):
			var runner2: float = catch_t + tag + _phys.base_distance / float(speed.call(bases[1]))
			var throw3 := catch_t + _phys.transfer_s * 0.5 + BattedBallSim.outfield_to_base_s(_phys, ball.fielder_to.distance_to(_phys.base_pos(3))) + float(_running["tagS"])
			if runner2 < throw3:
				bases[2] = bases[1]
				bases[1] = EMPTY
		return
	if occupied(2) and rng.randf() < float(_running["sacFlyChance"]):
		_score(res, bases[2], true)
		bases[2] = EMPTY
		res.note = "희생플라이"
		res.sac_fly = true


## 안타 때 주자가 from 루에서 to 루(4 = 홈)까지 가는 시간이 그 베이스로 가는 송구(+태그)보다 짧은가.
## 베이스 위 주자는 이미 움직이는 중이라 타자보다 빠르다 (runnerSpeedBonusMps), lead 만큼 먼저 출발한 셈
func _runner_beats_throw(ball: BattedBallSim.Result, from: int, to: int, mps: float, lead: float) -> bool:
	var legs := to - from
	var v := mps + float(_running["runnerSpeedBonusMps"])
	var runner_t := -lead + legs * _phys.base_distance / v + legs * _phys.round_base_s
	var target := Vector2.ZERO if to >= HOME_PLATE else _phys.base_pos(to)
	var outfield := not (ball.fielder in _phys.infield)
	var ready := ball.duration() + (_phys.pickup_s if outfield else _phys.transfer_s)
	var throw_t := ready + (BattedBallSim.outfield_to_base_s(_phys, ball.fielder_to.distance_to(target)) if outfield else ball.fielder_to.distance_to(target) / _phys.throw_mps)
	return runner_t + float(_running["extraBaseMarginS"]) < throw_t + float(_running["tagS"])


## 타구 물리가 없을 때의 예비 확률
func _fallback_extra(outcome: SwingJudge.Outcome, from: int, rng: RandomNumberGenerator) -> bool:
	if outcome == SwingJudge.Outcome.SINGLE and from == 2:
		return rng.randf() < float(_running["singleScoresFromSecond"])
	if outcome == SwingJudge.Outcome.DOUBLE and from == 1:
		return rng.randf() < float(_running["doubleScoresFromFirst"])
	return false


## i 루 주자가 밀려나는가 (뒤가 모두 차 있으면)
func _forced(i: int) -> bool:
	for j in range(0, i):
		if not occupied(j):
			return false
	return true


func _add_runs(side: int, runs: int) -> void:
	score[side] += runs
	line[side][inning - 1] += runs


func _end_half() -> void:
	outs = 0
	bases = [EMPTY, EMPTY, EMPTY]
	if top:
		# 마지막 이닝 초, 홈 팀이 이기고 있으면 말 공격 없이 끝
		if inning >= innings and score[HOME] > score[AWAY]:
			over = true
			return
		top = false
		line[HOME].append(0)
	else:
		if inning >= innings:
			over = true  # 정규 이닝 종료 (동점이면 무승부 — 프로토타입)
			return
		inning += 1
		top = true
		line[AWAY].append(0)
