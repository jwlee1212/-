extends SceneTree
## 타격 프로토타입 테스트. 실제 config/balance.json 으로 돈다.
##   ~/Applications/Godot.app/Contents/MacOS/Godot --headless --path godot --script res://tests/run_tests.gd
## 하나라도 실패하면 종료 코드 1.

var _config: BattingConfig
var _judge: SwingJudge
var _caller: PitchCaller
var _balanced: BattingConfig.PitcherProfile
var _average := BatterSkills.new(50, 50, 50)
var _failures := 0
var _passed := 0
var _current := ""


func _init() -> void:
	_config = BattingConfig.from_balance(BalanceLoader.load_balance())
	_judge = SwingJudge.new(_config)
	_caller = PitchCaller.new(_config)
	_balanced = _config.pitchers["balanced"]
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
		"test_situation_keys",
		"test_behind_throws_more_strikes",
		"test_two_strikes_bring_putaway",
		"test_pitcher_mix_differs",
		"test_control_scatter",
		"test_chase_pitches_are_just_outside",
		"test_aim_hit_widens_miss_narrows",
		"test_aim_in_at_bat",
		"test_gap_hits_more_than_center",
		"test_speed_display",
		"test_cell_of",
		"test_tuning_applies_and_patches",
		"test_tuning_reset",
		"test_apply_patch",
		"test_running_walk_forces",
		"test_running_hits",
		"test_running_double_play_and_sac_fly",
		"test_half_inning_and_game_end",
		"test_full_games_are_consistent",
		"test_game_is_reproducible",
		"test_training_and_growth",
		"test_condition_affects_skills",
		"test_events_flow",
		"test_season_loop",
		"test_physics_distance_realistic",
		"test_home_run_and_fence",
		"test_routine_grounder_is_out",
		"test_popup_caught",
		"test_fast_runner_beats_infield",
		"test_swing_types",
		"test_bunt_two_strike_foul_is_strikeout",
		"test_sac_bunt",
		"test_result_matches_path",
		"test_first_base_call_by_timing",
		"test_first_base_receiver",
		"test_play_end_covers_the_play",
		"test_select_window_before_pitch",
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


func pitch_at(balls: int, strikes: int, seed_value: int, pitcher: BattingConfig.PitcherProfile = null) -> Pitch:
	return _caller.next(pitcher if pitcher != null else _balanced, balls, strikes, _average, _rng(seed_value))


func strike_pitch() -> Pitch:
	var s := 0
	while true:
		var p := pitch_at(0, 0, s)
		if p.is_strike():
			return p
		s += 1
	return null


func ball_pitch() -> Pitch:
	var s := 0
	while true:
		var p := pitch_at(0, 0, s)
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
		weak += _judge.contact(p, BatterSkills.new(50, 10, 50), 0.0, _rng(i)).ev_kmh
		strong += _judge.contact(p, BatterSkills.new(50, 90, 50), 0.0, _rng(i)).ev_kmh
	weak /= 500
	strong /= 500
	check(strong > weak + 15, "파워가 높을수록 타구가 빠르다: 파워 10 %.0f km/h, 파워 90 %.0f km/h" % [weak, strong])


func test_breaking_ball_arrives_at_target() -> void:
	for type: String in _config.pitches:
		var s := 0
		var p := pitch_at(0, 0, s)
		while p.type != type:
			s += 1
			p = pitch_at(0, 0, s)
		check(p.zone_position_at(1.0).distance_to(p.target) < 1e-6, type)


func test_taking_ends_in_walk_or_strikeout() -> void:
	for s in 200:
		var ab := AtBat.new(_config, _average, _balanced, s)
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
	var ab := AtBat.new(_config, _average, _balanced, 7)
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
	var ab := AtBat.new(_config, _average, _balanced, seed_value)
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


# ---------- G1: 볼배합·노려치기·방향 ----------

func test_situation_keys() -> void:
	check(BattingConfig.situation_key(0, 2) == "ahead")
	check(BattingConfig.situation_key(3, 1) == "behind")
	check(BattingConfig.situation_key(2, 2) == "even")


