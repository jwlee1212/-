class_name BattingConfig
extends RefCounted
## 타격 수치 (config/balance.json 의 batting).
##
## 손맛 튜닝은 전부 이 수치로 한다 — 코드를 고치지 않고 json 만 바꾸면 된다.
## 능력치에 따라 달라지는 값은 0일 때(At0)와 100일 때(At100) 두 값만 적고 사이는 선형 보간한다.

const SECTION := "batting"


## 구종 하나의 수치
class PitchSpec:
	var id: String
	var label: String
	## 투수 손을 떠나 홈플레이트까지 걸리는 시간 (ms)
	var flight_ms: float
	var flight_jitter_ms: float
	## 막판에 휘는 양 (존 반폭 단위, +x 바깥쪽, +y 아래)
	var break_vec: Vector2
	## 유인구로 던질 때 노리는 방향 ("high" | "low" | "away" | "inside")
	var chase_spots: PackedStringArray


## 투수 성향
class PitcherProfile:
	var id: String
	var name: String
	## 모든 구종 비행 시간에 더하는 값 (음수 = 빠름)
	var speed_ms: float
	## 볼배합 가중치에 곱하는 배율 (구종 id -> 배율)
	var mix: Dictionary
	var putaway_pitch: String
	var putaway_spot: String
	## 0~1. 높을수록 포수 미트에 정확히 던진다
	var control: float
	## 한가운데 실투 확률
	var mistake_chance: float


## 볼카운트 상황별 볼배합
class Situation:
	var strike_chance: float
	## 스트라이크로 던질 때 노리는 범위 (작을수록 한가운데)
	var strike_radius: float
	## 볼 중 존 바로 밖 유인구의 비율
	var chase_share: float
	## 구종 id -> 가중치
	var mix: Dictionary


## 스윙 종류 (더쇼 방식)
class SwingType:
	var id: String
	var label: String
	## 판정 폭 배율
	var window_mul: float
	## 타구 속도 배율
	var ev_mul: float
	## 발사각 평균 이동 (도)
	var launch_shift: float


## 타구 만들기 수치 (balance.json batting.battedBall)
class BallParams:
	var ev_at0: float
	var ev_at100: float
	var ev_quality_share: float
	var ev_jitter: float
	var weak_ev_min: float
	var weak_ev_max: float
	var launch_mean: float
	var launch_sd: float
	var launch_per_pitch_height: float
	var weak_top_chance: float
	var weak_top_min: float
	var weak_top_max: float
	var weak_under_min: float
	var weak_under_max: float
	var bunt_ev_min: float
	var bunt_ev_max: float
	var bunt_launch_min: float
	var bunt_launch_max: float
	var bunt_popup_min: float
	var bunt_popup_max: float
	var bunt_spray_max: float


var pitches: Dictionary = {}  # 구종 id -> PitchSpec (json 순서 유지)
var speed_ref_kmh: float
var speed_kmh_per_ratio: float
## 공이 날아가는 동안 이 비율을 지나야 휘기 시작한다 (0~1)
var break_start_fraction: float
var pitchers: Dictionary = {}  # 투수 id -> PitcherProfile
var situations: Dictionary = {}  # "ahead" | "even" | "behind" -> Situation
var two_strike_putaway_chance: float
var chase_offset := Vector2.ZERO  # x = 최소, y = 최대
var wild_offset := Vector2.ZERO
var scatter_at_control0: float
var mistake_radius: float

var aim_enabled: bool
var aim_hit_window_factor: float
var aim_miss_window_factor: float
var aim_hit_ev_bonus: float
var catcher_hint: bool

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
var swing_types: Dictionary = {}  # id -> SwingType (json 순서 유지)
var ball := BallParams.new()
var ball_physics: BattedBallSim.Config


## 구종이 보이기 시작하는 지점 (비행 진행률). 선구안이 높을수록 일찍 보인다
func reveal_fraction(eye: int) -> float:
	return _lerp_rating(Vector2(reveal_fraction_at0, reveal_fraction_at100), eye)


func solid_window_ms(contact: int) -> float:
	return _lerp_rating(solid_window, contact)


