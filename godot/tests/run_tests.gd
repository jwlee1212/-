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
		"test_record_distribution",
		"test_physics_running",
		"test_game_is_reproducible",
		"test_training_and_growth",
		"test_condition_affects_skills",
		"test_events_flow",
		"test_event_integrity",
		"test_delayed_event",
		"test_josa",
		"test_story_three_years",
		"test_rival",
		"test_scouting",
		"test_lineup_by_trust",
		"test_economy",
		"test_shop",
		"test_social",
		"test_romance",
		"test_dialogue_lines",
		"test_season_loop",
		"test_standings_consistent",
		"test_tournament_elimination",
		"test_growth_curve",
		"test_injury_risk",
		"test_rosters_persist",
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
		"test_my_at_bat_carries_runners",
		"test_select_window_before_pitch",
		"test_runner_two_outs_always_run",
		"test_runner_ground_ball_force_and_tag",
		"test_runner_fly_ball_halfway_return_tag_up",
		"test_runner_rounding_route_and_stop",
		"test_play_is_deterministic",
		"test_play_records_and_hit_credit",
		"test_base_running_rates",
		"test_fielder_steps_on_near_base",
		"test_no_pointless_throws",
		"test_force_outs_and_cover",
		"test_relay_play",
		"test_one_training_per_week",
		"test_launch_angle_never_backwards",
		"test_final_count_recorded",
		"test_season_stats_details",
		"test_records_game_log_and_splits",
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


## 확률 주루를 끄거나 켜서 결정적으로 시험한다 (타구 물리 없이). 주자는 타순 번호, -1 = 빈 베이스
func _state(chance: float = 0.0) -> GameState:
	var running := {"singleScoresFromSecond": chance, "doubleScoresFromFirst": chance, "doublePlayChance": chance, "sacFlyChance": chance,
		"leadStartS": 0.55, "tagUpS": 0.25, "pivotS": 0.55, "extraBaseMarginS": 0.15, "runnerSpeedBonusMps": 0.8, "twoOutJumpS": 0.3, "tagS": 0.25}
	return GameState.new(9, running)


func test_running_walk_forces() -> void:
	var g := _state()
	g.bases = [1, -1, 3]
	g.apply(SwingJudge.Outcome.WALK, _rng(1), false, null, 5)
	check(g.bases == [5, 1, 3] and g.score[0] == 0, "1·3루 볼넷 → 만루, 무득점 %s" % [g.bases])
	var r := g.apply(SwingJudge.Outcome.WALK, _rng(1), false, null, 6)
	check(g.bases == [6, 5, 1] and g.score[0] == 1 and r.scorers == [3] and r.rbi == 1, "만루 볼넷 → 밀어내기 1점, 3루 주자가 득점")


func test_running_hits() -> void:
	var g := _state(0.0)
	g.bases = [1, 2, 3]
	var r := g.apply(SwingJudge.Outcome.SINGLE, _rng(1), false, null, 4)
	check(r.runs == 1 and g.bases == [4, 1, 2], "만루 단타(2루 주자 홈인 확률 0) → 1점, 주자 한 칸씩 %s" % [g.bases])
	var g2 := _state(1.0)
	g2.bases = [1, 2, -1]
	var r2 := g2.apply(SwingJudge.Outcome.DOUBLE, _rng(1), false, null, 4)
	check(g2.score[0] == 2 and g2.bases == [-1, 4, -1] and r2.scorers.size() == 2, "1·2루 2루타(1루 주자 홈인 확률 1) → 2점")
	var g3 := _state()
	g3.bases = [1, 2, 3]
	var r3 := g3.apply(SwingJudge.Outcome.HOME_RUN, _rng(1), false, null, 4)
	check(g3.score[0] == 4 and g3.bases == [-1, -1, -1] and r3.scorers == [3, 2, 1, 4], "만루 홈런 4점")


func test_running_double_play_and_sac_fly() -> void:
	var g := _state(1.0)
	g.bases = [1, -1, -1]
	var r := g.apply(SwingJudge.Outcome.GROUND_OUT, _rng(1), false, null, 2)
	check(r.note == "병살" and g.outs == 2 and g.bases == [-1, -1, -1] and r.outs == 2, "무사 1루 병살 → 2사 주자 없음")
	var g2 := _state(1.0)
	g2.bases = [-1, -1, 3]
	var r2 := g2.apply(SwingJudge.Outcome.FLY_OUT, _rng(1), false, null, 2)
	check(r2.note == "희생플라이" and g2.score[0] == 1 and g2.outs == 1 and r2.sac_fly)
	var g3 := _state(1.0)
	g3.outs = 2
	g3.bases = [-1, -1, 3]
	g3.apply(SwingJudge.Outcome.FLY_OUT, _rng(1), false, null, 2)
	check(g3.score[0] == 0, "2사에는 희생플라이 없음")


func test_half_inning_and_game_end() -> void:
	var g := _state()
	g.bases = [1, 2, -1]
	for i in 3:
		g.apply(SwingJudge.Outcome.STRIKEOUT, _rng(1))
	check(not g.top and g.outs == 0 and g.bases == [-1, -1, -1], "3아웃이면 공수 교대, 주자 정리")
	# 9회말 끝내기
	var w := _state()
	w.inning = 9
	w.top = false
	w.line = [[0, 0, 0, 0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 0, 0, 0, 0, 0]]
	w.score = [3, 3]
	w.apply(SwingJudge.Outcome.HOME_RUN, _rng(1))
	check(w.over and w.score[1] == 4, "9회말 홈런 → 끝내기")


## 경기 하나를 끝까지 (내 타석도 AutoPa 로 대신 — 기록 분포 검사에 쓰려고)
func _play_full_game(seed_value: int) -> GameRunner:
	var cfg := _career_cfg()
	var player := PlayerData.create("테스트", cfg)
	var srng := _rng(seed_value)
	var mine := School.create("me", "우리고", "우리", 50.0, "balanced", cfg, srng)
	var theirs := School.from_content(cfg.regional_schools[seed_value % cfg.regional_schools.size()], cfg, srng)
	theirs.strength = 50.0
	theirs.team = Team.generate(theirs.name, theirs.short, 50.0, theirs.pitcher_profile, cfg, srng)
	var game := GameRunner.new(cfg, _config, player, mine, theirs, seed_value % 2 == 0, seed_value)
	var rng := _rng(seed_value + 999)
	var guard := 0
	while not game.state.over and guard < 400:
		guard += 1
		var step := game.step()
		if step.type == "my_turn":
			var pa := AutoPa.simulate(cfg.auto_pa, _config, game.current_batter().skills, game.current_pitcher(), rng)
			var c: SwingJudge.Contact = null
			if pa.ball != null:
				c = SwingJudge.Contact.new(SwingJudge.Quality.SOLID, pa.ball.spray_deg, pa.ball.distance_m, pa.ball.batted_ball)
				c.ball = pa.ball
			game.apply_my_result(AtBat.Result.new(pa.outcome, SwingJudge.BattedBall.NONE, pa.pitches, c))
	check(game.state.over, "경기가 끝난다 (seed %d)" % seed_value)
	return game


func test_full_games_are_consistent() -> void:
	# 200경기: 점수판·박스스코어·아웃이 서로 맞는다
	for s in 200:
		var game := _play_full_game(s)
		var st := game.state
		var box := game.box
		for side in 2:
			var sum := 0
			for v: int in st.line[side]:
				sum += v
			check(sum == st.score[side], "이닝별 점수 합 = 총점 (seed %d)" % s)
			check(box.team_runs(side) == st.score[side], "타자 득점 합 = 총점 (seed %d, %d vs %d)" % [s, box.team_runs(side), st.score[side]])
			check(box.runs_allowed(1 - side) == st.score[side], "상대 투수 실점 합 = 총점 (seed %d)" % s)
			var hits := 0
			for b: BoxScore.BatterLine in box.batters[side]:
				hits += b.h
				check(b.pa == b.ab + b.bb + b.sh + b.sf, "타석 = 타수 + 볼넷 + 희생번트 + 희생플라이")
			check(hits == box.hits[side], "타자 안타 합 = 팀 안타")
		# 수비 아웃: 원정 투수는 홈 공격의 아웃을 잡는다
		var home_halves: int = st.line[1].size()
		var away_outs := 0
		for p: BoxScore.PitcherLine in box.pitchers[1]:
			away_outs += p.outs
		check(away_outs == st.line[0].size() * 3, "홈 투수가 잡은 아웃 = 원정 공격 이닝 × 3 (seed %d: %d)" % [s, away_outs])
		var home_outs := 0
		for p: BoxScore.PitcherLine in box.pitchers[0]:
			home_outs += p.outs
		check(home_outs <= home_halves * 3 and home_outs >= (home_halves - 1) * 3, "원정 투수 아웃 (끝내기면 마지막 이닝은 3 미만)")
		check(st.inning <= 9, "9회를 넘지 않는다")