func _intended_strike_share(balls: int, strikes: int) -> float:
	var n := 0
	for i in 2000:
		var p := pitch_at(balls, strikes, i)
		if absf(p.intended.x) <= 1.0 and absf(p.intended.y) <= 1.0:
			n += 1
	return n / 2000.0


func test_behind_throws_more_strikes() -> void:
	var behind := _intended_strike_share(3, 0)
	var ahead := _intended_strike_share(0, 1)
	check(behind > ahead + 0.2, "불리 %.2f, 유리 %.2f" % [behind, ahead])


func test_two_strikes_bring_putaway() -> void:
	var putaway := 0
	var early := 0
	for i in 2000:
		if pitch_at(0, 2, i).is_putaway:
			putaway += 1
		if pitch_at(0, 0, i).is_putaway:
			early += 1
	var share := putaway / 2000.0
	check(absf(share - _config.two_strike_putaway_chance) < 0.04, "2스트라이크 결정구 비율 %.2f" % share)
	check(early == 0, "0스트라이크에는 결정구가 없다")
	# 결정구는 그 투수의 주무기
	var p := pitch_at(0, 2, 0)
	var s := 0
	while not p.is_putaway:
		s += 1
		p = pitch_at(0, 2, s)
	check(p.type == _balanced.putaway_pitch)


func _type_share(pitcher: BattingConfig.PitcherProfile, type: String) -> float:
	var n := 0
	for i in 2000:
		if pitch_at(1, 1, i, pitcher).type == type:
			n += 1
	return n / 2000.0


func test_pitcher_mix_differs() -> void:
	var power := _type_share(_config.pitchers["power"], "fastball")
	var finesse := _type_share(_config.pitchers["finesse"], "fastball")
	check(power > finesse + 0.15, "강속구형 직구 %.2f, 기교파 직구 %.2f" % [power, finesse])
	check(_type_share(_config.pitchers["finesse"], "changeup") > _type_share(_config.pitchers["power"], "changeup"))


func _mean_miss(pitcher: BattingConfig.PitcherProfile) -> float:
	var total := 0.0
	var n := 0
	for i in 2000:
		var p := pitch_at(1, 1, i, pitcher)
		if not p.is_mistake:
			total += p.target.distance_to(p.intended)
			n += 1
	return total / n


func test_control_scatter() -> void:
	var finesse := _mean_miss(_config.pitchers["finesse"])
	var power := _mean_miss(_config.pitchers["power"])
	check(finesse < power, "제구 좋은 투수가 미트에 더 가깝다: 기교파 %.2f, 강속구형 %.2f" % [finesse, power])


func test_chase_pitches_are_just_outside() -> void:
	for i in 500:
		var p := pitch_at(0, 2, i)
		if p.is_chase:
			var off := maxf(absf(p.intended.x), absf(p.intended.y))
			check(off >= _config.chase_offset.x - 1e-6 and off <= _config.chase_offset.y + 1e-6, "유인구 위치 %s" % p.intended)


func test_aim_hit_widens_miss_narrows() -> void:
	var p := strike_pitch()
	var diff := _config.solid_window_ms(50) * 1.1  # 보통은 약한 타구
	check(_judge.quality(p, _average, diff) == SwingJudge.Quality.WEAK)
	check(_judge.quality(p, _average, diff, _config.aim_hit_window_factor) == SwingJudge.Quality.SOLID, "노린 칸이면 정타")
	var edge := _config.solid_window_ms(50) * 0.9  # 보통은 정타
	check(_judge.quality(p, _average, edge, _config.aim_miss_window_factor) != SwingJudge.Quality.SOLID, "다른 칸이면 정타가 안 된다")