func weak_window_ms(contact: int) -> float:
	return _lerp_rating(weak_window, contact)


func foul_window_ms(contact: int) -> float:
	return _lerp_rating(foul_window, contact)




## 비행 시간 → 화면에 보여 줄 구속 (km/h). 게임 속 속도 차는 실제보다 크게 벌려 두었고 전체 속도도 손맛에 맞춰 바뀌므로,
## 직구(표준) 비행 시간에 대한 비율로 현실적인 숫자를 만든다
func speed_kmh(flight_ms: float) -> int:
	var fastball: float = (pitches["fastball"] as PitchSpec).flight_ms if pitches.has("fastball") else flight_ms
	return roundi(speed_ref_kmh + (1.0 - flight_ms / fastball) * speed_kmh_per_ratio)


func swing_type(id: String) -> SwingType:
	return swing_types.get(id, swing_types["normal"])


## 볼카운트 → 상황 이름
static func situation_key(balls: int, strikes: int) -> String:
	if strikes > balls:
		return "ahead"
	if balls > strikes:
		return "behind"
	return "even"


static func from_balance(balance: Dictionary) -> BattingConfig:
	var s: Dictionary = balance[SECTION]
	var c := BattingConfig.new()
	for id: String in _dict(s, "pitches"):
		if id.begins_with("_"):
			continue
		c.pitches[id] = _pitch_spec(id, _dict(s, "pitches." + id))
	c.speed_ref_kmh = num(s, "speedDisplay.refKmh")
	c.speed_kmh_per_ratio = num(s, "speedDisplay.kmhPerRatio")
	c.break_start_fraction = num(s, "breakStartFraction")
	for id: String in _dict(s, "pitchers"):
		if id.begins_with("_"):
			continue
		c.pitchers[id] = _pitcher(id, _dict(s, "pitchers." + id), c.pitches)
	for key in ["ahead", "even", "behind"]:
		c.situations[key] = _situation(_dict(s, "pitchCalling." + key), c.pitches)
	c.two_strike_putaway_chance = num(s, "pitchCalling.twoStrikePutawayChance")
	c.chase_offset = Vector2(num(s, "pitchCalling.chaseOffsetMin"), num(s, "pitchCalling.chaseOffsetMax"))
	c.wild_offset = Vector2(num(s, "pitchCalling.wildOffsetMin"), num(s, "pitchCalling.wildOffsetMax"))
	c.scatter_at_control0 = num(s, "pitchCalling.scatterAtControl0")
	c.mistake_radius = num(s, "pitchCalling.mistakeRadius")
	c.aim_enabled = _bool(s, "aim.enabled")
	c.aim_hit_window_factor = num(s, "aim.hitWindowFactor")
	c.aim_miss_window_factor = num(s, "aim.missWindowFactor")
	c.aim_hit_ev_bonus = num(s, "aim.hitEvBonusKmh")
	c.catcher_hint = _bool(s, "aim.catcherHint")
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
	for id: String in _dict(s, "swingTypes"):
		if id.begins_with("_"):
			continue
		var sd := _dict(s, "swingTypes." + id)
		var stype := SwingType.new()
		stype.id = id
		stype.label = sd["label"]
		stype.window_mul = num(sd, "windowMul")
		stype.ev_mul = num(sd, "evMul")
		stype.launch_shift = num(sd, "launchShiftDeg")
		c.swing_types[id] = stype
	assert(c.swing_types.has("normal"), "balance.json: swingTypes.normal 이 없다")
	var b := c.ball
	b.ev_at0 = num(s, "battedBall.evKmhAt0")
	b.ev_at100 = num(s, "battedBall.evKmhAt100")
	b.ev_quality_share = num(s, "battedBall.evQualityShare")
	b.ev_jitter = num(s, "battedBall.evJitterKmh")
	b.weak_ev_min = num(s, "battedBall.weakEvKmhMin")
	b.weak_ev_max = num(s, "battedBall.weakEvKmhMax")
	b.launch_mean = num(s, "battedBall.launchMeanDeg")
	b.launch_sd = num(s, "battedBall.launchSdDeg")
	b.launch_per_pitch_height = num(s, "battedBall.launchPerPitchHeightDeg")
	b.weak_top_chance = num(s, "battedBall.weakTopChance")
	b.weak_top_min = num(s, "battedBall.weakTopLaunchMinDeg")
	b.weak_top_max = num(s, "battedBall.weakTopLaunchMaxDeg")
	b.weak_under_min = num(s, "battedBall.weakUnderLaunchMinDeg")
	b.weak_under_max = num(s, "battedBall.weakUnderLaunchMaxDeg")
	b.bunt_ev_min = num(s, "battedBall.bunt.evKmhMin")
	b.bunt_ev_max = num(s, "battedBall.bunt.evKmhMax")
	b.bunt_launch_min = num(s, "battedBall.bunt.launchMinDeg")
	b.bunt_launch_max = num(s, "battedBall.bunt.launchMaxDeg")
	b.bunt_popup_min = num(s, "battedBall.bunt.popupLaunchMinDeg")
	b.bunt_popup_max = num(s, "battedBall.bunt.popupLaunchMaxDeg")
	b.bunt_spray_max = num(s, "battedBall.bunt.sprayMaxDeg")
	c.ball_physics = BattedBallSim.Config.from(balance)
	return c


