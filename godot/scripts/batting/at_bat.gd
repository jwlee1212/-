class_name AtBat
extends RefCounted
## 타석 하나 (볼카운트 진행).
##
## 화면은 next_pitch() 로 공을 받아 날리고, 유저가 탭하면 swing(), 탭 없이 공이 지나가면 take() 를 부른다.
## 4볼 → 볼넷, 3스트라이크 → 삼진, 2스트라이크 이후 파울은 카운트를 올리지 않는다.

## 공 하나의 판정 종류
enum Call { CALLED_STRIKE, BALL, SWINGING_STRIKE, FOUL, IN_PLAY }


## 타석 하나의 최종 결과
class Result:
	var outcome: SwingJudge.Outcome
	var batted_ball: SwingJudge.BattedBall
	var pitches: int
	var contact: SwingJudge.Contact  # 인플레이가 아니면 null
	## 번트 타구였는가 (주자가 있으면 경기가 희생번트로 바꾼다)
	var bunt := false
	## 맞은 공의 플레이 (주자·송구·판정). 인플레이가 아니면 null
	var play: PlaySimulator.PlayResult = null
	## 타석을 끝낸 공을 던질 때의 카운트 (기록실 카운트별 기록). 모르면 -1
	var balls := -1
	var strikes := -1

	func _init(o: SwingJudge.Outcome, b: SwingJudge.BattedBall, n: int, c: SwingJudge.Contact) -> void:
		outcome = o
		batted_ball = b
		pitches = n
		contact = c
		bunt = c != null and c.bunt

	## 리그 시뮬레이션에 넘길 기록. 이름은 옛 Kotlin 엔진 PaOutcome·BattedBallType 과 같다.
	## 실제 경기 연결(주루·타점)은 아직 하지 않는다
	func to_record() -> Dictionary:
		return {
			"outcome": SwingJudge.Outcome.keys()[outcome],
			"batted_ball": null if batted_ball == SwingJudge.BattedBall.NONE else SwingJudge.BattedBall.keys()[batted_ball],
			"pitches": pitches,
		}


## 노려치기 결과
enum Aim { NONE, HIT, MISS }


## 공 하나를 처리한 결과. timing_diff_ms 는 스윙했을 때만 의미 있다 (탭 시각 − 도착 시각, 음수면 빠름)
class PitchOutcome:
	var call: Call
	var swung: bool
	var timing_diff_ms: float
	var contact: SwingJudge.Contact  # 스윙 안 했으면 null
	var result: Result  # 이 공으로 타석이 끝났으면 그 결과, 아니면 null
	var aim := Aim.NONE  # 노려치기를 걸고 스윙했을 때 그 칸에 왔는가

	func _init(p_call: Call, p_swung: bool, diff: float, c: SwingJudge.Contact, r: Result) -> void:
		call = p_call
		swung = p_swung
		timing_diff_ms = diff
		contact = c
		result = r


## 능력치. 바꾸면 다음 공부터 반영된다 (프로토타입 슬라이더용)
var skills: BatterSkills
## 상대 투수. 바꾸면 다음 공부터 반영된다
var pitcher: BattingConfig.PitcherProfile
## 노려치기로 찍어 둔 존 칸 (0~8, 없으면 -1). 바꾸면 다음 스윙부터 반영된다
var aim_cell := -1
## 스윙 종류 ("normal" | "contact" | "power" | "bunt"). 바꾸면 다음 스윙부터 반영된다
var swing_type := "normal"
## 경기 상황 (주자·아웃). 연습이면 주자 없음. {"bases": [1·2·3루 타순 번호 또는 -1], "outs", "batter", "speed_of"}
var situation := {}
var balls := 0
var strikes := 0
var pitch_count := 0
var current: Pitch = null
var result: Result = null

var _config: BattingConfig
var _judge: SwingJudge
var _caller: PitchCaller
var _rng: RandomNumberGenerator
## 지금 처리 중인 공을 던질 때의 카운트
var _pitch_balls := 0
var _pitch_strikes := 0


func _init(config: BattingConfig, p_skills: BatterSkills, p_pitcher: BattingConfig.PitcherProfile, seed_value: int) -> void:
	_config = config
	_judge = SwingJudge.new(config)
	_caller = PitchCaller.new(config)
	skills = p_skills
	pitcher = p_pitcher
	_rng = RandomNumberGenerator.new()
	_rng.seed = seed_value


