class_name BattingSession
extends RefCounted
## 타석 화면의 상태. 판정은 전부 AtBat 이 하고, 여기서는 시간 흐름과 연출 단계만 관리한다.
##
## 시간은 Time.get_ticks_usec() 하나로 잰다 — 공을 놓은 순간과 탭한 순간을 같은 시계로 재야
## 타이밍 판정이 프레임 속도와 무관해진다. 시드는 타석마다 1, 2, 3… 으로 올라간다.

## 효과음을 내야 할 때. pitch_scale 은 재생 속도(1 = 원음, 높으면 높고 맑은 소리)
signal sound_requested(sfx: SoundSynth.Sfx, volume: float, pitch_scale: float)
## 한 타석 모드에서 타석이 끝나고 유저가 탭했을 때 (경기 화면이 결과를 경기에 반영한다)
signal at_bat_finished(result: AtBat.Result)

enum Phase {
	WAITING,  ## 다음 공을 기다리는 중
	WINDUP,   ## 투수 와인드업. 이 동안은 탭해도 스윙하지 않는다
	FLIGHT,   ## 공이 날아오는 중
	HIT,      ## 맞은 공이 날아가는 중
	OVER,     ## 타석 끝. 탭하면 다음 타석
}

enum Tone { GOOD, BAD, NEUTRAL, BIG }



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
		if outcome != SwingJudge.Outcome.WALK and outcome != SwingJudge.Outcome.SAC_BUNT:
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
## 스윙 종류 ("normal" | "contact" | "power" | "bunt"). 타석이 바뀌어도 유지된다
var swing_type := "normal"
## 한 타석 모드: 타석이 끝나면 새 타석을 시작하지 않고 at_bat_finished 를 보낸다
var single := false
## 경기 상황 (주자·아웃). 내 타석을 만들 때마다 넘긴다. 연습이면 비어 있다 (주자 없음)
var situation := {}
var skills := BatterSkills.new(50, 50, 50)
var pitcher: BattingConfig.PitcherProfile
## 노려치기로 찍어 둔 존 칸 (0~8, 없으면 -1). 타석이 바뀌어도 유지된다
var aim_cell := -1
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
	pitcher = config.pitchers.values()[0]
	at_bat = _new_at_bat()
	_enter(Phase.WAITING)


static func now_ms() -> float:
	return Time.get_ticks_usec() / 1000.0


func since(t_ms: float) -> float:
	return now_ms() - t_ms


func update_skills(new_skills: BatterSkills) -> void:
	skills = new_skills
	at_bat.skills = new_skills


## 경기 속 내 타석 하나를 시작한다 (능력치·상대 투수·시드는 커리어가 정한다)
func start_single(p_skills: BatterSkills, p_pitcher: BattingConfig.PitcherProfile, seed_value: int, p_situation: Dictionary = {}) -> void:
	single = true
	skills = p_skills
	pitcher = p_pitcher
	situation = p_situation
	at_bat = AtBat.new(config, skills, pitcher, seed_value)
	at_bat.aim_cell = aim_cell
	at_bat.swing_type = swing_type
	at_bat.situation = situation
	pitch = null
	callout = null
	impact = null
	last_timing_ms = NAN
	_enter(Phase.WAITING)


## 수치를 새로 읽었을 때 (디버그 패널의 "다시 읽기"). 기록은 유지하고 지금 타석은 새로 시작한다
func replace_config(p_config: BattingConfig, p_presentation: Presentation) -> void:
	config = p_config
	presentation = p_presentation
	pitcher = config.pitchers.get(pitcher.id, config.pitchers.values()[0])
	at_bat = _new_at_bat()
	pitch = null
	_enter(Phase.WAITING)


## 선택 시간 진행 (0~1). 선택 시간이 아니면 -1
func select_progress() -> float:
	if phase != Phase.WAITING:
		return -1.0
	var e := since(phase_since_ms) - presentation.pitch_interval_ms
	return clampf(e / presentation.select_ms, 0.0, 1.0) if e >= 0.0 else -1.0


## 다 골랐으면 바로 던지게 한다 (스윙 버튼을 선택 시간에 누르면)
func ready_now() -> void:
	if phase == Phase.WAITING:
		pitch = at_bat.next_pitch()
		_enter(Phase.WINDUP)


func set_swing_type(id: String) -> void:
	swing_type = id
	at_bat.swing_type = id


## 정타 뒤 플레이가 끝나는 시각 (HIT 단계 시작부터 ms): 히트스톱 + 튀어 나감 + 중계 화면 재생
func hit_play_end_ms() -> float:
	var c := last_result.contact if last_result != null else null
	if c == null or c.ball == null:
		return presentation.hit_stop_ms + presentation.hit_flight_ms
	# 주자 플레이가 있으면 모든 주자가 멈출 때까지, 없으면 공·송구가 끝날 때까지
	var end := last_result.play.duration if last_result.play != null else c.ball.play_end()
	return presentation.hit_stop_ms + presentation.launch_ms + end * 1000.0 / presentation.broadcast_speed


