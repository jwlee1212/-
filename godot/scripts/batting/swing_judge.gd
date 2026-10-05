class_name SwingJudge
extends RefCounted
## 탭 타이밍 → 판정, 판정 → 타구, 타구 → 타석 결과. 화면과 무관한 순수 로직.

## 스윙 판정 등급. 타이밍 차이가 작을수록 위 등급
enum Quality { SOLID, WEAK, FOUL, MISS }

## 타구 종류
enum BattedBall { NONE, GROUND, FLY, LINE }

## 타석 결과. 이름은 옛 Kotlin 엔진의 PaOutcome 과 같게 맞췄다 (나중에 리그 시뮬레이션과 연결할 때 그대로 쓰려고)
enum Outcome { SINGLE, DOUBLE, TRIPLE, HOME_RUN, WALK, STRIKEOUT, GROUND_OUT, FLY_OUT, LINE_OUT }

const HITS := [Outcome.SINGLE, Outcome.DOUBLE, Outcome.TRIPLE, Outcome.HOME_RUN]


## 맞은 공의 궤적 요약.
## angle_deg: 0 = 가운데, 음수 = 3루 쪽(일찍 친 당겨친 타구), 양수 = 1루 쪽(늦게 친 밀어친 타구)
class Contact:
	var quality: Quality
	var angle_deg: float
	var distance_m: float
	var batted_ball: BattedBall

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


## 타이밍 차이로 등급을 정한다. 존 밖 공은 판정 폭이 좁아진다
func quality(pitch: Pitch, skills: BatterSkills, timing_diff_ms: float) -> Quality:
	var factor := 1.0 if pitch.is_strike() else _config.out_of_zone_window_factor
	var d := absf(timing_diff_ms)
	if d <= _config.solid_window_ms(skills.contact) * factor:
		return Quality.SOLID
	if d <= _config.weak_window_ms(skills.contact) * factor:
		return Quality.WEAK
	if d <= _config.foul_window_ms(skills.contact) * factor:
		return Quality.FOUL
	return Quality.MISS


func contact(pitch: Pitch, skills: BatterSkills, timing_diff_ms: float, rng: RandomNumberGenerator) -> Contact:
	var factor := 1.0 if pitch.is_strike() else _config.out_of_zone_window_factor
	var solid := _config.solid_window_ms(skills.contact) * factor
	var weak := _config.weak_window_ms(skills.contact) * factor
	var d := absf(timing_diff_ms)
	var side := signf(timing_diff_ms)
	match quality(pitch, skills, timing_diff_ms):
		Quality.SOLID:
			var q := 1.0 - d / solid
			var power := skills.power / 100.0
			var distance := _config.solid_base_m \
				+ _config.solid_power_bonus_m * power * ((1.0 - _config.solid_quality_share) + _config.solid_quality_share * q) \
				+ _config.solid_jitter_m * rng.randf_range(-1.0, 1.0)
			var fly := rng.randf() < _config.solid_fly_chance_for(skills.power)
			return Contact.new(Quality.SOLID, timing_diff_ms / solid * _config.solid_max_angle_deg, distance,
				BattedBall.FLY if fly else BattedBall.LINE)
		Quality.WEAK:
			var t := clampf((d - solid) / (weak - solid), 0.0, 1.0)
			var ground := rng.randf() < _config.weak_ground_chance
			var angle := side * lerpf(_config.solid_max_angle_deg, _config.weak_max_angle_deg, t)
			var distance := lerpf(_config.weak_min_m, _config.weak_max_m, rng.randf())
			return Contact.new(Quality.WEAK, angle, distance, BattedBall.GROUND if ground else BattedBall.FLY)
		Quality.FOUL:
			return Contact.new(Quality.FOUL, side * _config.foul_angle_deg, 0.0, BattedBall.NONE)
	return Contact.new(Quality.MISS, 0.0, 0.0, BattedBall.NONE)


## 인플레이 타구의 결과. 수비수 위치는 아직 없어서 거리·방향·확률로 단순하게 정한다
func outcome_of(c: Contact, rng: RandomNumberGenerator) -> Outcome:
	if c.quality == Quality.SOLID:
		return _solid_outcome(c, rng)
	assert(c.quality == Quality.WEAK, "파울·헛스윙은 인플레이가 아니다")
	if rng.randf() < _config.weak_hit_chance:
		return Outcome.SINGLE
	return Outcome.GROUND_OUT if c.batted_ball == BattedBall.GROUND else Outcome.FLY_OUT


func _solid_outcome(c: Contact, rng: RandomNumberGenerator) -> Outcome:
	if c.distance_m >= _config.fence_m:
		return Outcome.HOME_RUN
	var fly := c.batted_ball == BattedBall.FLY
	if c.distance_m >= _config.double_min_m:
		if fly and rng.randf() < _config.deep_fly_out_chance:
			return Outcome.FLY_OUT
		var corner := absf(c.angle_deg) >= _config.triple_min_angle_deg
		return Outcome.TRIPLE if corner and rng.randf() < _config.triple_chance else Outcome.DOUBLE
	if fly:
		return Outcome.SINGLE if rng.randf() < _config.short_fly_hit_chance else Outcome.FLY_OUT
	return Outcome.SINGLE if rng.randf() < _config.line_hit_chance else Outcome.LINE_OUT
