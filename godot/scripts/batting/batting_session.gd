class_name BattingSession
extends RefCounted
## 타석 화면의 상태. 판정은 전부 AtBat 이 하고, 여기서는 시간 흐름과 연출 단계만 관리한다.
##
## 시간은 Time.get_ticks_usec() 하나로 잰다 — 공을 놓은 순간과 탭한 순간을 같은 시계로 재야
## 타이밍 판정이 프레임 속도와 무관해진다. 시드는 타석마다 1, 2, 3… 으로 올라간다.

## 효과음을 내야 할 때
signal sound_requested(sfx: SoundSynth.Sfx, volume: float)

enum Phase {
	WAITING,  ## 다음 공을 기다리는 중
	WINDUP,   ## 투수 와인드업. 이 동안은 탭해도 스윙하지 않는다
	FLIGHT,   ## 공이 날아오는 중
	HIT,      ## 맞은 공이 날아가는 중
	OVER,     ## 타석 끝. 탭하면 다음 타석
}

enum Tone { GOOD, BAD, NEUTRAL, BIG }

## 타석 결과 문구가 뜨기까지 타구가 날아가는 비율 (맞자마자 결과를 보여 주면 날아가는 장면의 기대감이 사라진다)
const CALLOUT_DELAY_SHARE := 0.55


## 화면에 잠깐 띄우는 판정 문구
class Callout:
	var text: String
	var detail: String
	var since_ms: float
	var tone: Tone
	var delay_ms: float


## 공이 배트에 맞은 순간 (섬광·흔들림·파편 연출용)
class Impact:
	var since_ms: float
	var quality: SwingJudge.Quality
	var home_run: bool


## 이번 세션 누적 기록
class Tally:
	var plate_appearances := 0
	var at_bats := 0
	var hits := 0
	var home_runs := 0
	var strikeouts := 0
	var walks := 0

	func add(outcome: SwingJudge.Outcome) -> void:
		plate_appearances += 1
		if outcome != SwingJudge.Outcome.WALK:
			at_bats += 1
		if SwingJudge.is_hit(outcome):
			hits += 1
		if outcome == SwingJudge.Outcome.HOME_RUN:
			home_runs += 1
		if outcome == SwingJudge.Outcome.STRIKEOUT:
			strikeouts += 1
		if outcome == SwingJudge.Outcome.WALK:
			walks += 1

	func average() -> String:
		if at_bats == 0:
			return "-"
		var avg := roundi(hits * 1000.0 / at_bats)
		return "1.000" if avg >= 1000 else ".%03d" % avg


var config: BattingConfig
var presentation: Presentation
var skills := BatterSkills.new(50, 50, 50)
var at_bat: AtBat
var seed_value := 0

var phase := Phase.WAITING
var phase_since_ms := 0.0
## 지금 던진 공 (WINDUP·FLIGHT·HIT 에서 유효)
var pitch: Pitch
## 스윙 기록 (FLIGHT 중 이미 휘둘렀는지, HIT 의 맞은 시점)
var swung := false
var swing_at_ms := 0.0
var swing_since_ms := 0.0
var swing_outcome: AtBat.PitchOutcome
var mitt_played := false
var last_result: AtBat.Result

var callout: Callout
var impact: Impact
## 마지막 스윙의 타이밍 차이 (ms, +면 늦음). 아직 없으면 NAN
var last_timing_ms := NAN
var spray: Array = []  # [angle_deg, distance_m, outcome]
var tally := Tally.new()


func _init(p_config: BattingConfig, p_presentation: Presentation) -> void:
	config = p_config
	presentation = p_presentation
	at_bat = _new_at_bat()
	_enter(Phase.WAITING)


static func now_ms() -> float:
	return Time.get_ticks_usec() / 1000.0


func since(t_ms: float) -> float:
	return now_ms() - t_ms


func update_skills(new_skills: BatterSkills) -> void:
	skills = new_skills
	at_bat.skills = new_skills


## 매 프레임 호출. 시간이 지나 단계가 넘어가야 하면 넘긴다
func tick() -> void:
	var e := since(phase_since_ms)
	match phase:
		Phase.WAITING:
			if e >= presentation.pitch_interval_ms:
				pitch = at_bat.next_pitch()
				_enter(Phase.WINDUP)
		Phase.WINDUP:
			if e >= presentation.windup_ms:
				swung = false
				mitt_played = false
				_enter(Phase.FLIGHT)
				_sound(SoundSynth.Sfx.PITCH, 0.8)
		Phase.FLIGHT:
			# 안 쳤거나 헛스윙이면 공이 도착하는 순간 미트 소리
			var missed := not swung or swing_outcome.call == AtBat.Call.SWINGING_STRIKE
			if e >= pitch.flight_ms and missed and not mitt_played:
				mitt_played = true
				_sound(SoundSynth.Sfx.MITT, 1.0)
			if e >= pitch.flight_ms + config.take_grace_ms:
				_after_pitch(swing_outcome if swung else at_bat.take())
		Phase.HIT:
			if e >= presentation.hit_stop_ms + presentation.hit_flight_ms:
				# 타구가 떨어지는 순간 안타면 함성
				var o := last_result.outcome
				if SwingJudge.is_hit(o) and o != SwingJudge.Outcome.HOME_RUN:
					_sound(SoundSynth.Sfx.CHEER_SMALL, 1.0)
				_finish_at_bat(last_result)


