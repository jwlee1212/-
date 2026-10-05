class_name BattingConfig
extends RefCounted
## 타격 프로토타입 수치 (config/balance.json 의 battingPrototype).
##
## 손맛 튜닝은 전부 이 수치로 한다 — 코드를 고치지 않고 json 만 바꾸면 된다.
## 능력치에 따라 달라지는 값은 0일 때(At0)와 100일 때(At100) 두 값만 적고 사이는 선형 보간한다.

const SECTION := "battingPrototype"


## 구종 하나의 수치
class PitchSpec:
	var label: String
	## 구종 선택 가중치
	var weight: float
	## 투수 손을 떠나 홈플레이트까지 걸리는 시간 (ms)
	var flight_ms: float
	var flight_jitter_ms: float
	## 막판에 휘는 양 (존 반폭 단위). 가로는 좌우 무작위, 세로는 아래로
	var break_x: float
	var break_y: float


var pitches: Dictionary = {}  # Pitch.Type -> PitchSpec
## 공이 날아가는 동안 이 비율을 지나야 휘기 시작한다 (0~1)
var break_start_fraction: float
var strike_chance: float
var ball_offset_min: float
var ball_offset_max: float
var reveal_fraction_at0: float
var reveal_fraction_at100: float
## 탭 시각에서 빼 주는 입력 지연 보정. 폰에서 늘 늦게 맞는다면 이 값을 올린다
var input_latency_ms: float
var solid_window := Vector2.ZERO  # x = At0, y = At100
var weak_window := Vector2.ZERO
var foul_window := Vector2.ZERO
var out_of_zone_window_factor: float
## 공이 홈플레이트를 지나고 이만큼 더 기다려도 탭이 없으면 "안 침"으로 본다
var take_grace_ms: float
var solid_max_angle_deg: float
var weak_max_angle_deg: float
var foul_angle_deg: float
var solid_base_m: float
var solid_power_bonus_m: float
var solid_quality_share: float
var solid_jitter_m: float
var weak_min_m: float
var weak_max_m: float
var fence_m: float
var solid_fly_chance := Vector2.ZERO
var line_hit_chance: float
var short_fly_hit_chance: float
var double_min_m: float
var deep_fly_out_chance: float
var triple_min_angle_deg: float
var triple_chance: float
var weak_ground_chance: float
var weak_hit_chance: float


## 구종이 보이기 시작하는 지점 (비행 진행률). 선구안이 높을수록 일찍 보인다
func reveal_fraction(eye: int) -> float:
	return _lerp_rating(Vector2(reveal_fraction_at0, reveal_fraction_at100), eye)


func solid_window_ms(contact: int) -> float:
	return _lerp_rating(solid_window, contact)


func weak_window_ms(contact: int) -> float:
	return _lerp_rating(weak_window, contact)


func foul_window_ms(contact: int) -> float:
	return _lerp_rating(foul_window, contact)


func solid_fly_chance_for(power: int) -> float:
	return _lerp_rating(solid_fly_chance, power)


static func from_balance(balance: Dictionary) -> BattingConfig:
	var s: Dictionary = balance[SECTION]
	var c := BattingConfig.new()
	c.pitches[Pitch.Type.FASTBALL] = _spec(_dict(s, "pitches.fastball"))
	c.pitches[Pitch.Type.BREAKING] = _spec(_dict(s, "pitches.breaking"))
	c.break_start_fraction = num(s, "breakStartFraction")
	c.strike_chance = num(s, "zone.strikeChance")
	c.ball_offset_min = num(s, "zone.ballOffsetMin")
	c.ball_offset_max = num(s, "zone.ballOffsetMax")
	c.reveal_fraction_at0 = num(s, "eye.revealFractionAt0")
	c.reveal_fraction_at100 = num(s, "eye.revealFractionAt100")
	c.input_latency_ms = num(s, "timing.inputLatencyMs")
	c.solid_window = _pair(s, "timing.solidWindowMs")
	c.weak_window = _pair(s, "timing.weakWindowMs")
	c.foul_window = _pair(s, "timing.foulWindowMs")
	c.out_of_zone_window_factor = num(s, "timing.outOfZoneWindowFactor")
	c.take_grace_ms = num(s, "timing.takeGraceMs")
	c.solid_max_angle_deg = num(s, "spray.solidMaxAngleDeg")
	c.weak_max_angle_deg = num(s, "spray.weakMaxAngleDeg")
	c.foul_angle_deg = num(s, "spray.foulAngleDeg")
	c.solid_base_m = num(s, "distance.solidBaseM")
	c.solid_power_bonus_m = num(s, "distance.solidPowerBonusM")
	c.solid_quality_share = num(s, "distance.solidQualityShare")
	c.solid_jitter_m = num(s, "distance.solidJitterM")
	c.weak_min_m = num(s, "distance.weakMinM")
	c.weak_max_m = num(s, "distance.weakMaxM")
	c.fence_m = num(s, "distance.fenceM")
	c.solid_fly_chance = _pair(s, "outcomes.solidFlyChance")
	c.line_hit_chance = num(s, "outcomes.lineHitChance")
	c.short_fly_hit_chance = num(s, "outcomes.shortFlyHitChance")
	c.double_min_m = num(s, "outcomes.doubleMinM")
	c.deep_fly_out_chance = num(s, "outcomes.deepFlyOutChance")
	c.triple_min_angle_deg = num(s, "outcomes.tripleMinAngleDeg")
	c.triple_chance = num(s, "outcomes.tripleChance")
	c.weak_ground_chance = num(s, "outcomes.weakGroundChance")
	c.weak_hit_chance = num(s, "outcomes.weakHitChance")
	return c


## 점으로 이은 경로("timing.solidWindowMsAt0")의 숫자. 없으면 바로 멈춘다 — 오타를 조용히 넘기지 않으려고
static func num(s: Dictionary, path: String) -> float:
	var v: Variant = _at(s, path)
	assert(v is float or v is int, "balance.json: %s 가 숫자가 아니다" % path)
	return float(v)


static func _dict(s: Dictionary, path: String) -> Dictionary:
	var v: Variant = _at(s, path)
	assert(v is Dictionary, "balance.json: %s 가 객체가 아니다" % path)
	return v


static func _at(s: Dictionary, path: String) -> Variant:
	var cur: Variant = s
	for key in path.split("."):
		assert(cur is Dictionary and (cur as Dictionary).has(key), "balance.json: %s 가 없다" % path)
		cur = cur[key]
	return cur


static func _pair(s: Dictionary, path: String) -> Vector2:
	return Vector2(num(s, path + "At0"), num(s, path + "At100"))


static func _spec(d: Dictionary) -> PitchSpec:
	var p := PitchSpec.new()
	p.label = d["label"]
	p.weight = num(d, "weight")
	p.flight_ms = num(d, "flightMs")
	p.flight_jitter_ms = num(d, "flightJitterMs")
	p.break_x = num(d, "breakX")
	p.break_y = num(d, "breakY")
	return p


static func _lerp_rating(range_at0_at100: Vector2, rating: int) -> float:
	var t := clampi(rating, 0, 100) / 100.0
	return lerpf(range_at0_at100.x, range_at0_at100.y, t)