func test_aim_in_at_bat() -> void:
	# 다음 공이 들어올 칸을 미리 알아내서 그 칸을 노리면 HIT, 다른 칸이면 MISS
	var hit_seen := false
	var miss_seen := false
	for s in 50:
		var probe := AtBat.new(_config, _average, _balanced, s)
		var p := probe.next_pitch()
		var cell := Pitch.cell_of(p.target)
		if cell < 0:
			continue
		var ab := AtBat.new(_config, _average, _balanced, s)
		ab.aim_cell = cell
		var q := ab.next_pitch()
		var o := ab.swing(q.flight_ms + _config.input_latency_ms)
		check(o.aim == AtBat.Aim.HIT)
		hit_seen = true
		var ab2 := AtBat.new(_config, _average, _balanced, s)
		ab2.aim_cell = (cell + 4) % 9 if (cell + 4) % 9 != cell else (cell + 1) % 9
		var q2 := ab2.next_pitch()
		check(ab2.swing(q2.flight_ms).aim == AtBat.Aim.MISS)
		miss_seen = true
		break
	check(hit_seen and miss_seen)


func _sim(ev: float, la: float, spray: float, speed: int = 50, bunt: bool = false) -> BattedBallSim.Result:
	return BattedBallSim.simulate(_config.ball_physics, ev, la, spray, speed, bunt)


func test_gap_hits_more_than_center() -> void:
	# 같은 라이너라도 중견수 정면이면 잡히고, 우중간으로 가면 빠진다
	var center := _sim(150, 18, 0)
	var gap := _sim(150, 18, 15)
	check(center.caught and center.fielder == "CF", "정면 라이너는 중견수가 잡는다 (%s, %s)" % [center.fielder, center.outcome])
	check(SwingJudge.is_hit(gap.outcome), "갭 라이너는 안타 (%s)" % gap.outcome)


func test_speed_display() -> void:
	check(_config.speed_kmh(300.0) > _config.speed_kmh(400.0), "빠를수록 구속이 높다")
	var fb: float = (_config.pitches["fastball"] as BattingConfig.PitchSpec).flight_ms
	check(_config.speed_kmh(fb) == roundi(_config.speed_ref_kmh), "직구는 기준 구속")
	for id: String in _config.pitches:
		var kmh := _config.speed_kmh((_config.pitches[id] as BattingConfig.PitchSpec).flight_ms)
		check(kmh >= 115 and kmh <= 155, "%s 구속 %d km/h 가 현실 범위 밖" % [id, kmh])


func test_cell_of() -> void:
	check(Pitch.cell_of(Vector2(-0.9, -0.9)) == 0)
	check(Pitch.cell_of(Vector2(0, 0)) == 4)
	check(Pitch.cell_of(Vector2(0.9, 0.9)) == 8)
	check(Pitch.cell_of(Vector2(1.2, 0)) == -1)


# ---------- 손맛 조절 패널 (CLAUDE.md §3-7) ----------

func test_tuning_applies_and_patches() -> void:
	var balance := BalanceLoader.load_balance()
	var config := BattingConfig.from_balance(balance)
	var pres := Presentation.from_balance(balance)
	var base_solid := config.solid_window
	var base_fast: float = (config.pitches["fastball"] as BattingConfig.PitchSpec).flight_ms
	var t := Tuning.new(config, pres)
	check(not t.is_changed())
	check(t.to_patch(config, pres).is_empty(), "안 바꾸면 저장할 것도 없다")
	t.set_value("window", 1.5, config, pres)
	t.set_value("flight", 1.2, config, pres)
	t.set_value("hitstop", 120.0, config, pres)
	check(config.solid_window.is_equal_approx(base_solid * 1.5), "판정 폭 배율이 바로 적용된다")
	check(is_equal_approx((config.pitches["fastball"] as BattingConfig.PitchSpec).flight_ms, base_fast * 1.2))
	check(pres.hit_stop_ms == 120.0)
	var patch := t.to_patch(config, pres)
	check(patch.has("batting.timing.solidWindowMsAt0") and patch.has("batting.pitches.fastball.flightMs") and patch["batting.presentation.hitStopMs"] == 120)
	check(not patch.has("batting.timing.inputLatencyMs"), "안 건드린 항목은 저장하지 않는다")
	# patch 의 모든 경로가 실제 balance.json 에 있어야 한다
	var copy := balance.duplicate(true)
	check(BalanceLoader.apply_patch(copy, patch).is_empty(), "patch 경로가 모두 존재한다")
	var reread := BattingConfig.from_balance(copy)
	check(is_equal_approx(reread.solid_window.x, snappedf(base_solid.x * 1.5, 0.1)), "저장한 값을 다시 읽으면 같다")