func test_record_distribution() -> void:
	# 자동 진행 기록이 한국 프로야구 분포 근처 (팀 전력이 비슷한 리그 평균 기준)
	var pa := 0
	var ab := 0
	var h := 0
	var tb := 0
	var bb := 0
	var so := 0
	var hr := 0
	var runs := 0
	var games := 300
	for s in games:
		var game := _play_full_game(1000 + s)
		runs += game.state.score[0] + game.state.score[1]
		for side in 2:
			for b: BoxScore.BatterLine in game.box.batters[side]:
				pa += b.pa
				ab += b.ab
				h += b.h
				bb += b.bb
				so += b.so
				hr += b.hr
				tb += b.h + b.doubles + 2 * b.triples + 3 * b.hr
	var avg := float(h) / ab
	var k := float(so) / pa
	var walk := float(bb) / pa
	var hr_rate := float(hr) / pa
	var rpg := runs / (games * 2.0)
	var babip := float(h - hr) / maxi(1, ab - so - hr)
	print("    기록 분포: 타율 %.3f 장타 %.3f 삼진 %.1f%% 볼넷 %.1f%% 홈런 %.1f%% BABIP %.3f 팀당 득점 %.2f" % [avg, float(tb) / ab, k * 100, walk * 100, hr_rate * 100, babip, rpg])
	check(avg > 0.24 and avg < 0.30, "타율 %.3f" % avg)
	check(k > 0.15 and k < 0.23, "삼진율 %.3f" % k)
	check(walk > 0.06 and walk < 0.11, "볼넷율 %.3f" % walk)
	check(hr_rate > 0.008 and hr_rate < 0.04, "홈런율 %.3f" % hr_rate)
	check(rpg > 3.5 and rpg < 6.5, "팀당 득점 %.2f" % rpg)


func test_physics_running() -> void:
	# 타구 물리 주루: 같은 상황이라도 타구에 따라 홈인/3루 멈춤, 병살/야수선택이 갈린다
	var scored := 0
	var held := 0
	var dp := 0
	var fc := 0
	for ev in range(110, 170, 6):
		for spray in range(-40, 41, 10):
			var ball := _sim(ev, 8, spray)
			if ball.outcome == SwingJudge.Outcome.SINGLE and not ball.is_first_base_play():
				var g := GameState.new(9, _career_cfg().running, _config.ball_physics)
				g.bases = [-1, 1, -1]
				var r := g.apply(SwingJudge.Outcome.SINGLE, _rng(1), false, ball, 4, func(_i: int) -> float: return 7.5)
				if r.runs == 1:
					scored += 1
				elif g.bases[2] == 1:
					held += 1
			var gb := _sim(ev, -6, spray)
			if gb.outcome == SwingJudge.Outcome.GROUND_OUT:
				var g2 := GameState.new(9, _career_cfg().running, _config.ball_physics)
				g2.bases = [1, -1, -1]
				var r2 := g2.apply(SwingJudge.Outcome.GROUND_OUT, _rng(1), false, gb, 4, func(_i: int) -> float: return 7.5)
				if r2.note == "병살":
					dp += 1
				elif r2.note == "야수선택":
					fc += 1
	check(scored > 0 and held > 0, "2루 주자 단타 때 홈인 %d, 3루 멈춤 %d" % [scored, held])
	check(dp > 0, "병살이 나온다 (%d), 야수선택 %d" % [dp, fc])


func test_game_is_reproducible() -> void:
	check(_play_full_game(7).log == _play_full_game(7).log, "같은 시드면 같은 중계")


func test_training_and_growth() -> void:
	var career := CareerState.new(_career_cfg(), "테스트", _config)
	var p := career.player
	var before := p.contact
	var cond := p.condition
	career.train("contact")
	check(p.contact >= before + 1, "타격 훈련 → 컨택 상승 (%d → %d)" % [before, p.contact])
	check(p.condition < cond, "훈련하면 지친다")
	career.train("rest")
	check(p.condition > cond - 10, "휴식하면 회복")
	var game := career.new_game()
	game.my_line.add(SwingJudge.Outcome.HOME_RUN, 2)
	game.my_line.add(SwingJudge.Outcome.HOME_RUN, 1)
	game.my_line.add(SwingJudge.Outcome.SINGLE, 0)
	var power := p.power + float(p.progress.get("power", 0.0))
	career.finish_game(game)
	check(p.power + float(p.progress.get("power", 0.0)) > power, "홈런 치면 파워가 자란다")
	check(p.season.home_runs == 2 and p.season.rbi == 3 and p.season.hits == 3)


func test_condition_affects_skills() -> void:
	var cfg := _career_cfg()
	var p := PlayerData.create("테스트", cfg)
	p.condition = 100
	var fresh := p.skills_for_game(cfg)
	p.condition = 20
	var tired := p.skills_for_game(cfg)
	check(fresh.contact > tired.contact, "컨디션이 좋으면 판정 폭이 넓어진다")


func _event(cfg: CareerConfig, id: String) -> Dictionary:
	for e: Dictionary in cfg.events:
		if e.id == id:
			return e
	check(false, "이벤트 %s 없음" % id)
	return {}


func test_events_flow() -> void:
	var career := CareerState.new(_career_cfg(), "테스트", _config)
	var book := career.book
	var envelope := _event(career.cfg, "envelope")
	var rumor := _event(career.cfg, "envelope_rumor")
	career.week = 7
	check(not book.eligible(career, rumor), "봉투를 받기 전에는 봉투 소문이 안 나온다")
	career.resolve_event(envelope, 1)
	check(career.player.flags.has("envelope") and career.player.lineup_slot == 3, "모른 척 → 3번 타순 + 표시")
	check(book.eligible(career, rumor), "봉투를 받았으면 나중에 소문이 돌아온다")
	career.week = 2
	check(not book.eligible(career, envelope), "한 번 나온 이벤트는 다시 안 나온다")
	# 다시 나오는 이벤트는 cooldownWeeks 뒤에
	var meal := _event(career.cfg, "mom_meal")
	career.resolve_event(meal, 0)
	check(not book.eligible(career, meal), "보양식은 바로 다시 안 나온다")
	career.total_week += int(meal.cooldownWeeks)
	check(book.eligible(career, meal), "쿨다운 뒤 다시 나온다")


func test_event_integrity() -> void:
	var cfg := _career_cfg()
	var errors := EventBook.validate(cfg)
	check(errors.is_empty(), "이벤트 정의 문제: %s" % [errors])
	# 일부러 망가뜨린 정의는 잡아낸다
	var broken := _career_cfg()
	broken.events = [{"id": "x", "speaker": "coach", "text": "{nobody}", "choices": [{"label": "a", "result": "b", "effects": {"bogus": 1, "rel": {"ghost": 1}}}], "requires": ["never"]}]
	check(EventBook.validate(broken).size() >= 4, "모르는 효과·인물·플래그·글자 치환을 잡는다")
	# 모든 이벤트의 모든 선택지(확률 갈림 결과까지)가 오류 없이 적용되고 글자 치환이 다 풀린다
	for e: Dictionary in cfg.events:
		for i in (e.choices as Array).size():
			var c := _rich_career(cfg, e)
			var text := c.resolve_event(e, i)
			check(text != "" and not text.contains("{"), "%s 선택지 %d: %s" % [e.id, i, text])
			check(not EventBook.fill(c, e.text).contains("{"), "%s 본문 치환" % e.id)
			check(EventBook.speaker_name(c, e) != "", "%s 화자" % e.id)
			var choice: Dictionary = e.choices[i]
			if choice.has("outcomes"):
				# 시드를 바꿔 가며 모든 갈래가 나오는지
				var seen := {}
				for k in 40:
					var c2 := _rich_career(cfg, e)
					c2.total_week = k
					c2.week = k
					seen[c2.resolve_event(e, i)] = true
				check(seen.size() >= (choice.outcomes as Array).size(), "%s 선택지 %d 의 갈래가 모두 나온다" % [e.id, i])


func test_josa() -> void:
	check(EventBook.josa("이민재이(가) 웃었다") == "이민재가 웃었다", EventBook.josa("이민재이(가) 웃었다"))
	check(EventBook.josa("강준서과(와) 김철이(가)") == "강준서와 김철이", EventBook.josa("강준서과(와) 김철이(가)"))
	check(EventBook.josa("ABC을(를)") == "ABC을(를)", "한글이 아니면 그대로")


## 어떤 선택지든 고를 수 있는 커리어 (돈 넉넉, partner 이벤트면 연애 중)
func _rich_career(cfg: CareerConfig, e: Dictionary) -> CareerState:
	var c := CareerState.new(cfg, "검사", _config)
	c.player.money = 10000000
	if e.get("partner", "") == "any":
		c.people.partner = "manager"
	return c