## 중계 화면에서 지금 보여 줄 공의 시각 (초, 실제 시간). 아직 중계 전이면 음수
func broadcast_time() -> float:
	var e := since(phase_since_ms) - presentation.hit_stop_ms - presentation.launch_ms
	return e * presentation.broadcast_speed / 1000.0


## 다음 투수로 바꾼다 (다음 공부터)
func cycle_pitcher() -> void:
	var ids: Array = config.pitchers.keys()
	var next_id: String = ids[(ids.find(pitcher.id) + 1) % ids.size()]
	pitcher = config.pitchers[next_id]
	at_bat.pitcher = pitcher


## 매 프레임 호출. 시간이 지나 단계가 넘어가야 하면 넘긴다
func tick() -> void:
	var e := since(phase_since_ms)
	match phase:
		Phase.WAITING:
			# 앞 공의 여운(pitchInterval) 뒤, 투수가 세트 자세로 기다리는 선택 시간(selectMs)이 지나면 던진다
			if e >= presentation.pitch_interval_ms + presentation.select_ms:
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
			if e >= hit_play_end_ms() + presentation.after_play_hold_ms:
				# 타구가 떨어지는 순간 안타면 함성
				var o := last_result.outcome
				if SwingJudge.is_hit(o) and o != SwingJudge.Outcome.HOME_RUN:
					_sound(SoundSynth.Sfx.CHEER_SMALL, 1.0)
				_finish_at_bat(last_result)


## 화면 탭. zone_pos 는 탭한 곳의 존 좌표 (존 가운데 0, 가장자리 ±1).
## 공이 날아오는 중이면 스윙, 투구 전에 존을 탭하면 노려치기 칸 지정(같은 칸을 다시 탭하면 해제), 타석이 끝났으면 다음 타석
func tap(zone_pos: Vector2 = Vector2.INF) -> void:
	match phase:
		Phase.WAITING, Phase.WINDUP:
			if not config.aim_enabled:
				return
			var cell := Pitch.cell_of(zone_pos)
			if cell >= 0:
				aim_cell = -1 if cell == aim_cell else cell
				at_bat.aim_cell = aim_cell
				_sound(SoundSynth.Sfx.CRACK_FOUL, 0.35)
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
			if single:
				at_bat_finished.emit(at_bat.result)
				return
			at_bat = _new_at_bat()
			_enter(Phase.WAITING)


func _play_swing_sounds(o: AtBat.PitchOutcome) -> void:
	_sound(SoundSynth.Sfx.SWING, 0.7)
	var c := o.contact
	match c.quality:
		SwingJudge.Quality.SOLID:
			# 타이밍이 완벽할수록 높고 맑은 "딱!"
			var q := clampf(1.0 - absf(o.timing_diff_ms) / config.solid_window_ms(skills.contact), 0.0, 1.0)
			_sound(SoundSynth.Sfx.CRACK_SOLID, 0.85 + 0.15 * q, 1.0 + presentation.crack_pitch_spread * (q - 0.5) * 2.0)
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


func _sound(sfx: SoundSynth.Sfx, volume: float, pitch_scale: float = 1.0) -> void:
	sound_requested.emit(sfx, volume * presentation.sfx_volume, pitch_scale)


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
			_set_callout("헛스윙!", aim_text(o.aim) + timing_text(o.timing_diff_ms), Tone.BAD)
		AtBat.Call.FOUL:
			_set_callout("파울", aim_text(o.aim) + timing_text(o.timing_diff_ms), Tone.NEUTRAL)


func _show_result_callout(r: AtBat.Result) -> void:
	var tone := Tone.BAD
	if r.outcome == SwingJudge.Outcome.HOME_RUN:
		tone = Tone.BIG
	elif SwingJudge.is_hit(r.outcome) or r.outcome == SwingJudge.Outcome.WALK:
		tone = Tone.GOOD
	var detail := ""
	var delay := 0.0
	if r.contact != null:
		detail = "%s%s · %dm · %s" % [aim_text(swing_outcome.aim), direction_text(r.contact.angle_deg), roundi(r.contact.distance_m), timing_text(last_timing_ms)]
		var ball := r.contact.ball
		if ball != null and ball.is_first_base_play():
			# 1루 판정은 간발의 차를 함께 보여 준다
			detail = "%s%s · 1루 %.2f초 차" % [aim_text(swing_outcome.aim), direction_text(r.contact.angle_deg), absf(ball.play_margin)]
		# 인플레이 타구는 중계 화면에서 플레이가 끝난 뒤에 결과를 띄운다
		delay = hit_play_end_ms() + presentation.after_play_hold_ms * 0.3
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
	var ab := AtBat.new(config, skills, pitcher, seed_value)
	ab.aim_cell = aim_cell
	ab.swing_type = swing_type
	ab.situation = situation
	return ab


static func aim_text(aim: AtBat.Aim) -> String:
	match aim:
		AtBat.Aim.HIT: return "노린 공! · "
		AtBat.Aim.MISS: return "노린 코스 아님 · "
	return ""


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
		SwingJudge.Outcome.SAC_BUNT: return "희생번트"
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