func is_over() -> bool:
	return result != null


func next_pitch() -> Pitch:
	assert(not is_over(), "끝난 타석이다")
	assert(current == null, "이전 공을 아직 처리하지 않았다")
	current = _caller.next(pitcher, balls, strikes, skills, _rng)
	return current


## 스윙. tap_ms 는 투수가 공을 놓은 순간부터 탭까지의 시간. 입력 지연 보정은 여기서 뺀다
func swing(tap_ms: float) -> PitchOutcome:
	var pitch := _take_current()
	var diff := tap_ms - _config.input_latency_ms - pitch.flight_ms
	# 노려치기: 찍어 둔 칸에 오면 판정 폭이 넓어지고 정타 거리가 늘어난다. 다른 데로 오면 좁아진다
	var aim := Aim.NONE
	var aim_factor := 1.0
	var bonus_kmh := 0.0
	if _config.aim_enabled and aim_cell >= 0 and swing_type != "bunt":
		if Pitch.cell_of(pitch.target) == aim_cell:
			aim = Aim.HIT
			aim_factor = _config.aim_hit_window_factor
			bonus_kmh = _config.aim_hit_ev_bonus
		else:
			aim = Aim.MISS
			aim_factor = _config.aim_miss_window_factor
	var c := _judge.contact(pitch, skills, diff, _rng, aim_factor, bonus_kmh, swing_type)
	var o: PitchOutcome
	match c.quality:
		SwingJudge.Quality.MISS:
			o = _strike(Call.SWINGING_STRIKE, true, diff, c)
		SwingJudge.Quality.FOUL:
			if swing_type == "bunt" and strikes >= 2:
				# 2스트라이크 번트 파울은 삼진 (야구 규칙)
				o = _strike(Call.FOUL, true, diff, c)
			else:
				if strikes < 2:
					strikes += 1
				o = PitchOutcome.new(Call.FOUL, true, diff, c, null)
		_:
			_judge.outcome_of(c, skills)
			# 주자·수비까지 진행해 최종 결과를 정한다 (화면도 이 플레이를 그린다)
			var play := _run_play(c)
			var res := Result.new(play.batter_outcome, c.batted_ball, pitch_count, c)
			res.play = play
			o = _finish(PitchOutcome.new(Call.IN_PLAY, true, diff, c, res))
	o.aim = aim
	return o


## 치지 않음
func take() -> PitchOutcome:
	var pitch := _take_current()
	if pitch.is_strike():
		return _strike(Call.CALLED_STRIKE, false, 0.0, null)
	balls += 1
	var r: Result = Result.new(SwingJudge.Outcome.WALK, SwingJudge.BattedBall.NONE, pitch_count, null) if balls >= 4 else null
	return _finish(PitchOutcome.new(Call.BALL, false, 0.0, null, r))


func _strike(call: Call, swung: bool, diff: float, c: SwingJudge.Contact) -> PitchOutcome:
	strikes += 1
	var r: Result = Result.new(SwingJudge.Outcome.STRIKEOUT, SwingJudge.BattedBall.NONE, pitch_count, null) if strikes >= 3 else null
	return _finish(PitchOutcome.new(call, swung, diff, c, r))


func _finish(o: PitchOutcome) -> PitchOutcome:
	result = o.result
	if result != null:
		result.balls = _pitch_balls
		result.strikes = _pitch_strikes
	return o


func _run_play(c: SwingJudge.Contact) -> PlaySimulator.PlayResult:
	var bases: Array = situation.get("bases", [-1, -1, -1])
	var batter: int = situation.get("batter", 0)
	var given: Callable = situation.get("speed_of", Callable())
	var my_speed := skills.speed
	var speed_of := func(i: int) -> int:
		if i == batter:
			return my_speed
		return given.call(i) if given.is_valid() else 50
	return PlaySimulator.new(_config.ball_physics, c.ball, batter, bases, speed_of, int(situation.get("outs", 0)), _rng).run()


func _take_current() -> Pitch:
	assert(current != null, "던진 공이 없다")
	var pitch := current
	current = null
	pitch_count += 1
	_pitch_balls = balls
	_pitch_strikes = strikes
	return pitch