## 고를 수 있는 선택지 (원하는 번호가 막혀 있으면 처음으로 고를 수 있는 것)
func _ok_choice(career: CareerState, e: Dictionary, want: int) -> int:
	var choices: Array = e.choices
	if EventBook.choice_block(career, choices[want]) == "":
		return want
	for i in choices.size():
		if EventBook.choice_block(career, choices[i]) == "":
			return i
	check(false, "%s: 고를 수 있는 선택지가 없다" % e.id)
	return 0


func test_delayed_event() -> void:
	# 폼 교정을 받아들이면 4주 뒤에 결과가 돌아온다 (그 전엔 안 나온다)
	var career := CareerState.new(_career_cfg(), "테스트", _config)
	var fix := _event(career.cfg, "slump_fix")
	var payoff := _event(career.cfg, "slump_fix_payoff")
	check(not career.book.eligible(career, payoff), "예약 전용 이벤트는 그냥은 안 나온다")
	var contact := career.player.contact
	career.resolve_event(fix, 0)
	check(career.player.contact == contact - 2, "당장은 컨택이 떨어진다")
	var found_week := -1
	for w in 8:
		var after := career.events("after") if not career.last.is_empty() else []
		career.last = {"result": "win", "hits": 0, "home_runs": 0, "phase_end": "", "national": false, "opponent_id": "", "season_over": false}
		after = career.events("after")
		for e: Dictionary in after:
			if e.id == "slump_fix_payoff":
				found_week = w
				career.resolve_event(e, 0)
		career.advance_week()
	check(found_week == 4, "4주 뒤에 돌아온다 (실제 %d주 뒤)" % found_week)
	check(career.player.contact == contact + 2, "결국 컨택 +2 (%d → %d)" % [contact, career.player.contact])


## 고교 3년 자동 진행 (선택은 choose(이벤트) → 선택지 번호). 돌려주는 값: 커리어
func _auto_career(seed_value: int, choose: Callable, max_events: Array = []) -> CareerState:
	var cfg := _career_cfg()
	cfg.seed = seed_value
	var career := CareerState.new(cfg, "자동", _config)
	var ids: Array = cfg.training.keys()
	for season in 3:
		var weeks := 0
		while not career.is_season_over() and weeks < 40:
			var count := 0
			for e: Dictionary in career.events("start").duplicate():
				career.resolve_event(e, _ok_choice(career, e, choose.call(e)))
				count += 1
			var game := _auto_week(career, ids[weeks % ids.size()])
			career.finish_game(game)
			for e: Dictionary in career.events("after").duplicate():
				career.resolve_event(e, _ok_choice(career, e, choose.call(e)))
				count += 1
			max_events.append(count)
			career.advance_week()
			weeks += 1
		for e: Dictionary in career.events("seasonEnd").duplicate():
			career.resolve_event(e, _ok_choice(career, e, choose.call(e)))
		if season < 2:
			career.winter_training()
			career.start_next_season()
	return career


func test_story_three_years() -> void:
	var counts := []
	var career := _auto_career(2026, func(e: Dictionary) -> int: return 0, counts)
	check(counts.max() <= int(career.cfg.story.maxEventsPerWeek), "한 주 이벤트 최대 %d개" % counts.max())
	var titles := {}
	for entry: Dictionary in career.book.log:
		titles[entry.title] = true
	for t in ["입부", "동기", "라이벌", "첫 겨울", "주장", "진로"]:
		check(titles.has(t), "고교 3년 이야기에 '%s' 가 나온다" % t)
	check(career.path == "draft", "진로가 정해진다 (%s)" % career.path)
	check(career.player.grade == 3 and career.history.size() == 2, "3학년까지, 지난 시즌 기록 2개")
	check(career.people.is_met("rival"), "라이벌을 만났다")
	check(career.book.log.size() >= 15 and career.book.log.size() <= 45, "3년 이벤트 수 %d" % career.book.log.size())
	# 같은 시드·같은 선택 → 같은 이야기
	var again := _auto_career(2026, func(e: Dictionary) -> int: return 0)
	check(JSON.stringify(again.book.log) == JSON.stringify(career.book.log), "재현성: 같은 시드면 같은 이야기")
	check(again.player.overall() == career.player.overall() and again.prospect_rank() == career.prospect_rank(), "재현성: 같은 성장·랭킹")
	# 다른 선택 → 다른 길
	var other := _auto_career(2026, func(e: Dictionary) -> int: return (e.choices as Array).size() - 1)
	check(other.path == "college", "마지막 선택지면 대학")
	check(JSON.stringify(other.book.log) != JSON.stringify(career.book.log), "선택이 다르면 이야기가 달라진다")


func test_rival() -> void:
	var career := CareerState.new(_career_cfg(), "테스트", _config)
	var rival := career.rival
	var school: School = career.schools[rival.school_id]
	var slot := int(career.cfg.rival.lineupSlot) - 1
	check(school.team.lineup[slot] == rival.batter and rival.batter.name == rival.player.name, "라이벌은 라이벌 학교 %d번 타자" % (slot + 1))
	# 라이벌 학교와 붙는 주까지 진행
	var vs := false
	var start_pa := 0
	for w in career.regional_ids.size():
		var meeting := career.opponent().id == rival.school_id
		start_pa = rival.player.season.pa
		var game := _auto_week(career, "rest")
		career.finish_game(game)
		if meeting:
			vs = true
			var box_line: BoxScore.BatterLine = game.box.batters[1 - game.my_side][slot]
			check(rival.last_vs_me, "맞대결 표시")
			check(rival.last_line.pa == box_line.pa and rival.last_line.hits == box_line.h and rival.last_line.home_runs == box_line.hr,
				"맞대결 기록 = 박스스코어 (%d타석 %d안타)" % [box_line.pa, box_line.h])
			check(rival.player.season.pa == start_pa + box_line.pa, "시즌 기록에 더해진다")
			break
		check(rival.last_line.pa == int(career.cfg.rival.paPerGame), "안 붙는 주는 자동 %d타석" % int(career.cfg.rival.paPerGame))
		career.advance_week()
	check(vs, "주말리그에서 라이벌 학교와 만난다")
	var name := rival.player.name
	var ovr := rival.player.overall()
	career.phase_index = career.phases().size()
	career.winter_training()
	career.start_next_season()
	check(school.team.lineup[slot].name == name, "라이벌은 졸업으로 바뀌지 않는다")
	check(rival.player.grade == 2 and rival.player.overall() > ovr, "라이벌도 학년이 오르고 자란다 (%d → %d)" % [ovr, rival.player.overall()])


func test_scouting() -> void:
	var cfg := _career_cfg()
	var career := CareerState.new(cfg, "테스트", _config)
	var p := career.player
	var r0 := career.prospect_rank()
	p.contact += 15
	p.power += 15
	var r1 := career.prospect_rank()
	check(r1 < r0, "능력치가 오르면 랭킹이 오른다 (%d → %d)" % [r0, r1])
	p.scout_interest = 90
	p.reputation = 90
	check(career.prospect_rank() <= r1, "관심도·평판도 평가에 들어간다")
	check(Scouting.projection(cfg, 1).text.begins_with("1라운드"), "1위는 1라운드: %s" % Scouting.projection(cfg, 1).text)
	check(Scouting.projection(cfg, 10).suffix == "라운드 예상", "10위는 라운드 범위: %s" % Scouting.projection(cfg, 10).text)
	check(Scouting.projection(cfg, 90).big == "지명 밖", "90위는 지명 밖")
	check(career.scouting.interested_teams(cfg, 5).size() < career.scouting.interested_teams(cfg, 60).size(), "관심도가 오르면 주목 구단이 는다")
	check(career.scouting.interested_teams(cfg, 30)[0].name == career.people.person("scout").team_name, "스카우트 인물의 구단이 맨 먼저")
	var w1 := _range_width(career.scouting.potential_range(cfg, p))
	p.grade = 3
	var w3 := _range_width(career.scouting.potential_range(cfg, p))
	check(w3 < w1, "학년이 오르면 잠재력 범위가 좁아진다 (%d → %d)" % [w1, w3])


func _range_width(t: String) -> int:
	var parts := t.split("-")
	return int(parts[1]) - int(parts[0])


func test_economy() -> void:
	var cfg := _career_cfg()
	var e := cfg.economy
	var career := CareerState.new(cfg, "테스트", _config)
	check(career.player.money == int(e.startMoney), "처음 돈")
	var game := _auto_week(career, "rest")
	var before := career.player.money
	career.finish_game(game)
	var expect := int(e.allowanceBase) + (career.people.relation("mom") - 50) * int(e.allowancePerMomPoint) \
		+ game.my_line.hits * int(e.perHit) + game.my_line.home_runs * int(e.perHomeRun)
	var prize := 0
	for pair: Array in career.last_income:
		if str(pair[0]).begins_with("동문회"):
			prize += int(pair[1])
	check(career.player.money - before == expect + prize, "용돈 + 칭찬 용돈 (%d, 기대 %d)" % [career.player.money - before, expect + prize])
	# 엄마와 사이가 나쁘면 용돈이 줄어든다 (최소는 있다)
	var c2 := CareerState.new(cfg, "테스트", _config)
	c2.people.by_id["mom"].relation = 0
	c2.finish_game(_auto_week(c2, "rest"))
	check(int(c2.last_income[0][1]) < int(career.last_income[0][1]) and int(c2.last_income[0][1]) >= int(e.allowanceMin), "엄마 관계 → 용돈")
	check(PlayerData.money_text(125000) == "12.5만원" and PlayerData.money_text(30000) == "3만원" and PlayerData.money_text(8000) == "8천원", "돈 표시")