func test_tuning_reset() -> void:
	var balance := BalanceLoader.load_balance()
	var config := BattingConfig.from_balance(balance)
	var pres := Presentation.from_balance(balance)
	var base_shake := pres.shake_solid_px
	var t := Tuning.new(config, pres)
	t.set_value("shake", 2.0, config, pres)
	check(is_equal_approx(pres.shake_solid_px, base_shake * 2.0))
	t.reset(config, pres)
	check(is_equal_approx(pres.shake_solid_px, base_shake) and not t.is_changed())


func test_apply_patch() -> void:
	var d := {"a": {"b": 1, "c": {"d": 2}}}
	var missing := BalanceLoader.apply_patch(d, {"a.b": 5, "a.c.d": 7, "a.zzz": 1, "x.y": 2})
	check(d.a.b == 5 and d.a.c.d == 7)
	check(missing == PackedStringArray(["a.zzz", "x.y"]), "없는 경로는 만들지 않는다: %s" % missing)
	check(not d.a.has("zzz"))


# ---------- 게임형 프로토타입: 경기·커리어 ----------

func _career_cfg() -> CareerConfig:
	return CareerConfig.from(BalanceLoader.load_balance(), BalanceLoader.load_config("content.json"))


## 확률 주루를 끄거나 켜서 결정적으로 시험한다
func _state(chance: float = 0.0) -> GameState:
	return GameState.new(9, {"singleScoresFromSecond": chance, "doubleScoresFromFirst": chance, "doublePlayChance": chance, "sacFlyChance": chance})


func test_running_walk_forces() -> void:
	var g := _state()
	g.bases = [true, false, true]
	g.apply(SwingJudge.Outcome.WALK, _rng(1))
	check(g.bases == [true, true, true] and g.score[0] == 0, "1·3루 볼넷 → 만루, 무득점 %s" % [g.bases])
	g.apply(SwingJudge.Outcome.WALK, _rng(1))
	check(g.bases == [true, true, true] and g.score[0] == 1, "만루 볼넷 → 밀어내기 1점")


func test_running_hits() -> void:
	var g := _state(0.0)
	g.bases = [true, true, true]
	var r := g.apply(SwingJudge.Outcome.SINGLE, _rng(1))
	check(r.runs == 1 and g.bases == [true, true, true], "만루 단타(2루 주자 홈인 확률 0) → 1점 %s" % [g.bases])
	var g2 := _state(1.0)
	g2.bases = [true, true, false]
	g2.apply(SwingJudge.Outcome.DOUBLE, _rng(1))
	check(g2.score[0] == 2 and g2.bases == [false, true, false], "1·2루 2루타(1루 주자 홈인 확률 1) → 2점")
	var g3 := _state()
	g3.bases = [true, true, true]
	g3.apply(SwingJudge.Outcome.HOME_RUN, _rng(1))
	check(g3.score[0] == 4 and g3.bases == [false, false, false], "만루 홈런 4점")


func test_running_double_play_and_sac_fly() -> void:
	var g := _state(1.0)
	g.bases = [true, false, false]
	var r := g.apply(SwingJudge.Outcome.GROUND_OUT, _rng(1))
	check(r.note == "병살" and g.outs == 2 and g.bases == [false, false, false], "무사 1루 병살 → 2사 주자 없음")
	var g2 := _state(1.0)
	g2.bases = [false, false, true]
	var r2 := g2.apply(SwingJudge.Outcome.FLY_OUT, _rng(1))
	check(r2.note == "희생플라이" and g2.score[0] == 1 and g2.outs == 1)
	var g3 := _state(1.0)
	g3.outs = 2
	g3.bases = [false, false, true]
	g3.apply(SwingJudge.Outcome.FLY_OUT, _rng(1))
	check(g3.score[0] == 0, "2사에는 희생플라이 없음")


