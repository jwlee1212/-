class_name PitchCaller
extends RefCounted
## 투수 볼배합: 볼카운트와 투수 성향으로 다음 공(구종·노리는 곳)을 정하고, 제구에 따라 실제 도착점을 흔든다.
##
## 순서
## 1. 2스트라이크면 일정 확률로 결정구 (그 투수의 결정구를 유인구 자리로)
## 2. 아니면 상황(유리·대등·불리)별 구종 가중치 × 투수 성향 배율로 구종을 고른다
## 3. 상황별 확률로 스트라이크/볼을 정한다. 볼이면 대부분 존 바로 밖 유인구, 일부는 크게 빠지는 공
## 4. 제구: 노린 곳(포수 미트)에서 제구력만큼 흩어진다. 낮은 확률로 한가운데 실투

var _config: BattingConfig


func _init(config: BattingConfig) -> void:
	_config = config


func next(pitcher: BattingConfig.PitcherProfile, balls: int, strikes: int, skills: BatterSkills, rng: RandomNumberGenerator) -> Pitch:
	var situation: BattingConfig.Situation = _config.situations[BattingConfig.situation_key(balls, strikes)]
	var p := Pitch.new()

	if strikes >= 2 and rng.randf() < _config.two_strike_putaway_chance:
		# 결정구: 그 투수의 주무기를 유인구 자리로
		p.type = _forced_or(pitcher.putaway_pitch, rng)
		p.intended = _chase_spot(pitcher.putaway_spot, rng)
		p.is_putaway = true
		p.is_chase = true
	else:
		p.type = _forced_or(_pick_type(situation, pitcher, rng), rng)
		if rng.randf() < situation.strike_chance:
			var r := situation.strike_radius
			p.intended = Vector2(rng.randf_range(-r, r), rng.randf_range(-r, r))
		elif rng.randf() < situation.chase_share:
			var spots: PackedStringArray = _config.catalog.spec(p.type).chase_spots
			p.intended = _chase_spot(spots[rng.randi_range(0, spots.size() - 1)], rng)
			p.is_chase = true
		else:
			p.intended = _wild_spot(rng)

	# 제구: 제구력이 낮을수록 미트에서 멀리 흩어진다. 가끔 한가운데로 몰리는 실투
	if rng.randf() < pitcher.mistake_chance:
		var m := _config.mistake_radius
		p.target = Vector2(rng.randf_range(-m, m), rng.randf_range(-m, m))
		p.is_mistake = true
	else:
		var sigma := _config.scatter_at_control0 * (1.0 - pitcher.control)
		p.target = p.intended + Vector2(rng.randfn(0.0, sigma), rng.randfn(0.0, sigma))

	# 공 크기: 공이 조금이라도 존에 걸치면 스트라이크다. 위의 위치들은 "공 중심이 ±1 안이면 스트라이크"로 짠 값이라
	# 존+공 반지름(reach) 비율로 넓혀서, 스트라이크·볼 비율은 그대로 두고 화면의 공이 판정과 맞게 한다
	p.reach = 1.0 + _config.zone_ball_radius
	p.intended *= p.reach
	p.target *= p.reach

	# 비행 시간: 구속 비율로 정한 기본값 + 투수 성향 보정 + 공마다 흔들림. 이 값이 궤적의 도착 시각이자 판정 기준 시각
	var catalog := _config.catalog
	var spec := catalog.spec(p.type)
	p.label = spec.name_ko
	p.hand = pitcher.hand
	p.flight_ms = catalog.flight_ms(p.type) + pitcher.speed_ms + catalog.flight_jitter_ms * rng.randf_range(-1.0, 1.0)
	p.speed_kmh = catalog.speed_kmh(p.type, p.flight_ms)
	p.trajectory = PitchTrajectory.make(spec, p.target, p.flight_ms, p.hand, catalog.location_range)
	p.reveal_fraction = _config.reveal_fraction(skills.eye)
	return p


## 개발 빌드의 구종 버튼으로 고른 구종이 있으면 그것, 아니면 볼배합이 고른 구종
func _forced_or(type: String, rng: RandomNumberGenerator) -> String:
	var forced := _config.forced_pitch_type
	if forced == "":
		return type
	if forced == "random":
		return PitchCatalog.IDS[rng.randi_range(0, PitchCatalog.IDS.size() - 1)]
	return forced


## 상황별 가중치 × 투수 배율로 구종을 고른다
func _pick_type(situation: BattingConfig.Situation, pitcher: BattingConfig.PitcherProfile, rng: RandomNumberGenerator) -> String:
	var weights := {}
	var total := 0.0
	for id: String in _config.pitches:
		var w: float = situation.mix[id] * pitcher.mix[id]
		weights[id] = w
		total += w
	var roll := rng.randf() * total
	for id: String in weights:
		roll -= weights[id]
		if roll < 0.0:
			return id
	return weights.keys().back()


## 존 바로 밖 유인구 자리. spot 쪽 축만 존을 벗어나고 다른 축은 존 안
func _chase_spot(spot: String, rng: RandomNumberGenerator) -> Vector2:
	var off := rng.randf_range(_config.chase_offset.x, _config.chase_offset.y)
	var along := rng.randf_range(-0.8, 0.8)
	match spot:
		"high":
			return Vector2(along, -off)
		"low":
			return Vector2(along, off)
		"away":
			return Vector2(off, along)
		"inside":
			return Vector2(-off, along)
	assert(false, "알 수 없는 유인구 방향: %s" % spot)
	return Vector2(off, along)


## 크게 빠지는 공 (아무 축이나)
func _wild_spot(rng: RandomNumberGenerator) -> Vector2:
	var off := rng.randf_range(_config.wild_offset.x, _config.wild_offset.y) * (1.0 if rng.randf() < 0.5 else -1.0)
	var along := rng.randf_range(-1.0, 1.0)
	return Vector2(off, along) if rng.randf() < 0.5 else Vector2(along, off)