## 점으로 이은 경로("timing.solidWindowMsAt0")의 숫자. 없으면 바로 멈춘다 — 오타를 조용히 넘기지 않으려고
static func num(s: Dictionary, path: String) -> float:
	var v: Variant = _at(s, path)
	assert(v is float or v is int, "balance.json: %s 가 숫자가 아니다" % path)
	return float(v)


static func _bool(s: Dictionary, path: String) -> bool:
	var v: Variant = _at(s, path)
	assert(v is bool, "balance.json: %s 가 true/false 가 아니다" % path)
	return v


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


static func _pitch_spec(id: String, d: Dictionary) -> PitchSpec:
	var p := PitchSpec.new()
	p.id = id
	p.label = d["label"]
	p.flight_ms = num(d, "flightMs")
	p.flight_jitter_ms = num(d, "flightJitterMs")
	p.break_vec = Vector2(num(d, "breakX"), num(d, "breakY"))
	p.chase_spots = PackedStringArray(d["chaseSpots"])
	return p


static func _pitcher(id: String, d: Dictionary, pitches: Dictionary) -> PitcherProfile:
	var p := PitcherProfile.new()
	p.id = id
	p.name = d["name"]
	p.speed_ms = num(d, "speedMs")
	p.mix = _mix(_dict(d, "mix"), pitches, "pitchers.%s.mix" % id)
	p.putaway_pitch = _at(d, "putaway.pitch")
	assert(pitches.has(p.putaway_pitch), "balance.json: 투수 %s 의 결정구 %s 가 구종 목록에 없다" % [id, p.putaway_pitch])
	p.putaway_spot = _at(d, "putaway.spot")
	p.control = num(d, "control")
	p.mistake_chance = num(d, "mistakeChance")
	return p


static func _situation(d: Dictionary, pitches: Dictionary) -> Situation:
	var s := Situation.new()
	s.strike_chance = num(d, "strikeChance")
	s.strike_radius = num(d, "strikeRadius")
	s.chase_share = num(d, "chaseShare")
	s.mix = _mix(_dict(d, "mix"), pitches, "pitchCalling.mix")
	return s


## 구종 가중치. 모든 구종이 있어야 한다 (빠뜨리면 그 구종이 영영 안 나오는 실수를 막으려고)
static func _mix(d: Dictionary, pitches: Dictionary, where: String) -> Dictionary:
	var out := {}
	for id: String in pitches:
		assert(d.has(id), "balance.json: %s 에 구종 %s 가 없다" % [where, id])
		out[id] = num(d, id)
	return out


static func _lerp_rating(range_at0_at100: Vector2, rating: int) -> float:
	var t := clampi(rating, 0, 100) / 100.0
	return lerpf(range_at0_at100.x, range_at0_at100.y, t)