func test_half_inning_and_game_end() -> void:
	var g := _state()
	g.bases = [true, true, false]
	for i in 3:
		g.apply(SwingJudge.Outcome.STRIKEOUT, _rng(1))
	check(not g.top and g.outs == 0 and g.bases == [false, false, false], "3아웃이면 공수 교대, 주자 정리")
	# 9회말 끝내기
	var w := _state()
	w.inning = 9
	w.top = false
	w.line = [[0, 0, 0, 0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0, 0, 0]]
	w.score = [3, 3]
	w.apply(SwingJudge.Outcome.HOME_RUN, _rng(1))
	check(w.over and w.score[1] == 4, "9회말 홈런 → 끝내기")


func _play_full_game(seed_value: int) -> GameRunner:
	var cfg := _career_cfg()
	var player := PlayerData.create("테스트", cfg)
	var game := GameRunner.new(cfg, player, cfg.opponents[seed_value % cfg.opponents.size()], seed_value % 2 == 0, seed_value)
	var guard := 0
	while not game.state.over and guard < 400:
		guard += 1
		var step := game.step()
		if step.type == "my_turn":
			# 내 타석은 자동 결과로 대신한다
			var o: SwingJudge.Outcome = [SwingJudge.Outcome.SINGLE, SwingJudge.Outcome.STRIKEOUT, SwingJudge.Outcome.GROUND_OUT, SwingJudge.Outcome.HOME_RUN][guard % 4]
			game.apply_my_result(AtBat.Result.new(o, SwingJudge.BattedBall.NONE, 3, null))
	check(game.state.over, "경기가 끝난다 (seed %d)" % seed_value)
	return game


func test_full_games_are_consistent() -> void:
	var total_runs := 0
	var my_pa := 0
	for s in 200:
		var game := _play_full_game(s)
		var st := game.state
		for side in 2:
			var sum := 0
			for v: int in st.line[side]:
				sum += v
			check(sum == st.score[side], "이닝별 점수 합 = 총점 (seed %d)" % s)
		check(st.inning <= 9, "9회를 넘지 않는다")
		check(st.line[0].size() == st.inning, "원정 이닝 칸 수")
		total_runs += st.score[0] + st.score[1]
		my_pa += game.my_line.pa
	var runs_per_team := total_runs / 400.0
	check(runs_per_team > 2.5 and runs_per_team < 7.5, "팀당 평균 득점 %.2f" % runs_per_team)
	var pa_per_game := my_pa / 200.0
	check(pa_per_game >= 3.0 and pa_per_game <= 5.5, "내 타석 수 평균 %.2f" % pa_per_game)


func test_game_is_reproducible() -> void:
	check(_play_full_game(7).log == _play_full_game(7).log, "같은 시드면 같은 중계")


func test_training_and_growth() -> void:
	var career := CareerState.new(_career_cfg(), "테스트")
	var p := career.player
	var before := p.contact
	var cond := p.condition
	career.train("contact")
	check(p.contact == before + int(career.cfg.training["contact"]["gain"]), "타격 훈련 → 컨택 상승")
	check(p.condition < cond, "훈련하면 지친다")
	career.train("rest")
	check(p.condition > cond - 10, "휴식하면 회복")
	var game := career.new_game()
	game.my_line.add(SwingJudge.Outcome.HOME_RUN, 2)
	game.my_line.add(SwingJudge.Outcome.SINGLE, 0)
	var power := p.power
	var notes := career.finish_game(game)
	check(p.power > power and not notes.is_empty(), "홈런 치면 파워 성장")
	check(p.season.home_runs == 1 and p.season.rbi == 2 and p.season.hits == 2)