## 화면 탭. 공이 날아오는 중이면 스윙, 타석이 끝났으면 다음 타석
func tap() -> void:
	match phase:
		Phase.FLIGHT:
			if swung:
				return
			var at_ms := since(phase_since_ms)
			var o := at_bat.swing(at_ms)
			last_timing_ms = o.timing_diff_ms
			swung = true
			swing_at_ms = at_ms
			swing_since_ms = now_ms()
			swing_outcome = o
			_play_swing_sounds(o)
			if o.call == AtBat.Call.IN_PLAY:
				last_result = o.result
				_enter(Phase.HIT)
				_show_result_callout(o.result)
			else:
				# 헛스윙·파울은 공이 끝까지 지나간 뒤 다음으로
				_show_pitch_callout(o)
		Phase.OVER:
			at_bat = _new_at_bat()
			_enter(Phase.WAITING)


func _play_swing_sounds(o: AtBat.PitchOutcome) -> void:
	_sound(SoundSynth.Sfx.SWING, 0.7)
	var c := o.contact
	match c.quality:
		SwingJudge.Quality.SOLID:
			_sound(SoundSynth.Sfx.CRACK_SOLID, 1.0)
		SwingJudge.Quality.WEAK:
			_sound(SoundSynth.Sfx.CRACK_WEAK, 1.0)
		SwingJudge.Quality.FOUL:
			_sound(SoundSynth.Sfx.CRACK_FOUL, 1.0)
		SwingJudge.Quality.MISS:
			return
	var home_run := o.result != null and o.result.outcome == SwingJudge.Outcome.HOME_RUN
	if home_run:
		_sound(SoundSynth.Sfx.CHEER_BIG, 1.0)
	impact = Impact.new()
	impact.since_ms = now_ms()
	impact.quality = c.quality
	impact.home_run = home_run


func _sound(sfx: SoundSynth.Sfx, volume: float) -> void:
	sound_requested.emit(sfx, volume * presentation.sfx_volume)


func _after_pitch(o: AtBat.PitchOutcome) -> void:
	if o.call != AtBat.Call.SWINGING_STRIKE and o.call != AtBat.Call.FOUL:
		_show_pitch_callout(o)
	if o.result != null:
		_finish_at_bat(o.result)
	else:
		_enter(Phase.WAITING)


func _finish_at_bat(r: AtBat.Result) -> void:
	tally.add(r.outcome)
	if r.contact != null:
		spray.append([r.contact.angle_deg, r.contact.distance_m, r.outcome])
	else:
		_show_result_callout(r)
	last_result = r
	_enter(Phase.OVER)


func _show_pitch_callout(o: AtBat.PitchOutcome) -> void:
	match o.call:
		AtBat.Call.CALLED_STRIKE:
			_set_callout("스트라이크!", "", Tone.BAD)
		AtBat.Call.BALL:
			_set_callout("볼", "", Tone.NEUTRAL)
		AtBat.Call.SWINGING_STRIKE:
			_set_callout("헛스윙!", timing_text(o.timing_diff_ms), Tone.BAD)
		AtBat.Call.FOUL:
			_set_callout("파울", timing_text(o.timing_diff_ms), Tone.NEUTRAL)


func _show_result_callout(r: AtBat.Result) -> void:
	var tone := Tone.BAD
	if r.outcome == SwingJudge.Outcome.HOME_RUN:
		tone = Tone.BIG
	elif SwingJudge.is_hit(r.outcome) or r.outcome == SwingJudge.Outcome.WALK:
		tone = Tone.GOOD
	var detail := ""
	var delay := 0.0
	if r.contact != null:
		detail = "%s · %dm · %s" % [direction_text(r.contact.angle_deg), roundi(r.contact.distance_m), timing_text(last_timing_ms)]
		delay = presentation.hit_stop_ms + presentation.hit_flight_ms * CALLOUT_DELAY_SHARE
	_set_callout(outcome_text(r.outcome), detail, tone, delay)


func _set_callout(text: String, detail: String, tone: Tone, delay_ms: float = 0.0) -> void:
	var c := Callout.new()
	c.text = text
	c.detail = detail
	c.since_ms = now_ms()
	c.tone = tone
	c.delay_ms = delay_ms
	callout = c


func _enter(p: Phase) -> void:
	phase = p
	phase_since_ms = now_ms()


func _new_at_bat() -> AtBat:
	seed_value += 1
	return AtBat.new(config, skills, seed_value)


static func outcome_text(o: SwingJudge.Outcome) -> String:
	match o:
		SwingJudge.Outcome.SINGLE: return "안타!"
		SwingJudge.Outcome.DOUBLE: return "2루타!"
		SwingJudge.Outcome.TRIPLE: return "3루타!"
		SwingJudge.Outcome.HOME_RUN: return "홈런!!"
		SwingJudge.Outcome.WALK: return "볼넷"
		SwingJudge.Outcome.STRIKEOUT: return "삼진"
		SwingJudge.Outcome.GROUND_OUT: return "땅볼 아웃"
		SwingJudge.Outcome.FLY_OUT: return "뜬공 아웃"
		SwingJudge.Outcome.LINE_OUT: return "직선타 아웃"
	return "?"


## 타구 방향. 오른손 타자 기준 — 음수(일찍 침)는 좌측
static func direction_text(angle_deg: float) -> String:
	if absf(angle_deg) > 45.0:
		return "3루 쪽 파울" if angle_deg < 0 else "1루 쪽 파울"
	if angle_deg < -27.0: return "좌측"
	if angle_deg < -9.0: return "좌중간"
	if angle_deg <= 9.0: return "중앙"
	if angle_deg <= 27.0: return "우중간"
	return "우측"


static func timing_text(diff_ms: float) -> String:
	var ms := roundi(diff_ms)
	if ms > 0:
		return "%dms 늦음" % ms
	if ms < 0:
		return "%dms 빠름" % -ms
	return "딱 맞음"