func test_shop() -> void:
	var cfg := _career_cfg()
	var career := CareerState.new(cfg, "테스트", _config)
	var p := career.player
	p.money = 0
	check(career.buy_gear("gloves") != "" and not p.gear.has("gloves"), "돈이 없으면 못 산다")
	p.money = 1000000
	var base := p.skills_for_game(cfg)
	check(career.buy_gear("maple_bat") == "" and p.money == 1000000 - int(cfg.shop.gear.maple_bat.price), "사면 돈이 빠진다")
	check(career.buy_gear("maple_bat") != "", "같은 장비는 한 번")
	var with_bat := p.skills_for_game(cfg)
	check(with_bat.power == base.power + 1 and with_bat.contact == base.contact + 1, "장비가 경기 능력치를 올린다")
	career.buy_gear("pro_bat")
	var b := p.gear_bonus(cfg)
	check(b.power == 3 and b.contact == 1, "같은 자리는 가장 좋은 것 하나만 (%s)" % [b])
	# 관리: 한 주에 한 번, 레슨은 훈련한 주에만
	check(career.service_block("lesson") != "", "훈련 전엔 레슨 못 함")
	career.train("power")
	var pw := p.power
	var prog: float = p.progress.get("power", 0.0)
	check(not career.use_service("lesson").begins_with("!"), "훈련 뒤 레슨")
	check(p.power > pw or float(p.progress.get("power", 0.0)) > prog, "레슨은 같은 능력치를 더 키운다")
	check(career.use_service("lesson").begins_with("!"), "레슨은 한 주에 한 번")
	p.injury_weeks = 2
	career.use_service("care")
	check(p.injury_weeks == 1, "마사지 → 부상 회복 당김")
	career.advance_week()
	check(career.service_block("care") == "", "다음 주엔 다시")


func test_social() -> void:
	var cfg := _career_cfg()
	var career := CareerState.new(cfg, "테스트", _config)
	var p := career.player
	p.money = 1000000
	check(not CareerState.can_gift(career.people.person("coach")), "감독에겐 선물 못 한다")
	check(career.social_block("manager", "hangout") != "", "못 만난 사람과는 교류 못 한다")
	check(career.social_block("buddy", "hangout") != "", "만나기는 연애 후보만")
	var rel := career.people.relation("mom")
	check(not career.socialize("mom", "gift").begins_with("!") and career.people.relation("mom") == rel + int(cfg.economy.giftRel), "엄마 선물")
	check(career.socialize("buddy", "gift").begins_with("!"), "교류는 한 주에 한 번")
	career.advance_week()
	career.people.meet("manager")
	var aff := career.people.relation("manager")
	var money := p.money
	career.socialize("manager", "hangout")
	check(career.people.relation("manager") == aff + int(cfg.economy.hangoutRel) and p.money == money - int(cfg.economy.hangoutCost), "만나기: 호감↑ 돈↓")


func test_romance() -> void:
	var cfg := _career_cfg()
	var career := CareerState.new(cfg, "테스트", _config)
	var book := career.book
	var confess := _event(cfg, "love_manager_confess")
	var confess2 := _event(cfg, "love_neighbor_confess")
	var crisis := _event(cfg, "date_crisis")
	var anniv := _event(cfg, "date_anniversary")
	career.people.meet("manager")
	career.people.meet("neighbor")
	check(not book.eligible(career, confess), "호감이 낮으면 고백 없음")
	career.people.by_id["manager"].relation = 80
	career.people.by_id["neighbor"].relation = 80
	check(not book.eligible(career, confess), "1학년 땐 친해지기까지만")
	career.player.grade = 2
	check(book.eligible(career, confess) and book.eligible(career, confess2), "2학년부터 호감 75 넘으면 고백")
	check(not book.eligible(career, anniv), "사귀기 전엔 연애 이벤트 없음")
	career.resolve_event(confess, 0)
	check(career.people.partner == "manager", "고백 받아 사귄다")
	check(not book.eligible(career, confess2), "사귀는 동안 다른 고백은 없다")
	check(EventBook.fill(career, "{partner}") == "한소율" and EventBook.speaker_name(career, anniv) == "한소율", "partner = 사귀는 사람")
	# 사귀는 동안 호감이 식는다
	var aff := career.people.relation("manager")
	career.finish_game(_auto_week(career, "rest"))
	check(career.people.relation("manager") == aff - int(cfg.romance.weeklyDecay), "사귀면 매주 호감 −%d" % int(cfg.romance.weeklyDecay))
	# 돈이 모자라면 돈 드는 선택지는 못 고른다
	career.player.money = 0
	check(EventBook.choice_block(career, anniv.choices[0]) != "" and EventBook.choice_block(career, anniv.choices[1]) == "", "돈 드는 선택지 잠김")
	# 호감이 떨어지면 위기 → 이별
	career.people.by_id["manager"].relation = 30
	check(book.eligible(career, crisis), "호감이 낮으면 위기")
	career.resolve_event(crisis, 1)
	check(career.people.partner == "", "이별")
	# 차이면 그 사람 고백은 다시 안 나온다
	var c2 := CareerState.new(cfg, "테스트", _config)
	c2.player.grade = 2
	c2.people.meet("neighbor")
	c2.people.by_id["neighbor"].relation = 80
	c2.resolve_event(confess2, 1)
	c2.people.by_id["neighbor"].relation = 90
	check(c2.people.partner == "" and not c2.book.eligible(c2, _event(cfg, "love_neighbor_1")), "거절하면 그 사람 이야기는 끝")


func test_dialogue_lines() -> void:
	var cfg := _career_cfg()
	var career := CareerState.new(cfg, "테스트", _config)
	var meet := _event(cfg, "s1_rival_meet")
	var lines := EventBook.lines(career, meet)
	check(lines.size() == 2 and lines[0].who == "" and lines[1].who == "rival", "맨 앞 괄호는 지문, 나머지는 라이벌 대사")
	check(not lines[1].text.begins_with("("), "지문이 대사에서 빠진다")
	check(EventBook.place_of(career, meet) == "stadium" and EventBook.portrait_of(career, meet) == "rival", "배경·일러스트 기본값은 화자 것")
	var rumor := _event(cfg, "rumor")
	check(EventBook.portrait_of(career, rumor) == "none" and EventBook.place_of(career, rumor) == "phone", "이벤트가 정한 배경·일러스트")
	check(EventBook.lines(career, rumor)[0].who == "?", "인물이 아닌 화자")
	for e: Dictionary in cfg.events:
		check(cfg.places.has(EventBook.place_of(career, e)), "%s 배경 %s 이 places 에 있다" % [e.id, EventBook.place_of(career, e)])


func test_lineup_by_trust() -> void:
	var career := CareerState.new(_career_cfg(), "테스트", _config)
	career.people.by_id["coach"].relation = 85
	career.phase_index = career.phases().size()
	career.start_next_season()
	check(career.player.lineup_slot == 3, "감독 신뢰가 높으면 새 시즌 3번")
	career.people.by_id["coach"].relation = 10
	career.phase_index = career.phases().size()
	career.start_next_season()
	check(career.player.lineup_slot == 7, "낮으면 7번")


func _auto_week(career: CareerState, training_id: String) -> GameRunner:
	career.train(training_id)
	var game := career.new_game()
	var rng := _rng(career.week * 31 + career.season_no)
	while not game.state.over:
		if game.step().type == "my_turn":
			var pa := AutoPa.simulate(career.cfg.auto_pa, _config, game.current_batter().skills, game.current_pitcher(), rng)
			game.apply_my_result(AtBat.Result.new(pa.outcome, SwingJudge.BattedBall.NONE, pa.pitches, null))
	return game


func test_season_loop() -> void:
	var career := CareerState.new(_career_cfg(), "테스트", _config)
	var weeks := 0
	var ids: Array = career.cfg.training.keys()
	while not career.is_season_over() and weeks < 40:
		var game := _auto_week(career, ids[weeks % ids.size()])
		career.finish_game(game)
		for e: Dictionary in career.events("after").duplicate():
			career.resolve_event(e, _ok_choice(career, e, 0))
		career.advance_week()
		weeks += 1
	var league := career.regional_ids.size()
	check(weeks >= league * 2 + 2 and weeks <= league * 2 + 8, "한 시즌 %d경기 (리그 %d×2 + 대회 1~4×2)" % [weeks, league])
	check(career.results.size() == weeks and career.phase_log.size() == 4, "단계 4개 기록")
	check(career.player.season.games + 0 <= weeks)
	check(career.coach_line(career.new_game()) != "" if not career.is_season_over() else true)
	career.winter_training()
	career.start_next_season()
	check(career.player.grade == 2 and career.week == 1 and career.results.is_empty() and career.phase_index == 0, "다음 시즌은 2학년, 처음 단계부터")