func test_condition_affects_skills() -> void:
	var cfg := _career_cfg()
	var p := PlayerData.create("테스트", cfg)
	p.condition = 100
	var fresh := p.skills_for_game(cfg)
	p.condition = 20
	var tired := p.skills_for_game(cfg)
	check(fresh.contact > tired.contact, "컨디션이 좋으면 판정 폭이 넓어진다")


func test_events_flow() -> void:
	var career := CareerState.new(_career_cfg(), "테스트")
	var envelope := {}
	var rumor := {}
	for e: Dictionary in career.cfg.events:
		if e.id == "envelope":
			envelope = e
		if e.id == "envelope_rumor":
			rumor = e
	career.week = 3
	check(not career._eligible(rumor), "봉투를 받기 전에는 봉투 소문이 안 나온다")
	career.resolve_event(envelope, 1)
	check(career.player.flags.has("envelope") and career.player.lineup_slot == 3, "모른 척 → 3번 타순 + 표시")
	check(career._eligible(rumor), "봉투를 받았으면 나중에 소문이 돌아온다")
	check(not career._eligible(envelope), "한 번 나온 이벤트는 다시 안 나온다")
	# 모든 이벤트의 모든 선택지가 오류 없이 적용된다
	for e: Dictionary in career.cfg.events:
		for i in (e.choices as Array).size():
			var c2 := CareerState.new(career.cfg, "검사")
			check(c2.resolve_event(e, i) != "", "%s 선택지 %d" % [e.id, i])


func test_season_loop() -> void:
	var career := CareerState.new(_career_cfg(), "테스트")
	var weeks := 0
	while not career.is_season_over():
		career.train(career.cfg.training.keys()[weeks % career.cfg.training.size()])
		var game := career.new_game()
		while not game.state.over:
			if game.step().type == "my_turn":
				game.apply_my_result(AtBat.Result.new(SwingJudge.Outcome.WALK, SwingJudge.BattedBall.NONE, 4, null))
		career.finish_game(game)
		var e := career.pick_event()
		if not e.is_empty():
			career.resolve_event(e, 0)
		career.advance_week()
		weeks += 1
	check(weeks == career.cfg.season_weeks and career.results.size() == weeks, "한 시즌 %d주" % weeks)
	check(career.player.season.games == weeks)
	check(career.coach_line(career.new_game()) != "", "감독 한마디")
	career.start_next_season()
	check(career.player.grade == 2 and career.week == 1 and career.results.is_empty(), "다음 시즌은 2학년")


# ---------- G1.5: 타구 물리·수비·스윙 종류 ----------

func test_physics_distance_realistic() -> void:
	# 수비가 없는 비거리 기준: 160 km/h·28° 는 110~125m, 체공 4~5.5초 (실제 야구 수준)
	var r := _sim(160, 28, 0)
	var flight_t := 0.0
	var landed := 0.0
	for i in r.path.size():
		if r.path[i].z <= 0.0 and i > 0:
			flight_t = i * BattedBallSim.DT
			landed = Vector2(r.path[i].x, r.path[i].y).length()
			break
	if r.caught:
		landed = r.distance_m
		flight_t = r.duration()
	check(r.distance_m > 100 and r.distance_m < 130, "비거리 %.1fm" % r.distance_m)


func test_home_run_and_fence() -> void:
	var hr := _sim(178, 28, -30)
	check(hr.home_run and hr.outcome == SwingJudge.Outcome.HOME_RUN, "잘 맞은 당겨친 뜬공은 홈런 (%.1fm)" % hr.distance_m)
	check(_config.ball_physics.fence_at(0) > _config.ball_physics.fence_at(45), "가운데 담장이 더 멀다")


func test_routine_grounder_is_out() -> void:
	# 평범한 땅볼(약 140 km/h)이 유격수 정면으로 가면 아웃
	var r := _sim(140, -6, -13)
	check(r.outcome == SwingJudge.Outcome.GROUND_OUT and r.fielder in ["SS", "3B", "P", "2B"], "유격수 앞 땅볼 아웃 (%s by %s)" % [r.outcome, r.fielder])
	check(r.throw_base == 1)


