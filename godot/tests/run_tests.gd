extends SceneTree
## 타격 프로토타입 테스트. 실제 config/balance.json 으로 돈다.
##   ~/Applications/Godot.app/Contents/MacOS/Godot --headless --path godot --script res://tests/run_tests.gd
## 하나라도 실패하면 종료 코드 1.

var _config: BattingConfig
var _judge: SwingJudge
var _average := BatterSkills.new(50, 50, 50)
var _failures := 0
var _passed := 0
var _current := ""


func _init() -> void:
	_config = BattingConfig.from_balance(BalanceLoader.load_balance())
	_judge = SwingJudge.new(_config)
	for test in [
		"test_skills_change_windows_and_reveal",
		"test_timing_decides_quality",
		"test_out_of_zone_is_worse",
		"test_early_pulls_late_pushes",
		"test_power_increases_distance",
		"test_breaking_ball_arrives_at_target",
		"test_taking_ends_in_walk_or_strikeout",
		"test_foul_with_two_strikes_is_not_strikeout",
		"test_same_seed_same_result",
		"test_result_record",
		"test_sound_synthesis",
	]:
		_current = test
		var before := _failures
		call(test)
		if _failures == before:
			_passed += 1
			print("  ok   ", test)
		else:
			print("  FAIL ", test)
	print("%d 통과, %d 실패" % [_passed, _failures])
	quit(1 if _failures > 0 else 0)


func check(cond: bool, message: String = "") -> void:
	if not cond:
		_failures += 1
		printerr("    [%s] %s" % [_current, message])


func strike_pitch() -> Pitch:
	var s := 0
	while true:
		var p := Pitch.generate(_config, _average, _rng(s))
		if p.is_strike():
			return p
		s += 1
	return null


func ball_pitch() -> Pitch:
	var s := 0
	while true:
		var p := Pitch.generate(_config, _average, _rng(s))
		if not p.is_strike():
			return p
		s += 1
	return null


func _rng(seed_value: int) -> RandomNumberGenerator:
	var r := RandomNumberGenerator.new()
	r.seed = seed_value
	return r


func test_skills_change_windows_and_reveal() -> void:
	check(_config.solid_window_ms(100) > _config.solid_window_ms(0), "컨택이 높으면 정타 폭이 넓다")
	check(_config.solid_window_ms(50) < _config.weak_window_ms(50))
	check(_config.weak_window_ms(50) < _config.foul_window_ms(50))
	check(_config.reveal_fraction(100) < _config.reveal_fraction(0), "선구안이 높으면 더 일찍 보인다")


func test_timing_decides_quality() -> void:
	var p := strike_pitch()
	check(_judge.quality(p, _average, 0.0) == SwingJudge.Quality.SOLID)
	check(_judge.quality(p, _average, _config.solid_window_ms(50) + 1) == SwingJudge.Quality.WEAK)
	check(_judge.quality(p, _average, -(_config.weak_window_ms(50) + 1)) == SwingJudge.Quality.FOUL)
	check(_judge.quality(p, _average, _config.foul_window_ms(50) + 1) == SwingJudge.Quality.MISS)


func test_out_of_zone_is_worse() -> void:
	var diff := _config.solid_window_ms(50) * 0.9
	check(_judge.quality(strike_pitch(), _average, diff) == SwingJudge.Quality.SOLID)
	check(_judge.quality(ball_pitch(), _average, diff) != SwingJudge.Quality.SOLID)


func test_early_pulls_late_pushes() -> void:
	var p := strike_pitch()
	var early := _judge.contact(p, _average, -_config.solid_window_ms(50) * 0.8, _rng(1))
	var late := _judge.contact(p, _average, _config.solid_window_ms(50) * 0.8, _rng(1))
	check(early.angle_deg < 0 and late.angle_deg > 0, "early %.1f late %.1f" % [early.angle_deg, late.angle_deg])


func test_power_increases_distance() -> void:
	var p := strike_pitch()
	var weak := 0.0
	var strong := 0.0
	for i in 500:
		weak += _judge.contact(p, BatterSkills.new(50, 10, 50), 0.0, _rng(i)).distance_m
		strong += _judge.contact(p, BatterSkills.new(50, 90, 50), 0.0, _rng(i)).distance_m
	weak /= 500
	strong /= 500
	check(strong > weak + 30, "파워 10: %.0fm, 파워 90: %.0fm" % [weak, strong])


func test_breaking_ball_arrives_at_target() -> void:
	var p := Pitch.generate(_config, _average, _rng(3))
	check(p.zone_position_at(1.0).distance_to(p.target) < 1e-6)


func test_taking_ends_in_walk_or_strikeout() -> void:
	for s in 200:
		var ab := AtBat.new(_config, _average, s)
		while not ab.is_over():
			ab.next_pitch()
			ab.take()
		match ab.result.outcome:
			SwingJudge.Outcome.WALK:
				check(ab.balls == 4)
			SwingJudge.Outcome.STRIKEOUT:
				check(ab.strikes == 3)
			_:
				check(false, "안 쳤는데 %s" % ab.result.outcome)
		check(ab.balls + ab.strikes == ab.result.pitches)


func test_foul_with_two_strikes_is_not_strikeout() -> void:
	var ab := AtBat.new(_config, _average, 7)
	var fouls := 0
	while not ab.is_over() and fouls < 10:
		var p := ab.next_pitch()
		if ab.strikes < 2:
			ab.swing(p.flight_ms + _config.foul_window_ms(50) * 3)  # 확실한 헛스윙
		else:
			var factor := 1.0 if p.is_strike() else _config.out_of_zone_window_factor
			var foul_diff := (_config.weak_window_ms(50) + _config.foul_window_ms(50)) / 2.0 * factor
			var o := ab.swing(p.flight_ms + _config.input_latency_ms + foul_diff)
			check(o.call == AtBat.Call.FOUL)
			fouls += 1
	check(ab.strikes == 2 and not ab.is_over())


func test_same_seed_same_result() -> void:
	check(_play(42) == _play(42))


func _play(seed_value: int) -> Array:
	var ab := AtBat.new(_config, _average, seed_value)
	var log := []
	while not ab.is_over():
		var p := ab.next_pitch()
		var o := ab.swing(p.flight_ms + 10)
		log.append([o.call, o.timing_diff_ms, o.contact.angle_deg, o.contact.distance_m])
	log.append(ab.result.to_record())
	return log


func test_result_record() -> void:
	var r := AtBat.Result.new(SwingJudge.Outcome.HOME_RUN, SwingJudge.BattedBall.FLY, 3, null)
	var rec := r.to_record()
	check(rec["outcome"] == "HOME_RUN" and rec["batted_ball"] == "FLY" and rec["pitches"] == 3, str(rec))


func test_sound_synthesis() -> void:
	var sounds := SoundSynth.all()
	check(sounds.size() == SoundSynth.Sfx.size(), "모든 효과음이 있다")
	for sfx: SoundSynth.Sfx in sounds:
		var samples: PackedFloat32Array = sounds[sfx]
		var peak := 0.0
		for v in samples:
			check(not is_nan(v), "%s NaN" % sfx)
			peak = maxf(peak, absf(v))
		check(samples.size() > 0 and peak >= 0.05 and peak <= 1.0, "%s 최대 진폭 %.2f" % [SoundSynth.Sfx.keys()[sfx], peak])
	check(SoundSynth.all()[SoundSynth.Sfx.CRACK_SOLID] == sounds[SoundSynth.Sfx.CRACK_SOLID], "같은 소리는 매번 똑같다")