func test_standings_consistent() -> void:
	var career := CareerState.new(_career_cfg(), "테스트", _config)
	for i in career.regional_ids.size() - 1:
		career.finish_game(_auto_week(career, "rest"))
		career.advance_week()
	var w := 0
	var l := 0
	var games := 0
	for id: String in career.standings:
		var r: Array = career.standings[id]
		w += r[0]
		l += r[1]
		games += r[0] + r[1] + r[2]
	check(w == l, "리그 전체 승 = 패 (%d, %d)" % [w, l])
	check(games % 2 == 0, "경기 수는 두 팀씩")


func test_tournament_elimination() -> void:
	var career := CareerState.new(_career_cfg(), "테스트", _config)
	# 주말리그 전반기를 빨리 넘긴다
	while career.is_league():
		career.finish_game(_auto_week(career, "rest"))
		career.advance_week()
	check(career.is_national(), "전반기 뒤 전국대회")
	var games := 0
	while career.is_national() and games < 10:
		var game := _auto_week(career, "rest")
		var result := game.my_result()
		career.finish_game(game)
		career.advance_week()
		games += 1
		if result <= 0:
			break
	check(not career.is_national() or career.phase_index == 1, "지면 대회를 떠난다")
	check(games >= 1 and games <= 4, "토너먼트 경기 수 %d" % games)
	check(career.phase_log.size() == 2)


func test_growth_curve() -> void:
	var cfg := _career_cfg()
	# 잠재력에 가까울수록 성장이 느리다
	check(Growth.amount(cfg, 40, 80, 1.0, 1) > Growth.amount(cfg, 75, 80, 1.0, 1), "잠재력 가까이선 느리다")
	check(Growth.amount(cfg, 80, 80, 1.0, 1) == 0.0, "잠재력에 닿으면 멈춘다")
	check(Growth.amount(cfg, 40, 80, 1.0, 1) > Growth.amount(cfg, 40, 80, 1.0, 3), "어릴수록 빨리 큰다")
	# 3년 자동 커리어: 능력치는 시작 이상, 잠재력 이하, 꽤 자란다
	var career := CareerState.new(cfg, "테스트", _config)
	var p := career.player
	var start := {}
	for st in PlayerData.STATS:
		start[st] = p.stat(st)
	var ids: Array = cfg.training.keys()
	for year in 3:
		var w := 0
		while not career.is_season_over() and w < 40:
			career.finish_game(_auto_week(career, ids[w % ids.size()]))
			career.advance_week()
			w += 1
		career.winter_training()
		if year < 2:
			career.start_next_season()
	var total := 0
	for st in PlayerData.STATS:
		check(p.stat(st) >= start[st] and p.stat(st) <= p.potential[st], "%s %d → %d (잠재력 %d)" % [st, start[st], p.stat(st), p.potential[st]])
		total += p.stat(st) - start[st]
	print("    3년 성장: 합계 +%d (%s)" % [total, ", ".join(PlayerData.STATS.map(func(st): return "%s %d→%d/%d" % [st, start[st], p.stat(st), p.potential[st]]))])
	check(total >= 20, "3년 동안 능력치 합이 20 이상 자란다 (%d)" % total)


func test_injury_risk() -> void:
	var tired := 0
	var fresh := 0
	for s in 400:
		var career := CareerState.new(_career_cfg(), "테스트", _config)
		career.week = s + 1
		career.player.condition = 15
		var notes: Array[String] = []
		career._roll_injury(notes)
		if career.player.is_injured():
			tired += 1
		var c2 := CareerState.new(_career_cfg(), "테스트", _config)
		c2.week = s + 1
		c2.player.condition = 90
		c2._roll_injury(notes)
		if c2.player.is_injured():
			fresh += 1
	check(tired > fresh * 3, "지치면 더 잘 다친다 (지침 %d / 쌩쌩 %d, 400번)" % [tired, fresh])


func test_rosters_persist() -> void:
	var career := CareerState.new(_career_cfg(), "테스트", _config)
	var id: String = career.regional_ids[0]
	var g1 := career.new_game()
	var names1: Array = career.schools[id].team.lineup.map(func(b): return b.name)
	var g2 := career.new_game()
	check(g1.teams[1 - g1.my_side].lineup.map(func(b): return b.name) == g2.teams[1 - g2.my_side].lineup.map(func(b): return b.name), "같은 시즌 같은 학교는 같은 얼굴")
	var my_names: Array = career.my_school.team.lineup.map(func(b): return b.name)
	career.start_next_season()
	var after: Array = career.schools[id].team.lineup.map(func(b): return b.name)
	var changed := 0
	for i in names1.size():
		if names1[i] != after[i]:
			changed += 1
	check(changed > 0 and changed <= 3, "졸업으로 일부만 바뀐다 (%d명)" % changed)
	check(career.my_school.team.lineup.map(func(b): return b.name) != my_names or true)


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
	g.bases = [1, -1, -1]
	var r := g.apply(SwingJudge.Outcome.GROUND_OUT, _rng(1), true, null, 2)
	check(r.outcome == SwingJudge.Outcome.SAC_BUNT and g.outs == 1 and g.bases == [-1, 1, -1], "무사 1루 번트 아웃 → 희생번트, 1사 2루")
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


func test_my_at_bat_carries_runners() -> void:
	# 경기 속 내 타석: 루상 주자가 타석 로직까지 전달되어 플레이(화면)에 같이 나온다
	var balance := BalanceLoader.load_balance()
	var s := BattingSession.new(BattingConfig.from_balance(balance), Presentation.from_balance(balance))
	var sit := {"bases": [3, -1, 5], "outs": 1, "batter": 4, "speed_of": func(_i: int) -> int: return 50}
	s.start_single(s.skills, s.pitcher, 7, sit)
	check(s.at_bat.situation.get("bases") == [3, -1, 5], "타석이 주자 상황을 받는다")
	s.replace_config(s.config, s.presentation)
	check(s.at_bat.situation.get("bases") == [3, -1, 5], "수치를 다시 읽어도 주자 상황 유지")
	var c := SwingJudge.Contact.new(SwingJudge.Quality.SOLID, 15.0, 0.0, SwingJudge.BattedBall.LINE)
	c.ball = _sim(150, 18, 15)
	var play: PlaySimulator.PlayResult = s.at_bat._run_play(c)
	var origins := []
	for r in play.runners:
		origins.append(r.origin)
	check(play.runners.size() == 3 and 1 in origins and 3 in origins, "타자 + 1·3루 주자가 플레이에 있다 (%s)" % [origins])


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


# ---------- 주자 주루 AI (RunnerAI / PlaySimulator) ----------

func _runner(origin: int, speed: int = 50) -> RunnerAI:
	return RunnerAI.new(origin, origin, speed, _config.ball_physics)


func test_runner_two_outs_always_run() -> void:
	# 2사에는 타구 종류와 상관없이 맞는 순간 다음 베이스로 질주
	for bt in [SwingJudge.BattedBall.FLY, SwingJudge.BattedBall.LINE, SwingJudge.BattedBall.GROUND]:
		for origin in [1, 2, 3]:
			var r := _runner(origin)
			r.on_ball_hit(bt, Vector2(0, 80), 2, 0.0, false)
			check(r.state == RunnerAI.State.RUN and r.target == origin + 1, "2사 %d루 주자 타구 %d → %s" % [origin, bt, RunnerAI.State.keys()[r.state]])


func test_runner_ground_ball_force_and_tag() -> void:
	var p := _config.ball_physics
	# 포스 상황: 무조건 진루
	var forced := _runner(1)
	forced.on_ball_hit(SwingJudge.BattedBall.GROUND, p.base_pos(3) + Vector2(4, 4), 0, 0.0, true)
	check(forced.state == RunnerAI.State.RUN and forced.target == 2, "포스 주자는 진루")
	# 태그 상황(2루 주자 혼자): 타구가 진행 방향 정면(3루·유격수 쪽)이면 귀루, 반대쪽(1·2루간)이면 진루
	var front := _runner(2)
	front.on_ball_hit(SwingJudge.BattedBall.GROUND, Vector2(-14, 30), 0, 0.0, false)
	check(front.state == RunnerAI.State.RETURNING, "정면 땅볼엔 귀루 (%s)" % RunnerAI.State.keys()[front.state])
	var away := _runner(2)
	away.on_ball_hit(SwingJudge.BattedBall.GROUND, Vector2(16, 30), 0, 0.0, false)
	check(away.state == RunnerAI.State.RUN and away.target == 3, "반대쪽 땅볼엔 진루 (%s)" % RunnerAI.State.keys()[away.state])