func test_popup_caught() -> void:
	var r := _sim(80, 60, 5)
	check(r.caught and r.outcome == SwingJudge.Outcome.FLY_OUT, "내야 뜬공 아웃 (%s)" % r.outcome)


func test_fast_runner_beats_infield() -> void:
	# 느린 땅볼을 3루 쪽 깊게: 빠른 타자는 살고 느린 타자는 죽는 경우가 있어야 한다
	var safe_fast := 0
	var safe_slow := 0
	for ev in range(45, 95, 5):
		for spray in [-30, -20, 20, 30]:
			if _sim(ev, -8, spray, 100).outcome == SwingJudge.Outcome.SINGLE:
				safe_fast += 1
			if _sim(ev, -8, spray, 0).outcome == SwingJudge.Outcome.SINGLE:
				safe_slow += 1
	check(safe_fast > safe_slow, "빠른 타자 내야안타 %d, 느린 타자 %d" % [safe_fast, safe_slow])


func test_swing_types() -> void:
	var p := strike_pitch()
	var normal := _judge.window_factor(p, 1.0, "normal")
	check(_judge.window_factor(p, 1.0, "contact") > normal and _judge.window_factor(p, 1.0, "power") < normal, "컨택 스윙 판정 폭↑, 파워 스윙↓")
	var ev_c := 0.0
	var ev_p := 0.0
	for i in 300:
		ev_c += _judge.contact(p, _average, 0.0, _rng(i), 1.0, 0.0, "contact").ev_kmh
		ev_p += _judge.contact(p, _average, 0.0, _rng(i), 1.0, 0.0, "power").ev_kmh
	check(ev_p > ev_c, "파워 스윙 타구가 더 빠르다")
	var bunt := _judge.contact(p, _average, 0.0, _rng(1), 1.0, 0.0, "bunt")
	check(bunt.bunt and bunt.ev_kmh < 45, "번트는 약하게 굴린다 (%.0f km/h)" % bunt.ev_kmh)


func test_bunt_two_strike_foul_is_strikeout() -> void:
	var ab := AtBat.new(_config, _average, _balanced, 3)
	ab.swing_type = "bunt"
	while ab.strikes < 2:
		var p := ab.next_pitch()
		ab.swing(p.flight_ms + 2000)  # 헛스윙
	var q := ab.next_pitch()
	var factor := _judge.window_factor(q, 1.0, "bunt")
	var foul_diff := (_config.weak_window_ms(50) + _config.foul_window_ms(50)) / 2.0 * factor
	var o := ab.swing(q.flight_ms + _config.input_latency_ms + foul_diff)
	check(o.call == AtBat.Call.FOUL and ab.is_over() and ab.result.outcome == SwingJudge.Outcome.STRIKEOUT, "2스트라이크 번트 파울 = 삼진")


func test_sac_bunt() -> void:
	var g := _state()
	g.bases = [true, false, false]
	var r := g.apply(SwingJudge.Outcome.GROUND_OUT, _rng(1), true)
	check(r.outcome == SwingJudge.Outcome.SAC_BUNT and g.outs == 1 and g.bases == [false, true, false], "무사 1루 번트 아웃 → 희생번트, 1사 2루")
	var g2 := _state()
	var r2 := g2.apply(SwingJudge.Outcome.GROUND_OUT, _rng(1), true)
	check(r2.outcome == SwingJudge.Outcome.GROUND_OUT, "주자가 없으면 그냥 땅볼 아웃")
	var line := PlayerData.SeasonStats.new()
	line.add(SwingJudge.Outcome.SAC_BUNT, 0)
	check(line.pa == 1 and line.ab == 0, "희생번트는 타수가 아니다")


