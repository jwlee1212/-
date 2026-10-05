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

	func _init(o: SwingJudge.Outcome, b: SwingJudge.BattedBall, n: int, c: SwingJudge.Contact) -> void:
		outcome = o
		batted_ball = b
		pitches = n
		contact = c

	## 리그 시뮬레이션에 넘길 기록. 이름은 옛 Kotlin 엔진 PaOutcome·BattedBallType 과 같다.
	## 실제 경기 연결(주루·타점)은 아직 하지 않는다
	func to_record() -> Dictionary:
		return {
			"outcome": SwingJudge.Outcome.keys()[outcome],
			"batted_ball": null if batted_ball == SwingJudge.BattedBall.NONE else SwingJudge.BattedBall.keys()[batted_ball],
			"pitches": pitches,
		}


## 공 하나를 처리한 결과. timing_diff_ms 는 스윙했을 때만 의미 있다 (탭 시각 − 도착 시각, 음수면 빠름)
class PitchOutcome:
	var call: Call
	var swung: bool
	var timing_diff_ms: float
	var contact: SwingJudge.Contact  # 스윙 안 했으면 null
	var result: Result  # 이 공으로 타석이 끝났으면 그 결과, 아니면 null

	func _init(p_call: Call, p_swung: bool, diff: float, c: SwingJudge.Contact, r: Result) -> void:
		call = p_call
		swung = p_swung
		timing_diff_ms = diff
		contact = c
		result = r


## 능력치. 바꾸면 다음 공부터 반영된다 (프로토타입 슬라이더용)
var skills: BatterSkills
var balls := 0
var strikes := 0
var pitch_count := 0
var current: Pitch = null
var result: Result = null

var _config: BattingConfig
var _judge: SwingJudge
var _rng: RandomNumberGenerator


func _init(config: BattingConfig, p_skills: BatterSkills, seed_value: int) -> void:
	_config = config
	_judge = SwingJudge.new(config)
	skills = p_skills
	_rng = RandomNumberGenerator.new()
	_rng.seed = seed_value


func is_over() -> bool:
	return result != null


func next_pitch() -> Pitch:
	assert(not is_over(), "끝난 타석이다")
	assert(current == null, "이전 공을 아직 처리하지 않았다")
	current = Pitch.generate(_config, skills, _rng)
	return current


## 스윙. tap_ms 는 투수가 공을 놓은 순간부터 탭까지의 시간. 입력 지연 보정은 여기서 뺀다
func swing(tap_ms: float) -> PitchOutcome:
	var pitch := _take_current()
	var diff := tap_ms - _config.input_latency_ms - pitch.flight_ms
	var c := _judge.contact(pitch, skills, diff, _rng)
	match c.quality:
		SwingJudge.Quality.MISS:
			return _strike(Call.SWINGING_STRIKE, true, diff, c)
		SwingJudge.Quality.FOUL:
			if strikes < 2:
				strikes += 1
			return PitchOutcome.new(Call.FOUL, true, diff, c, null)
	var outcome := _judge.outcome_of(c, _rng)
	return _finish(PitchOutcome.new(Call.IN_PLAY, true, diff, c, Result.new(outcome, c.batted_ball, pitch_count, c)))


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
	return o


func _take_current() -> Pitch:
	assert(current != null, "던진 공이 없다")
	var pitch := current
	current = null
	pitch_count += 1
	return pitch
