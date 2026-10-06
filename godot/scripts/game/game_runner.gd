class_name GameRunner
extends RefCounted
## 한 경기 진행. 타석을 하나씩 처리한다: 내 차례면 "my_turn" 을 돌려주고(화면이 타석 화면을 띄운다),
## 아니면 자동으로 결과를 정해 문자 중계 한 줄을 만든다.

var state: GameState
var cfg: CareerConfig
var my_side: int
var opponent: Dictionary
## 이번 경기 내 기록
var my_line := PlayerData.SeasonStats.new()
## 문자 중계 (최근 것이 끝)
var log: Array[String] = []

var _player: PlayerData
var _rng: RandomNumberGenerator
var _lineups := [[], []]  # 팀별 타자 이름 9명 (내 자리는 내 이름)


func _init(p_cfg: CareerConfig, player: PlayerData, p_opponent: Dictionary, home: bool, seed_value: int) -> void:
	cfg = p_cfg
	_player = player
	opponent = p_opponent
	my_side = GameState.HOME if home else GameState.AWAY
	state = GameState.new(cfg.innings, cfg.running)
	_rng = RandomNumberGenerator.new()
	_rng.seed = seed_value
	for side in [GameState.AWAY, GameState.HOME]:
		for i in 9:
			_lineups[side].append(_random_name())
	_lineups[my_side][player.lineup_slot - 1] = player.name
	log.append("%s vs %s — 경기 시작!" % [team_name(GameState.AWAY), team_name(GameState.HOME)])


func team_name(side: int) -> String:
	return cfg.my_school["name"] if side == my_side else opponent["name"]


func team_short(side: int) -> String:
	return cfg.my_school["short"] if side == my_side else opponent["short"]


func is_my_turn() -> bool:
	return not state.over and state.batting_side() == my_side and state.batter_index[my_side] == _player.lineup_slot - 1


func current_batter_name() -> String:
	var side := state.batting_side()
	return _lineups[side][state.batter_index[side]]


## 다음 타석. 돌려주는 값: {"type": "my_turn" | "auto" | "over", "text": 중계 한 줄}
func step() -> Dictionary:
	if state.over:
		return {"type": "over"}
	if is_my_turn():
		return {"type": "my_turn"}
	var side := state.batting_side()
	var outcome := _auto_outcome(side)
	var name := current_batter_name()
	var half := state.half_text()
	var r := state.apply(outcome, _rng)
	var text := "[%s] %s %d번 %s — %s%s" % [half, team_short(side), (state.batter_index[side] + 8) % 9 + 1, name,
		outcome_label(outcome), _runs_text(r)]
	log.append(text)
	_after_half(r)
	return {"type": "auto", "text": text}


## 내 타석 결과를 경기에 반영한다
func apply_my_result(result: AtBat.Result) -> String:
	var half := state.half_text()
	var r := state.apply(result.outcome, _rng, result.bunt)
	var outcome: SwingJudge.Outcome = r.outcome
	my_line.add(outcome, r.rbi)
	var text := "[%s] ★ %s — %s%s" % [half, _player.name, outcome_label(outcome), _runs_text(r)]
	log.append(text)
	_after_half(r)
	return text


## 우리 팀 기준 승패: 1 승, 0 무, -1 패
func my_result() -> int:
	var mine: int = state.score[my_side]
	var theirs: int = state.score[1 - my_side]
	return signi(mine - theirs)


func _after_half(r: Dictionary) -> void:
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


## 자동 타석: 기본 확률표에 팀 전력 차만큼 안타 배율을 곱한다
func _auto_outcome(side: int) -> SwingJudge.Outcome:
	var bat: float = cfg.my_team_strength if side == my_side else float(opponent["strength"])
	var pitch: float = float(opponent["strength"]) if side == my_side else cfg.my_team_strength
	var hit_mul := maxf(0.3, 1.0 + (bat - pitch) * cfg.strength_hit_mul_per_point)
	var weights := {}
	var total := 0.0
	for o: int in cfg.outcome_weights:
		var w: float = cfg.outcome_weights[o] * (hit_mul if SwingJudge.is_hit(o) else 1.0)
		weights[o] = w
		total += w
	var roll := _rng.randf() * total
	for o: int in weights:
		roll -= weights[o]
		if roll < 0.0:
			return o
	return SwingJudge.Outcome.GROUND_OUT


func _random_name() -> String:
	return cfg.surnames[_rng.randi_range(0, cfg.surnames.size() - 1)] + cfg.given_names[_rng.randi_range(0, cfg.given_names.size() - 1)]


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
