class_name SwingJudge
extends RefCounted
## 탭 타이밍 → 판정 → 타구(타구 속도·발사각·방향) → 타구 물리·수비(BattedBall) → 타석 결과.
## 화면과 무관한 순수 로직.

## 스윙 판정 등급. 타이밍 차이가 작을수록 위 등급
enum Quality { SOLID, WEAK, FOUL, MISS }

## 타구 종류
enum BattedBall { NONE, GROUND, FLY, LINE }

## 타석 결과. 이름은 옛 Kotlin 엔진의 PaOutcome 과 같게 맞췄다 (나중에 리그 시뮬레이션과 연결할 때 그대로 쓰려고)
enum Outcome { SINGLE, DOUBLE, TRIPLE, HOME_RUN, WALK, STRIKEOUT, GROUND_OUT, FLY_OUT, LINE_OUT, SAC_BUNT }

const HITS := [Outcome.SINGLE, Outcome.DOUBLE, Outcome.TRIPLE, Outcome.HOME_RUN]


## 맞은 공.
## angle_deg: 방향. 0 = 가운데, 음수 = 3루 쪽(일찍 친 당겨친 타구), 양수 = 1루 쪽(늦게 친 밀어친 타구)
class Contact:
	var quality: Quality
	var angle_deg: float
	var distance_m: float
	var batted_ball: BattedBall
	var ev_kmh := 0.0
	var launch_deg := 0.0
	var bunt := false
	## 타구 물리·수비 결과 (outcome_of 뒤에 채워진다)
	var ball: BattedBallSim.Result = null

	func _init(q: Quality, angle: float, distance: float, batted: BattedBall) -> void:
		quality = q
		angle_deg = angle
		distance_m = distance
		batted_ball = batted


var _config: BattingConfig


func _init(config: BattingConfig) -> void:
	_config = config


static func is_hit(outcome: Outcome) -> bool:
	return outcome in HITS


## 판정 폭 배율: 존 밖 공은 좁아지고, 노려치기·스윙 종류 배율을 곱한다
func window_factor(pitch: Pitch, aim_factor: float, swing_type: String = "normal") -> float:
	var zone := 1.0 if pitch.is_strike() else _config.out_of_zone_window_factor
	return zone * aim_factor * _config.swing_type(swing_type).window_mul


## 타이밍 차이로 등급을 정한다
func quality(pitch: Pitch, skills: BatterSkills, timing_diff_ms: float, aim_factor: float = 1.0, swing_type: String = "normal") -> Quality:
	var factor := window_factor(pitch, aim_factor, swing_type)
	var d := absf(timing_diff_ms)
	if d <= _config.solid_window_ms(skills.contact) * factor:
		return Quality.SOLID
	if d <= _config.weak_window_ms(skills.contact) * factor:
		return Quality.WEAK
	if d <= _config.foul_window_ms(skills.contact) * factor:
		return Quality.FOUL
	return Quality.MISS


## 타구 만들기. aim_factor: 노려치기 판정 폭 배율, ev_bonus_kmh: 노린 공을 정타로 쳤을 때 더해지는 타구 속도
func contact(pitch: Pitch, skills: BatterSkills, timing_diff_ms: float, rng: RandomNumberGenerator,
		aim_factor: float = 1.0, ev_bonus_kmh: float = 0.0, swing_type: String = "normal") -> Contact:
	var factor := window_factor(pitch, aim_factor, swing_type)
	var solid := _config.solid_window_ms(skills.contact) * factor
	var weak := _config.weak_window_ms(skills.contact) * factor
	var d := absf(timing_diff_ms)
	var side := signf(timing_diff_ms)
	var st := _config.swing_type(swing_type)
	var bb := _config.ball
	var q := quality(pitch, skills, timing_diff_ms, aim_factor, swing_type)
	if q == Quality.FOUL:
		return Contact.new(Quality.FOUL, side * _config.foul_angle_deg, 0.0, BattedBall.NONE)
	if q == Quality.MISS:
		return Contact.new(Quality.MISS, 0.0, 0.0, BattedBall.NONE)

	var c: Contact
	if swing_type == "bunt":
		# 번트: 짧게 굴린다. 빗맞으면 뜬다
		var popup := q == Quality.WEAK and rng.randf() < 0.5
		var spray := clampf(timing_diff_ms / weak, -1.0, 1.0) * bb.bunt_spray_max
		c = Contact.new(q, spray, 0.0, BattedBall.GROUND)
		c.ev_kmh = rng.randf_range(bb.bunt_ev_min, bb.bunt_ev_max)
		c.launch_deg = rng.randf_range(bb.bunt_popup_min, bb.bunt_popup_max) if popup else rng.randf_range(bb.bunt_launch_min, bb.bunt_launch_max)
		c.bunt = true
		return c

	var height_shift := pitch.target.y * bb.launch_per_pitch_height  # 낮은 공(y+)일수록 낮게 뜬다
	if q == Quality.SOLID:
		var accuracy := 1.0 - d / solid
		var ev := lerpf(bb.ev_at0, bb.ev_at100, skills.power / 100.0) * ((1.0 - bb.ev_quality_share) + bb.ev_quality_share * accuracy)
		ev = (ev + rng.randfn(0.0, bb.ev_jitter)) * st.ev_mul + ev_bonus_kmh
		c = Contact.new(Quality.SOLID, timing_diff_ms / solid * _config.solid_max_angle_deg, 0.0, BattedBall.NONE)
		c.ev_kmh = ev
		c.launch_deg = rng.randfn(bb.launch_mean + st.launch_shift, bb.launch_sd) + height_shift
	else:
		var t := clampf((d - solid) / (weak - solid), 0.0, 1.0)
		var angle := side * lerpf(_config.solid_max_angle_deg, _config.weak_max_angle_deg, t)
		c = Contact.new(Quality.WEAK, angle, 0.0, BattedBall.NONE)
		c.ev_kmh = rng.randf_range(bb.weak_ev_min, bb.weak_ev_max) * st.ev_mul
		var topped := rng.randf() < bb.weak_top_chance
		c.launch_deg = rng.randf_range(bb.weak_top_min, bb.weak_top_max) if topped else rng.randf_range(bb.weak_under_min, bb.weak_under_max)
	return c


## 인플레이 타구의 결과: 타구 물리 + 수비수 도달로 정한다. 결과 계산(c.ball)은 화면이 그대로 그린다
func outcome_of(c: Contact, skills: BatterSkills) -> Outcome:
	assert(c.quality == Quality.SOLID or c.quality == Quality.WEAK, "파울·헛스윙은 인플레이가 아니다")
	var r := BattedBallSim.simulate(_config.ball_physics, c.ev_kmh, c.launch_deg, c.angle_deg, skills.speed, c.bunt)
	c.ball = r
	c.batted_ball = r.batted_ball
	c.distance_m = r.distance_m
	return r.outcome
