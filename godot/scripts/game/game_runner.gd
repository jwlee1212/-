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

var _player: PlayerData
var _rng: RandomNumberGenerator


## 두 학교의 고정 명단으로 경기를 만든다. playing = false 면 내 선수는 결장 (그 자리는 원래 명단 선수)
func _init(p_cfg: CareerConfig, p_bc: BattingConfig, player: PlayerData, my_school: School, p_opponent: School, home: bool,
		seed_value: int, playing: bool = true) -> void:
	cfg = p_cfg
	bc = p_bc
	_player = player
	opponent = p_opponent
	me_playing = playing
	my_side = GameState.HOME if home else GameState.AWAY
	state = GameState.new(cfg.innings, cfg.running, bc.ball_physics)
	_rng = RandomNumberGenerator.new()
	_rng.seed = seed_value
	var mine := my_school.team.for_game(player if playing else null, player.skills_for_game(cfg))
	var theirs := opponent.team.for_game(null, null)
	teams.assign([mine, theirs] if my_side == GameState.AWAY else [theirs, mine])
	box = BoxScore.new(teams[0].pitchers.size(), teams[1].pitchers.size())
	log.append("%s vs %s — 경기 시작!" % [team_name(GameState.AWAY), team_name(GameState.HOME)])


func team_name(side: int) -> String:
	return teams[side].name


func team_short(side: int) -> String:
	return teams[side].short


func is_my_turn() -> bool:
	return me_playing and not state.over and state.batting_side() == my_side and state.batter_index[my_side] == _player.lineup_slot - 1


func current_batter() -> Team.Batter:
	var side := state.batting_side()
	return teams[side].lineup[state.batter_index[side]]


func current_batter_name() -> String:
	return current_batter().name


## 지금 공을 던지는 (수비 팀) 투수
func current_pitcher() -> Team.Pitcher:
	var def := 1 - state.batting_side()
	return teams[def].pitchers[pitcher_index[def]]


## 다음 타석. 돌려주는 값: {"type": "my_turn" | "auto" | "over", "text": 중계 한 줄,
##   "highlight": 하이라이트로 보여 줄 플레이(PlayResult, 없으면 null), "side": 공격한 팀}
func step() -> Dictionary:
	if state.over:
		return {"type": "over"}
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
	var pitcher_profile := current_pitcher().profile
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