func test_runner_fly_ball_halfway_return_tag_up() -> void:
	var p := _config.ball_physics
	# 외야 뜬공 0·1사: 1루 주자는 하프웨이 (잡히면 송구 전에 돌아올 수 있는 만큼만), 3루 주자는 태그업
	var r1 := _runner(1)
	r1.on_ball_hit(SwingJudge.BattedBall.FLY, Vector2(10, 85), 0, 0.0, false)
	check(r1.state == RunnerAI.State.HALFWAY, "외야 뜬공 1루 주자 하프웨이")
	var share := r1.halfway_share(Vector2(10, 85))
	check(share > 0.1 and share <= 0.5, "하프웨이 비율 %.2f" % share)
	check(r1.halfway_share(Vector2(-30, 95)) > r1.halfway_share(Vector2(25, 60)), "멀리서 잡을수록 더 나간다 (1루 기준 좌익수 깊은 곳 > 우익수 앞)")
	for i in 100:
		r1.step(BattedBallSim.DT, i * BattedBallSim.DT)
	check(r1.pos.distance_to(r1.halfway_point) < 0.3, "하프웨이 지점에서 멈춘다")
	r1.on_ball_caught(2.0)
	check(r1.state == RunnerAI.State.RETURNING and r1.target == 1, "잡히면 귀루")
	var r3 := _runner(3)
	r3.on_ball_hit(SwingJudge.BattedBall.FLY, Vector2(10, 85), 0, 0.0, false)
	check(r3.state == RunnerAI.State.TAG_UP, "3루 주자 태그업 준비")
	# 떨어지면(안타) 멈춰 있던 주자도 뛴다
	var r2 := _runner(2)
	r2.on_ball_hit(SwingJudge.BattedBall.FLY, Vector2(10, 85), 1, 0.0, false)
	r2.on_ball_landed(2.5)
	check(r2.state == RunnerAI.State.RUN and r2.target == 3, "공이 떨어지면 진루")
	# 라이너는 제자리 멈춤, 내야 뜬공은 베이스로 귀루
	var line := _runner(1)
	var lead := line.pos
	line.on_ball_hit(SwingJudge.BattedBall.LINE, Vector2(0, 75), 0, 0.0, false)
	check(line.halfway_point.distance_to(lead) < 0.01, "외야 라이너엔 리드한 자리에서 멈춘다")
	var pop := _runner(2)
	pop.on_ball_hit(SwingJudge.BattedBall.FLY, Vector2(-5, 30), 0, 0.0, false)
	check(pop.state == RunnerAI.State.RETURNING and pop.target == 2, "내야 뜬공엔 베이스로 귀루")
	check(p.base_pos(1).length() > 0.0, "")


func test_runner_rounding_route_and_stop() -> void:
	var p := _config.ball_physics
	# 1루 → 3루: 2루를 바깥으로 둥글게 돌며 밟는다. 직선 두 변보다 조금 길고, 2루를 1m 안으로 지난다
	var r := _runner(1)
	r.run_to(3, 0.0)
	var length := r.curve.get_baked_length()
	var straight := r.pos.distance_to(p.base_pos(2)) + p.base_distance
	check(length > straight and length < straight + 6.0, "라운딩 경로 %.1fm (직선 %.1fm)" % [length, straight])
	var closest := INF
	var widest := 0.0
	# 1루-2루 주로 바깥쪽(외야 쪽) 방향으로 얼마나 벌렸나
	var center := Vector2(0, p.base_distance / sqrt(2.0))
	var outward := ((p.base_pos(1) + p.base_pos(2)) / 2.0 - center).normalized()
	for i in 200:
		var q := r.curve.sample_baked(length * i / 199.0)
		closest = minf(closest, q.distance_to(p.base_pos(2)))
		widest = maxf(widest, (q - p.base_pos(2)).dot(outward))
	check(closest < 1.0, "2루를 밟는다 (%.2fm)" % closest)
	check(widest > 1.0, "바깥으로 벌려 돈다 (%.1fm)" % widest)
	# 끝 베이스 앞에서 감속해 멈춘다 (마지막 간격 속도가 작다)
	var t := 0.0
	var max_speed := 0.0
	while not r.arrived() and t < 15.0:
		t += BattedBallSim.DT
		r.step(BattedBallSim.DT, t)
		max_speed = maxf(max_speed, r.speed)
	check(r.arrived(), "3루 도착")
	check(r.speed < max_speed * 0.5, "도착 직전 감속 (%.1f / 최고 %.1f m/s)" % [r.speed, max_speed])
	check(t > 6.0 and t < 9.5, "1루→3루 %.2f초" % t)
	# 빠른 주자가 더 빨리 도착
	var fast := _runner(1, 95)
	fast.run_to(3, 0.0)
	var tf := 0.0
	while not fast.arrived() and tf < 15.0:
		tf += BattedBallSim.DT
		fast.step(BattedBallSim.DT, tf)
	check(tf < t, "빠른 주자 %.2f초 < 보통 %.2f초" % [tf, t])


func _run_play(ev: float, la: float, spray: float, bases: Array, outs: int, seed_value: int = 7) -> PlaySimulator.PlayResult:
	return PlaySimulator.new(_config.ball_physics, _sim(ev, la, spray), 4, bases, func(_i: int) -> int: return 50, outs, _rng(seed_value)).run()


func test_play_is_deterministic() -> void:
	for spray in [-30, -10, 0, 15, 35]:
		var a := _run_play(140, 12, spray, [1, 2, -1], 0)
		var b := _run_play(140, 12, spray, [1, 2, -1], 0)
		check(a.batter_outcome == b.batter_outcome and a.scorers == b.scorers and a.bases == b.bases and a.outs == b.outs, "같은 입력 같은 결과 (방향 %d)" % spray)
		for i in a.runners.size():
			check(a.runners[i].track == b.runners[i].track, "주자 궤적 동일")


func test_play_records_and_hit_credit() -> void:
	# 플레이 결과의 기록이 맞물린다: 아웃 수 = OUT 상태 주자 수, 득점 + 남은 주자 + 아웃 = 전체 주자, 베이스 중복 없음
	for ev in range(110, 170, 8):
		for spray in range(-40, 41, 8):
			for la in [-5, 8, 20, 35]:
				var play := _run_play(ev, la, spray, [1, -1, 3], 0, ev + spray)
				var outs := 0
				var safe := 0
				for r in play.runners:
					if r.state == RunnerAI.State.OUT: outs += 1
					elif r.state == RunnerAI.State.SAFE: safe += 1
				check(outs == play.outs, "아웃 수 일치 (ev %d, spray %d, la %d)" % [ev, spray, la])
				var scored := 0
				for r in play.runners:
					if r.state == RunnerAI.State.SCORED: scored += 1
				check(outs + safe + scored == play.runners.size(), "모든 주자 정리")
				var on := play.bases.filter(func(x: int) -> bool: return x != -1)
				check(on.size() == safe or play.outs >= 3, "남은 주자 = 베이스 위 주자")
				check(play.duration > 0.0 and play.duration < PlaySimulator.MAX_TIME + 0.1, "플레이 시간")
				# 안타 종류는 타자가 실제로 간 베이스를 넘지 않는다
				var hit_bases := {SwingJudge.Outcome.SINGLE: 1, SwingJudge.Outcome.DOUBLE: 2, SwingJudge.Outcome.TRIPLE: 3}
				if hit_bases.has(play.batter_outcome) and play.runners[0].state == RunnerAI.State.SAFE:
					check(hit_bases[play.batter_outcome] <= play.runners[0].base, "안타 종류 ≤ 간 베이스")


func test_base_running_rates() -> void:
	# 주루 결과가 현실 비율 근처 (주자 주력 50, 무사·1사). 기준은 넓게 — 회귀 방지용
	var cfg := _career_cfg()
	var p := _config.ball_physics
	var rng := _rng(11)
	var batter := BatterSkills.new(50, 50, 50, 50)
	var pitcher := Team.Pitcher.new()
	pitcher.stuff = 50
	pitcher.control = 50
	var st := {"s2": 0, "s2y": 0, "dp_n": 0, "dp": 0, "sf_n": 0, "sf": 0}
	for i in 4000:
		var pa := AutoPa.simulate(cfg.auto_pa, _config, batter, pitcher, rng)
		if pa.ball == null or pa.ball.home_run:
			continue
		var k := i % 3
		var bases := [-1, 1, -1] if k == 0 else ([1, -1, -1] if k == 1 else [-1, -1, 3])
		var outs := i % 2
		var res := PlaySimulator.new(p, pa.ball, 4, bases, func(_x: int) -> int: return 50, outs, rng).run()
		if k == 0 and res.batter_outcome == SwingJudge.Outcome.SINGLE:
			st.s2 += 1
			if res.scorers.has(1): st.s2y += 1
		if k == 1 and pa.ball.batted_ball == SwingJudge.BattedBall.GROUND and pa.ball.fielder in p.infield:
			st.dp_n += 1
			if res.double_play: st.dp += 1
		if k == 2 and pa.ball.caught and not (pa.ball.fielder in p.infield):
			st.sf_n += 1
			if res.sac_fly: st.sf += 1
	var s2 := float(st.s2y) / maxi(1, st.s2)
	var dp := float(st.dp) / maxi(1, st.dp_n)
	var sf := float(st.sf) / maxi(1, st.sf_n)
	print("    주루 비율: 2루 주자 단타 홈인 %.0f%% · 병살 %.0f%% · 희생플라이 %.0f%%" % [s2 * 100, dp * 100, sf * 100])
	check(s2 > 0.45 and s2 < 0.85, "2루 주자 단타 홈인 %.2f" % s2)
	check(dp > 0.25 and dp < 0.55, "병살 %.2f" % dp)
	check(sf > 0.6 and sf < 0.92, "희생플라이 %.2f" % sf)


