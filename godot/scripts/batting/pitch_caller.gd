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
		p.type = pitcher.putaway_pitch
		p.intended = _chase_spot(pitcher.putaway_spot, rng)
		p.is_putaway = true
		p.is_chase = true
	else:
		p.type = _pick_type(situation, pitcher, rng)
		if rng.randf() < situation.strike_chance:
			var r := situation.strike_radius
			p.intended = Vector2(rng.randf_range(-r, r), rng.randf_range(-r, r))
		elif rng.randf() < situation.chase_share:
			var spots: PackedStringArray = (_config.pitches[p.type] as BattingConfig.PitchSpec).chase_spots
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

	var spec: BattingConfig.PitchSpec = _config.pitches[p.type]
	p.label = spec.label
	p.flight_ms = spec.flight_ms + pitcher.speed_ms + spec.flight_jitter_ms * rng.randf_range(-1.0, 1.0)
	p.break_vec = spec.break_vec
	p.break_start = _config.break_start_fraction
	p.reveal_fraction = _config.reveal_fraction(skills.eye)
	return p


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