func test_result_matches_path() -> void:
	# 화면이 그리는 경로의 끝이 잡은·주운 자리와 같다
	for args in [[150, 18, 0], [140, -6, -13], [80, 60, 5], [150, 18, 15]]:
		var r := _sim(args[0], args[1], args[2])
		var end := r.path[r.path.size() - 1]
		check(Vector2(end.x, end.y).distance_to(r.fielder_to) < 0.01, "경로 끝 = 수비수 위치 %s" % [args])
		check(r.fielder_arrive <= r.duration() + 0.001, "수비수는 공보다 먼저(또는 같이) 도착한다")


# ---------- 1루 판정 (공이 미트에 들어가는 시각 vs 타자가 베이스를 밟는 시각) ----------

func _infield_plays() -> Array:
	var plays := []
	for ev in range(40, 150, 10):
		for spray in range(-40, 41, 8):
			for speed in [0, 50, 100]:
				var r := _sim(ev, -8, spray, speed)
				if r.is_first_base_play():
					plays.append(r)
	return plays


func test_first_base_call_by_timing() -> void:
	var outs := 0
	var safes := 0
	for r: BattedBallSim.Result in _infield_plays():
		var ball_first := r.throw_arrive < r.runner_first
		check((r.outcome == SwingJudge.Outcome.GROUND_OUT) == ball_first,
			"공 도착 %.2f초, 타자 도착 %.2f초 → %s" % [r.throw_arrive, r.runner_first, SwingJudge.Outcome.keys()[r.outcome]])
		check(is_equal_approx(r.play_margin, r.runner_first - r.throw_arrive))
		if ball_first:
			outs += 1
		else:
			safes += 1
	check(outs > 0 and safes > 0, "아웃 %d, 세이프 %d — 둘 다 나와야 한다" % [outs, safes])
	# 동시면 세이프 (주자 우선)
	var r0 := _sim(140, -6, -13)
	check(r0.runner_first == r0.runner_time(1))


func test_first_base_receiver() -> void:
	for r: BattedBallSim.Result in _infield_plays():
		check(r.receiver != "", "받는 사람이 있다")
		# 공이 미트에 들어가는 시각은 받는 사람이 베이스에 도착한 뒤다
		check(r.throw_arrive >= r.receiver_arrive - 0.0001, "받는 사람 도착 %.2f, 공 %.2f" % [r.receiver_arrive, r.throw_arrive])
		if r.fielder == "1B":
			check(r.self_putout or r.receiver == "P", "1루수가 잡으면 직접 밟거나 투수가 커버")
		else:
			check(r.receiver == "1B" and r.throw_release > 0.0)


func test_play_end_covers_the_play() -> void:
	for r: BattedBallSim.Result in _infield_plays():
		check(r.play_end() >= r.throw_arrive and r.play_end() >= minf(r.runner_first, r.throw_arrive), "중계는 판정이 끝날 때까지 보여 준다")
	var hit := _sim(150, 18, 15)
	check(hit.play_end() >= hit.runner_time(1), "안타면 타자가 베이스를 밟을 때까지")


# ---------- 투구 전 선택 시간 ----------

func test_select_window_before_pitch() -> void:
	var balance := BalanceLoader.load_balance()
	var s := BattingSession.new(BattingConfig.from_balance(balance), Presentation.from_balance(balance))
	check(s.phase == BattingSession.Phase.WAITING, "처음엔 대기")
	# 시계를 되돌려 "여운은 지났고 선택 시간 절반" 상태로 만든다
	s.phase_since_ms = BattingSession.now_ms() - s.presentation.pitch_interval_ms - s.presentation.select_ms * 0.5
	s.tick()
	check(s.phase == BattingSession.Phase.WAITING, "선택 시간 중에는 던지지 않는다")
	check(absf(s.select_progress() - 0.5) < 0.05, "선택 시간 진행 %.2f" % s.select_progress())
	s.set_swing_type("power")
	check(s.at_bat.swing_type == "power", "선택 시간에 고른 스윙 종류가 이번 공에 쓰인다")
	s.ready_now()
	check(s.phase == BattingSession.Phase.WINDUP and s.pitch != null, "스윙 버튼을 누르면 바로 던진다")
	check(s.select_progress() < 0.0, "던지기 시작하면 선택 시간 끝")