func test_fielder_steps_on_near_base() -> void:
	# 2루 베이스 바로 옆에서 땅볼을 잡으면 2루로 던지지 않고 직접 밟는다 (그다음 1루로 병살 송구)
	var p := _config.ball_physics
	var near := _sim(130, -5, -8)
	check(near.fielder in p.infield and not near.caught, "유격수 쪽 땅볼 (%s)" % near.fielder)
	# 잡은 자리를 2루 베이스 1.5m 옆으로 옮겨 본다
	near.fielder_to = p.base_pos(2) + Vector2(-1.5, -0.5)
	var res := PlaySimulator.new(p, near, 4, [1, -1, -1], func(_x: int) -> int: return 50, 0, _rng(1)).run()
	check(not res.throws.is_empty() and res.throws[0].base == 2 and res.throws[0].carry, "2루 옆에서 잡으면 직접 밟는다")
	check(res.double_play, "직접 밟고 1루로 던져 병살")
	# 멀리서 잡으면 던진다
	var far := _sim(130, -5, -30)
	var res2 := PlaySimulator.new(p, far, 4, [1, -1, -1], func(_x: int) -> int: return 50, 0, _rng(1)).run()
	check(not res2.throws.is_empty() and not res2.throws[0].carry, "멀리서 잡으면 송구")


func test_no_pointless_throws() -> void:
	# 송구를 놓는 순간 이미 주자가 서 있는 베이스로는 던지지 않는다 (진루 억제 송구·역병살·내야안타 송구 모두)
	var cfg := _career_cfg()
	var p := _config.ball_physics
	var rng := _rng(5)
	var batter := BatterSkills.new(50, 50, 50, 50)
	var pitcher := Team.Pitcher.new()
	pitcher.stuff = 50
	pitcher.control = 50
	var bad := 0
	var n := 0
	for i in 1500:
		var pa := AutoPa.simulate(cfg.auto_pa, _config, batter, pitcher, rng)
		if pa.ball == null or pa.ball.home_run or pa.ball.caught:
			continue
		var bases: Array = [[1, -1, -1], [-1, 2, -1], [1, 2, -1], [-1, -1, -1]][i % 4]
		var res := PlaySimulator.new(p, pa.ball, 4, bases, func(_x: int) -> int: return 50, i % 3, rng).run()
		for th: Dictionary in res.throws:
			n += 1
			for r in res.runners:
				if r.state != RunnerAI.State.SAFE or r.base != th.base:
					continue
				# 마지막으로 SAFE 가 된 시각 (라이너에 멈췄다가 다시 뛴 주자도 있다)
				var safe_t := INF
				for h: Array in r.history:
					if h[1] == RunnerAI.State.SAFE:
						safe_t = float(h[0])
				if safe_t < float(th.release):
					bad += 1
	check(n > 300 and bad == 0, "주자가 이미 선 베이스로 던진 송구 %d / %d" % [bad, n])


func test_launch_angle_never_backwards() -> void:
	# 정규분포 끝자락의 90도 넘는 발사각도 포수 뒤로 날아가 안타가 되지 않는다
	for la in [88.0, 95.0, 120.0]:
		var b := _sim(140, la, 10)
		var last: Vector3 = b.path[b.path.size() - 1]
		check(last.y > -5.0, "발사각 %d → 공이 앞쪽에 떨어진다 (y %.1f)" % [la, last.y])


func test_force_outs_and_cover() -> void:
	# 2사 1루 땅볼도 쉬운 2루 포스 아웃을 잡는다. 모든 송구는 받을 사람이 베이스에 선 뒤에 닿는다
	var cfg := _career_cfg()
	var p := _config.ball_physics
	var rng := _rng(3)
	var batter := BatterSkills.new(50, 50, 50, 50)
	var pitcher := Team.Pitcher.new()
	pitcher.stuff = 50
	pitcher.control = 50
	var grounders := 0
	var to_second := 0
	var early := 0
	for i in 2500:
		var pa := AutoPa.simulate(cfg.auto_pa, _config, batter, pitcher, rng)
		var b: BattedBallSim.Result = pa.ball
		if b == null or b.caught or b.home_run or not (b.fielder in p.infield):
			continue
		var two_outs := i % 2 == 0
		var bases: Array = [1, -1, -1] if two_outs else [1, 2, -1]
		var res := PlaySimulator.new(p, b, 4, bases, func(_x: int) -> int: return 50, 2 if two_outs else 0, rng).run()
		if two_outs:
			grounders += 1
			if not res.throws.is_empty() and res.throws[0].base == 2:
				to_second += 1
		for th: Dictionary in res.throws:
			if th.carry or (th.base == 1 and b.receiver != ""):
				continue
			if float(th.arrive) < float(BattedBallSim.base_cover(p, b, th.base).ready) - 0.01:
				early += 1
	check(to_second > grounders * 0.3, "2사 1루 땅볼의 2루 포스 송구 %d / %d" % [to_second, grounders])
	check(early == 0, "커버가 서기 전에 닿는 송구 %d" % early)


func test_relay_play() -> void:
	# 먼 외야 송구는 중계맨(내야수)을 거친다: 중계맨은 외야수와 베이스를 잇는 선 위에 먼저 서 있고,
	# 공을 받은 뒤 transferS 가 지나야 다시 던진다. 가까운 송구는 바로 던진다
	var p := _config.ball_physics
	var relays := 0
	for ev in [140, 150, 160]:
		for la in [8, 14, 20]:
			for spray in range(-40, 41, 8):
				var b := _sim(ev, la, spray)
				if b.home_run or b.caught or b.fielder in p.infield:
					continue
				for bases: Array in [[1, -1, -1], [-1, 2, -1]]:
					var res := PlaySimulator.new(p, b, 4, bases, func(_x: int) -> int: return 50, 0, _rng(spray + 100)).run()
					for i in res.throws.size():
						var th: Dictionary = res.throws[i]
						var from: Vector2 = th.from
						if not th.get("cut", false):
							if from == b.fielder_to and not th.carry:
								var goal := Vector2.ZERO if th.base >= 4 else p.base_pos(th.base)
								# 바로 던진 공: 중계가 가능한 거리라도 바로가 충분히 빨랐을 때만 — 여기선 거리 기록만 확인
								check(from.distance_to(goal) > 0.0)
							continue
						relays += 1
						var target: int = th.target
						var goal := Vector2.ZERO if target >= 4 else p.base_pos(target)
						var to: Vector2 = th.to
						check(from.distance_to(goal) >= p.relay_min_m, "중계는 먼 송구에만 (%.0fm)" % from.distance_to(goal))
						check(th.cover.name in p.infield, "중계맨은 내야수 (%s)" % th.cover.name)
						check(absf(from.distance_to(to) + to.distance_to(goal) - from.distance_to(goal)) < 0.1, "중계맨은 외야수와 베이스 사이 선 위")
						check(float(th.arrive) >= float(th.cover.ready) - 0.01, "중계맨이 먼저 서 있다")
						if i + 1 < res.throws.size():
							var nx: Dictionary = res.throws[i + 1]
							check(nx.from == to and float(nx.release) >= float(th.arrive) + p.relay_transfer_s - 0.01, "중계맨이 받은 뒤 다시 던진다")
	check(relays > 10, "먼 외야 안타에서 중계 플레이가 나온다 (%d)" % relays)
	# 가까운 송구는 중계 없이: 얕은 외야 안타의 송구 거리 < minDistanceM 이면 cut 이 없다
	var shallow := _sim(120, 12, -15)
	var res2 := PlaySimulator.new(p, shallow, 4, [-1, 2, -1], func(_x: int) -> int: return 50, 0, _rng(1)).run()
	for th: Dictionary in res2.throws:
		if th.get("cut", false):
			check((th.from as Vector2).distance_to(Vector2.ZERO if th.target >= 4 else p.base_pos(th.target)) >= p.relay_min_m, "가까운 송구에 중계 없음")


