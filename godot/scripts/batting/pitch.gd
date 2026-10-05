class_name Pitch
extends RefCounted
## 공 하나.
##
## 좌표는 스트라이크존 기준: 가운데가 (0, 0), 존 가장자리가 ±1, y 는 아래가 +.
## 공은 존 가운데를 향해 곧게 오다가 break_start 이후 휘어서 마지막에 target 에 도착한다.
## 그래서 막판에 휘는 변화구는 일찍 판단하기 어렵다.

## 구종. 프로토타입은 두 가지만
enum Type { FASTBALL, BREAKING }

var type: Type
var label: String
var flight_ms: float
var target := Vector2.ZERO
var break_vec := Vector2.ZERO
var break_start: float
## 비행 진행률이 이 값을 넘으면 구종이 화면에 드러난다 (선구안)
var reveal_fraction: float


func is_strike() -> bool:
	return absf(target.x) <= 1.0 and absf(target.y) <= 1.0


## 진행률 progress(0=투수 손, 1=홈플레이트)에서 공의 존 좌표
func zone_position_at(progress: float) -> Vector2:
	var p := clampf(progress, 0.0, 1.0)
	var bend := 0.0
	if p > break_start:
		var k := (p - break_start) / (1.0 - break_start)
		bend = k * k
	return (target - break_vec) * p + break_vec * bend


func is_revealed_at(progress: float) -> bool:
	return progress >= reveal_fraction


## 투구 생성. 구종·속도·코스를 시드 난수로 정한다
static func generate(config: BattingConfig, skills: BatterSkills, rng: RandomNumberGenerator) -> Pitch:
	var p := Pitch.new()
	p.type = _pick_type(config, rng)
	var spec: BattingConfig.PitchSpec = config.pitches[p.type]
	p.label = spec.label
	p.target = _pick_target(config, rng)
	var side := 1.0 if rng.randf() < 0.5 else -1.0
	p.flight_ms = spec.flight_ms + spec.flight_jitter_ms * rng.randf_range(-1.0, 1.0)
	p.break_vec = Vector2(spec.break_x * side, spec.break_y)
	p.break_start = config.break_start_fraction
	p.reveal_fraction = config.reveal_fraction(skills.eye)
	return p


static func _pick_type(config: BattingConfig, rng: RandomNumberGenerator) -> Type:
	var total := 0.0
	for spec: BattingConfig.PitchSpec in config.pitches.values():
		total += spec.weight
	var roll := rng.randf() * total
	for type: Type in config.pitches.keys():
		roll -= (config.pitches[type] as BattingConfig.PitchSpec).weight
		if roll < 0.0:
			return type
	return config.pitches.keys().back()


## 스트라이크면 존 안쪽, 볼이면 한 축만 존 밖으로 벗어난 코스
static func _pick_target(config: BattingConfig, rng: RandomNumberGenerator) -> Vector2:
	if rng.randf() < config.strike_chance:
		return Vector2(rng.randf_range(-0.9, 0.9), rng.randf_range(-0.9, 0.9))
	var off := rng.randf_range(config.ball_offset_min, config.ball_offset_max) * (1.0 if rng.randf() < 0.5 else -1.0)
	var inside := rng.randf_range(-1.0, 1.0)
	return Vector2(off, inside) if rng.randf() < 0.5 else Vector2(inside, off)