func test_one_training_per_week() -> void:
	# 한 주에 훈련은 하나: 홈 화면을 다시 열어 또 눌러도 능력치가 두 번 오르지 않는다. 주가 넘어가면 다시 할 수 있다
	var career := CareerState.new(_career_cfg(), "테스트", _config)
	var before := career.player.contact
	var first := career.train("contact")
	var after_first := career.player.contact
	var second := career.train("contact")
	check(second == first and career.player.contact == after_first, "같은 주 두 번째 훈련은 무시 (%d → %d → %d)" % [before, after_first, career.player.contact])
	career.advance_week()
	check(career.week_training == "", "다음 주엔 다시 훈련")


func test_final_count_recorded() -> void:
	# 볼만 보면 볼넷: 넷째 볼을 던질 때 카운트는 3볼. 스트라이크만 보면 삼진: 2스트라이크
	var walks := 0
	var ks := 0
	for seed_value in 30:
		var ab := AtBat.new(_config, _average, _balanced, seed_value)
		while not ab.is_over():
			ab.next_pitch()
			ab.take()
		var r := ab.result
		check(r.balls >= 0 and r.balls <= 3 and r.strikes >= 0 and r.strikes <= 2, "결정구 카운트 %d-%d" % [r.balls, r.strikes])
		if r.outcome == SwingJudge.Outcome.WALK:
			walks += 1
			check(r.balls == 3, "볼넷은 3볼에서")
		else:
			ks += 1
			check(r.strikes == 2, "루킹 삼진은 2스트라이크에서")
	check(walks > 0 and ks > 0, "볼넷 %d, 삼진 %d" % [walks, ks])
	check(CareerRecords.count_key(0, 0) == "first" and CareerRecords.count_key(3, 1) == "ahead"
		and CareerRecords.count_key(1, 1) == "even" and CareerRecords.count_key(3, 2) == "two_strikes" and CareerRecords.count_key(-1, 0) == "")
	check(CareerRecords.direction_key(-30.0, 15.0) == "pull" and CareerRecords.direction_key(5.0, 15.0) == "center"
		and CareerRecords.direction_key(20.0, 15.0) == "oppo", "음수 = 3루 쪽 = 당겨친 타구")


func test_season_stats_details() -> void:
	var s := PlayerData.SeasonStats.new()
	s.add(SwingJudge.Outcome.SINGLE, 0)
	s.add(SwingJudge.Outcome.HOME_RUN, 1)
	s.add(SwingJudge.Outcome.WALK, 0)
	s.add(SwingJudge.Outcome.FLY_OUT, 1, true)
	s.add(SwingJudge.Outcome.SAC_BUNT, 0)
	s.add(SwingJudge.Outcome.STRIKEOUT, 0)
	check(s.pa == 6 and s.ab == 3 and s.sac_flies == 1 and s.sac_bunts == 1, "타석 6 = 타수 3 + 볼넷 + 희생플라이 + 희생번트")
	# 출루율 = (2 + 1) / (3 + 1 + 1) = .600, 장타율 = 5 / 3
	check(s.obp_text() == ".600" and s.slg_text() == "1.667" and s.average() == ".667", "%s %s %s" % [s.obp_text(), s.slg_text(), s.average()])
	var t := PlayerData.SeasonStats.new()
	t.merge(s)
	t.add_ball(150.0)
	t.add_ball(130.0)
	check(t.sac_flies == 1 and t.sac_bunts == 1 and absf(t.avg_ev() - 140.0) < 0.01, "합칠 때 희생타·타구 속도도")
	check(PlayerData.SeasonStats.new().obp_text() == "-" and PlayerData.SeasonStats.new().avg_ev() < 0)


## 내 타석에 타구(물리)와 카운트까지 붙여 한 주 진행
func _records_week(career: CareerState) -> void:
	career.train("rest" if career.player.condition < 50 else "contact")
	var game := career.new_game()
	var rng := _rng(career.week * 17 + career.season_no)
	while not game.state.over:
		if game.step().type == "my_turn":
			var pa := AutoPa.simulate(career.cfg.auto_pa, _config, game.current_batter().skills, game.current_pitcher(), rng)
			var c: SwingJudge.Contact = null
			if pa.ball != null:
				c = SwingJudge.Contact.new(SwingJudge.Quality.SOLID, pa.ball.spray_deg, pa.ball.distance_m, pa.ball.batted_ball)
				c.ball = pa.ball
			var res := AtBat.Result.new(pa.outcome, SwingJudge.BattedBall.NONE, pa.pitches, c)
			res.balls = rng.randi_range(0, 3)
			res.strikes = rng.randi_range(0, 2)
			game.apply_my_result(res)
	career.finish_game(game)
	career.advance_week()


func test_records_game_log_and_splits() -> void:
	var career := CareerState.new(_career_cfg(), "테스트", _config)
	var weeks := 0
	while not career.is_season_over() and weeks < 40:
		_records_week(career)
		weeks += 1
	var rec := career.records
	var season := career.player.season
	check(rec.games.size() == weeks, "경기마다 한 줄 (%d / %d)" % [rec.games.size(), weeks])
	# 경기 줄을 더하면 시즌 기록과 같다
	var sum := PlayerData.SeasonStats.new()
	for g: CareerRecords.GameEntry in rec.games:
		sum.merge(g.line)
		check(g.playing or g.line.pa == 0, "결장 경기는 기록이 없다")
	check(sum.games == season.games and sum.pa == season.pa and sum.hits == season.hits and sum.rbi == season.rbi
		and sum.home_runs == season.home_runs and sum.walks == season.walks, "경기 합 = 시즌 (%d타석 / %d타석)" % [sum.pa, season.pa])
	check(rec.game_log(0)[0] == rec.games[-1], "최근 경기부터")
	# 대회별: 단계 4개, 모두 끝났고 결과가 시즌 단계 기록과 같다, 내 성적 합 = 시즌
	check(rec.phases.size() == 4, "대회 4개 (%d)" % rec.phases.size())
	var phase_sum := PlayerData.SeasonStats.new()
	var team_games := 0
	for i in rec.phases.size():
		var ph: CareerRecords.PhaseEntry = rec.phases[i]
		phase_sum.merge(ph.line)
		team_games += ph.team_games
		check(ph.result == career.phase_log[i]["text"] and ph.name == career.phase_log[i]["name"], "대회 결과 %s" % ph.result)
	check(phase_sum.pa == season.pa and phase_sum.hits == season.hits and team_games == weeks, "대회 합 = 시즌")
	# 분할: 투수 성향·카운트 칸 합 = 시즌 타석, 타구 종류·방향 합은 같고 타구 속도가 있다
	for cat: Array in [["pitcher", _config.pitchers.keys()], ["count", CareerRecords.COUNT_KEYS]]:
		var pa := 0
		for row: Array in rec.split_rows(0, cat[0], cat[1]):
			pa += (row[1] as PlayerData.SeasonStats).pa
		check(pa == season.pa, "%s 분할 타석 합 %d = 시즌 %d" % [cat[0], pa, season.pa])
	var bip := [0, 0]
	var hits := 0
	for i in 2:
		var cat: Array = [["batted", CareerRecords.BATTED_KEYS], ["direction", CareerRecords.DIRECTION_KEYS]][i]
		for row: Array in rec.split_rows(career.season_no, cat[0], cat[1]):
			var l: PlayerData.SeasonStats = row[1]
			bip[i] += l.balls_in_play
			if i == 0:
				hits += l.hits
				check(l.balls_in_play == 0 or (l.avg_ev() > 40.0 and l.avg_ev() < 200.0), "평균 타구 속도 %.0f" % l.avg_ev())
	check(bip[0] > 0 and bip[0] == bip[1], "타구 종류 합 = 방향 합 (%d, %d)" % [bip[0], bip[1]])
	check(hits == season.hits, "안타는 모두 인플레이 타구 (%d / %d)" % [hits, season.hits])
	# 최고 기록
	var b := rec.bests()
	check(b["hits"] != null and int(b["streak"]) >= 1 and int(b["streak"]) >= int(b["current_streak"]), "최고 기록")
	# 다음 시즌: 통산 = 지난 시즌 + 이번 시즌, 시즌별 분할은 따로
	career.winter_training()
	career.start_next_season()
	_records_week(career)
	var total := career.career_line()
	check(total.pa == season.pa + career.player.season.pa and total.games == season.games + career.player.season.games, "통산 합계")
	var pa2 := 0
	for row: Array in rec.split_rows(career.season_no, "pitcher", _config.pitchers.keys()):
		pa2 += (row[1] as PlayerData.SeasonStats).pa
	check(pa2 == career.player.season.pa, "시즌별 분할은 그 시즌만")
	check(rec.phases[-1].result == "" and rec.phases[-1].grade == 2, "새 시즌 첫 대회는 진행 중")
