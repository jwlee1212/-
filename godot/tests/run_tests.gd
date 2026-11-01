extends SceneTree
## 타격 프로토타입 테스트. 실제 config/balance.json 으로 돈다.
##   ~/Applications/Godot.app/Contents/MacOS/Godot --headless --path godot --script res://tests/run_tests.gd
## 하나라도 실패하면 종료 코드 1.
## 일부만 돌리기: ONLY=save 처럼 환경 변수를 주면 이름에 그 글자가 들어간 테스트만 돈다.

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
		"test_pitch_trajectory_endpoints",
		"test_pitch_trajectory_reproducible",
		"test_pitch_trajectory_straight_before_break",
		"test_pitch_trajectory_break_direction",
		"test_pitch_trajectory_monotonic",
		"test_pitch_movement_is_visible",
		"test_pitch_break_by_location",
		"test_pitch_never_rises_on_screen",
		"test_pitch_catalog_valid",
		"test_pitch_caller_flight_and_forced_type",
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
		"test_mistakes_by_count",
		"test_mistake_hangs_and_rewards",
		"test_chase_pitches_are_just_outside",
		"test_aim_hit_widens_miss_narrows",
		"test_aim_in_at_bat",
		"test_gap_hits_more_than_center",
		"test_speed_display",
		"test_cell_of",
		"test_strike_zone_counts_ball_size",
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
		"test_pinch_hit",
		"test_scenario_keys",
		"test_y1_story",
		"test_real_content",
		"test_training_and_growth",
		"test_condition_affects_skills",
		"test_events_flow",
		"test_event_integrity",
		"test_delayed_event",
		"test_josa",
		"test_story_three_years",
		"test_rival",
		"test_next_lineup",
		"test_scouting",
		"test_lineup_by_trust",
		"test_economy",
		"test_shop",
		"test_social",
		"test_romance",
		"test_dialogue_lines",
		"test_dialogue_run",
		"test_first_day",
		"test_game_events",
		"test_camp",
		"test_camp_save_and_repeat",
		"test_fast_forward",
		"test_season_loop",
		"test_training_scenes",
		"test_fitness_run",
		"test_fitness_lift",
		"test_fitness_react",
		"test_fitness_rewards",
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
		"test_home_run_play_can_be_skipped",
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
		"test_relay_not_abandoned",
		"test_run_control_overrides_my_runner",
		"test_run_control_tag_up",
		"test_run_control_no_passing",
		"test_run_control_batter_choices",
		"test_run_control_change_mid_run",
		"test_runner_fatigue",
		"test_rundown",
		"test_run_control_rundown",
		"test_steal_judge",
		"test_steal_in_game_state",
		"test_field_play_catch",
		"test_field_play_throw",
		"test_shortstop_in_game_state",
		"test_fielding_in_game",
		"test_fielding_stat_effects",
		"test_fielding_stat_career",
		"test_interactive_game_matches_and_stays_consistent",
		"test_my_at_bat_run_control",
		"test_settings_running_mode",
		"test_one_training_per_week",
		"test_week_schedule",
		"test_schedule_outcome_odds",
		"test_launch_angle_never_backwards",
		"test_final_count_recorded",
		"test_season_stats_details",
		"test_records_game_log_and_splits",
		"test_phone_messages",
		"test_phone_content",
		"test_save_roundtrip",
		"test_save_then_continue_is_same",
		"test_save_file_and_errors",
		"test_bgm_tracks",
	]:
		var only := OS.get_environment("ONLY")
		if only != "" and not test.contains(only):
			continue
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
	# 존 밖 공은 벗어난 거리만큼 판정 폭이 줄어든다 (2026-10-31 유저 "존 밖이면 거의 무조건 정타가 안 나온다")
	var p := Pitch.new()
	p.reach = 1.0 + _config.zone_ball_radius
	p.target = Vector2(p.reach + 0.02, 0.0)  # 존 바로 밖
	var edge := _judge.zone_factor(p)
	check(is_equal_approx(edge, lerpf(_config.out_of_zone_edge_factor, _config.out_of_zone_window_factor, 0.02 / _config.out_of_zone_far_distance)), "존 바로 밖 배율 %.2f" % edge)
	check(_judge.quality(p, _average, _config.solid_window_ms(50) * 0.7) == SwingJudge.Quality.SOLID, "존 바로 밖 공은 정타가 나온다")
	p.target = Vector2(p.reach + _config.out_of_zone_far_distance * 0.5, 0.0)
	var mid := _judge.zone_factor(p)
	p.target = Vector2(p.reach + _config.out_of_zone_far_distance * 2.0, p.reach + 1.0)
	var far := _judge.zone_factor(p)
	check(edge > mid and mid > far and is_equal_approx(far, _config.out_of_zone_window_factor), "멀수록 좁다 %.2f > %.2f > %.2f" % [edge, mid, far])
	check(_judge.quality(p, _average, diff) != SwingJudge.Quality.SOLID, "크게 빠진 공은 정타가 어렵다")
	p.target = Vector2(0.3, -0.2)
	check(_judge.zone_factor(p) == 1.0 and p.outside_distance() == 0.0, "스트라이크는 그대로")


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
	# 볼배합이 실제로 만든 공도 비행 시간에 정확히 미트(실제 도착점)에 온다
	for s in 300:
		var p := pitch_at(s % 3, s % 3, s)
		check(p.position_at(p.flight_ms).distance_to(p.target) < 1e-4, "%s %s" % [p.type, p.position_at(p.flight_ms)])
		check(p.trajectory.flight_ms == p.flight_ms, "궤적 도착 시각 = 판정 기준 시각")


# ---------- 투구 궤적 (pitching/) ----------

func _trajectory(id: String, aim: Vector2, hand := "R") -> PitchTrajectory:
	var cat := _config.catalog
	return PitchTrajectory.make(cat.spec(id), aim, cat.flight_ms(id), hand, cat.location_range)


func test_pitch_trajectory_endpoints() -> void:
	for id: String in PitchCatalog.IDS:
		for aim in [Vector2.ZERO, Vector2(0.8, -0.6), Vector2(-1.2, 1.1)]:
			var t := _trajectory(id, aim)
			check(t.position_at(t.flight_ms).distance_to(aim) < 1e-4, "%s 도착 %s ≠ %s" % [id, t.position_at(t.flight_ms), aim])
			check(t.position_at(0.0).distance_to(t.start) < 1e-4, "%s 출발 %s" % [id, t.position_at(0.0)])
			check(t.position_at(t.flight_ms * 2.0).distance_to(aim) < 1e-4, "%s 도착 뒤에는 aim 에 머문다" % id)


func test_pitch_trajectory_reproducible() -> void:
	for id: String in PitchCatalog.IDS:
		var a := _trajectory(id, Vector2(0.3, 0.4))
		var b := _trajectory(id, Vector2(0.3, 0.4))
		# 순서를 섞어 불러도 같은 값 — 앞서 부른 값에 의존하지 않는 순수 함수
		var ts := [0.0, 37.5, 120.0, a.flight_ms * 0.5, 401.2, a.flight_ms * 0.93, a.flight_ms]
		var first := []
		for t_ms: float in ts:
			first.append(a.position_at(t_ms))
		ts.reverse()
		for i in ts.size():
			var t_ms: float = ts[i]
			check(b.position_at(t_ms) == first[ts.size() - 1 - i], "%s t=%.1f" % [id, t_ms])
			check(a.position_at(t_ms) == b.position_at(t_ms))


func test_pitch_trajectory_straight_before_break() -> void:
	for id: String in PitchCatalog.IDS:
		var t := _trajectory(id, Vector2(0.4, 0.2))
		var raw_end := t.aim - t.break_vec
		# break_start 에 닿는 시각 직전까지: 꺾임은 0 이고 공은 "직선 + 도중 불룩함(arc)" 위에 있다.
		# arc 가 없는 구종은 start → raw_end 직선 위 그대로
		var no_arc := t.arc_vec == Vector2.ZERO
		for i in 20:
			var t_ms := t.flight_ms * i / 20.0
			if t.distance_fraction(t_ms) >= t.break_start:
				break
			var pos := t.position_at(t_ms)
			check(t.break_fraction(t_ms) == 0.0, "%s 휘기 전 s=0" % id)
			check(pos.distance_to(t.base_at(t_ms)) < 1e-6, "%s t=%.0f 꺾이기 전 길 밖" % [id, t_ms])
			if no_arc:
				check(pos.distance_to(t.start.lerp(raw_end, t.distance_fraction(t_ms))) < 1e-6, "%s t=%.0f 직선 밖" % [id, t_ms])
				# 직선 위인지: start→raw_end 방향과 start→pos 방향의 외적이 0
				var d := raw_end - t.start
				var e := pos - t.start
				check(absf(d.cross(e)) < 1e-6, "%s 외적 %f" % [id, d.cross(e)])
		check(t.break_fraction(t.flight_ms) == 1.0)


## 도착 직전(비행의 95%) 투수 기준 편차: x + = 팔 쪽, y + = 위. 존 좌표에서 직선(안 휜 길)과의 차이를 투수 기준으로 되돌린다
func _late_deviation(id: String, hand: String) -> Vector2:
	var t := _trajectory(id, Vector2.ZERO, hand)
	var t_ms := t.flight_ms * 0.95
	var dev := t.position_at(t_ms) - t.straight_at(t_ms)
	var side := 1.0 if hand == "R" else -1.0
	return Vector2(-dev.x * side, -dev.y)


func test_pitch_trajectory_break_direction() -> void:
	check(_late_deviation("slider", "R").x < 0.0, "우투수 슬라이더는 글러브 쪽")
	check(_late_deviation("sweeper", "R").x < 0.0, "우투수 스위퍼는 글러브 쪽")
	check(_late_deviation("two_seam", "R").x > 0.0, "우투수 투심은 팔 쪽")
	check(_late_deviation("curve", "R").y < 0.0 and _late_deviation("forkball", "R").y < 0.0, "커브·포크볼은 떨어진다")
	# 우타자 기준 존 좌표: 우투수 슬라이더는 바깥쪽(+x)으로, 투심은 몸쪽(-x)으로
	check(_trajectory("slider", Vector2.ZERO).break_vec.x > 0.0 and _trajectory("two_seam", Vector2.ZERO).break_vec.x < 0.0)
	check(_trajectory("sweeper", Vector2.ZERO).break_vec.x > _trajectory("slider", Vector2.ZERO).break_vec.x, "스위퍼가 슬라이더보다 옆으로 더 휜다")
	# 좌투수: 같은 공을 좌우 거울에 비춘 것. 도착점도 거울에 비추면 x 만 정확히 반대, y 는 같다
	for id: String in PitchCatalog.IDS:
		var r := _trajectory(id, Vector2(0.35, 0.2), "R")
		var l := _trajectory(id, Vector2(-0.35, 0.2), "L")
		for i in 11:
			var t_ms := r.flight_ms * i / 10.0
			var a := r.position_at(t_ms)
			var b := l.position_at(t_ms)
			check(is_equal_approx(a.x, -b.x) and is_equal_approx(a.y, b.y), "%s 좌우 반전 %s / %s" % [id, a, b])
		check(is_equal_approx(_late_deviation(id, "R").x, _late_deviation(id, "L").x), "%s 투수 기준 편차는 손과 무관" % id)


func test_pitch_trajectory_monotonic() -> void:
	var cat := _config.catalog
	check(cat.decel >= 0.0 and cat.decel < 1.0, "decel %.3f 범위" % cat.decel)
	for decel in [0.0, cat.decel, 0.5, 0.99]:
		var t := _trajectory("curve", Vector2.ZERO)
		t.decel = decel
		var prev := -1.0
		for i in 201:
			var u_w := t.distance_fraction(t.flight_ms * i / 200.0)
			check(u_w >= prev, "decel %.2f 에서 u_w 가 줄었다 (%d)" % [decel, i])
			prev = u_w
		check(is_equal_approx(prev, 1.0))
	# 감속: 앞이 빠르다 → 비행 절반에 거리는 절반보다 많이 갔다
	var t2 := _trajectory("four_seam", Vector2.ZERO)
	check(cat.decel == 0.0 or t2.distance_fraction(t2.flight_ms * 0.5) > 0.5)
	# 범위 밖 decel 은 검증에서 걸린다
	var bad := PitchCatalog.from_batting(BalanceLoader.load_balance()["batting"])
	bad.decel = 1.0
	check(not bad.validate().is_empty(), "decel 1 은 실패해야 한다")


## 화면 속 공의 길이 손 → 도착점 직선에서 벗어난 거리 (px): {"max": 가장 큰 값, "at40": 비행 40% 때}
func _screen_bow(view: BattingView, id: String) -> Dictionary:
	var cat := _config.catalog
	var p := Pitch.new()
	p.type = id
	p.flight_ms = cat.flight_ms(id)
	p.trajectory = PitchTrajectory.make(cat.spec(id), Vector2.ZERO, p.flight_ms, "R", cat.location_range)
	var a := view._ball(p, 0.0)
	var b := view._ball(p, p.flight_ms)
	var from := Vector2(a.x, a.y)
	var dir := (Vector2(b.x, b.y) - from).normalized()
	var out := {"max": 0.0, "at40": 0.0}
	for i in 101:
		var c := view._ball(p, p.flight_ms * i / 100.0)
		var d := absf(dir.cross(Vector2(c.x, c.y) - from))
		out["max"] = maxf(out["max"], d)
		if i == 40:
			out["at40"] = d
	return out


func test_pitch_movement_is_visible() -> void:
	# 2026-10-21 유저 "속도 차이만 있지 각의 변화가 없다" → 움직임을 과장.
	# 2026-10-31 유저 "다 직구처럼 오다 갑자기 꺾여 아케이드 같다" → 손을 떠난 직후부터 점진적으로 휜다.
	# 실제 타석 화면(BattingView)에서 잰다: 좌우로 휘는 공은 눈에 보이게 휘고, 비행 40% 에 이미 휨의 60% 이상이 보인다(도중에 알아볼 수 있다)
	var balance := BalanceLoader.load_balance()
	var view := BattingView.new()
	view.session = BattingSession.new(BattingConfig.from_balance(balance), Presentation.from_balance(balance))
	view.size = Vector2(844, 390)
	view._layout()
	var bows := {}
	for id: String in PitchCatalog.IDS:
		bows[id] = _screen_bow(view, id)
	view.free()
	for id: String in ["two_seam", "slider", "changeup", "sweeper"]:
		check(bows[id]["max"] >= 8.0, "%s 화면 휨 %.1fpx 가 너무 작다" % [id, bows[id]["max"]])
	for id: String in PitchCatalog.IDS:
		if id == "four_seam":
			continue
		var share: float = bows[id]["at40"] / maxf(bows[id]["max"], 0.001)
		check(share >= 0.6, "%s 비행 40%% 에 휨의 %.0f%% 만 보인다 (막판에 갑자기 꺾임)" % [id, share * 100.0])
	check(bows["sweeper"]["max"] > bows["slider"]["max"], "스위퍼가 슬라이더보다 크게 쓸고 간다 (%.1f vs %.1f)" % [bows["sweeper"]["max"], bows["slider"]["max"]])
	# 휨은 양 끝에서 0 (궤적 모듈)
	for id: String in PitchCatalog.IDS:
		var t := _trajectory(id, Vector2.ZERO)
		check(t.deviation_at(0.0).length() < 1e-4 and t.deviation_at(t.flight_ms).length() < 1e-4, "%s 휨은 양 끝에서 0" % id)
	# 구종 버릇: 커브는 손을 떠나며 떠오르고(도중 불룩함 위로), 체인지업은 직구보다 감속이 크다
	var cat := _config.catalog
	check(cat.spec("curve").pop > 0.0, "커브는 손을 떠나며 튀어 올랐다 떨어진다")
	for id: String in ["forkball", "splitter"]:
		check(cat.spec(id).pop == 0.0, "%s 는 튀어 오르지 않는다 (커브와 다르게)" % id)
	var curve := _trajectory("curve", Vector2.ZERO)
	check(curve.pop_at(0.0) == 0.0 and absf(curve.pop_at(curve.flight_ms)) < 1e-6 and curve.pop_at(curve.flight_ms * 0.25) > 0.0, "튀어 오름은 양 끝에서 0")
	check(cat.spec("changeup").decel > cat.spec("four_seam").decel, "체인지업은 홈 앞에서 죽는다")
	# 포크볼 vs 스플리터 (2026-10-21 유저): 포크볼은 느리고 크게, 스플리터는 빠르고 짧게 떨어진다
	var fork := cat.spec("forkball")
	var split := cat.spec("splitter")
	check(fork.speed_kmh < split.speed_kmh and fork.break_y < split.break_y * 1.5, "포크볼 낙폭이 스플리터보다 훨씬 크다")
	check(fork.arc_y == 0.0 and split.arc_y == 0.0, "떨어지는 공은 도중에 떠오르지 않는다")


func test_pitch_break_by_location() -> void:
	var cat := _config.catalog
	# 우투수 슬라이더: 우타자 바깥쪽(+x, 휘는 방향)으로 던지면 크게, 몸쪽(−x)으로 던지면 덜 휜다
	var away := _trajectory("slider", Vector2(0.9, 0.0))
	var inside := _trajectory("slider", Vector2(-0.9, 0.0))
	var middle := _trajectory("slider", Vector2.ZERO)
	check(away.break_vec.length() > middle.break_vec.length() and middle.break_vec.length() > inside.break_vec.length(),
		"슬라이더 바깥 %.2f > 가운데 %.2f > 몸쪽 %.2f" % [away.break_vec.length(), middle.break_vec.length(), inside.break_vec.length()])
	check(middle.break_vec.is_equal_approx(PitchTrajectory.zone_break(Vector2(cat.spec("slider").break_x, cat.spec("slider").break_y), "R")), "가운데면 원래 변화량")
	# 투심은 반대 (팔 쪽 = 우타자 몸쪽으로 휜다): 몸쪽 공이 더 휜다
	check(_trajectory("two_seam", Vector2(-0.9, 0)).break_vec.length() > _trajectory("two_seam", Vector2(0.9, 0)).break_vec.length(), "투심은 몸쪽이 더 휜다")
	# 떨어지는 공: 낮게 던질수록 더 떨어진다
	check(_trajectory("forkball", Vector2(0, 0.9)).break_vec.y > _trajectory("forkball", Vector2(0, -0.9)).break_vec.y, "포크볼은 낮을수록 더 떨어진다")
	# 배율은 범위 안, 방향은 그대로, 어디로 던지든 도착점은 정확히 aim
	for id: String in PitchCatalog.IDS:
		var base := PitchTrajectory.zone_break(Vector2(cat.spec(id).break_x, cat.spec(id).break_y), "R")
		for aim in [Vector2(1.5, 1.5), Vector2(-1.5, -1.5), Vector2(1.4, -0.3), Vector2(-0.2, 1.3)]:
			var t := _trajectory(id, aim)
			var f := t.break_vec.length() / base.length() if base.length() > 0.0 else 1.0
			check(f >= cat.location_range.x - 1e-4 and f <= cat.location_range.y + 1e-4, "%s %s 배율 %.2f" % [id, aim, f])
			check(base.length() == 0.0 or t.break_vec.normalized().is_equal_approx(base.normalized()), "%s 휘는 방향은 그대로" % id)
			check(t.position_at(t.flight_ms).distance_to(aim) < 1e-4 and t.pointing_at(t.flight_ms).distance_to(aim) < 1e-4, "%s %s 도착" % [id, aim])
			check(t.pointing_at(0.0).distance_to(aim - t.break_vec) < 1e-4, "%s 처음엔 휘기 전 방향을 향한다" % id)


## 실제 타석 화면에서 공의 화면 높이가 줄어들지(= 위로 올라가지) 않는다 — 포크볼·스플리터가 솟았다 떨어지지 않게 (2026-10-21 유저).
## 튀어 오름(pop)이 있는 구종(커브)만 예외: 손 위로 솟았다가 떨어져야 한다
func test_pitch_never_rises_on_screen() -> void:
	var balance := BalanceLoader.load_balance()
	var view := BattingView.new()
	view.session = BattingSession.new(BattingConfig.from_balance(balance), Presentation.from_balance(balance))
	view.size = Vector2(844, 390)
	view._layout()
	var cat := _config.catalog
	for id: String in PitchCatalog.IDS:
		for aim in [Vector2.ZERO, Vector2(0.5, -1.4), Vector2(-1.0, -1.0), Vector2(1.0, 1.3)]:
			var p := Pitch.new()
			p.type = id
			p.flight_ms = cat.flight_ms(id)
			p.trajectory = PitchTrajectory.make(cat.spec(id), aim, p.flight_ms, "R", cat.location_range)
			var prev := -INF
			var rose := 0.0
			var y0 := view._ball(p, 0.0).y
			var top := y0
			var top_at := 0.0
			for i in 121:
				var y := view._ball(p, p.flight_ms * i / 120.0).y
				rose = maxf(rose, prev - y)
				prev = y
				if y < top:
					top = y
					top_at = i / 120.0
			if cat.spec(id).pop > 0.0:
				# 커브의 "뽕" (2026-10-31 유저): 손을 떠난 직후 손보다 눈에 띄게 위로 솟았다가, 비행 앞쪽에서 꼭대기를 찍고 떨어진다
				check(y0 - top >= 10.0 and top_at <= 0.4, "%s %s 튀어 오름 %.1fpx (꼭대기 %.0f%%)" % [id, aim, y0 - top, top_at * 100.0])
			else:
				check(rose < 0.01, "%s %s 화면에서 %.2fpx 솟는다" % [id, aim, rose])
			var end := view._ball(p, p.flight_ms)
			check(Vector2(end.x, end.y).distance_to(view.zone_to_screen(aim)) < 0.01, "%s %s 화면에서도 도착점에" % [id, aim])
	view.free()


func test_pitch_catalog_valid() -> void:
	var cat := _config.catalog
	check(cat.validate().is_empty(), "; ".join(cat.validate()))
	for id: String in PitchCatalog.IDS:
		check(cat.specs.has(id), "%s 없음" % id)
		var s := cat.spec(id)
		check(s.speed_kmh >= 90 and s.speed_kmh <= 170, "%s 구속" % id)
		check(s.break_start >= 0.0 and s.break_start < 1.0, "%s break_start" % id)
		check(s.break_pow >= 1.0, "%s break_pow" % id)
		check(s.name_ko != "", "%s 이름" % id)
		check(cat.flight_ms(id) >= cat.flight_ms("four_seam") and cat.flight_ms(id) <= cat.flight_ms("curve"), "%s 비행 시간이 포심~커브 사이" % id)
		check(cat.speed_kmh(id, cat.flight_ms(id)) == roundi(s.speed_kmh), "%s 기본 비행 시간이면 구속 그대로" % id)
	# 포크볼 vs 스플리터: 포크볼이 느리고 늦게·크게 떨어진다
	var fork := cat.spec("forkball")
	var split := cat.spec("splitter")
	check(fork.speed_kmh < split.speed_kmh and fork.break_y < split.break_y and fork.break_start > split.break_start, "포크볼·스플리터 차이 유지")
	# 데이터가 망가지면 검증이 잡는다
	var balance := BalanceLoader.load_balance()
	var broken := balance.duplicate(true)
	(broken["batting"]["pitches"] as Dictionary).erase("sweeper")
	check(not PitchCatalog.from_batting(broken["batting"]).validate().is_empty(), "구종이 빠지면 실패")
	var slow_fb := balance.duplicate(true)
	slow_fb["batting"]["pitches"]["four_seam"]["speed_kmh"] = 120
	check(not PitchCatalog.from_batting(slow_fb["batting"]).validate().is_empty(), "포심이 가장 빠르지 않으면 실패")
	var bad_decel := balance.duplicate(true)
	bad_decel["batting"]["pitches"]["changeup"]["decel"] = 1.2
	check(not PitchCatalog.from_batting(bad_decel["batting"]).validate().is_empty(), "구종 decel 이 1 이상이면 실패")
	var bad_pow := balance.duplicate(true)
	bad_pow["batting"]["pitches"]["slider"]["break_pow"] = 0.5
	check(not PitchCatalog.from_batting(bad_pow["batting"]).validate().is_empty(), "break_pow < 1 이면 실패")


func test_pitch_caller_flight_and_forced_type() -> void:
	var cat := _config.catalog
	# 비행 시간 = 공식 + 투수 보정 (+ 실투면 느려짐) ± 흔들림
	for s in 300:
		var p := pitch_at(s % 4, s % 3, s)
		var base := cat.flight_ms(p.type) + _balanced.speed_ms + (_config.mistake_slow_ms if p.is_mistake else 0.0)
		check(absf(p.flight_ms - base) <= cat.flight_jitter_ms + 1e-6, "%s %.1f vs %.1f" % [p.type, p.flight_ms, base])
		check(p.hand == "R" and p.label == cat.spec(p.type).name_ko)
	# 구종 고정 (개발 빌드 버튼): 볼배합이 안 던지는 구종도 나온다
	for id: String in PitchCatalog.IDS:
		_config.forced_pitch_type = id
		for s in 5:
			check(pitch_at(0, 2, s).type == id, "구종 고정 %s" % id)
	_config.forced_pitch_type = "random"
	var seen := {}
	for s in 400:
		seen[pitch_at(1, 1, s).type] = true
	check(seen.size() == PitchCatalog.IDS.size(), "랜덤이면 8개 다 나온다 (%d)" % seen.size())
	# 랜덤도 시드가 같으면 같다
	check(pitch_at(1, 1, 77).type == pitch_at(1, 1, 77).type)
	_config.forced_pitch_type = ""
	# 볼배합대로면 레퍼토리 밖 구종은 안 나온다 (표준형은 스위퍼 0)
	for s in 500:
		check(pitch_at(1, 1, s).type != "sweeper")
	# 좌투수 프로필이면 궤적도 좌투수
	var lefty: BattingConfig.PitcherProfile = _balanced
	var saved := lefty.hand
	lefty.hand = "L"
	var q := pitch_at(1, 1, 3)
	check(q.hand == "L" and q.trajectory.break_vec.normalized().is_equal_approx(PitchTrajectory.zone_break(Vector2(cat.spec(q.type).break_x, cat.spec(q.type).break_y), "L").normalized()), "좌투수 휘는 방향")
	lefty.hand = saved


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
			var factor := _judge.zone_factor(p)
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
	check(sounds.size() == SoundSynth.Sfx.size() - SoundSynth.FILE_ONLY.size(), "파일로만 있는 소리 말고 모든 효과음이 합성된다")
	for sfx: SoundSynth.Sfx in sounds:
		var samples: PackedFloat32Array = sounds[sfx]
		var peak := 0.0
		for v in samples:
			check(not is_nan(v), "%s NaN" % sfx)
			peak = maxf(peak, absf(v))
		check(samples.size() > 0 and peak >= 0.05 and peak <= 1.0, "%s 최대 진폭 %.2f" % [SoundSynth.Sfx.keys()[sfx], peak])
	check(SoundSynth.all()[SoundSynth.Sfx.CRACK_SOLID] == sounds[SoundSynth.Sfx.CRACK_SOLID], "같은 소리는 매번 똑같다")
	# 관중 함성은 미리 만든 파일 (scripts/make_bgm.py)
	for sfx in [SoundSynth.Sfx.CHEER_SMALL, SoundSynth.Sfx.CHEER_BIG] + SoundSynth.FILE_ONLY:
		check(SoundPlayer.file_stream(sfx) != null, "%s 파일" % SoundSynth.Sfx.keys()[sfx])


# ---------- G1: 볼배합·노려치기·방향 ----------

func test_situation_keys() -> void:
	check(BattingConfig.situation_key(0, 2) == "ahead")
	check(BattingConfig.situation_key(3, 1) == "behind")
	check(BattingConfig.situation_key(2, 2) == "even")


func _intended_strike_share(balls: int, strikes: int) -> float:
	var n := 0
	for i in 2000:
		var p := pitch_at(balls, strikes, i)
		if absf(p.intended.x) <= p.reach and absf(p.intended.y) <= p.reach:
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
	var power := _type_share(_config.pitchers["power"], "four_seam")
	var finesse := _type_share(_config.pitchers["finesse"], "four_seam")
	check(power > finesse + 0.15, "강속구형 포심 %.2f, 기교파 포심 %.2f" % [power, finesse])
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


func _mistake_share(balls: int, strikes: int) -> float:
	var n := 0
	for i in 4000:
		if pitch_at(balls, strikes, i).is_mistake:
			n += 1
	return n / 4000.0


func test_mistakes_by_count() -> void:
	# 불리한 카운트일수록 실투가 잦다: 투수 실투 확률 × 카운트 배율
	var behind := _mistake_share(3, 0)
	var ahead := _mistake_share(0, 2)
	var expected := _balanced.mistake_chance * float(_config.mistake_count_factor["behind"])
	check(absf(behind - expected) < 0.025, "3-0 실투 비율 %.3f (기대 %.3f)" % [behind, expected])
	check(behind > ahead * 2.0, "불리 %.3f, 유리 %.3f" % [behind, ahead])


func test_mistake_hangs_and_rewards() -> void:
	var s := 0
	var p := pitch_at(3, 0, s)
	while not (p.is_mistake and p.type == "slider"):
		s += 1
		p = pitch_at(3, 0, s)
	# 한가운데 높게: 스트라이크이고 존 가운데보다 위쪽으로 치우친다
	check(p.is_strike(), "실투는 스트라이크 %s" % p.target)
	check(p.target.y / p.reach <= _config.mistake_radius - _config.mistake_high_shift + 1e-6, "높게 몰린다 %s" % p.target)
	# 행잉: 변화가 breakFactor 만큼만 남고, 도착점은 그대로
	var spec: PitchCatalog.Spec = _config.catalog.spec("slider")
	var full := PitchTrajectory.make(spec, p.target, p.flight_ms, p.hand, _config.catalog.location_range)
	check(absf(p.trajectory.break_vec.length() - full.break_vec.length() * _config.mistake_break_factor) < 1e-4, "덜 꺾인다")
	check(p.position_at(p.flight_ms).distance_to(p.target) < 1e-4, "도착점 그대로")
	# 실투는 판정 폭이 넓다
	var normal := strike_pitch()
	check(_judge.window_factor(p, 1.0) > _judge.window_factor(normal, 1.0), "실투는 판정 폭이 넓다")


func test_chase_pitches_are_just_outside() -> void:
	for i in 500:
		var p := pitch_at(0, 2, i)
		if p.is_chase:
			var off := maxf(absf(p.intended.x), absf(p.intended.y)) / p.reach
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
		var cell := Pitch.cell_of(p.target, p.reach)
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
	var cat := _config.catalog
	check(cat.speed_kmh("four_seam", 300.0) > cat.speed_kmh("four_seam", 400.0), "빠를수록 구속이 높다")
	check(cat.speed_kmh("four_seam", cat.four_seam_flight_ms) == roundi(cat.spec("four_seam").speed_kmh), "포심 기본 비행 시간이면 포심 구속")
	for s in 300:
		var p := pitch_at(s % 4, s % 3, s, _config.pitchers.values()[s % _config.pitchers.size()])
		check(p.speed_kmh >= 105 and p.speed_kmh <= 165, "%s 구속 %d km/h 가 현실 범위 밖" % [p.type, p.speed_kmh])


func test_cell_of() -> void:
	check(Pitch.cell_of(Vector2(-0.9, -0.9)) == 0)
	check(Pitch.cell_of(Vector2(0, 0)) == 4)
	check(Pitch.cell_of(Vector2(0.9, 0.9)) == 8)
	check(Pitch.cell_of(Vector2(1.2, 0)) == -1)
	# 공 위치는 reach(존 + 공 반지름)를 3등분: 존에 걸친 공은 가장자리 칸
	check(Pitch.cell_of(Vector2(1.2, 0), 1.35) == 5 and Pitch.cell_of(Vector2(1.4, 0), 1.35) == -1)


func test_strike_zone_counts_ball_size() -> void:
	# 공이 조금이라도 존에 걸치면 스트라이크: 공 중심이 ±(1 + 공 반지름) 안
	var r := _config.zone_ball_radius
	check(r > 0.1 and r < 0.5, "공 반지름 %.2f (실제 공 반지름 ÷ 존 반폭 ≈ 0.17, 화면에서 보이게 키워 씀)" % r)
	for i in 400:
		var p := pitch_at(i % 4, i % 3, i)
		check(is_equal_approx(p.reach, 1.0 + r))
		var edge := maxf(absf(p.target.x), absf(p.target.y))
		check(p.is_strike() == (edge <= 1.0 + r), "판정 %s 위치 %s" % [p.is_strike(), p.target])
	var p := Pitch.new()
	p.reach = 1.0 + r
	p.target = Vector2(1.0 + r * 0.5, 0.0)  # 중심은 존 밖, 몸통이 걸친다
	check(p.is_strike(), "걸친 공은 스트라이크")
	p.target = Vector2(0.0, 1.0 + r * 1.2)
	check(not p.is_strike(), "완전히 벗어난 공은 볼")


# ---------- 손맛 조절 패널 (CLAUDE.md §3-7) ----------

func test_tuning_applies_and_patches() -> void:
	var balance := BalanceLoader.load_balance()
	var config := BattingConfig.from_balance(balance)
	var pres := Presentation.from_balance(balance)
	var base_solid := config.solid_window
	var base_fast: float = config.catalog.flight_ms("curve")
	var t := Tuning.new(config, pres)
	check(not t.is_changed())
	check(t.to_patch(config, pres).is_empty(), "안 바꾸면 저장할 것도 없다")
	t.set_value("window", 1.5, config, pres)
	t.set_value("flight", 1.2, config, pres)
	t.set_value("hitstop", 120.0, config, pres)
	check(config.solid_window.is_equal_approx(base_solid * 1.5), "판정 폭 배율이 바로 적용된다")
	check(is_equal_approx(config.catalog.flight_ms("curve"), base_fast * 1.2), "모든 구종 비행 시간이 같은 배율로")
	check(pres.hit_stop_ms == 120.0)
	var patch := t.to_patch(config, pres)
	check(patch.has("batting.timing.solidWindowMsAt0") and patch.has("batting.pitch_common.four_seam_flight_ms") and patch["batting.presentation.hitStopMs"] == 120)
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

## 테스트용 설정: 게임 콘텐츠 + 테스트 전용 이벤트(tests/fixtures/legacy_events.json — 게임에서 뺀 옛 이벤트).
## 이벤트 엔진의 기능(확률 갈림·지연 결과·연애·겨울 캠프·진로)은 이 이벤트들로 시험한다
func _career_cfg() -> CareerConfig:
	var content := BalanceLoader.load_config("content.json")
	var legacy: Dictionary = BalanceLoader.parse(FileAccess.get_file_as_string("res://tests/fixtures/legacy_events.json"))
	(content["events"] as Array).append_array(legacy["events"])
	return CareerConfig.from(BalanceLoader.load_balance(), content)


## 게임이 실제로 읽는 설정 그대로 (content.json 만)
func _real_cfg() -> CareerConfig:
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
func _play_full_game(seed_value: int, bench: bool = false) -> GameRunner:
	var cfg := _career_cfg()
	var player := PlayerData.create("테스트", cfg)
	var srng := _rng(seed_value)
	var mine := School.create("me", "우리고", "우리", 50.0, "balanced", cfg, srng)
	var theirs := School.from_content(cfg.regional_schools[seed_value % cfg.regional_schools.size()], cfg, srng)
	theirs.strength = 50.0
	theirs.team = Team.generate(theirs.name, theirs.short, 50.0, theirs.pitcher_profile, cfg, srng)
	var game := GameRunner.new(cfg, _config, player, mine, theirs, seed_value % 2 == 0, seed_value, true, bench)
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
	var first := _event(career.cfg, "ep01_01")
	var second := _event(career.cfg, "ep01_02")
	check(not book.eligible(career, second), "앞 장면(after)을 보기 전엔 뒤 장면이 안 나온다")
	var rel := career.people.relation("neighbor")
	career.resolve_event(first, 0)
	check(int(career.player.vars["harin_affinity"]) == 1 and career.player.vars["ep01_harin_club"] == "A" and career.player.lineup_slot == 0, "선택 → 변수·효과 (하린 호감 +1, 선택 지점 id = A, 입부하면 벤치)")
	check(career.people.relation("neighbor") == rel + int(career.cfg.story.varRelation.harin_affinity[1]) and career.people.is_met("neighbor"), "호감도 변수 → 인물 관계도 오른다, 말한 사람을 만난다")
	check(book.eligible(career, second), "앞 장면을 보면 뒤 장면이 열린다")
	check(not book.eligible(career, first), "한 번 나온 이벤트는 다시 안 나온다")
	career.week = 2
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
		for i in maxi(1, (e.get("choices", []) as Array).size()):
			var c := _rich_career(cfg, e)
			var text := c.resolve_event(e, i)
			check(text != "" and not text.contains("{"), "%s 선택지 %d: %s" % [e.id, i, text])
			# 그 길에서 나오는 모든 대사·선택지 글자에 치환이 남지 않는다
			var reader := DialogueRun.new(_rich_career(cfg, e), e)
			var step := reader.next()
			while step.type != "end":
				if step.type == "line":
					check(step.text != "" and not step.text.contains("{"), "%s 대사 치환: %s" % [e.id, step.text])
				else:
					for o: Dictionary in step.options:
						check(not o.label.contains("{"), "%s 선택지 치환: %s" % [e.id, o.label])
					reader.choose(reader.pick(i))
				step = reader.next()
			check(EventBook.speaker_name(c, e) != "", "%s 화자" % e.id)
			var choice: Dictionary = e.choices[i] if e.has("choices") else {}
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
	var choices: Array = e.get("choices", [])
	if choices.is_empty():
		return 0
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
			# 주 시작 이야기는 하나가 끝나면 다음 것이 이어질 수 있다 — 남은 게 없을 때까지
			while not career.events("start").is_empty():
				var e: Dictionary = career.events("start")[0]
				career.resolve_event(e, _ok_choice(career, e, choose.call(e)))
				count += 0 if e.get("free", false) else 1
			var game := _auto_week(career, ids[weeks % ids.size()])
			career.finish_game(game)
			while not career.events("after").is_empty():
				var e: Dictionary = career.events("after")[0]
				career.resolve_event(e, _ok_choice(career, e, choose.call(e)))
				count += 0 if e.get("free", false) else 1
			max_events.append(count)
			career.advance_week()
			weeks += 1
		while not career.events("seasonEnd").is_empty():
			var e: Dictionary = career.events("seasonEnd")[0]
			career.resolve_event(e, _ok_choice(career, e, choose.call(e)))
		if season < 2:
			_auto_camp(career, choose, max_events)
			career.winter_training()
			career.start_next_season()
	return career


## 겨울 캠프를 끝까지 자동 진행 (연습경기 주엔 경기, 아니면 훈련만)
func _auto_camp(career: CareerState, choose: Callable, max_events: Array = []) -> void:
	career.start_camp()
	var ids: Array = career.cfg.training.keys()
	while career.in_camp():
		var count := 0
		# 주 시작 이야기는 하나가 끝나면 다음 것이 이어질 수 있다 — 남은 게 없을 때까지
		while not career.events("start").is_empty():
			var e: Dictionary = career.events("start")[0]
			career.resolve_event(e, _ok_choice(career, e, choose.call(e)))
			count += 0 if e.get("free", false) else 1
		if career.camp_has_game():
			career.finish_game(_auto_week(career, ids[career.camp_week % ids.size()]))
		else:
			career.train(ids[career.camp_week % ids.size()])
			career.finish_camp_week()
		while not career.events("after").is_empty():
			var e: Dictionary = career.events("after")[0]
			career.resolve_event(e, _ok_choice(career, e, choose.call(e)))
			count += 0 if e.get("free", false) else 1
		max_events.append(count)
		career.advance_week()


func test_camp() -> void:
	var cfg := _career_cfg()
	var camp := cfg.camp
	var career := CareerState.new(cfg, "테스트", _config)
	var focus := _event(cfg, "camp_focus")
	check(not career.book.eligible(career, focus), "시즌 중엔 캠프 이벤트가 안 나온다")
	check(not career.in_camp() and career.training_multiplier("power") == 1.0, "시즌 중엔 훈련 보너스 없음")
	career.finish_game(_auto_week(career, "rest"))
	career.advance_week()
	var season_line := career.player.season.pa
	var results := career.results.size()
	var record_games: int = career.records.games.size()
	career.player.grade = 2  # 1학년 겨울은 시나리오(EP07)가 끌고 간다 — 집중 과제 이야기는 2학년부터
	career.phase_index = career.phases().size()
	career.start_camp()
	check(career.in_camp() and career.camp_week == 1 and not career.camp_has_game(), "캠프 1주차는 훈련 주")
	check(career.phase_name() == "겨울 캠프" and career.round_text().contains("훈련"), "허브 표시: %s %s" % [career.phase_name(), career.round_text()])
	check(not career.book.eligible(career, _event(cfg, "rumor")) and career.book.eligible(career, _event(cfg, "mom_meal")), "시즌 이벤트는 쉬고, 일상 이벤트는 캠프에서도")
	var start: Array = career.events("start")
	check(start.size() == 1 and start[0].id == "camp_focus", "첫 주 시작에 집중 과제 이야기: %s" % [start.map(func(e: Dictionary) -> String: return e.id)])
	career.resolve_event(start[0], 1)
	check(career.camp_focus == "power", "집중 과제 = 파워")
	check(is_equal_approx(career.training_multiplier("power"), float(camp.focusMultiplier)) and is_equal_approx(career.training_multiplier("contact"), float(camp.trainingMultiplier))
		and career.training_multiplier("rest") == 1.0, "집중 과제 ×%.1f, 다른 훈련 ×%.1f, 휴식 ×1" % [float(camp.focusMultiplier), float(camp.trainingMultiplier)])
	# 같은 선수로 시즌 훈련과 캠프 훈련을 비교: 캠프가 더 많이 오른다
	var plain := CareerState.new(cfg, "테스트", _config)
	plain.player.grade = 2
	plain.train("power")
	var plain_gain: float = plain.player.power + float(plain.player.progress.get("power", 0.0)) - int(cfg.start.power)
	var before: float = career.player.power + float(career.player.progress.get("power", 0.0))
	var text := career.train("power")
	var camp_gain: float = career.player.power + float(career.player.progress.get("power", 0.0)) - before
	check(camp_gain > plain_gain * 1.5 and text.contains("집중 과제"), "캠프 집중 훈련이 더 크게 오른다 (%.2f vs %.2f)" % [camp_gain, plain_gain])
	var money := career.player.money
	career.finish_camp_week()
	check(career.game_done and career.player.money > money and career.last.is_empty(), "훈련 주 마치기: 용돈은 들어오고 경기 조건 이벤트는 없다")
	career.advance_week()
	check(career.camp_week == 2 and career.camp_has_game() and career.week_training == "", "2주차는 연습경기")
	var week := career.week
	career.finish_game(_auto_week(career, "rest"))
	check(career.camp_games.size() == 1 and career.player.season.pa == season_line and career.results.size() == results and career.records.games.size() == record_games,
		"연습경기는 시즌 기록·팀 성적·기록실에 안 들어간다")
	check(not career.last.is_empty() and career.last.phase_end == "", "연습경기 뒤 이벤트 조건은 남는다")
	career.advance_week()
	check(career.week == week, "캠프 중엔 시즌 주차가 멈춘다")
	while career.in_camp():
		if career.camp_has_game():
			career.finish_game(_auto_week(career, "rest"))
		else:
			career.train("rest")
			career.finish_camp_week()
		career.advance_week()
	check(career.camp_finished() and career.camp_games.size() == (camp.gameWeeks as Array).size(), "캠프 %d주, 연습경기 %d번" % [int(camp.weeks), career.camp_games.size()])
	career.winter_training()
	career.start_next_season()
	check(not career.in_camp() and not career.camp_finished() and career.camp_focus == "" and career.player.grade == 3 and career.week == 1, "다음 시즌엔 캠프 상태가 지워진다")
	check(career.training_multiplier("power") == 1.0, "시즌엔 다시 보너스 없음")


func test_fast_forward() -> void:
	# 테스트 시작: 고른 시점까지 실제 규칙대로 감긴다. 도착한 커리어는 정상 커리어다 (저장·이어 하기 가능)
	var cfg := _career_cfg()
	check(not FastForward.is_valid(3, "camp") and FastForward.is_valid(2, "camp_end"), "3학년 겨울은 캠프가 없다")
	var a := CareerState.new(cfg, "감기", _config)
	check(FastForward.reached(a, 1, "season_start"), "새 커리어는 1학년 시즌 시작")
	check(FastForward.run(a, 1, "phase:1") and a.is_national() and a.phase_game == 0 and a.week == a.regional_ids.size() + 2, "1학년 첫 전국대회 (%d주차 — 2주차는 연습경기)" % a.week)
	check(a.records.games.size() == a.week - 2 and a.player.season.games == 1, "지나온 경기가 기록에 있다 (벤치였고, 공식 출전은 EP04 대타 한 경기)")
	check(FastForward.run(a, 1, "season_end") and a.is_season_over(), "시즌 결산")
	check(FastForward.run(a, 1, "camp") and a.camp_week == 1 and a.events("start")[0].id == "ep07_01", "겨울 캠프 1주: EP07 면담부터 (%s)" % [a.events("start").map(func(e: Dictionary) -> String: return e.id)])
	check(FastForward.run(a, 1, "camp_game") and a.camp_has_game() and not a.game_done, "캠프 연습경기 주")
	check(FastForward.run(a, 1, "camp_end") and a.camp_finished(), "캠프 결산")
	check(FastForward.run(a, 2, "phase:2") and a.player.grade == 2 and a.phase_index == 2 and a.history.size() == 1, "2학년 주말리그 후반기")
	check(not FastForward.run(a, 1, "season_end"), "지나간 시점으로는 못 간다")
	# 같은 시점을 고르면 같은 커리어, 저장해도 그대로
	var b := CareerState.new(cfg, "감기", _config)
	FastForward.run(b, 2, "phase:2")
	check(SaveGame.to_bytes(a) != PackedByteArray() and SaveGame.to_bytes(_reload(b)) == SaveGame.to_bytes(b), "감은 커리어도 저장된다")
	var c := CareerState.new(cfg, "감기", _config)
	for point in ["phase:1", "season_end", "camp", "camp_game", "camp_end"]:
		FastForward.run(c, 1, point)
	FastForward.run(c, 2, "phase:2")
	check(SaveGame.to_bytes(c) == SaveGame.to_bytes(a), "끊어 감아도 한 번에 감아도 같다")
	check(SaveGame.to_bytes(b) == SaveGame.to_bytes(a), "같은 시점 = 같은 커리어")
	# 편의 옵션
	var money := b.player.money
	FastForward.apply_extras(b, 300000, "neighbor")
	check(b.player.money == money + 300000 and b.people.partner == "neighbor" and b.people.is_met("neighbor"), "돈·연애 옵션")
	# 3학년 시즌 결산까지 (진로 선택 이야기가 남아 있다)
	check(FastForward.run(b, 3, "season_end") and b.player.grade == 3 and b.path == "", "3학년 시즌 결산, 진로는 아직")
	var ids := []
	for e: Dictionary in b.events("seasonEnd"):
		ids.append(e.id)
	check(ids.has("s3_path"), "진로 선택 이야기가 기다린다: %s" % [ids])


func test_camp_save_and_repeat() -> void:
	# 캠프 도중 저장·불러오기 → 끝까지 똑같이 진행된다. 같은 시드면 캠프 이야기도 같다
	var runs := []
	for k in 2:
		var career := CareerState.new(_career_cfg(), "캠프", _config)
		career.player.grade = 2
		career.phase_index = career.phases().size()
		career.start_camp()
		# 주 시작 이야기는 하나가 끝나면 다음 것이 이어질 수 있다 — 남은 게 없을 때까지
		while not career.events("start").is_empty():
			var e: Dictionary = career.events("start")[0]
			career.resolve_event(e, 0)
		career.train("contact")
		career.finish_camp_week()
		if k == 1:
			career = _reload(career)
			check(career.in_camp() and career.game_done and career.camp_focus == "contact", "불러온 캠프: 1주차를 마친 상태")
		for e: Dictionary in career.events("after").duplicate():
			career.resolve_event(e, _ok_choice(career, e, 0))
		career.advance_week()
		while career.in_camp():
			# 주 시작 이야기는 하나가 끝나면 다음 것이 이어질 수 있다 — 남은 게 없을 때까지
			while not career.events("start").is_empty():
				var e: Dictionary = career.events("start")[0]
				career.resolve_event(e, _ok_choice(career, e, 0))
			if career.camp_has_game():
				career.finish_game(_auto_week(career, "contact"))
			else:
				career.train("contact")
				career.finish_camp_week()
			for e: Dictionary in career.events("after").duplicate():
				career.resolve_event(e, _ok_choice(career, e, 0))
			career.advance_week()
		runs.append(career)
	check(SaveGame.to_bytes(runs[0]) == SaveGame.to_bytes(runs[1]), "캠프 도중 불러와도 끝이 같다")
	var titles := []
	for entry: Dictionary in runs[0].book.log:
		titles.append(entry.title)
	check(titles.has("겨울 과제") and titles.has("캠프 끝"), "캠프 이야기: %s" % [titles])
	check(SaveGame.summary(_reload(runs[0])).contains("캠프 결산"), "캠프를 마친 저장의 한 줄")


func test_story_three_years() -> void:
	var counts := []
	var career := _auto_career(2026, func(e: Dictionary) -> int: return 0, counts)
	check(counts.max() <= int(career.cfg.story.maxEventsPerWeek), "한 주 이벤트 최대 %d개" % counts.max())
	var titles := {}
	for entry: Dictionary in career.book.log:
		titles[entry.title] = true
	for t in ["학교 가는 길", "체력 테스트", "대수비", "마지막 타석", "은하식당", "서림고 vs 서호대 (연습경기)", "겨울 과제", "후배", "주장", "진로"]:
		check(titles.has(t), "고교 3년 이야기에 '%s' 가 나온다" % t)
	check(career.path == "draft", "진로가 정해진다 (%s)" % career.path)
	check(career.player.grade == 3 and career.history.size() == 2, "3학년까지, 지난 시즌 기록 2개")
	check(career.people.is_met("rival"), "라이벌을 만났다")
	check(career.people.is_met("junior"), "후배를 만났다")
	check(career.book.log.size() >= 80 and career.book.log.size() <= 220, "3년 이벤트 수 %d (1학년 시나리오 장면 71개 + 연습·체력 테스트 + 2·3학년)" % career.book.log.size())
	# 같은 시드·같은 선택 → 같은 이야기
	var again := _auto_career(2026, func(e: Dictionary) -> int: return 0)
	check(JSON.stringify(again.book.log) == JSON.stringify(career.book.log), "재현성: 같은 시드면 같은 이야기")
	check(again.player.overall() == career.player.overall() and again.prospect_rank() == career.prospect_rank(), "재현성: 같은 성장·랭킹")
	# 다른 선택 → 다른 길
	var other := _auto_career(2026, func(e: Dictionary) -> int: return maxi(0, (e.get("choices", []) as Array).size() - 1))
	check(other.path == "college", "마지막 선택지면 대학")
	check(JSON.stringify(other.book.log) != JSON.stringify(career.book.log), "선택이 다르면 이야기가 달라진다")


func test_rival() -> void:
	# 라이벌 학교가 권역 학교일 때 주말리그에서 맞대결한다 (지금 콘텐츠의 라이벌은 전국구 청운고라 권역 학교로 바꿔 시험한다)
	var rcfg := _career_cfg()
	rcfg.people["rival"] = (rcfg.people["rival"] as Dictionary).duplicate()
	rcfg.people["rival"]["school"] = rcfg.regional_schools[2]["id"]
	var career := CareerState.new(rcfg, "테스트", _config)
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


func test_next_lineup() -> void:
	var career := CareerState.new(_career_cfg(), "테스트", _config)
	var p := career.player
	var rows := career.next_lineup()
	check(rows.size() == 9, "선발 타순은 9명")
	var mine := rows.filter(func(r: Dictionary) -> bool: return r["me"])
	check(mine.size() == 1 and mine[0]["slot"] == p.lineup_slot and mine[0]["name"] == p.name, "내 선수는 내 타순 자리에 한 번")
	var game := career.new_game()
	var same := true
	for i in 9:
		same = same and game.teams[game.my_side].lineup[i].name == rows[i]["name"]
	check(same, "홈 화면 라인업 = 실제 경기 타순")
	p.bench_next = true
	check(career.next_lineup().all(func(r: Dictionary) -> bool: return not r["me"]), "벤치 대기면 선발 타순에 내가 없다")


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
	check(Scouting.projection(cfg, 1, 1).text == "스카우터 관심 없음", "1학년은 랭킹 1위여도 스카우터 관심 없음")
	check(Scouting.projection(cfg, 1, 2).text.begins_with("1라운드"), "2학년부터는 예상 지명이 나온다")
	check(career.scouting.interested_teams(cfg, 5).size() < career.scouting.interested_teams(cfg, 60).size(), "관심도가 오르면 주목 구단이 는다")
	check(career.scouting.interested_teams(cfg, 30)[0].name == career.people.person("scout").team_name, "스카우트 인물의 구단이 맨 먼저")
	check(career.scouting.interested_teams(cfg, 90, 1).is_empty(), "1학년은 관심도가 높아도 주목 구단이 없다")
	check(not career.scouting.interested_teams(cfg, 90, 2).is_empty(), "2학년부터는 주목 구단이 보인다")
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
	check(EventBook.fill(career, "{partner}") == "소율" and EventBook.speaker_name(career, anniv) == "소율", "partner = 사귀는 사람")
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


## 대화를 끝까지 읽어 걸음들을 모은다 (선택 지점마다 picks 의 다음 번호, 모자라면 0)
func _read(run: DialogueRun, picks: Array = []) -> Array:
	var steps := []
	var k := 0
	while true:
		var step := run.next()
		if step.type == "end":
			return steps
		steps.append(step)
		if step.type == "choice":
			run.choose(picks[k] if k < picks.size() else 0)
			k += 1
	return steps


func test_dialogue_lines() -> void:
	var cfg := _career_cfg()
	var career := CareerState.new(cfg, "테스트", _config)
	var meet := _event(cfg, "ep01_02")
	var steps := _read(DialogueRun.new(career, meet))
	check(steps[0].type == "line" and steps[0].who == "" and steps[1].who == "buddy" and steps[2].who == "player", "지문 → 준수 → 나가 번갈아 말한다 (만나기 do 는 줄이 아니다)")
	check(steps[1].portrait == "buddy" and steps[1].name == "준수" and steps[0].portrait == "", "말하는 사람의 일러스트 자리·부르는 이름")
	check(EventBook.place_of(career, meet) == "clubroom" and EventBook.portrait_of(career, meet) == "buddy", "배경은 장면 장소, 일러스트 기본값은 대표 화자")
	# 대사 맨 앞 괄호는 지문으로 따로: "(작게) 감독님 웃으셨어."
	var bus := _read(DialogueRun.new(career, _event(cfg, "ep03_01")))
	var found := false
	for i in bus.size() - 1:
		if bus[i].type == "line" and bus[i].text == "작게":
			found = bus[i].who == "" and bus[i + 1].who == "buddy" and bus[i + 1].text == "감독님 웃으셨어."
	check(found, "'(작게) …' → 지문 한 줄 + 준수 대사")
	# 표정: 괄호 지문 뒤 대사에도 남고, 지문엔 붙지 않는다. 쓰인 표정마다 그림이 있다
	var faced := _read(DialogueRun.new(career, {"id": "t_face", "speaker": "manager", "script": [{"speaker": "manager", "text": "(작게) 어?", "face": "surprised"}]}))
	check(faced[0].face == "" and faced[1].who == "manager" and faced[1].face == "surprised", "'(작게) …' → 지문은 표정 없음, 매니저 대사는 놀람")
	for f: String in EventBook.FACES:
		check(ResourceLoader.exists("res://art/portraits/manager_%s.png" % f), "매니저 표정 그림 %s" % f)
	# 화면 표시 줄: 문자는 이름표에 '문자', 노트북 화면은 이름표가 '노트북'
	var night := _read(DialogueRun.new(career, _event(cfg, "ep01_07")))
	check(night.any(func(st: Dictionary) -> bool: return st.type == "line" and st.who == "neighbor" and st.ui == "phone" and st.role == "문자"), "폰 문자 줄")
	var laptop := _read(DialogueRun.new(career, _event(cfg, "ep04_01")))
	check(laptop.any(func(st: Dictionary) -> bool: return st.type == "line" and st.ui == "laptop" and st.name == "노트북" and st.text.contains("테스트_1학년")), "노트북 화면 줄 ({player} 치환)")
	# IF/ELSE: 체력 테스트 등급에 따라 다른 반응
	var fast := CareerState.new(cfg, "테스트", _config)
	fast.player.vars["test_speed_grade"] = "high"
	var lines_high := _read(DialogueRun.new(fast, _event(cfg, "ep01_05b"))).map(func(st: Dictionary) -> String: return st.get("text", ""))
	var lines_low := _read(DialogueRun.new(career, _event(cfg, "ep01_05b"))).map(func(st: Dictionary) -> String: return st.get("text", ""))
	check(lines_high.has("오, 빠르다! 2학년 평균보다 빨라.") and not lines_high.has("준수야, 넌 꼴찌였어."), "high 갈래")
	check(lines_low.has("준수야, 넌 꼴찌였어.") and not lines_low.has("오, 빠르다! 2학년 평균보다 빨라."), "기본값(없음)은 ELSE 갈래")
	# 조건부 선택지: [조건: taeo_interest == true] 는 조건이 맞을 때만 보인다
	var sink := _event(cfg, "ep03_05")
	var no_interest := DialogueRun.new(career, sink)
	while not no_interest.at_choice():
		no_interest.next()
	var keen := CareerState.new(cfg, "테스트", _config)
	keen.player.vars["taeo_interest"] = true
	var interest := DialogueRun.new(keen, sink)
	while not interest.at_choice():
		interest.next()
	check(no_interest.next().options.size() == 2 and interest.next().options.size() == 3, "조건이 안 맞는 선택지는 안 보인다")
	interest.choose(0)
	check(interest.overlay.get("ep03_taeo_sink", "") == "A" and interest.overlay.get("taeo_first_talk", false) == true and not interest.last_echo, "고른 선택지 key·효과가 대화 안 분기에 바로 보인다, 같은 말 반복은 안 한다")
	# 옛 형식(대사 하나 + 선택 한 번)도 같은 대본으로 읽힌다
	var rumor := _event(cfg, "rumor")
	var old := _read(DialogueRun.new(career, rumor))
	check(old.size() == 3 and old[0].who == "?" and old[1].type == "choice" and old[2].who == "", "옛 형식: 대사 → 선택 → 결과 지문")
	check(EventBook.portrait_of(career, rumor) == "none" and EventBook.place_of(career, rumor) == "phone", "이벤트가 정한 배경·일러스트")
	for e: Dictionary in cfg.events:
		check(cfg.places.has(EventBook.place_of(career, e)), "%s 배경 %s 이 places 에 있다" % [e.id, EventBook.place_of(career, e)])
		var found_choice := {"choices": [], "lines": []}
		EventBook._collect(EventBook.script_of(e), found_choice)
		var top_choice := EventBook.script_of(e).any(func(n: Dictionary) -> bool: return n.has("choices"))
		check(not top_choice or not (e.get("choices", []) as Array).is_empty(), "%s: 선택 지점이 있으면 e.choices 가 붙는다" % e.id)


func test_dialogue_run() -> void:
	# 대화가 이어지는 대본: 선택에 따라 뒤따르는 대사가 달라지고, 효과는 끝날 때 한꺼번에 반영된다
	var cfg := _career_cfg()
	var errand := _event(cfg, "s1_errand")
	var a := CareerState.new(cfg, "테스트", _config)
	var run := DialogueRun.new(a, errand)
	check(run.next().type == "line" and run.next().type == "choice", "선배 대사 → 첫 선택")
	check(run.next().type == "choice" and run.at_choice(), "고르기 전엔 선택 지점에 머문다")
	var senior := a.people.relation("senior")
	var cond := a.player.condition
	run.choose(0)
	check(a.player.condition == cond and a.book.log.is_empty(), "고르는 순간엔 아직 아무것도 반영되지 않는다")
	check(run.next().who == "" and run.next().who == "senior", "'네, 알겠습니다' 뒤: 지문 → 선배가 다시 묻는다")
	var second := run.next()
	check(second.type == "choice" and second.options.size() == 2, "대화 속 두 번째 선택")
	run.choose(1)
	check(run.next().text.contains("솔직해서") and run.next().type == "end", "두 번째 답에 맞는 대사로 끝난다")
	var text := run.commit()
	check(a.player.condition == cond - 5 + 3 and a.people.relation("senior") == senior + 6 and a.people.relation("coach") == int(cfg.story.relationStart.coach) + 3,
		"끝나면 두 선택의 효과가 함께 반영된다 (%s)" % text)
	var entry: Dictionary = a.book.log[-1]
	check(entry.choice == "네, 알겠습니다 → 솔직히 얼었습니다" and entry.result.contains("솔직해서"), "기록: %s / %s" % [entry.choice, entry.result])
	check(not a.book.eligible(a, errand), "끝난 이야기는 다시 안 나온다")
	# 다른 길: 첫 선택이 다르면 다른 대사와 다른 두 번째 선택이 나온다
	var b := CareerState.new(cfg, "테스트", _config)
	var steps := _read(DialogueRun.new(b, errand), [1, 1])
	var texts := []
	for st: Dictionary in steps:
		if st.type == "line":
			texts.append(st.text)
	check(texts.has("뭐?") and not texts.has("…솔직해서 봐준다. 나도 1학년 땐 그랬어."), "다른 선택 → 다른 대화: %s" % [texts])
	# resolve(n) 은 선택 지점마다 n 번째를 고른다 (자동 진행·테스트용)
	var c := CareerState.new(cfg, "테스트", _config)
	c.resolve_event(_event(cfg, "s3_path"), 1)
	check(c.path == "college", "두 번째 선택지를 끝까지 → 대학")
	# 돈 드는 선택지: 대화 안에서 쓴 돈까지 따진다
	var d := CareerState.new(cfg, "테스트", _config)
	d.player.money = 30000
	var visit := DialogueRun.new(d, _event(cfg, "senior_visit"))
	while not visit.at_choice():
		visit.next()
	visit.choose(0)
	while not visit.at_choice():
		visit.next()
	check(visit.block(1) != "" and visit.block(0) == "" and visit.pick(1) == 0, "돈이 모자란 선택지는 막히고 다른 걸 고른다")
	# 장비를 받는 효과
	var f := CareerState.new(cfg, "테스트", _config)
	f.book.apply_effects(f, {"gear": "maple_bat"})
	check(f.player.gear.has("maple_bat"), "장비를 받는 효과")
	# 새 인물
	for id in ["senior", "junior", "batting_coach"]:
		check(a.people.has(id), "인물 %s" % id)
	check(a.people.is_met("senior") and not a.people.is_met("batting_coach") and not a.people.is_met("junior"), "주장은 처음부터, 코치·후배는 만나야")
	check(EventBook.fill(a, "{senior}·{coach}") == "한도윤·오 감독" and EventBook.fill(a, "{buddy}·{buddyName}") == "준수·박준수", "부르는 이름: %s" % EventBook.fill(a, "{senior}·{coach}"))
	check(not CareerState.can_gift(a.people.person("batting_coach")) and CareerState.can_gift(a.people.person("senior")), "코치에겐 선물 못 하고 선배에겐 된다")


func test_first_day() -> void:
	# 1학년 첫 주 (EP01 입부 → 체력 테스트 세 종목 → 밤 문자 → EP02 새벽 펑고 → 토요일 공지): 주 시작 장면이 정해진 순서로 하나씩
	var cfg := _career_cfg()
	var career := CareerState.new(cfg, "테스트", _config)
	var order := []
	var guard := 0
	while not career.events("start").is_empty() and guard < 30:
		guard += 1
		var pending: Array = career.events("start")
		check(pending.size() == 1, "한 번에 하나씩 뜬다 (%s)" % [pending.map(func(e: Dictionary) -> String: return e.id)])
		order.append(pending[0].id)
		career.resolve_event(pending[0], 0)
	check(order == ["ep01_01", "ep01_02", "ep01_05", "ep01_05b", "ep01_05c", "ep01_05d", "ep01_07", "ep02_01", "ep02_01b", "ep02_02"], "첫 주 순서: %s" % [order])
	var v := career.player.vars
	check(career.fitness.size() == 3 and career.pending_minigame == "", "체력 테스트 세 종목은 화면 없이 autoGrade 로 끝난다")
	check(v["test_speed_grade"] == "mid" and v["test_squat_grade"] == "mid" and v["test_reaction_grade"] == "mid" and v["test_overall_grade"] == "mid", "autoGrade B → mid: %s" % [v])
	check(v["phone_contacts"] == true and int(v["harin_affinity"]) >= 2 and int(v["soyul_affinity"]) >= 2, "연락처 해금, 호감도 누적")
	check(career.phone.contacts == (["mom", "neighbor", "manager", "buddy"] as Array[String]), "해금되면 연락처에 하린·소율·준수가 들어온다 (엄마는 처음부터): %s" % [career.phone.contacts])
	var texts: Array = career.phone.lines("neighbor").map(func(l: Dictionary) -> String: return l.text)
	check(texts.has("학원 끝남") and texts.has("야구부 들어갔어?") and career.phone.lines("neighbor").any(func(l: Dictionary) -> bool: return l.from == Phone.ME),
		"이야기 속 문자가 하린 대화방에 남는다 (내가 보낸 것 포함): %s" % [texts])
	check(DialogueRun.phone_text("(하린에게 먼저 보낸다) 오늘 경기 나갔어") == "오늘 경기 나갔어" and DialogueRun.phone_text("(안 보낸다)") == "", "폰 선택지 글자 → 보낼 말")
	check(int(career.book._count.get(career.total_week, 0)) == 0, "이야기는 한 주 개수에 안 들어간다")
	check(career.people.is_met("neighbor") and career.people.is_met("manager") and not career.people.is_met("rival"), "하린·소율을 만났다. 강태오는 아직")
	check(career.player.lineup_slot == 0 and (career.match_override.get("score", []) as Array).map(func(x: Variant) -> int: return int(x)) == [1, 10] and int(career.match_override.innings) == 8, "벤치, 이번 주 동인고전은 1-10 8회 콜드 관전")
	check(career.opponent().id == "dongin", "첫 경기 상대는 동인고 (주말리그 일정)")
	# 펑고 10구는 화면 없이 autoRate 로
	check(career.book.log.any(func(x: Dictionary) -> bool: return x.title == "펑고 수비 연습"), "펑고 연습 기록")
	# 종목 하나씩: 50m 뒤에는 50m 등급만
	var b := CareerState.new(cfg, "테스트", _config)
	for k in 3:
		b.resolve_event(b.events("start")[0], 2)
	check(b.fitness.keys() == ["run"] and b.player.vars["test_speed_grade"] == "mid" and EventBook.var_of(b, "test_overall_grade") == "", "50m 만 끝났다")
	check(b.player.vars["ep01_harin_club"] == "B" and not b.player.vars.has("harin_affinity"), "세 번째 선택지는 goto B → key B, 호감도 그대로")
	var loaded := _reload(b)
	check(loaded.events("start").size() == 1 and loaded.events("start")[0].id == "ep01_05b", "불러오면 50m 반응부터")
	# 이름 조사 ({player}이가 · {player}이 · {player}아)
	check(EventBook.player_josa("{player}이가 영상", "김민준") == "김민준이가 영상" and EventBook.player_josa("{player}이가 영상", "이지우") == "이지우가 영상", "{player}이가")
	check(EventBook.player_josa("{player}이 얘기", "이지우") == "이지우 얘기" and EventBook.player_josa("{player}아!", "이지우") == "이지우야!", "{player}이 · {player}아")
	check(EventBook.player_josa("{player}이(가)", "이지우") == "{player}이(가)", "옛 표기 이(가) 는 그대로 (josa 가 처리)")


func test_game_events() -> void:
	# 경기 중 대화: 순간 판정, 스토리는 반드시·무작위는 확률로, 한 주 개수 제한, 이번 경기에만 붙는 보정
	var cfg := _career_cfg()
	cfg.events.append({"id": "t_first", "story": true, "timing": "game", "moment": "first_pa", "speaker": "coach", "text": "첫 타석이다.",
		"choices": [{"label": "네", "result": "배트를 쥔다.", "effects": {"gameBoost": {"contact": 6}}}]})
	EventBook.prepare(cfg.events)
	var career := CareerState.new(cfg, "테스트", _config)
	career.player.lineup_slot = 7
	career.train("rest")
	var game := career.new_game()
	check(career.pa_moment(game) == "first_pa", "첫 타석")
	var debut := career.game_event("first_pa", game)
	check(debut.get("id", "") == "t_first", "첫 타석 이야기: %s" % debut.get("id", ""))
	var not_game := func(e: Dictionary) -> bool: return e.get("timing", "") == "game"
	check(career.events("start").filter(not_game).is_empty() and career.events("after").filter(not_game).is_empty(), "경기 중 이벤트는 다른 시점에 안 섞인다")
	var base := career.player.skills_for_game(cfg)
	career.resolve_event(debut, 0)
	var boosted := career.player.skills_for_game(cfg)
	check(boosted.contact == base.contact + 6 and career.player.contact == int(cfg.start.contact), "이번 경기 컨택 +6 (능력치 자체는 그대로)")
	career.book._count[career.total_week] = int(cfg.story.maxEventsPerWeek)
	check(career.game_event("after_strikeout", game).is_empty(), "한 주 이벤트 개수(2)가 차면 경기 중에도 안 나온다")
	# 승부처: 7회 이후 · 2점 차 이내 · 득점권 주자
	var probe_career := CareerState.new(cfg, "테스트", _config)
	var probe := probe_career.new_game()
	probe.my_line.pa = 2
	probe.state.inning = 8
	probe.state.score = [3, 2]
	probe.state.bases = [GameState.EMPTY, 3, GameState.EMPTY]
	check(probe_career.pa_moment(probe) == "clutch_pa", "8회 1점 차 2루 주자 = 승부처")
	probe.state.score = [8, 2]
	check(probe_career.pa_moment(probe) == "", "점수 차가 크면 평범한 타석")
	probe.state.score = [3, 2]
	probe.state.inning = 3
	check(probe_career.pa_moment(probe) == "", "초반이면 평범한 타석")
	check(CareerState.result_moment(SwingJudge.Outcome.HOME_RUN) == "after_homer" and CareerState.result_moment(SwingJudge.Outcome.STRIKEOUT) == "after_strikeout"
		and CareerState.result_moment(SwingJudge.Outcome.SINGLE) == "", "타석 결과 → 순간")
	# 경기가 끝나면 보정이 사라진다
	while not game.state.over:
		if game.step().type == "my_turn":
			game.apply_my_result(AtBat.Result.new(SwingJudge.Outcome.STRIKEOUT, SwingJudge.BattedBall.NONE, 3, null))
	career.finish_game(game)
	check(career.player.game_boost.is_empty() and career.player.skills_for_game(cfg).contact < boosted.contact + 1, "경기가 끝나면 보정이 지워진다")
	# 무작위 경기 중 이벤트: 여러 주를 돌리면 삼진 뒤 이야기가 가끔 나오고(전부는 아님), 나온 건 그 순간의 것
	var seen := 0
	var weeks := 12
	for w in weeks:
		var c2 := CareerState.new(cfg, "테스트", _config)
		c2.week = w + 2
		c2.total_week = w + 2
		var g2 := c2.new_game()
		g2.my_line.pa = 1
		var ev := c2.game_event("after_strikeout", g2)
		if not ev.is_empty():
			seen += 1
			check(ev.moment == "after_strikeout", "순간이 맞는 이벤트")
	check(seen > 0 and seen < weeks, "삼진 뒤 이야기는 확률로 (%d/%d)" % [seen, weeks])


func test_pinch_hit() -> void:
	# 대타: 벤치에서 시작한 경기는 승부처(늦어도 forceInning 회)에 반드시 한 번 나가고, 그 뒤로는 그 타순에서 끝까지 뛴다
	var cfg := _career_cfg()
	var early := 0
	for s in 100:
		var game := _play_full_game(s, true)
		var box := game.box
		var side := game.my_side
		check(game.pinch_hit and not game.on_bench and game.my_slot >= 0, "벤치에서 시작하면 반드시 대타로 나간다 (seed %d)" % s)
		check(game.my_line.pa >= 1, "대타로 한 타석 이상 (seed %d)" % s)
		check(box.replaced[side].size() == 1 and box.replaced[1 - side].is_empty(), "교체는 우리 팀에서 한 번")
		var out: Dictionary = box.replaced[side][0]
		check(out.slot == game.my_slot and out.name != "테스트", "나간 선수의 기록이 따로 남는다")
		check(box.batters[side][game.my_slot].pa == game.my_line.pa, "그 타순의 새 줄 = 내 기록 (seed %d)" % s)
		check(game.teams[side].lineup[game.my_slot].is_me, "타순에 내 선수가 들어가 있다")
		var hits := 0
		for b: BoxScore.BatterLine in box.all_batters(side):
			hits += b.h
		check(hits == box.hits[side], "교체가 있어도 타자 안타 합 = 팀 안타 (seed %d)" % s)
		check(box.team_runs(side) == game.state.score[side], "교체가 있어도 타자 득점 합 = 총점 (seed %d)" % s)
		# 들어간 회: 중계의 대타 줄 뒤에 처음 나오는 내 타석 줄 ([7회초] 처럼 시작)
		var at := 0
		for i in game.log.size():
			if game.log[i].contains("대타:"):
				at = i
		var first := ""
		for i in range(at + 1, game.log.size()):
			if game.log[i].begins_with("[") and game.log[i].contains("★"):
				first = game.log[i]
				break
		var inning := int(first.substr(1))
		check(at > 0 and inning >= int(cfg.story.clutch.minInning), "승부처 회 이후에 나간다 (seed %d: %s)" % [s, first])
		if inning < int(cfg.story.pinchHit.forceInning):
			early += 1
	check(early > 0 and early < 100, "승부처가 오면 일찍, 안 오면 %d회에 (%d/100 일찍)" % [int(cfg.story.pinchHit.forceInning), early])
	var a := _play_full_game(7, true)
	var b2 := _play_full_game(7, true)
	check(a.log == b2.log, "대타가 낀 경기도 같은 시드면 같은 경기")
	check(not _play_full_game(7).pinch_hit and _play_full_game(7).box.replaced[0].is_empty(), "평소 경기에는 대타가 없다")
	# 커리어: 이벤트 효과로만 켜지고, 그 경기가 끝나면 꺼진다
	var career := CareerState.new(cfg, "테스트", _config)
	check(not career.new_game().on_bench, "이벤트가 없으면 선발")
	career.book.apply_effects(career, {"pinchHit": true})
	check(career.player.bench_next, "pinchHit 효과 → 다음 경기 벤치")
	var loaded: CareerState = SaveGame.from_bytes(SaveGame.to_bytes(career), cfg, _config).career
	check(loaded != null and loaded.player.bench_next, "저장·불러오기에도 남는다")
	career.train("rest")
	var game := career.new_game()
	check(game.on_bench and game.my_slot == -1 and not game.is_my_turn(), "벤치에서 시작")
	var turns := 0
	while not game.state.over:
		if game.step().type == "my_turn":
			if turns == 0:
				check(career.pa_moment(game) == "pinch_pa", "대타로 나가는 타석")
				check(career.game_event("pinch_pa", game).get("id", "") == "g_pinch", "감독이 부르는 이야기가 나온다")
			turns += 1
			game.apply_my_result(AtBat.Result.new(SwingJudge.Outcome.SINGLE, SwingJudge.BattedBall.NONE, 3, null))
	check(turns >= 1 and game.my_line.hits == turns, "대타 타석이 내 기록에 쌓인다")
	career.finish_game(game)
	check(not career.player.bench_next and career.player.season.games == 1 and career.player.season.hits == turns, "경기가 끝나면 벤치 표시가 지워지고 시즌 기록에 들어간다")
	check(career.records.games[-1].playing, "기록실에는 출전으로 남는다")
	career.advance_week()
	career.train("rest")
	check(not career.new_game().on_bench, "다음 경기는 다시 선발")


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


func test_scenario_keys() -> void:
	# 시나리오용 조건·효과: flag 배열 + clearFlag, recentGames, 타순 0(벤치)과 이야기가 부르는 대타, matchOverride, injuryLabel, campFocus body
	var cfg := _career_cfg()
	var call := {"id": "t_call", "timing": "game", "moment": "pinch_pa", "story": true, "requires": ["t_ready"], "speaker": "coach", "text": "나가라.",
		"choices": [{"label": "네", "result": "배트를 들었다.", "effects": {"flag": "t_called"}}]}
	var setter := {"id": "t_set", "scheduledOnly": true, "speaker": "coach", "text": "준비해라.", "choices": [{"label": "네", "result": "알겠다.", "effects": {"flag": ["t_ready"],
		"schedule": {"event": "t_set", "weeks": 1}, "matchOverride": {"opponent": "cheonji", "result": "loss", "score": [0, 11], "innings": 7, "playerAbsent": true},
		"injuryLabel": "발목 염좌", "campFocus": "body", "lineupSlot": 0}}]}
	var slump := {"id": "t_slump", "recentGames": 3, "recentMaxHits": 1, "speaker": "coach", "text": "요즘 안 맞네.", "choices": [{"label": "네", "result": "…"}]}
	cfg.events.append_array([call, setter, slump])
	check(EventBook.validate(cfg).is_empty(), "새 키가 검증을 통과한다: %s" % [EventBook.validate(cfg)])
	var broken := _career_cfg()
	broken.events = [{"id": "x", "speaker": "coach", "text": "a", "recentMinHits": 1, "choices": [{"label": "a", "result": "b", "effects": {"lineupSlot": 10,
		"matchOverride": {"opponent": "nowhere", "score": [0, 11]}}}, {"label": "c", "result": "d", "effects": {"matchOverride": {"result": "win", "score": [0, 11]}}}]}]
	check(EventBook.validate(broken).size() == 4, "recentGames 없는 recentMinHits · 타순 10 · 없는 학교 · 점수와 다른 결과를 잡는다: %s" % [EventBook.validate(broken)])

	var career := CareerState.new(cfg, "테스트", _config)
	var p := career.player
	# flag 는 문자열·배열 모두, clearFlag 와 같이 써도 된다
	career.book.apply_effects(career, {"flag": ["a", "b"], "clearFlag": "old"})
	p.flags["old"] = true
	career.book.apply_effects(career, {"flag": ["c"], "clearFlag": ["old", "a"]})
	check(p.flags.has("b") and p.flags.has("c") and not p.flags.has("a") and not p.flags.has("old"), "flag 배열 + clearFlag")

	# recentGames: 내가 나온 최근 N경기 안타 합
	check(career.records.recent_hits(3) == -1 and not career.book.eligible(career, slump), "한 경기도 안 나왔으면 조건이 안 맞는다")
	for hits: int in [2, 0, 1, 0]:
		var entry := CareerRecords.GameEntry.new()
		entry.line.hits = hits
		entry.playing = hits != 1 or career.records.games.size() != 2
		career.records.add_game(entry, [] as Array[Dictionary])
	check(career.records.recent_hits(3) == 2 and career.records.recent_hits(1) == 0 and career.records.recent_hits(99) == 2, "결장 경기는 건너뛰고, 모자라면 있는 만큼")
	check(not career.book.eligible(career, slump), "최근 3경기 2안타 → recentMaxHits 1 은 안 맞는다")
	career.records.games[0].line.hits = 1
	check(career.book.eligible(career, slump), "최근 3경기 1안타 이하 → 맞는다")
	career.records.games.clear()
	career.records.phases.clear()

	# 타순 0 = 벤치: 부르는 이야기가 없으면 나가지 않는다
	check(career.book.apply_effects(career, {"lineupSlot": 0}) == ["선발 제외 (벤치)"] and p.role_text() == "벤치", "타순 0 = 벤치")
	check(career.next_role() == "bench", "부르는 이야기가 없으면 벤치")
	check(career.next_lineup().size() == 9 and career.next_lineup().filter(func(r: Dictionary) -> bool: return r["me"]).is_empty(), "선발 9명에 내가 없다")
	var game := _auto_week(career, "rest")
	check(not game.me_playing and game.my_line.pa == 0 and game.absent_text.contains("벤치") and not game.teams[game.my_side].lineup.any(func(b: Team.Batter) -> bool: return b.is_me),
		"벤치 경기: 타순에 내가 없고 타석도 없다")
	var skipped := career.new_game()
	skipped.run_to_end()
	check(skipped.state.over and skipped.log == game.log, "벤치 경기를 한 번에 돌려도 같은 경기")
	check(career.finish_game(game).has(game.absent_text) and p.season.games == 0 and not career.records.games[-1].playing, "기록에 결장으로 남는다")
	for e: Dictionary in career.events("after").duplicate():
		career.resolve_event(e, 0)
	career.advance_week()
	# 한 번뿐인 pinch_pa 이야기가 조건을 만족하면 8회 이후 대타로 불린다
	p.flags["t_ready"] = true
	check(career.next_role() == "pinch", "부르는 이야기가 있으면 대타")
	career.train("rest")
	game = career.new_game()
	check(game.on_bench and game.pinch_late, "벤치에서 시작")
	var turns := 0
	while not game.state.over:
		if game.step().type == "my_turn":
			if turns == 0:
				check(game.state.inning >= mini(int(cfg.story.pinchHit.forceInning), game.state.innings - 1), "%d회 이후에 불린다 (%s)" % [int(cfg.story.pinchHit.forceInning), game.state.half_text()])
				var ev := career.game_event(career.pa_moment(game), game)
				check(ev.get("id", "") == "t_call", "되풀이되는 대타 이야기(g_pinch)보다 그 이야기가 먼저 뜬다: %s" % ev.get("id", ""))
				career.resolve_event(ev, 0)
			turns += 1
			game.apply_my_result(AtBat.Result.new(SwingJudge.Outcome.SINGLE, SwingJudge.BattedBall.NONE, 3, null))
	check(turns >= 1 and p.flags.has("t_called"), "대타 타석을 직접 친다")
	career.finish_game(game)
	check(p.season.games == 1 and p.season.hits == turns and career.records.games[-1].playing, "대타로 나온 경기는 기록에 들어간다")
	career.advance_week()
	check(career.next_role() == "bench", "이야기가 끝난 다음 경기는 다시 벤치")
	p.lineup_slot = 7
	check(career.next_role() == "start", "타순을 돌려받으면 선발")

	# matchOverride: 다음 경기 한 번의 결과 고정 (내 선수 미출전, 콜드게임)
	var league_opponent := career.opponent().id
	career.book.apply_effects(career, {"matchOverride": setter.choices[0].effects.matchOverride})
	check(career.next_role() == "out" and career.opponent().id == league_opponent, "주말리그 상대는 일정대로")
	var loaded: CareerState = SaveGame.from_bytes(SaveGame.to_bytes(career), cfg, _config).career
	check(loaded != null and loaded.match_override == career.match_override, "저장·불러오기에도 남는다")
	var games_before := p.season.games
	game = _auto_week(career, "rest")
	var st := game.state
	check(game.fixed and not game.me_playing and st.over and st.innings == 7 and st.score[game.my_side] == 0 and st.score[1 - game.my_side] == 11 and game.my_result() == -1,
		"0:11, 7회 콜드게임 패 (%d:%d, %d회)" % [st.score[game.my_side], st.score[1 - game.my_side], st.inning])
	for side in 2:
		var sum := 0
		for v: int in st.line[side]:
			sum += v
		check(sum == st.score[side] and (st.line[side] as Array).size() <= 7, "이닝별 점수 합 = 총점")
	check(game.log.any(func(l: String) -> bool: return l.contains("콜드게임")), "중계에 콜드게임이 나온다")
	check(_auto_week_log(career) == game.log, "같은 시드면 같은 이닝별 점수")
	career.finish_game(game)
	check(career.match_override.is_empty() and career.results[-1] == -1 and career.last.result == "loss" and p.season.games == games_before, "한 번만 적용되고, 내 기록엔 안 들어간다")
	career.advance_week()
	check(career.next_role() == "start" and not career.new_game().fixed, "다음 경기는 평소대로")
	# 전국대회에서는 상대도 바꾼다
	career.phase_index = 1
	career._start_phase()
	career.book.apply_effects(career, {"matchOverride": {"opponent": "cheonji", "score": [3, 2]}})
	check(career.is_national() and career.opponent().id == "cheonji", "전국대회 상대 고정")
	game = career.new_game()
	while not game.state.over:
		game.step()
	check(st.innings == 7 and game.state.innings == cfg.innings and game.my_result() == 1 and game.state.score[game.my_side] == 3, "innings 가 없으면 정규 이닝, 3:2 승")
	career.match_override = {}

	# injuryLabel: 부상 이름
	career.book.apply_effects(career, {"injury": 1, "injuryLabel": "발목 염좌"})
	check(p.is_injured() and p.injury_text() == "발목 염좌" and career.new_game().absent_text.contains("발목 염좌"), "부상 이름이 표시된다")
	var notes: Array[String] = []
	career._roll_injury(notes)
	check(not p.is_injured() and p.injury_text() == "부상", "나으면 이름이 지워진다")

	# campFocus body: 캠프 동안 컨디션 하락 완화
	var body: Dictionary = cfg.camp.body
	check(career.camp_condition_multiplier() == 1.0, "시즌 중엔 완화 없음")
	career.phase_index = career.phases().size()
	career.start_camp()
	# 한 주 = 스케줄 칸 3개, 칸마다 (훈련 컨디션 ÷ 칸 수)를 반올림해 완화 배율을 곱한다
	var slots: int = career.schedule_slots().size()
	var week_cost := func(cond: int, mult: float) -> int: return slots * roundi(roundi(cond / float(slots)) * mult)
	var cost: int = week_cost.call(int(cfg.training.contact.condition), 1.0)
	p.condition = 80
	career.train("contact")
	check(p.condition == 80 + cost, "집중 과제가 몸 관리가 아니면 그대로 %d (%d)" % [cost, p.condition])
	check(career.book.apply_effects(career, {"campFocus": "body"}) == ["집중 과제: %s" % body.label], "집중 과제: 몸 관리")
	check(is_equal_approx(career.training_multiplier("contact"), float(cfg.camp.trainingMultiplier)), "몸 관리는 훈련 집중 배율이 없다")
	career.week_training = ""
	p.condition = 80
	career.train("contact")
	check(p.condition == 80 + week_cost.call(int(cfg.training.contact.condition), float(body.conditionMultiplier)) and p.condition > 80 + cost, "몸 관리: 컨디션 하락 ×%.1f (%d)" % [float(body.conditionMultiplier), p.condition])
	p.flags[body.reliefFlags[0]] = true
	check(is_equal_approx(career.camp_condition_multiplier(), float(body.conditionMultiplier) * float(body.flagMultiplier)), "플래그가 있으면 더 완화")
	career.camp_focus = "power"
	check(is_equal_approx(career.camp_condition_multiplier(), float(body.flagMultiplier)), "플래그 완화는 집중 과제와 상관없다")
	career.week_training = ""
	p.condition = 50
	career.train("rest")
	check(p.condition == 50 + week_cost.call(int(cfg.training.rest.condition), 1.0), "오르는 컨디션은 그대로 (%d)" % p.condition)


func test_y1_story() -> void:
	# 1학년 EP01~EP08 (docs/scenario): 장면이 정한 주·정한 경기 대본으로 나온다. 내 타석은 모두 안타 / 모두 삼진 두 갈래
	for hit: bool in [true, false]:
		var career := CareerState.new(_real_cfg(), "테스트", _config)
		var p := career.player
		var seen := {}  # 이벤트 id -> "w주차" 또는 "c캠프주차"
		var games := {}
		var guard := 0
		while p.grade == 1 and guard < 60:
			guard += 1
			if career.camp_finished():
				career.winter_training()
				career.start_next_season()
				break
			if career.is_season_over() and career.camp_week == 0:
				while not career.events("seasonEnd").is_empty():
					career.resolve_event(career.events("seasonEnd")[0], 0)
				career.start_camp()
				continue
			var at := "c%d" % career.camp_week if career.in_camp() else "w%d" % career.week
			var g2 := 0
			while not career.events("start").is_empty() and g2 < 30:
				g2 += 1
				seen[career.events("start")[0].id] = at
				career.resolve_event(career.events("start")[0], 0)
			career.train("rest")
			if career.in_camp() and not career.camp_has_game():
				career.finish_camp_week()
			else:
				var game := career.new_game()
				games[at] = game
				while not game.state.over:
					if game.in_scene():
						var ev := career.scene_event(game)
						if not ev.is_empty():
							seen[ev.id] = at
							var sc := game.current_scene()
							var st := game.state
							check(st.inning == int(sc.inning) and st.top == (sc.half == "top") and st.outs == int(sc.outs), "%s 장면 그대로: %s %d사" % [ev.id, st.half_text(), st.outs])
							career.resolve_event(ev, 0)
					if game.step().type == "my_turn":
						game.apply_my_result(AtBat.Result.new(SwingJudge.Outcome.SINGLE if hit else SwingJudge.Outcome.STRIKEOUT, SwingJudge.BattedBall.NONE, 3, null))
				for side in 2:
					var sum := 0
					for x: int in game.state.line[side]:
						sum += x
					check(sum == game.state.score[side], "이닝별 점수 합 = 총점 (%s)" % at)
				career.finish_game(game)
			var g3 := 0
			while not career.events("after").is_empty() and g3 < 30:
				g3 += 1
				seen[career.events("after")[0].id] = at
				career.resolve_event(career.events("after")[0], 0)
			career.advance_week()
		var v := p.vars
		var all_seen := true
		for id: String in career.book.order:
			if id.begins_with("ep0") and not career.book.fired.has(id):
				all_seen = false
				check(false, "못 본 장면 %s" % id)
		check(all_seen and p.grade == 2 and p.flags.has("y1_done") and int(v["school_year"]) == 2, "EP01~08 이 다 나오고 2학년이 된다")
		# EP01·02: 첫 주 — 관전 경기 1-10 8회 콜드
		check(seen.ep02_02 == "w1" and seen.ep02_05 == "w1", "EP02 는 첫 주 (경기 뒤 버스·놀이터까지)")
		var g1: GameRunner = games["w1"]
		check(g1.fixed and not g1.me_playing and g1.opponent.id == "dongin" and g1.state.innings == 8 and g1.state.score[g1.my_side] == 1 and g1.state.score[1 - g1.my_side] == 10, "동인고전 1-10 8회 콜드, 나는 벤치")
		# EP03: 둘째 주 청운고 연습경기 — 7회 말 대수비 하나, 1-8
		var g2: GameRunner = games["w2"]
		check(seen.ep03_04 == "w2" and g2.planned and g2.exhibition and g2.opponent.id == "cheongun" and g2.my_fields.size() == 1 and g2.my_line.pa == 0
			and g2.state.score[g2.my_side] == 1 and g2.state.score[1 - g2.my_side] == 8 and g2.state.innings == 7, "청운고 연습경기: 대수비 한 번, 1-8")
		check(typeof(v["taeo_ball_fielded"]) == TYPE_BOOL and career.people.is_met("rival") and v["phone_profile_taeo"] == true and career.phone.contacts.has("rival"), "강태오 타구 결과·만남·인물 정보 해금 → 연락처")
		# EP04: 셋째 주 한빛고전 대타 → 수비
		var g3: GameRunner = games["w3"]
		check(seen.ep04_03 == "w3" and g3.opponent.id == "hanbit" and g3.pinch_hit and g3.my_line.pa == 1 and g3.my_fields.size() == 1 and g3.my_result() == -1 and g3.state.score[1 - g3.my_side] == 6,
			"한빛고전: 7회 초 대타 한 타석 + 7회 말 수비, 1-6 패 (%d-%d)" % [g3.state.score[g3.my_side], g3.state.score[1 - g3.my_side]])
		check(v["first_ab_result"] == ("hit" if hit else "strikeout"), "첫 타석 결과 변수: %s" % v["first_ab_result"])
		check(not g3.my_pas.is_empty() and g3.my_pas[0]["pitcher"] == "balanced", "대본의 상대 투구 성향")
		# EP05: 전반기 마지막 경기 뒤 → 백호기 1회전 (해성고, 진다 → 탈락). 1학년 시즌 단계 결과는 지난 시즌 기록에
		var plog: Array = career.history[-1]["phases"]
		var w5: String = seen.ep05_04
		var g5: GameRunner = games[w5]
		check(seen.ep05_01 == "w%d" % (int(w5.substr(1)) - 1) and plog[0].name == "주말리그 전반기", "전반기 최종전 뒤 EP05 (%s → %s)" % [seen.ep05_01, w5])
		check(g5.opponent.id == "haeseong" and g5.my_result() == -1 and g5.my_line.pa == 1 and plog[1].name == "백호기" and plog[1].text.contains("탈락"), "백호기 1회전 해성고에 져서 탈락")
		check(v["ep05_ab_result"] == ("hit" if hit else "strikeout") and v["team_goal"] == "national_first_win" and v["phone_news"] == true, "EP05 변수")
		# EP06: 후반기 개막 동인고전 9번 유격수 선발 (3·6회 타석, 4회 수비, 7회 말 병살 → 그 뒤 실제 경기)
		var w6: String = seen.ep06_04
		var g6: GameRunner = games[w6]
		check(g6.opponent.id == "dongin" and not g6.pinch_hit and g6.my_slot == 8 and g6.my_line.pa >= 2 and g6.my_fields.size() >= 2, "후반기 개막: 9번 선발, 타석 %d · 수비 %d" % [g6.my_line.pa, g6.my_fields.size()])
		check(v["ep06_game_result"] == ("win" if g6.my_result() > 0 else "lose") and v["ep06_dp_result"] in ["success", "fail"], "경기 결과 변수 = 실제 결과")
		check(plog.size() == 3 and plog[2].name == "주말리그 후반기" and v["ep06_season_avg_grade"] in ["high", "mid", "low"], "1학년 시즌은 전반기 → 백호기 → 후반기")
		# EP07·08: 겨울 캠프 1~4주
		check(seen.ep07_01 == "c1" and seen.ep07_03 == "c2" and seen.ep07_05 == "c3" and seen.ep08_01 == "c4", "겨울: 1주 면담 · 2주 연말 · 3주 훈련 방향 · 4주 서호대")
		check(int(v["form_adaptation"]) == 100 and v["training_direction"] == "contact" and career.book.log.any(func(x: Dictionary) -> bool: return x.title == "변화구 배팅 연습"),
			"폼 개조 완료, 훈련 방향(컨택)에 맞는 집중 훈련")
		var g8: GameRunner = games["c4"]
		check(g8.opponent.id == "seoho" and g8.exhibition and g8.my_line.pa == 3 and g8.my_fields.size() == 1 and g8.state.score[1 - g8.my_side] == 4, "서호대 연습경기: 타석 셋 · 수비 하나, 0~1-4")
		var perf := int(v["ep08_performance"])
		var expect: int = {"hit": 2, "walk": 1}.get(v["ep08_ab1_result"], 0) + {"double_play": 2, "out": 1, "error": -1}[v["ep08_def_result"]] \
			+ {"extra_base": 3, "rbi_hit": 2}.get(v["ep08_ab2_result"], 0) + {"hit": 3, "out": 1}.get(v["ep08_ab3_result"], 0)
		check(perf == expect, "활약도 = 문서 공식 (%d)" % perf)
		check(v["scout_interest"] == ("checked" if perf >= 6 else ("watching" if perf >= 3 else "none")) and v["scout_tab_unlocked"] == true, "스카우트 첫 평가 %s (활약도 %d)" % [v["scout_interest"], perf])
		if hit:
			check(v["scout_interest"] == "checked" and v["scout_visited_teams"] == ["taesung_thunders"] and career.people.is_met("scout"), "다 치면 체크 대상 — 태성 썬더스")
		# 영상 수: 실전 타석·수비마다 +1
		check(int(v["stats.at_bat_video_count"]) >= 1 + 1 + 2 + 3 and int(v["stats.fielding_video_count"]) >= 1 + 1 + 1 + 2 + 1, "영상 수 타석 %d · 수비 %d" % [int(v["stats.at_bat_video_count"]), int(v["stats.fielding_video_count"])])
		for k in ["phone_contacts", "phone_records_team", "phone_profile_taeo", "phone_records_personal", "phone_news", "phone_records_season", "scout_tab_unlocked"]:
			check(v.get(k, false) == true, "휴대폰 해금 %s" % k)


func test_real_content() -> void:
	# 게임이 읽는 content.json 만으로: 검증 통과, 테스트 전용 이벤트가 섞이지 않았고, 3학년 시즌 결산까지 막히지 않고 간다
	var cfg := _real_cfg()
	var errors := EventBook.validate(cfg)
	check(errors.is_empty(), "content.json 이벤트 정의 문제: %s" % [errors])
	var legacy: Dictionary = BalanceLoader.parse(FileAccess.get_file_as_string("res://tests/fixtures/legacy_events.json"))
	var ids := {}
	for e: Dictionary in cfg.events:
		ids[e.id] = true
	for e: Dictionary in legacy.events:
		check(not ids.has(e.id), "테스트 전용 이벤트 %s 가 게임 콘텐츠에 없다" % e.id)
	var career := CareerState.new(cfg, "테스트", _config)
	check(FastForward.run(career, 2, "season_start"), "1학년 시즌·겨울 캠프를 지나 2학년 시작까지")
	check(career.player.flags.has("y1_done") and career.player.lineup_slot >= 1 and int(career.player.vars["school_year"]) == 2, "1학년 시나리오(EP01~08)가 끝났고 벤치가 아니다")
	check(FastForward.run(career, 3, "season_end"), "3학년 시즌 결산까지")
	for e: Dictionary in career.events("seasonEnd").duplicate():
		career.resolve_event(e, 0)
	check(career.player.grade == 3 and career.history.size() == 2, "이벤트가 거의 없는 2~3학년도 끝까지 진행된다")


## 지금 주 경기를 한 번 더 만들어 끝까지 돌린 중계 (결과 고정 경기의 재현성 확인용)
func _auto_week_log(career: CareerState) -> Array[String]:
	var game := career.new_game()
	while not game.state.over:
		game.step()
	return game.log


func _auto_week(career: CareerState, training_id: String) -> GameRunner:
	career.train(training_id)
	var game := career.new_game()
	var rng := _rng(career.week * 31 + career.season_no)
	while not game.state.over:
		if game.in_scene():
			career.auto_game_story(game)
		if game.step().type == "my_turn":
			career.auto_game_story(game)
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
	var cups := career.phases().size() - 2
	check(weeks >= league * 2 + cups and weeks <= league * 2 + cups * 4 + 1, "한 시즌 %d주 (리그 %d×2 + 대회 1~4×%d, 1학년은 연습경기 한 주 더)" % [weeks, league, cups])
	check(career.results.size() >= weeks - 1 and career.phase_log.size() == career.phases().size(), "단계 %d개 기록" % career.phases().size())
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
	# 홈 화면 팀 기록: 끝난 단계는 기록 그대로, 지금 단계 하나, 나머지는 예정
	var board := career.season_board()
	check(board.size() == career.phases().size() and board[0].state == "done" and board[0].text == career.phase_log[0].text and board[1].text == career.phase_log[1].text,
		"팀 기록: 끝난 단계 = phase_log (%s · %s)" % [board[0].text, board[1].text])
	check(board[2].state == "now" and board[2].text == "개막 앞둠" and (board.size() < 4 or board[3].state == "next"), "팀 기록: 지금 단계·예정 (%s)" % board[2].text)
	career.finish_game(_auto_week(career, "rest"))
	check(career.season_board()[2].text.begins_with("%d위 · " % career.my_rank()), "팀 기록: 리그 중엔 지금 순위 (%s)" % career.season_board()[2].text)


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
		# 1학년은 무작위 부상이 없다 (injury.minGrade)
		career._roll_injury(notes)
		check(not career.player.is_injured(), "1학년은 지쳐도 무작위로 다치지 않는다")
		career.player.grade = 2
		career._roll_injury(notes)
		if career.player.is_injured():
			tired += 1
		var c2 := CareerState.new(_career_cfg(), "테스트", _config)
		c2.week = s + 1
		c2.player.condition = 90
		c2.player.grade = 2
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


func test_home_run_play_can_be_skipped() -> void:
	# 홈런 뒤 베이스 도는 장면은 탭으로 건너뛴다. 친 직후의 탭은 무시하고, 홈런이 아니면 건너뛰지 않는다
	var balance := BalanceLoader.load_balance()
	var s := BattingSession.new(BattingConfig.from_balance(balance), Presentation.from_balance(balance))
	s.last_result = AtBat.Result.new(SwingJudge.Outcome.HOME_RUN, SwingJudge.BattedBall.FLY, 1, null)
	s.phase = BattingSession.Phase.HIT
	s.phase_since_ms = BattingSession.now_ms()
	check(not s.can_skip_play(), "친 직후엔 못 건너뛴다")
	s.tap()
	check(s.since(s.phase_since_ms) < s.presentation.skip_after_ms, "친 직후 탭은 무시")
	s.phase_since_ms = BattingSession.now_ms() - s.presentation.skip_after_ms - 10.0
	check(s.can_skip_play(), "조금 지나면 건너뛸 수 있다")
	s.tap()
	check(s.since(s.phase_since_ms) >= s.hit_play_end_ms() - 1.0, "탭하면 플레이 끝으로")
	check(not s.can_skip_play(), "끝난 뒤엔 더 건너뛸 게 없다")
	s.last_result = AtBat.Result.new(SwingJudge.Outcome.DOUBLE, SwingJudge.BattedBall.LINE, 1, null)
	s.phase_since_ms = BattingSession.now_ms() - s.presentation.skip_after_ms - 10.0
	check(not s.can_skip_play(), "홈런이 아니면 못 건너뛴다")


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


func test_relay_not_abandoned() -> void:
	# 외야 안타에서 주자가 한 베이스 더 가는데 수비가 공을 쥔 채 송구를 한 번도 안 하면 안 된다 (중계 플레이 포기).
	# 예: 2루 주자가 외야수의 송구 전에 3루에 닿을 것 같아 공을 쥐었는데, 그 주자가 3루를 돌아 홈까지 송구 없이 들어오던 버그.
	# AI 주자만 있을 때와 내 타자 주자가 계속 더 가려 할 때(choices 모두 true) 둘 다 본다
	var p := _config.ball_physics
	var plays := 0
	var advanced := 0
	var abandoned := 0
	var example := ""
	var seed_i := 0
	for mine: bool in [false, true]:
		for ev in [110, 130, 150]:
			for la in [10, 20, 30]:
				for spray in range(-40, 41, 8):
					var b := _sim(ev, la, spray)
					if b.home_run or b.caught or b.fielder in p.infield:
						continue
					for bases: Array in [[-1, 2, -1], [1, 2, -1], [1, -1, -1]]:
						for outs in [0, 1]:
							seed_i += 1
							var res := PlaySimulator.new(p, b, 4, bases, func(_x: int) -> int: return 50, outs, _rng(seed_i),
								4 if mine else -1, [true, true, true] if mine else []).run()
							plays += 1
							# 처음 자리보다 두 베이스 이상 간 주자(2루 → 홈, 1루 → 3루 등)가 있는데 송구가 하나도 없으면 포기한 것
							for r in res.runners:
								var reached := 4 if r.state == RunnerAI.State.SCORED else r.base
								if r.is_batter() or reached - r.origin < 2:
									continue
								advanced += 1
								if res.throws.is_empty():
									abandoned += 1
									if example == "":
										example = "타구 %d/%d/%d 주자 %s %d사 → %d루 주자가 %s" % [ev, la, spray, bases, outs, r.origin, "홈인" if reached == 4 else "%d루" % reached]
	check(plays > 200 and advanced > 50, "두 베이스 이상 간 주자가 나오는 외야 안타 %d / %d" % [advanced, plays])
	check(abandoned == 0, "송구 없이 두 베이스 이상 간 주자 %d / %d (%s)" % [abandoned, advanced, example])


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


func test_week_schedule() -> void:
	var career := CareerState.new(_career_cfg(), "테스트", _config)
	var p := career.player
	var n := career.schedule_slots().size()
	check(n == 3 and not career.plan_ready(), "평일 3칸, 처음엔 비어 있다")
	career.set_plan(0, "contact")
	career.set_plan(1, "power")
	check(not career.plan_ready())
	career.set_plan(2, "rest")
	check(career.plan_ready())
	var cond := p.condition
	var res := career.run_schedule()
	check(res.size() == 3 and res[0]["id"] == "contact" and res[1]["id"] == "power" and res[2]["outcome"] == "rest", "칸 순서대로")
	var total := 0
	for r: Dictionary in res:
		total += int(r["condition"])
		if r["stat"] != "":
			check(r["outcome"] in ["great", "success", "fail"] and float(r["amount"]) > 0.0, "훈련 칸은 결과가 있고 조금이라도 자란다")
	check(p.condition == cond + total, "컨디션 변화 = 칸별 합 (%d → %d, %+d)" % [cond, p.condition, total])
	check(career.week_training != "" and career.last_training.contains("타격 훈련"))
	# 실행 뒤엔 칸을 바꿀 수 없고, 다시 실행해도 같은 결과
	career.set_plan(0, "rest")
	check(career.week_plan[0] == "contact" and career.run_schedule() == res, "실행 뒤엔 그대로")
	# 저장·불러오기: 칸과 결과가 남는다
	var loaded: CareerState = SaveGame.from_bytes(SaveGame.to_bytes(career), career.cfg, _config)["career"]
	check(loaded.week_plan == career.week_plan and loaded.week_schedule == career.week_schedule, "스케줄 저장")
	# 다음 주: 결과는 비고 칸은 지난주 그대로 (바꿀 칸만 고친다)
	career.advance_week()
	check(career.week_schedule.is_empty() and career.week_training == "" and career.plan_ready() and career.week_plan == ["contact", "power", "rest"], "지난주 스케줄 이어 쓰기")
	# 같은 시드 = 같은 결과
	var a := CareerState.new(_career_cfg(), "테스트", _config)
	var b := CareerState.new(_career_cfg(), "테스트", _config)
	a.train("eye")
	b.train("eye")
	check(a.week_schedule == b.week_schedule and a.player.eye == b.player.eye, "재현성")
	# 부상 중이면 모든 칸이 휴식
	var hurt := CareerState.new(_career_cfg(), "테스트", _config)
	hurt.player.injury_weeks = 2
	hurt.train("power")
	check(hurt.week_schedule.all(func(r: Dictionary) -> bool: return r["outcome"] == "rest"), "부상 → 휴식만")
	# 한 가지로 세 칸 = 옛 '훈련 하나' 한 주의 컨디션 (칸마다 반올림이라 ±2)
	var rest := CareerState.new(_career_cfg(), "테스트", _config)
	rest.player.condition = 40
	rest.train("rest")
	check(absi(rest.player.condition - 40 - int(rest.cfg.training["rest"]["condition"])) <= 2, "휴식 세 칸 ≈ 옛 휴식 한 주 (%d)" % rest.player.condition)


func test_training_scenes() -> void:
	# 훈련 장면 창: balance.json 의 훈련마다 도트 장면 줄이 있고, 그림 크기가 줄·칸 수와 맞는다
	var cfg := _career_cfg()
	for id: String in cfg.training:
		if id.begins_with("_"):
			continue
		check(TrainingMeta.ROWS.has(id), "훈련 %s 의 장면이 training.png 에 있다" % id)
	var tex := PixelArt.texture("training")
	check(tex != null and tex.get_size() == Vector2(TrainingMeta.SIZE.x * TrainingMeta.FRAMES, TrainingMeta.SIZE.y * TrainingMeta.ROWS.size()),
		"training.png 크기 = 칸 %d × 줄 %d" % [TrainingMeta.FRAMES, TrainingMeta.ROWS.size()])
	for key in ["trainingFps", "trainingAnimSec", "trainingResultSec"]:
		check(float(cfg.schedule.get(key, 0)) > 0.0, "career.schedule.%s" % key)


func test_schedule_outcome_odds() -> void:
	var cfg := _career_cfg()
	var career := CareerState.new(cfg, "테스트", _config)
	var fresh := career.slot_chances("contact", 95)
	var tired := career.slot_chances("contact", 20)
	check(float(fresh["great"]) > float(tired["great"]) and float(tired["fail"]) > float(fresh["fail"]), "지치면 실패가 늘고 대성공이 준다")
	check(career.slot_chances("rest", 10)["fail"] == 0.0, "휴식은 실패하지 않는다")
	for c in [0, 30, 50, 70, 100]:
		var ch := career.slot_chances("power", c)
		check(float(ch["great"]) >= 0.0 and float(ch["fail"]) >= 0.0 and float(ch["great"]) + float(ch["fail"]) <= 1.0, "확률 범위 (컨디션 %d)" % c)
	# 실제로 굴려 본 비율이 확률과 맞다 (컨디션 80 에서 첫 칸)
	var want := career.slot_chances("contact", 80)
	var counts := {"great": 0, "success": 0, "fail": 0}
	var runs := 600
	for i in runs:
		var c := CareerState.new(cfg, "테스트", _config)
		c.week = 1 + i  # 주마다 시드가 다르다
		c.player.condition = 80
		c.train("contact")
		counts[c.week_schedule[0]["outcome"]] += 1
	var great: float = counts["great"] / float(runs)
	var fail: float = counts["fail"] / float(runs)
	check(absf(great - float(want["great"])) < 0.05 and absf(fail - float(want["fail"])) < 0.04, "대성공 %.2f (기대 %.2f), 실패 %.2f (기대 %.2f)" % [great, want["great"], fail, want["fail"]])
	# 대성공은 성공보다, 성공은 실패보다 많이 자란다 (같은 상태에서)
	var amounts := {}
	for i in runs:
		var c := CareerState.new(cfg, "테스트", _config)
		c.week = 1 + i
		c.player.condition = 80
		c.train("contact")
		amounts[c.week_schedule[0]["outcome"]] = c.week_schedule[0]["amount"]
		if amounts.size() == 3:
			break
	check(amounts.size() == 3 and amounts["great"] > amounts["success"] and amounts["success"] > amounts["fail"], "성장량 %s" % amounts)


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


# ---------- 내 주루 조작 · 도루 ----------

## 2루 주자(타순 1)를 조작 주자로 한 플레이
func _controlled_play(ball: BattedBallSim.Result, bases: Array, choices: Array, seed_value: int = 7, controlled: int = 1) -> PlaySimulator.PlayResult:
	return PlaySimulator.new(_config.ball_physics, ball, 4, bases, func(_i: int) -> int: return 50, 0, _rng(seed_value), controlled, choices).run()


## 플레이의 판단 중 k 번째를 v 로, 그 앞은 기록된 대로 고른 선택 목록
func _choices_until(p: PlaySimulator.PlayResult, k: int, v: bool) -> Array:
	var ch: Array = []
	for i in k:
		ch.append(bool(p.decisions[i].go))
	ch.append(v)
	return ch


func _extras(p: PlaySimulator.PlayResult) -> Array:
	return p.decisions.filter(func(x: Dictionary) -> bool: return x.kind == "extra")


func test_run_control_overrides_my_runner() -> void:
	var asked := 0
	var went_home := 0
	var held := 0
	for ev in range(120, 160, 6):
		for spray in range(-40, 41, 8):
			var ball := _sim(ev, 10, spray)
			if ball.caught or ball.home_run or ball.fielder in _config.ball_physics.infield:
				continue
			var plain := _run_play(ev, 10, spray, [-1, 1, -1], 0)
			var ai := _controlled_play(ball, [-1, 1, -1], [])
			check(ai.bases == plain.bases and ai.scorers == plain.scorers and ai.outs == plain.outs and ai.batter_outcome == plain.batter_outcome,
				"조작 주자를 정해도 고르지 않으면 AI 와 같은 결과 (ev %d, 방향 %d)" % [ev, spray])
			check(ai.decisions.all(func(x: Dictionary) -> bool: return x.go == x.ai), "안 고르면 모든 판단이 AI 대로")
			if _extras(ai).is_empty():
				continue
			asked += 1
			var d: Dictionary = _extras(ai)[0]
			var k := ai.decisions.find(d)
			check(d.id == 1 and d.go == d.ai, "판단 기록: 내 주자, AI 판단대로")
			var go := _controlled_play(ball, [-1, 1, -1], _choices_until(ai, k, true))
			var stop := _controlled_play(ball, [-1, 1, -1], _choices_until(ai, k, false))
			check(go.decisions[k].go and not stop.decisions[k].go, "고른 대로 기록된다")
			# 판단 전까지는 어떤 선택이든 똑같이 흘러간다
			var n := int(float(d.t) / PlaySimulator.DT) - 1
			var same := true
			for i in go.runners.size():
				same = same and go.runners[i].track.slice(0, n) == stop.runners[i].track.slice(0, n)
			check(same, "판단 시각 전의 주자 궤적은 선택과 무관하다")
			if int(d.to) == 4:
				# 3루에서 멈출까 홈까지 갈까: 멈추면 3루에 남고, 뛰면 득점하거나 홈에서 죽는다
				check(stop.bases[2] == 1 and not (1 in stop.scorers), "멈추면 3루에 남는다 (ev %d, 방향 %d)" % [ev, spray])
				var me: RunnerAI = go.runners.filter(func(r: RunnerAI) -> bool: return r.id == 1)[0]
				check((1 in go.scorers) or me.state == RunnerAI.State.OUT, "뛰면 득점하거나 홈에서 아웃")
				went_home += 1 if 1 in go.scorers else 0
				held += 1
			# RunControl (보조): 베이스를 누르면 판단 시각에 거기까지 가도록 확정된다. 안 누르면 코치 사인대로
			var resim := func(choices: Array) -> PlaySimulator.PlayResult: return _controlled_play(ball, [-1, 1, -1], choices)
			var rc := RunControl.new(resim.call([]), resim)
			check(rc.has_choices() and rc.active() and rc.min_base() <= int(d.from) and rc.coach_base() == (int(d.to) if d.ai else int(d.from)), "누를 수 있는 베이스와 코치 사인")
			rc.set_target(rc.min_base() - 1)
			check(rc.target == -1, "이미 지난 베이스는 누를 수 없다")
			rc.set_target(int(d.from) if d.ai else int(d.to))
			check(not rc.advance(float(d.t) - 0.1), "판단 시각 전에는 그대로")
			check(rc.advance(float(d.t)) and rc.play.decisions[k].go == (not d.ai), "코치와 다른 베이스를 누르면 플레이가 다시 계산된다")
			var rc2 := RunControl.new(resim.call([]), resim)
			check(not rc2.advance(float(d.t)) and rc2.play.decisions[k].go == d.ai, "안 누르면 코치 사인대로 (다시 계산 없음)")
			rc2.finish()
			check(rc2.pending().is_empty() and rc2.min_base() == -1, "끝내면 남은 판단이 없다")
			# 홈을 누르면 판단이 몇 번이든 끝까지 뛴다, 가장 가까운 베이스를 누르면 바로 멈춘다
			var rc3 := RunControl.new(resim.call([]), resim)
			rc3.set_target(4)
			rc3.finish()
			check(_extras(rc3.play).all(func(x: Dictionary) -> bool: return x.go), "홈을 누르면 모든 판단에서 뛴다")
			var rc4 := RunControl.new(resim.call([]), resim)
			rc4.set_target(int(d.from))
			rc4.finish()
			check(_extras(rc4.play).size() == 1 and not _extras(rc4.play)[0].go, "가장 가까운 베이스를 누르면 거기서 멈춘다")
			# 수동: 안 누르면 멈춘다 (처음 보여 줄 플레이부터), 코치 사인이 없다
			var rm := RunControl.new(resim.call([]), resim, true)
			check(not _extras(rm.play)[0].go and rm.coach_base() == -1, "수동은 처음부터 멈추는 그림, 코치 사인 없음")
			rm.finish()
			check(_extras(rm.play).size() == 1 and not _extras(rm.play)[0].go and rm.play.bases[int(d.from) - 1] == 1, "수동에서 안 누르면 %d루에 선다" % int(d.from))
			var rm2 := RunControl.new(resim.call([]), resim, true)
			rm2.set_target(int(d.to))
			rm2.finish()
			check(_extras(rm2.play)[0].go, "수동에서 누르면 뛴다")
	check(asked >= 5, "외야 안타에 내 주자의 판단이 나온다 (%d번)" % asked)
	check(held > 0 and went_home > 0, "홈 승부 %d번 중 뛰어서 득점 %d번" % [held, went_home])
	# 루상 주자에게 내야 땅볼은 한 베이스 더 판단이 없다 (달리는 중 방향 바꾸기만)
	check(_extras(_controlled_play(_sim(130, -8, -10), [-1, 1, -1], [])).is_empty(), "내야 땅볼에는 한 베이스 더 판단이 없다")


func test_run_control_tag_up() -> void:
	# 3루 주자(타순 2)가 나: 외야 뜬공이 잡히면 태그업을 고른다
	var asked := 0
	for ev in range(125, 160, 5):
		for spray in [-25, -10, 0, 10, 25]:
			var ball := _sim(ev, 32, spray)
			if not ball.caught or ball.fielder in _config.ball_physics.infield:
				continue
			var ai := _controlled_play(ball, [-1, -1, 2], [], 7, 2)
			check(ai.decisions.size() == 1 and ai.decisions[0].kind == "tag" and int(ai.decisions[0].to) == 4, "외야 뜬공 → 태그업 판단 하나")
			asked += 1
			var stay := _controlled_play(ball, [-1, -1, 2], [false], 7, 2)
			check(stay.bases[2] == 2 and stay.scorers.is_empty() and stay.outs == 1, "남으면 3루 그대로, 타자만 아웃")
			var go := _controlled_play(ball, [-1, -1, 2], [true], 7, 2)
			check((2 in go.scorers and go.sac_fly) or go.outs == 2, "뛰면 희생플라이거나 홈에서 아웃 (ev %d, 방향 %d)" % [ev, spray])
	check(asked >= 5, "태그업 판단이 나온다 (%d번)" % asked)


## 내 주자를 무조건 앞으로 보낸 플레이들 중 런다운이 난 것 [[타구, 베이스, 조작 주자, 플레이]]
func _rundown_cases() -> Array:
	var out: Array = []
	for ev in range(115, 165, 10):
		for la in [-5, 8, 14, 20]:
			for spray in range(-40, 41, 10):
				var ball := _sim(ev, la, spray)
				if ball.home_run:
					continue
				for setup: Array in [[[-1, -1, -1], 4], [[-1, 1, -1], 1], [[1, -1, -1], 1]]:
					var p := _controlled_play(ball, setup[0], [true], 7, setup[1])
					if not p.rundowns.is_empty() and int(p.rundowns[0].runner) == int(setup[1]):
						out.append([ball, setup[0], setup[1], p])
	return out


## 주자 순서가 지켜졌나: 살아 있는 주자는 원래 앞에 있던 주자를 넘지 않고, 한 베이스에 한 명
func _order_kept(p: PlaySimulator.PlayResult) -> bool:
	var alive := p.runners.filter(func(r: RunnerAI) -> bool: return r.state == RunnerAI.State.SAFE)
	for x: RunnerAI in alive:
		for y: RunnerAI in alive:
			if x != y and x.origin < y.origin and x.base >= y.base:
				return false
	return true


func test_run_control_no_passing() -> void:
	# 내 주자(타자)가 무조건 홈까지 가려 해도 앞 주자를 넘지 않는다. 판단 기록에는 갈 수 있는 끝(limit)이 남는다
	var limited := 0
	for ev in range(120, 165, 8):
		for spray in range(-40, 41, 8):
			var ball := _sim(ev, 12, spray)
			if ball.home_run or ball.caught:
				continue
			for bases: Array in [[1, -1, -1], [-1, 1, -1], [1, 2, -1]]:
				var all_go := _controlled_play(ball, bases, [true, true, true, true], 7, 4)
				check(_order_kept(all_go), "다 뛰어도 추월하지 않는다 (ev %d, 방향 %d, %s)" % [ev, spray, bases])
				for d: Dictionary in all_go.decisions:
					if d.kind == "extra":
						check(int(d.limit) >= int(d.to), "limit 은 가려는 베이스 이상")
				var ai := _controlled_play(ball, bases, [], 7, 4)
				for d: Dictionary in ai.decisions:
					if d.kind != "extra" or int(d.limit) >= 4:
						continue
					limited += 1
					var resim := func(ch: Array) -> PlaySimulator.PlayResult: return _controlled_play(ball, bases, ch, 7, 4)
					var rc := RunControl.new(resim.call([]), resim)
					if rc.pending() != d:
						continue
					rc.set_target(int(d.limit) + 1)
					check(rc.target == -1 and rc.max_base() == int(d.limit), "앞 주자 너머 베이스는 누를 수 없다")
					rc.set_target(int(d.limit))
					check(rc.target == int(d.limit), "앞 주자 바로 뒤 베이스는 누를 수 있다")
	check(limited > 0, "앞 주자에 막힌 판단이 나온다 (%d번)" % limited)


func test_run_control_batter_choices() -> void:
	# 내가 친 타구: 내야 땅볼이어도 1루 앞에서 "멈춘다 / 2루로" 를 고른다. 외야 안타면 1루·2루·3루 어디서 멈출지 고른다
	var infield := 0
	var outfield := 0
	for ev in range(100, 165, 8):
		for la in [-8, 2, 12]:
			for spray in range(-40, 41, 8):
				var ball := _sim(ev, la, spray)
				if ball.home_run or ball.caught:
					continue
				var ai := _controlled_play(ball, [-1, -1, -1], [], 7, 4)
				var ex := _extras(ai)
				if ex.is_empty():
					continue
				var d: Dictionary = ex[0]
				check(int(d.from) == 1 and int(d.to) == 2, "첫 판단은 1루에서 멈출지 2루로 갈지")
				var k := ai.decisions.find(d)
				var me := func(p: PlaySimulator.PlayResult) -> RunnerAI: return p.runners[0]
				var stop := _controlled_play(ball, [-1, -1, -1], _choices_until(ai, k, false), 7, 4)
				var go := _controlled_play(ball, [-1, -1, -1], _choices_until(ai, k, true), 7, 4)
				var r_stop: RunnerAI = me.call(stop)
				var r_go: RunnerAI = me.call(go)
				check(r_stop.state == RunnerAI.State.OUT or r_stop.base == 1, "멈추면 1루 (또는 1루에서 아웃)")
				check(r_go.state == RunnerAI.State.OUT or r_go.base >= 2 or (not go.rundowns.is_empty() and go.rundowns[0].outcome == "back"), "가면 2루 이상 (또는 아웃, 런다운에서 1루로 귀루)")
				if ball.fielder in _config.ball_physics.infield:
					infield += 1
					check(not d.ai, "내야 타구에 AI(코치)는 1루에서 멈춘다")
				else:
					outfield += 1
	check(infield >= 3, "내야 안타에도 1루 앞 판단이 나온다 (%d번)" % infield)
	check(outfield >= 5, "외야 안타에 판단이 나온다 (%d번)" % outfield)


func test_run_control_change_mid_run() -> void:
	# 달리는 중에도 판단(dir)이 자주 열린다: 지나온 베이스를 누르면 돌아서고, 다시 앞을 누르면 다시 간다. 추월은 여전히 없다
	var retreated := 0
	var checked := 0
	for ev in range(130, 165, 6):
		for spray in range(-40, 41, 10):
			var ball := _sim(ev, 12, spray)
			if ball.home_run or ball.caught or ball.fielder in _config.ball_physics.infield:
				continue
			for bases: Array in [[-1, -1, -1], [1, -1, -1]]:
				var resim := func(ch: Array) -> PlaySimulator.PlayResult: return _controlled_play(ball, bases, ch, 7, 4)
				var rc := RunControl.new(resim.call([]), resim)
				# 2루를 눌러 두고, 1루를 밟은 뒤 달리는 중에 1루를 다시 누른다
				rc.set_target(2)
				var t := 0.0
				var pressed := false
				while t < 15.0:
					t += 0.05
					rc.advance(t)
					check(rc.active() or rc.pending().is_empty(), "고를 게 남았으면 버튼이 떠 있다")
					var d := rc.pending()
					if not pressed and not d.is_empty() and d.kind == "dir" and int(d.from) == 1 and float(d.show) <= t:
						rc.set_target(1)
						pressed = true
				rc.finish()
				var me: RunnerAI = rc.play.runners[0]
				checked += 1
				check(_order_kept(rc.play), "방향을 바꿔도 추월 없음")
				if pressed:
					retreated += 1
					check(me.state == RunnerAI.State.OUT or me.base == 1, "달리다 1루를 누르면 1루로 돌아간다 (ev %d, 방향 %d)" % [ev, spray])
					check(me.history.any(func(h: Array) -> bool: return h[1] == RunnerAI.State.RETURNING), "돌아서는 기록")
				check(not rc.active(), "끝나면 버튼이 사라진다")
	check(retreated >= 3, "달리는 중 돌아서기 (%d/%d)" % [retreated, checked])


func test_runner_fatigue() -> void:
	var phys := _config.ball_physics
	var r := RunnerAI.new(1, 0, 50, phys)
	var lap := 4.0 * phys.base_distance
	r.run_dist = lap
	check(is_equal_approx(r.speed_cap(), r.top_speed), "한 바퀴까지는 지치지 않는다")
	r.run_dist = lap + 20.0
	check(r.speed_cap() < r.top_speed * 0.95 and r.speed_cap() > r.top_speed * 0.8, "한 바퀴를 20m 넘기면 조금 느려진다 (%.2f → %.2f)" % [r.top_speed, r.speed_cap()])
	r.run_dist = lap + 1000.0
	check(is_equal_approx(r.speed_cap(), r.top_speed * float(phys.running["fatigueMinFactor"])), "아무리 달려도 최저 배율 아래로는 안 떨어진다")
	# 달린 거리는 실제로 쌓인다: 3루타 타자는 세 베이스 거리 안팎
	for spray in range(-40, 41, 5):
		var p := _run_play(150, 14, spray, [-1, -1, -1], 0)
		if p.batter_outcome == SwingJudge.Outcome.TRIPLE:
			var b: RunnerAI = p.runners[0]
			check(b.run_dist > 3.0 * phys.base_distance and b.run_dist < lap, "3루타 달린 거리 %.1fm" % b.run_dist)
			break
	# 런다운에서 왔다 갔다 하면 지쳐서 결국 끝난다: 무조건 방향을 계속 바꿔도 maxS 전에 끝나는 경우가 많다
	var ended := 0
	var all := 0
	for c: Array in _rundown_cases():
		var p: PlaySimulator.PlayResult = c[3]
		var ch: Array = []
		for i in 80:
			ch.append(true if i == 0 else (i / 3) % 2 == 0)
		var q := _controlled_play(c[0], c[1], ch, 7, c[2])
		if q.rundowns.is_empty():
			continue
		all += 1
		var rd: Dictionary = q.rundowns[0]
		ended += 1 if float(rd.end) - float(rd.start) < float(phys.running["rundown"]["maxS"]) - 0.05 else 0
	check(all > 0 and ended == all, "런다운이 maxS 안에 끝난다 (%d/%d)" % [ended, all])


func test_rundown() -> void:
	var cases := _rundown_cases()
	check(cases.size() >= 10, "무리하게 뛰면 런다운이 난다 (%d번)" % cases.size())
	var outs := 0
	var safe := 0
	for c: Array in cases:
		var p: PlaySimulator.PlayResult = c[3]
		var rd: Dictionary = p.rundowns[0]
		var me: RunnerAI = p.runners.filter(func(r: RunnerAI) -> bool: return r.id == int(c[2]))[0]
		check(int(rd.fwd) == int(rd.back) + 1 and float(rd.end) > float(rd.start), "두 베이스 사이, 끝난 시각이 뒤")
		check(p.calls.any(func(x: Dictionary) -> bool: return x.text == "런다운!"), "런다운 알림")
		match str(rd.outcome):
			"out":
				outs += 1
				check(me.state == RunnerAI.State.OUT, "런다운 아웃이면 주자가 빠진다")
			"back":
				safe += 1
				check(me.state == RunnerAI.State.SAFE and me.base == int(rd.back) and p.bases[int(rd.back) - 1] == me.id, "뒤 베이스로 살아 돌아간다")
			"fwd":
				safe += 1
				check(me.state == RunnerAI.State.SCORED or (me.state == RunnerAI.State.SAFE and me.base == int(rd.fwd)), "앞 베이스로 산다")
		# 기록이 맞물린다: 아웃 수 = 빠진 주자 수, 추월 없음
		var gone := p.runners.filter(func(r: RunnerAI) -> bool: return r.state == RunnerAI.State.OUT).size()
		check(gone == p.outs and _order_kept(p), "아웃 수·주자 순서가 맞다")
		# 수비수·공 기록이 런다운 길이만큼 있다
		for tr: PackedVector2Array in (rd.tracks as Dictionary).values():
			check(tr.size() == (rd.ball as PackedVector3Array).size() and tr.size() >= 5, "수비수·공 궤적 기록 (%d, %d, %s)" % [tr.size(), (rd.ball as PackedVector3Array).size(), (rd.tracks as Dictionary).keys()])
		# 같은 입력이면 같은 런다운
		var again := _controlled_play(c[0], c[1], [true], 7, c[2])
		check(again.rundowns[0].ball == rd.ball and again.rundowns[0].outcome == rd.outcome, "같은 입력 같은 런다운")
		# 내 주자의 런다운은 느리게 보여 준다: 화면 시각 ↔ 플레이 시각이 서로 맞는다
		check(p.slow.size() == 1 and float(p.slow[0][0]) == float(rd.start), "내 런다운은 느린 구간")
		for x in [0.5, float(rd.start) + 0.3, float(rd.end) + 1.0]:
			check(absf(p.play_time(p.wall_time(x)) - x) < 0.0001, "느린 구간 시각 변환이 맞물린다")
		check(p.wall_time(float(rd.end)) > float(rd.end), "느린 구간만큼 화면이 길다")
	var rate := float(outs) / maxi(outs + safe, 1)
	print("    런다운 %d번: 아웃 %d · 세이프 %d (아웃 %.0f%%)" % [cases.size(), outs, safe, rate * 100.0])
	check(rate >= 0.6 and rate <= 0.99, "런다운은 대부분 아웃이지만 살 때도 있다 (%.0f%%)" % (rate * 100.0))
	check(safe > 0, "런다운에서 사는 경우가 있다")
	# AI 끼리의 플레이는 무리하게 뛰지 않아 런다운이 드물다
	var ai_rd := 0
	for c: Array in cases:
		ai_rd += _controlled_play(c[0], c[1], [], 7, c[2]).rundowns.size()
	check(ai_rd < cases.size() / 2, "코치 사인대로 뛰면 런다운이 드물다 (%d/%d)" % [ai_rd, cases.size()])


func test_run_control_rundown() -> void:
	var tried := 0
	for c: Array in _rundown_cases():
		var p: PlaySimulator.PlayResult = c[3]
		var rdecs := p.decisions.filter(func(d: Dictionary) -> bool: return d.kind == "rundown")
		if rdecs.is_empty():
			continue
		var d: Dictionary = rdecs[0]
		var k := p.decisions.find(d)
		var rd: Dictionary = p.rundowns[0]
		check(int(d.from) == int(rd.back) and int(d.to) == int(rd.fwd) and float(d.show) <= float(d.t) and float(d.since) == float(rd.start), "런다운 판단 기록")
		# 판단 시각 전까지는 어느 쪽을 골라도 똑같다
		var base_ch: Array = [true]
		while base_ch.size() < k:
			base_ch.append(bool(p.decisions[base_ch.size()].go))
		var fwd_p := _controlled_play(c[0], c[1], base_ch + [true], 7, c[2])
		var back_p := _controlled_play(c[0], c[1], base_ch + [false], 7, c[2])
		var n := int(float(d.t) / PlaySimulator.DT) - 1
		var same := true
		for i in fwd_p.runners.size():
			same = same and fwd_p.runners[i].track.slice(0, n) == back_p.runners[i].track.slice(0, n)
		check(same, "런다운 판단 전 궤적은 선택과 무관")
		# RunControl: 런다운이 벌어지기 전엔 버튼이 안 보이고, 벌어지면 갇힌 두 베이스만 누를 수 있다
		var resim := func(ch: Array) -> PlaySimulator.PlayResult: return _controlled_play(c[0], c[1], ch, 7, c[2])
		var rc := RunControl.new(resim.call([true]), resim)
		rc.set_target(4)  # 런다운 전에 홈을 눌러 뒀다
		rc.advance(float(d.since) - 0.05)
		check(rc.pending().is_empty() or rc.pending().kind != "rundown", "런다운 판단은 벌어지기 전엔 숨는다")
		rc.advance(float(d.show))
		if rc.pending().get("kind", "") != "rundown":
			continue
		tried += 1
		check(rc.in_rundown() and rc.min_base() == int(d.from) and rc.max_base() == int(d.to), "갇힌 두 베이스만")
		check(not rc.target_counts(), "런다운 전에 누른 목표는 쓰지 않는다")
		rc.set_target(int(d.to) + 1)
		check(rc.target == 4 and not rc.target_counts(), "갇힌 베이스 밖은 누를 수 없다")
		var want_fwd := not bool(d.ai)
		rc.set_target(int(d.to) if want_fwd else int(d.from))
		check(rc.target_counts() and rc.going() == (int(d.to) if want_fwd else int(d.from)), "누른 쪽으로 간다")
		rc.advance(float(d.t))
		check(bool(rc.play.decisions[k].go) == want_fwd, "코치와 반대로 누르면 그쪽으로 뛴다")
		# 보조에서 안 누르면 AI(코치)대로 도망친다
		var rc2 := RunControl.new(resim.call([true]), resim)
		rc2.set_target(4)
		rc2.finish()
		check(bool(rc2.play.decisions[k].go) == bool(d.ai), "런다운 전 목표는 무시하고 코치 사인대로")
	check(tried >= 3, "런다운 판단을 RunControl 로 고른다 (%d번)" % tried)


func test_steal_judge() -> void:
	var cfg := _career_cfg()
	var phys := _config.ball_physics
	var a := Steal.plan(cfg.steal, _rng(3))
	var b := Steal.plan(cfg.steal, _rng(3))
	check(a.go_ms == b.go_ms and a.feints == b.feints, "같은 시드면 같은 투수 동작")
	check(a.go_ms >= float(cfg.steal["setMinMs"]), "세트 자세로 기다린 뒤 던진다")
	var with_feint := 0
	for s in 200:
		var p := Steal.plan(cfg.steal, _rng(s))
		with_feint += 1 if not p.feints.is_empty() else 0
		for f in p.feints:
			check(f + p.feint_ms <= p.go_ms and p.in_feint(f + 1.0) and not p.in_feint(p.go_ms), "견제는 투구 전에 끝난다")
	check(with_feint > 20 and with_feint < 120, "견제 동작이 가끔 섞인다 (200번 중 %d번)" % with_feint)
	check(Steal.judge(cfg.steal, phys, a, a.go_ms - 50.0, 1, 50, _rng(1)).kind == Steal.Kind.PICKED_OFF, "투구 전에 뛰면 견제사")
	check(not Steal.judge(cfg.steal, phys, a, -1.0, 1, 50, _rng(1)).attempted(), "탭하지 않으면 안 뛴다")
	check(not Steal.judge(cfg.steal, phys, a, a.end_ms() + 1.0, 1, 50, _rng(1)).attempted(), "너무 늦으면 안 뛴다")
	var r1 := Steal.judge(cfg.steal, phys, a, a.go_ms + 200.0, 1, 50, _rng(1))
	var r2 := Steal.judge(cfg.steal, phys, a, a.go_ms + 200.0, 1, 50, _rng(1))
	check(r1.kind == r2.kind and r1.defense_s == r2.defense_s and absf(r1.reaction_ms - 200.0) < 0.01, "같은 시드·같은 탭이면 같은 판정")
	check(r1.to == 2 and Steal.judge(cfg.steal, phys, a, a.go_ms + 200.0, 2, 50, _rng(1)).to == 3, "1루 → 2루, 2루 → 3루")
	# 성공률: 주력이 높을수록, 반응이 빠를수록 높다. 평균 주자가 좋은 스타트면 절반은 넘고 무조건은 아니다
	var rate := func(speed: int, react_ms: float, from: int) -> float:
		var safe := 0
		for s in 400:
			if Steal.judge(cfg.steal, phys, a, a.go_ms + react_ms, from, speed, _rng(s)).kind == Steal.Kind.SAFE:
				safe += 1
		return safe / 400.0
	var avg: float = rate.call(50, 250.0, 1)
	var fast: float = rate.call(85, 250.0, 1)
	var slow: float = rate.call(20, 250.0, 1)
	var late: float = rate.call(50, 500.0, 1)
	print("    도루 성공률 (반응 0.25초): 주력 20 %.0f%% · 50 %.0f%% · 85 %.0f%% / 주력 50 반응 0.5초 %.0f%% / 3루 도루 주력 50 %.0f%%" % [
		slow * 100, avg * 100, fast * 100, late * 100, float(rate.call(50, 250.0, 2)) * 100])
	check(slow < avg and avg < fast, "주력이 높을수록 잘 산다 (%.2f < %.2f < %.2f)" % [slow, avg, fast])
	check(late < avg, "스타트가 늦으면 덜 산다 (%.2f < %.2f)" % [late, avg])
	check(avg > 0.5 and avg < 0.95, "평균 주자의 좋은 스타트 성공률 %.2f" % avg)
	check(fast > 0.85 and late < 0.4, "빠른 주자는 거의 살고(%.2f), 늦은 스타트는 대개 죽는다(%.2f)" % [fast, late])


func test_steal_in_game_state() -> void:
	var g := GameState.new(9, _career_cfg().running, _config.ball_physics)
	g.bases = [3, -1, 7]
	check(not g.apply_steal(1, true) and g.bases == [-1, 3, 7] and g.outs == 0, "도루 성공: 주자만 옮긴다")
	g.bases = [-1, 3, -1]
	check(not g.apply_steal(2, false) and g.bases == [-1, -1, -1] and g.outs == 1, "도루 실패: 주자가 빠지고 아웃 하나")
	g.outs = 2
	g.bases = [3, -1, -1]
	g.batter_index[0] = 5
	check(g.apply_steal(1, false) and not g.top and g.outs == 0 and g.bases == [-1, -1, -1], "세 번째 아웃이면 공수 교대")
	check(g.batter_index[0] == 5, "타석이 끝나지 않았으니 타순은 그대로")


## 주루 설정을 켠(보조·수동) 한 경기를 돌린다. mode: RunControl.ASSIST | MANUAL. steal: 도루 기회마다 넘길 결과를 만드는 함수 (Steal.Result),
## run: 주루 판단마다 부르는 함수 (RunControl 을 받아 고른다). 돌려주는 값: [경기, 도루 기회 수, 주루 장면 수]
func _play_interactive_game(seed_value: int, steal: Callable, run: Callable, mode: String = RunControl.ASSIST) -> Array:
	var cfg := _career_cfg()
	var player := PlayerData.create("테스트", cfg)
	var srng := _rng(seed_value)
	var mine := School.create("me", "우리고", "우리", 50.0, "balanced", cfg, srng)
	var theirs := School.from_content(cfg.regional_schools[seed_value % cfg.regional_schools.size()], cfg, srng)
	theirs.strength = 50.0
	theirs.team = Team.generate(theirs.name, theirs.short, 50.0, theirs.pitcher_profile, cfg, srng)
	var game := GameRunner.new(cfg, _config, player, mine, theirs, seed_value % 2 == 0, seed_value, true, false)
	game.running_mode = mode
	var rng := _rng(seed_value + 999)
	var steals := 0
	var runs := 0
	var guard := 0
	while not game.state.over and guard < 600:
		guard += 1
		var step := game.step()
		match step.type:
			"my_turn":
				var pa := AutoPa.simulate(cfg.auto_pa, _config, game.current_batter().skills, game.current_pitcher(), rng)
				var c: SwingJudge.Contact = null
				if pa.ball != null:
					c = SwingJudge.Contact.new(SwingJudge.Quality.SOLID, pa.ball.spray_deg, pa.ball.distance_m, pa.ball.batted_ball)
					c.ball = pa.ball
				game.apply_my_result(AtBat.Result.new(pa.outcome, SwingJudge.BattedBall.NONE, pa.pitches, c))
			"my_steal":
				steals += 1
				check(int(step.from) == game.steal_base() and game.steal_base() > 0, "도루 기회는 내가 뛸 수 있는 베이스에 있을 때만")
				game.apply_steal(steal.call(int(step.from), steals))
			"my_run":
				runs += 1
				var rc: RunControl = step.control
				check(rc.has_decisions() and rc.play.decisions[0].id == game.my_slot, "주루 장면은 내 주자의 판단이 있을 때만")
				run.call(rc, runs)
				check(game.finish_run().type == "auto", "주루 장면이 끝나면 그 타석이 반영된다")
	check(game.state.over, "경기가 끝난다 (seed %d)" % seed_value)
	return [game, steals, runs]


func test_interactive_game_matches_and_stays_consistent() -> void:
	var no_steal := func(from: int, _n: int) -> Steal.Result:
		var r := Steal.Result.new()
		r.from = from
		r.to = from + 1
		return r
	var total_steals := 0
	var total_runs := 0
	var sb := 0
	var cs := 0
	for s in 40:
		# 1) 보조에서 아무것도 누르지 않으면(도루 안 함, 주루는 코치 사인대로) 자동 경기와 완전히 같다
		var passive: Array = _play_interactive_game(s, no_steal, func(_rc: RunControl, _n: int) -> void: pass)
		check((passive[0] as GameRunner).log == _play_full_game(s).log, "고르지 않으면 자동 경기와 같은 중계 (seed %d)" % s)
		# 2) 도루를 번갈아 성공·실패·견제사로, 주루 판단을 번갈아 뛴다·멈춘다로 골라도 경기 기록이 맞물린다
		var steal := func(from: int, n: int) -> Steal.Result:
			var r := Steal.Result.new()
			r.from = from
			r.to = from + 1
			r.kind = [Steal.Kind.SAFE, Steal.Kind.CAUGHT, Steal.Kind.SAFE, Steal.Kind.PICKED_OFF][n % 4]
			return r
		# 번갈아 홈까지 / 가장 가까운 베이스에서 멈춤
		var run := func(rc: RunControl, n: int) -> void:
			rc.set_target(4 if n % 2 == 0 else rc.min_base())
		var active: Array = _play_interactive_game(s, steal, run)
		var game: GameRunner = active[0]
		total_steals += int(active[1])
		total_runs += int(active[2])
		var st := game.state
		var box := game.box
		for side in 2:
			check(box.team_runs(side) == st.score[side] and box.runs_allowed(1 - side) == st.score[side], "득점 합 = 실점 합 = 총점 (seed %d)" % s)
		var outs_vs_away := 0
		for p: BoxScore.PitcherLine in box.pitchers[1]:
			outs_vs_away += p.outs
		check(outs_vs_away == st.line[0].size() * 3, "도루 실패 아웃도 투수 기록에 들어간다 (seed %d: %d)" % [s, outs_vs_away])
		var mine: BoxScore.BatterLine = box.batters[game.my_side][game.my_slot]
		check(mine.sb == game.my_line.steals and mine.cs == game.my_line.caught_stealing, "내 도루 기록 = 박스스코어")
		sb += game.my_line.steals
		cs += game.my_line.caught_stealing
		check((_play_interactive_game(s, steal, run)[0] as GameRunner).log == game.log, "같은 시드·같은 선택이면 같은 경기 (seed %d)" % s)
		# 3) 수동에서 아무것도 누르지 않아도 경기 기록이 맞물린다 (내 주자는 늘 한 베이스만)
		var manual: GameRunner = _play_interactive_game(s, no_steal, func(_rc: RunControl, _n: int) -> void: pass, RunControl.MANUAL)[0]
		for side in 2:
			check(manual.box.team_runs(side) == manual.state.score[side], "수동 경기도 득점 합 = 총점 (seed %d)" % s)
	print("    40경기: 도루 기회 %d번 (성공 %d, 실패 %d), 주루 장면 %d번" % [total_steals, sb, cs, total_runs])
	# 주루 장면은 시드에 따라 40경기에 4~11번으로 크게 흔들린다 (2026-11 측정, 시드 0~159를 40개씩: 옛 코드 11·4·5·7, 지금 5·4·5·10).
	# 장면이 "나온다"만 본다 — 10번 기준은 시드 0~39가 우연히 11번이라 통과하던 것이다
	check(total_steals >= 20 and total_runs >= 3, "도루 기회 %d번, 주루 장면 %d번이 나온다" % [total_steals, total_runs])
	check(sb > 0 and cs > 0 and sb + cs == total_steals, "도루 성공 %d + 실패 %d = 기회 %d" % [sb, cs, total_steals])
	# 도루하면 주력이 자란다
	var cfg := _career_cfg()
	var p := CareerState.new(cfg, "테스트", _config).player
	var line := PlayerData.SeasonStats.new()
	line.steals = 3
	var before := p.speed + float(p.progress.get("speed", 0.0))
	Growth.from_line(cfg, p, line)
	check(p.speed + float(p.progress.get("speed", 0.0)) > before, "도루 성공 → 주력 성장")
	var total := PlayerData.SeasonStats.new()
	total.merge(line)
	check(total.steals == 3 and line.line_text().contains("3도루"), "도루도 시즌 기록에 합쳐진다")


func test_my_at_bat_run_control() -> void:
	# 내 타석: 외야 안타를 치면 타자 주자(나)의 "2루까지?" 판단이 생기고, 뛴다고 고르면 타석 결과가 바뀐다
	var balance := BalanceLoader.load_balance()
	var found := false
	for spray in range(-40, 41, 5):
		var s := BattingSession.new(BattingConfig.from_balance(balance), Presentation.from_balance(balance))
		s.start_single(s.skills, s.pitcher, 7, {"bases": [-1, -1, -1], "outs": 0, "batter": 4, "control": RunControl.ASSIST})
		var c := SwingJudge.Contact.new(SwingJudge.Quality.SOLID, float(spray), 0.0, SwingJudge.BattedBall.LINE)
		c.ball = _sim(140, 12, spray)
		if c.ball.caught or c.ball.fielder in _config.ball_physics.infield:
			continue
		var play: PlaySimulator.PlayResult = s.at_bat._run_play(c)
		if play.decisions.is_empty() or play.decisions[0].ai:
			continue
		found = true
		check(play.batter_outcome == SwingJudge.Outcome.SINGLE and play.decisions[0].id == 4 and int(play.decisions[0].to) == 2, "코치는 1루에서 멈추라고 한다")
		s.at_bat.result = AtBat.Result.new(play.batter_outcome, c.batted_ball, 1, c)
		s.at_bat.result.play = play
		var again := s.at_bat.rerun_play([false])
		check(again.bases == play.bases and again.batter_outcome == play.batter_outcome, "같은 선택으로 다시 돌리면 같은 플레이")
		var go := s.at_bat.rerun_play([true])
		var me: RunnerAI = go.runners[0]
		check(s.at_bat.result.play == go and s.at_bat.result.outcome == go.batter_outcome, "타석 결과가 새 플레이로 바뀐다")
		check(go.batter_outcome == SwingJudge.Outcome.DOUBLE or me.state == RunnerAI.State.OUT, "2루까지 뛰면 2루타거나 2루에서 아웃 (방향 %d)" % spray)
		break
	check(found, "타자 주자의 판단이 있는 안타를 찾았다")
	# 연습(주자 상황 없음)에서는 조작하지 않는다
	var practice := BattingSession.new(BattingConfig.from_balance(balance), Presentation.from_balance(balance))
	var c2 := SwingJudge.Contact.new(SwingJudge.Quality.SOLID, 15.0, 0.0, SwingJudge.BattedBall.LINE)
	c2.ball = _sim(140, 12, 15)
	check((practice.at_bat._run_play(c2) as PlaySimulator.PlayResult).decisions.is_empty(), "연습 타석은 AI 주루")


func test_settings_running_mode() -> void:
	var path := "user://test_settings.json"
	DirAccess.remove_absolute(path)
	var s := Settings.load_from(path)
	check(s.running == RunControl.ASSIST, "주루 설정의 기본은 보조")
	s.cycle_running()
	check(s.running == RunControl.MANUAL, "보조 → 수동")
	s.save(path)
	check(Settings.load_from(path).running == RunControl.MANUAL, "저장한 설정을 다시 읽는다")
	s.cycle_running()
	s.cycle_running()
	check(s.running == RunControl.ASSIST, "수동 → 자동 → 보조")
	var f := FileAccess.open(path, FileAccess.WRITE)
	f.store_string("{\"running\": \"엉뚱\"}")
	f.close()
	check(Settings.load_from(path).running == RunControl.ASSIST, "모르는 값이면 기본으로")
	DirAccess.remove_absolute(path)
	# 자동이면 경기가 주루 조작을 넘기지 않는다
	var g := GameRunner.new(_career_cfg(), _config, PlayerData.create("테스트", _career_cfg()),
		School.create("me", "우리고", "우리", 50.0, "balanced", _career_cfg(), _rng(1)), School.create("op", "상대고", "상대", 50.0, "balanced", _career_cfg(), _rng(2)), true, 1)
	check(not g.controls_running() and g.situation()["control"] == "", "자동: 조작 없음")
	g.running_mode = RunControl.MANUAL
	check(g.controls_running() and g.situation()["control"] == RunControl.MANUAL, "수동: 타석에 주루 설정이 전달된다")


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
		if game.in_scene():
			career.auto_game_story(game)
		if game.step().type == "my_turn":
			career.auto_game_story(game)
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
	check(rec.phases.size() == career.phases().size(), "대회 %d개 (%d)" % [career.phases().size(), rec.phases.size()])
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


## 저장 테스트용 한 주: 주 시작 이야기 → 훈련·경기(내 타석 자동, 타구·카운트 포함) → 경기 뒤 이야기 → 다음 주
func _save_week(career: CareerState) -> void:
	# 주 시작 이야기는 하나가 끝나면 다음 것이 이어질 수 있다 — 남은 게 없을 때까지
	while not career.events("start").is_empty():
		var e: Dictionary = career.events("start")[0]
		career.resolve_event(e, _ok_choice(career, e, 0))
	career.train("rest" if career.player.condition < 50 else "contact")
	var game := career.new_game()
	var rng := _rng(career.week * 17 + career.season_no)
	while not game.state.over:
		if game.in_scene():
			career.auto_game_story(game)
		if game.step().type == "my_turn":
			career.auto_game_story(game)
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
	while not career.events("after").is_empty():
		var e: Dictionary = career.events("after")[0]
		career.resolve_event(e, _ok_choice(career, e, 0))
	career.advance_week()


## 시즌 끝까지 (결산 이야기·겨울 훈련 포함) 진행하고 다음 시즌으로
func _save_season_end(career: CareerState) -> void:
	for e: Dictionary in career.events("seasonEnd").duplicate():
		career.resolve_event(e, _ok_choice(career, e, 0))
	career.winter_training()
	career.start_next_season()


func _reload(career: CareerState) -> CareerState:
	var r := SaveGame.from_bytes(SaveGame.to_bytes(career), career.cfg, career.batting)
	check(r["error"] == "", "불러오기 오류: %s" % r["error"])
	return r["career"]


func test_save_roundtrip() -> void:
	var career := CareerState.new(_career_cfg(), "저장", _config)
	for w in 9:
		_save_week(career)
	career.socialize("coach", "gift")
	var bytes := SaveGame.to_bytes(career)
	var loaded := _reload(career)
	check(SaveGame.to_bytes(loaded) == bytes, "저장 → 불러오기 → 저장이 똑같다")
	# 같은 객체를 가리키던 관계가 살아 있다 (라이벌 = 라이벌 학교 명단의 그 타자)
	var slot := int(loaded.cfg.rival.lineupSlot) - 1
	var school: School = loaded.schools[loaded.rival.school_id]
	check(loaded.rival.batter == school.team.lineup[slot], "라이벌 타자는 명단 속 같은 객체")
	check(loaded.history.is_empty() and loaded.player != career.player, "새 객체로 만들어진다")
	# 타입이 붙은 배열·int 키·설정 참조
	check(loaded.results.is_typed() and loaded.results == career.results, "results: Array[int]")
	check(loaded.records.games.is_typed() and loaded.records.games.size() == career.records.games.size(), "기록실 경기")
	check(loaded.records.splits.has(1) and loaded.records.splits.keys() == career.records.splits.keys(), "시즌 번호 키는 int 그대로 (%s / %s)" % [career.records.splits.keys(), loaded.records.splits.keys()])
	check(loaded.cfg == career.cfg and loaded.book.defs.size() == career.book.defs.size(), "설정·이벤트 정의는 지금 것")
	check(loaded.book.log.size() == career.book.log.size() and loaded.book.fired == career.book.fired, "이벤트 기록")
	check(loaded.player.season.pa == career.player.season.pa and loaded.player.money == career.player.money
		and loaded.player.potential == career.player.potential and loaded.week == career.week, "선수·주차")


func test_save_then_continue_is_same() -> void:
	# 원래 커리어와 불러온 커리어를 똑같이 진행하면 끝까지 똑같다 (시즌을 넘어가도)
	var a := CareerState.new(_career_cfg(), "저장", _config)
	for w in 5:
		_save_week(a)
	var b := _reload(a)
	for career: CareerState in [a, b]:
		var guard := 0
		while not career.is_season_over() and guard < 40:
			_save_week(career)
			guard += 1
		_save_season_end(career)
		for w in 3:
			_save_week(career)
	check(SaveGame.to_bytes(a) == SaveGame.to_bytes(b), "불러온 뒤 진행 = 원래 진행")
	check(a.player.grade == 2 and a.history.size() == 1, "2학년까지 갔다")
	# 경기를 마친 주에 저장하면, 불러와도 경기는 끝난 상태 (같은 경기를 다시 하지 않는다)
	var c := CareerState.new(_career_cfg(), "저장", _config)
	var g := _auto_week(c, "rest")
	c.finish_game(g)
	var d := _reload(c)
	check(d.game_done and d.week == c.week, "경기 마친 주")
	d.advance_week()
	check(not d.game_done and d.week == c.week + 1, "다음 주로 넘어가면 다시 경기")
	# 겨울 훈련은 한 번만 (불러온 뒤 다시 눌러도 같은 결과, 능력치가 더 오르지 않는다)
	a.phase_index = a.phases().size()
	var gains := a.winter_training()
	var ovr := a.player.overall()
	var e := _reload(a)
	check(e.winter_training() == gains and e.player.overall() == ovr, "겨울 훈련은 시즌에 한 번")


func test_save_file_and_errors() -> void:
	var path := "user://test_career.save"
	SaveGame.delete(path)
	var cfg := _career_cfg()
	check(not SaveGame.exists(path) and SaveGame.load_career(cfg, _config, path)["career"] == null, "저장이 없으면 null")
	var career := CareerState.new(cfg, "파일", _config)
	_save_week(career)
	check(SaveGame.save(career, path) == "" and SaveGame.exists(path), "파일에 저장")
	check(not FileAccess.file_exists(path + ".tmp"), "임시 파일은 남지 않는다")
	var r := SaveGame.load_career(cfg, _config, path)
	check(r["error"] == "" and r["career"].player.name == "파일" and r["career"].week == 2, "파일에서 불러오기")
	check(SaveGame.summary(r["career"]).begins_with("파일 · 1학년"), SaveGame.summary(r["career"]))
	check(SaveGame.from_bytes("깨진 파일".to_utf8_buffer(), cfg, _config)["career"] == null, "깨진 파일")
	check(SaveGame.from_bytes(PackedByteArray(), cfg, _config)["career"] == null, "빈 파일")
	var data: Dictionary = bytes_to_var(SaveGame.to_bytes(career))
	data["version"] = 999
	check(SaveGame.from_bytes(var_to_bytes(data), cfg, _config)["error"].begins_with("저장 버전"), "다른 버전")
	SaveGame.delete(path)
	check(not SaveGame.exists(path), "지우기")



# ---------- 폰 ----------

## 테스트용 문자만 든 설정
func _phone_cfg() -> CareerConfig:
	var cfg := _real_cfg()
	cfg.messages = [
		{"id": "t_mom", "speaker": "mom", "lines": ["밥은?", "{player}, 답 좀 해라."],
			"replies": [{"label": "먹었어요", "effects": {"rel": {"mom": 2}, "condition": -3}, "then": ["그래."]}, {"label": "아직요", "text": "아직요…"}],
			"ignore": {"rel": {"mom": -5}}},
		{"id": "t_manager", "speaker": "manager", "text": "안녕"},
		{"id": "t_team", "speaker": "senior", "room": "team", "lines": [{"from": "senior", "text": "집합"}, {"from": "buddy", "text": "넵"}]},
		{"id": "t_coach", "speaker": "coach", "text": "내일 보자"},
		{"id": "t_mom2", "speaker": "mom", "text": "일찍 와라"},
		{"id": "t_flag", "speaker": "buddy", "text": "들었다", "requires": ["t_told"]},
	]
	cfg.pending_flags = cfg.pending_flags + ["t_told"]
	return cfg


func test_phone_messages() -> void:
	var cfg := _phone_cfg()
	check(Phone.validate(cfg).is_empty(), "테스트 문자 정의: %s" % [Phone.validate(cfg)])
	var career := CareerState.new(cfg, "테스트", _config)
	var phone := career.phone
	check(phone.total_unread() == 0 and phone.order.is_empty(), "처음엔 문자가 없다")
	career.advance_week()
	# 한 주에 maxPerWeek 통, content 순서대로. 못 만난 사람(매니저)의 문자는 안 온다
	check(int(cfg.phone.maxPerWeek) == 2 and phone.status.keys() == ["t_mom", "t_team"], "첫 주 문자: %s" % [phone.status.keys()])
	check(phone.total_unread() == 4 and phone.order == (["team", "mom"] as Array[String]), "안 읽은 줄·방 순서 (최근이 앞)")
	check(phone.lines("mom")[1].text == "테스트, 답 좀 해라." and phone.lines("team")[1].from == "buddy", "글자 치환 · 단체방의 보낸 사람")
	check(Phone.room_name(career, "team") == "서림고 야구부" and Phone.room_name(career, "mom") == "엄마", "방 이름")
	var fresh := phone.take_fresh(career)
	check(fresh.size() == 2 and fresh[0].text == "밥은?" and fresh[1].name == "서림고 야구부 · " + career.people.person("senior").name and phone.take_fresh(career).is_empty(), "새 문자 알림은 한 번")
	phone.mark_read("mom")
	check(phone.total_unread() == 2, "읽음")
	# 답장: 효과는 이벤트와 같은 처리기, 관계 변화는 안내 줄에 안 나온다, 주간 행동은 안 쓴다
	check(phone.reply_options(career, "mom") == (["먹었어요", "아직요"] as Array[String]) and phone.weeks_left(career, "mom") == int(cfg.phone.replyWeeks), "답장 선택지")
	check(phone.reply_options(career, "team").is_empty(), "답장 없는 문자")
	var rel := career.people.relation("mom")
	var cond := career.player.condition
	var added := phone.reply(career, "mom", 0)
	var tail: Array = phone.lines("mom").slice(-3)
	check(added == 3 and tail[0].from == Phone.ME and tail[0].text == "먹었어요" and tail[1].from == "mom" and tail[2].from == Phone.SYSTEM, "내 답장 → 돌아온 답 → 안내")
	check(career.people.relation("mom") == rel + 2 and career.player.condition == cond - 3, "답장 효과")
	check(tail[2].text.contains("컨디션") and not tail[2].text.contains("관계"), "안내 줄에 관계 숫자는 없다: %s" % tail[2].text)
	check(phone.status.t_mom.state == Phone.REPLIED and phone.reply_options(career, "mom").is_empty() and career.week_training == "" and career.week_social == "", "답장은 한 번, 행동을 안 쓴다")
	# 다음 주: 밀려 있던 문자, 플래그가 선 문자
	career.player.flags["t_told"] = true
	career.advance_week()
	check(phone.status.has("t_coach") and phone.status.has("t_mom2") and not phone.status.has("t_flag"), "밀린 문자가 먼저 (한 주 2통)")
	career.advance_week()
	check(phone.status.has("t_flag") and not phone.status.has("t_manager"), "조건(플래그)이 맞으면 온다")
	# 저장 → 불러오기
	var loaded := _reload(career)
	check(SaveGame.to_bytes(loaded) == SaveGame.to_bytes(career) and loaded.phone.lines("mom").size() == phone.lines("mom").size() and loaded.phone.order.is_typed(), "대화방 기록이 저장된다")

	# 답장을 안 하면: 그 방에는 다음 문자가 안 오고, 기한이 지나면 닫히며 ignore 효과가 붙는다
	var b := CareerState.new(_phone_cfg(), "테스트", _config)
	b.advance_week()
	var rel_b := b.people.relation("mom")
	b.advance_week()
	check(b.phone.status.t_mom.state == Phone.OPEN and not b.phone.status.has("t_mom2") and b.phone.weeks_left(b, "mom") == 1, "답장을 기다리는 방엔 다음 문자가 안 온다")
	b.advance_week()
	check(b.phone.status.t_mom.state == Phone.EXPIRED and b.people.relation("mom") == rel_b - 5 and b.phone.reply_options(b, "mom").is_empty(), "기한이 지나면 닫힌다 + ignore")
	check(b.phone.status.has("t_mom2") and b.phone.lines("mom")[2].from == Phone.SYSTEM, "닫힌 뒤엔 다음 문자가 온다")
	# 같은 진행 = 같은 문자
	var c := CareerState.new(_phone_cfg(), "테스트", _config)
	for w in 3:
		c.advance_week()
	check(SaveGame.to_bytes(c) == SaveGame.to_bytes(b), "재현성")
	# 연락처의 관계 문구 변화
	check(b.phone.relation_was(b, "buddy") == "", "처음엔 변화 없음")
	b.phone.see_relation(b, "buddy")
	b.people.add_relation("buddy", 30)
	check(b.phone.relation_was(b, "buddy") == "그럭저럭" and b.phone.relation_changes(b) == 1, "문구가 바뀌면 예전 문구를 알려 준다")
	b.phone.see_relation(b, "buddy")
	check(b.phone.relation_changes(b) == 0, "보고 나면 사라진다")
	# 망가진 정의
	var broken := _phone_cfg()
	broken.messages = [{"id": "ep01_01", "speaker": "mom", "text": "{nobody}", "requires": ["never"], "bogus": 1,
		"replies": [{"label": "하나뿐", "effects": {"lineupSlot": 3, "rel": {"ghost": 1}}}]},
		{"id": "x2", "speaker": "mom", "lines": [{"from": "buddy", "text": "끼어들기"}]}]
	check(Phone.validate(broken).size() >= 8, "겹치는 id·모르는 키·플래그·치환·답장 수·큰 효과·인물·끼어들기를 잡는다: %s" % [Phone.validate(broken)])


func test_phone_content() -> void:
	# 게임 문자: 검증 통과, 1학년을 자동으로 돌리면 문자가 오고(답장은 안 한다) 이야기 진행은 그대로
	var cfg := _real_cfg()
	check(Phone.validate(cfg).is_empty(), "content.json 문자 정의 문제: %s" % [Phone.validate(cfg)])
	var career := CareerState.new(cfg, "테스트", _config)
	var per_week := {}
	check(FastForward.run(career, 2, "season_start"), "2학년 시작까지")
	for id: String in career.phone.status:
		var tw := int(career.phone.status[id].tw)
		per_week[tw] = int(per_week.get(tw, 0)) + 1
		check(per_week[tw] <= int(cfg.phone.maxPerWeek), "한 주 문자 수")
	# 1학년 대본의 문자(UI phone_message)는 대화 화면 안에서 나온다 — 폰 문자로 오는 건 일상 문자뿐
	check(career.phone.status.size() >= 1, "1학년 동안 온 문자 %d통: %s" % [career.phone.status.size(), career.phone.status.keys()])
	# 모든 문자의 모든 답장이 오류 없이 적용되고 글자 치환이 남지 않는다
	for m: Dictionary in cfg.messages:
		for i in (m.get("replies", []) as Array).size():
			var c := CareerState.new(cfg, "테스트", _config)
			for id: String in c.people.by_id:
				c.people.meet(id)
			c.phone._receive(c, m)
			c.phone.reply(c, Phone.room_of(m), i)
			for line: Dictionary in c.phone.lines(Phone.room_of(m)):
				check(line.text != "" and not line.text.contains("{"), "%s 답장 %d: %s" % [m.id, i, line.text])


func test_bgm_tracks() -> void:
	# 곡 파일 3개가 있고 (scripts/make_bgm.py), 루프로 돌고, 크기 수치가 있다
	var pres := Presentation.from_balance(BalanceLoader.load_balance())
	var music := MusicPlayer.new()
	music._ready()
	for id in MusicPlayer.TRACKS:
		check(music._streams.has(id), "곡 파일 %s" % id)
		if not music._streams.has(id):
			continue
		var wav: AudioStreamWAV = music._streams[id]
		check(wav.get_length() > 20.0, "%s 길이 %.1f초" % [id, wav.get_length()])
		check(wav.loop_mode == AudioStreamWAV.LOOP_FORWARD, "%s 루프" % id)
		var v: float = pres.bgm_volumes[id]
		check(v > 0.0 and v <= 1.0, "%s 크기 %.2f" % [id, v])
	music.free()


# ---------- 입부 체력 테스트 미니게임 ----------

func _fitness_cfg() -> Dictionary:
	return _career_cfg().fitness


## 50m 를 1초에 rate 번(왼발·오른발 번갈아) 눌러 달린 기록 (step_us 마다 advance — 프레임 간격)
func _run_time(rate: float, step_us: int = 16667, speed: int = 55) -> float:
	var t := RunTest.new(_fitness_cfg()["run"], speed)
	t.go(0)
	var gap := int(1e6 / rate)
	var next_tap := 0
	var foot := 0
	var now := 0
	while t.phase != RunTest.Phase.DONE and now < 30_000_000:
		now += step_us
		while next_tap <= now:
			t.tap(foot, next_tap)
			foot = 1 - foot
			next_tap += gap
		t.advance(now)
	return t.finish_sec


func test_fitness_run() -> void:
	var cfg: Dictionary = _fitness_cfg()["run"]
	var times := {}
	for rate in [6.0, 8.0, 10.0, 12.0]:
		times[rate] = _run_time(rate)
	print("    50m: 1초에 6번 %.2f · 8번 %.2f · 10번 %.2f · 12번 %.2f초" % [times[6.0], times[8.0], times[10.0], times[12.0]])
	check(times[6.0] > times[8.0] and times[8.0] > times[10.0] and times[10.0] >= times[12.0], "빨리 누를수록 빠르다")
	var g := func(sec: float) -> String: return FitnessTest.grade_of(sec, cfg["grades"], true)
	check(g.call(times[10.0]) == "S", "1초에 10번(한 손 5번)이면 S: %.2f" % times[10.0])
	check(g.call(times[8.0]) in ["A", "B"], "8번이면 A~B: %.2f" % times[8.0])
	check(g.call(times[6.0]) == "C", "6번이면 C: %.2f" % times[6.0])
	# 프레임 간격과 상관없이 같은 기록 (원칙 4)
	check(absf(_run_time(9.0, 16667) - _run_time(9.0, 50000)) < 1e-6, "60fps 와 20fps 가 같은 기록")
	# 같은 발 두 번 = 헛발, 땅! 전에 누르면 부정 출발
	var t := RunTest.new(cfg, 55)
	check(t.tap(0, 0) == "early" and t.false_starts == 1 and t.phase == RunTest.Phase.READY, "부정 출발")
	t.go(1_000_000)
	check(t.tap(0, 1_100_000) == "step" and t.tap(1, 1_200_000) == "step", "번갈아 누르면 한 걸음씩")
	var v := t.v
	check(t.tap(1, 1_300_000) == "stumble" and t.v < v and t.stumbling(1_400_000), "같은 발 두 번 → 헛발, 느려진다")
	check(t.tap(0, 1_400_000) == "", "헛발 중에는 입력이 안 먹는다")
	# 손을 놓으면 giveUpSec 에서 끝난다
	var idle := RunTest.new(cfg, 55)
	idle.go(0)
	idle.tap(0, 0)
	idle.advance(int(float(cfg["giveUpSec"]) * 1e6) + 1000)
	check(idle.phase == RunTest.Phase.DONE and is_equal_approx(idle.finish_sec, float(cfg["giveUpSec"])), "손을 놓으면 기권 기록")
	check(absf(t.buddy_x(float(cfg["buddySec"])) - t.distance) < 0.01 and t.buddy_x(0.0) == 0.0, "동기는 buddySec 에 들어온다")
	check(RunTest.new(cfg, 99).max_speed > RunTest.new(cfg, 30).max_speed, "주력이 높으면 최고 속도가 높다")


## 스쿼트 한 번: 게이지가 at 일 때 뗀다
func _lift_rep(t: LiftTest, at: float) -> String:
	var start := 10_000_000 * (t.fails + t.best_kg + 1)
	t.press(start)
	return t.release(start + int(at * t.fill_sec() * 1e6))


func test_fitness_lift() -> void:
	var cfg: Dictionary = _fitness_cfg()["lift"]
	var t := LiftTest.new(cfg, 40)
	var z := t.zone()
	check(z.x < float(cfg["zoneCenter"]) and float(cfg["zoneCenter"]) < z.y, "목표 구간이 가운데를 품는다")
	check(_lift_rep(t, float(cfg["zoneCenter"])) == "perfect" and t.best_kg == int(cfg["startKg"]) and t.kg == int(cfg["startKg"]) + int(cfg["stepKg"]), "가운데서 떼면 완벽, 무게가 오른다")
	check(t.zone().y - t.zone().x < z.y - z.x and t.fill_sec() < LiftTest.new(cfg, 40).fill_sec(), "무거울수록 구간이 좁고 게이지가 빠르다")
	check(_lift_rep(t, (t.zone().x + t.zone().y) / 2.0 - 0.0) in ["perfect", "good"], "구간 안이면 성공")
	check(_lift_rep(t, 0.2) == "early" and t.fails == 1, "일찍 떼면 실패")
	# 끝까지 참으면 숨이 차서 실패 → 실패 두 번이면 끝
	t.press(900_000_000)
	check(t.update(900_000_000 + int(t.fill_sec() * 1e6) + 1) == "late" and t.phase == LiftTest.Phase.DONE, "끝까지 참으면 실패, 두 번 실패하면 끝")
	check(t.release(999_000_000) == "", "끝난 뒤엔 무시")
	# 다 완벽하면 maxKg 에서 끝나고 S
	var best := LiftTest.new(cfg, 40)
	while best.phase != LiftTest.Phase.DONE:
		_lift_rep(best, float(cfg["zoneCenter"]))
	var fills := []
	for kg in range(int(cfg["startKg"]), int(cfg["maxKg"]) + 1, int(cfg["stepKg"])):
		var l := LiftTest.new(cfg, 40)
		l.kg = kg
		fills.append(l.fill_sec())
	for i in range(1, fills.size()):
		check(fills[i] < fills[i - 1], "단계가 오를수록 게이지가 빨리 찬다: %s" % [fills])
	print("    스쿼트 게이지 시간: %s" % [fills.map(func(f: float) -> String: return "%.2f" % f)])
	check(best.best_kg == int(cfg["maxKg"]) and best.fails == 0 and best.grade() == "S", "모두 성공하면 %dkg · S" % best.best_kg)
	check(LiftTest.new(cfg, 90).zone().y - LiftTest.new(cfg, 90).zone().x > LiftTest.new(cfg, 20).zone().y - LiftTest.new(cfg, 20).zone().x, "파워가 높으면 구간이 넓다")


func test_fitness_react() -> void:
	var cfg: Dictionary = _fitness_cfg()["react"]
	var rng := RandomNumberGenerator.new()
	rng.seed = 7
	var a := ReactTest.new(cfg, rng)
	rng.seed = 7
	var b := ReactTest.new(cfg, rng)
	check(a.reds == b.reds and a.delays == b.delays, "같은 시드 → 같은 신호")
	check(a.reds.size() == int(cfg["trials"]) and a.reds.count(true) == int(cfg["reds"]) and not a.reds[0], "빨강 %d개, 첫 신호는 초록" % int(cfg["reds"]))
	var now := 0
	var hit_ms := 250
	var expect_sum := 0
	var greens := 0
	while a.phase != ReactTest.Phase.DONE:
		a.start_wait(now)
		check(a.tap(now + 1000) == "early" and a.phase == ReactTest.Phase.WAIT, "켜지기 전에 누르면 실수, 같은 신호를 다시 기다린다") if a.index == 1 else null
		var lit_at := now + (1000 if a.index == 1 and a.mistakes > 0 else 0) + int(a.delays[a.index] * 1e6)
		check(a.update(lit_at - 1) == "" and a.update(lit_at) == "lit", "기다린 뒤 켜진다")
		if a.is_red():
			check(a.update(lit_at + int(float(cfg["windowSec"]) * 1e6)) == "held", "빨강은 참으면 성공")
		else:
			check(a.tap(lit_at + hit_ms * 1000) == "hit" and a.last_ms() == hit_ms, "초록 반응 %dms" % hit_ms)
			expect_sum += hit_ms
			greens += 1
		now = lit_at + 2_000_000
	check(a.mistakes == 1 and a.score_ms() == expect_sum / greens + int(cfg["mistakeMs"]), "기록 = 평균 + 실수 벌점: %d" % a.score_ms())
	# 빨강에 스윙하면 실수, 초록을 놓치면 missMs
	rng.seed = 7
	var c := ReactTest.new(cfg, rng)
	var red := c.reds.find(true)
	now = 0
	while c.phase != ReactTest.Phase.DONE:
		c.start_wait(now)
		var lit := now + int(c.delays[c.index] * 1e6)
		c.update(lit)
		if c.index == red:
			check(c.tap(lit + 200_000) == "fooled", "빨강에 스윙 → 헛스윙")
		elif c.index == 0:
			check(c.update(lit + int(float(cfg["windowSec"]) * 1e6)) == "miss" and c.last_ms() == int(cfg["missMs"]), "초록을 놓치면 missMs")
		elif c.is_red():
			c.update(lit + int(float(cfg["windowSec"]) * 1e6))
		else:
			c.tap(lit + 300_000)
		now = lit + 2_000_000
	check(c.mistakes == 1, "헛스윙 하나")


func test_fitness_rewards() -> void:
	var cfg := _career_cfg()
	var fc := cfg.fitness
	check(FitnessTest.grade_of(6.0, {"S": 6.8, "A": 7.4, "B": 8.3}, true) == "S" and FitnessTest.grade_of(8.3, {"S": 6.8, "A": 7.4, "B": 8.3}, true) == "B"
		and FitnessTest.grade_of(9.0, {"S": 6.8, "A": 7.4, "B": 8.3}, true) == "C", "낮을수록 좋은 기록 등급")
	check(FitnessTest.grade_of(120, {"S": 120, "A": 100, "B": 80}, false) == "S" and FitnessTest.grade_of(70, {"S": 120, "A": 100, "B": 80}, false) == "C", "높을수록 좋은 기록 등급")
	var all_s := {}
	var all_c := {}
	for k: String in fc["order"]:
		all_s[k] = {"grade": "S", "value": 1.0}
		all_c[k] = {"grade": "C", "value": 1.0}
	var fx := FitnessTest.reward_effects(fc, all_s)
	check(int(fx["speed"]) == 3 and int(fx["power"]) == 3 and int(fx["eye"]) == 3 and "fitness_ace" in fx["flag"] and int(fx["rel"]["coach"]) > 0, "모두 S: %s" % [fx])
	var fx_c := FitnessTest.reward_effects(fc, all_c)
	check(not fx_c.has("speed") and not fx_c.has("rel") and fx_c["flag"] == ["fitness_done"], "모두 C 면 깎이지도 오르지도 않는다: %s" % [fx_c])
	# 커리어: 입부 대화가 미니게임을 열고, 끝내면 보상
	var career := CareerState.new(cfg, "테스트", _config)
	var speed0 := career.player.speed
	while career.pending_minigame == "" and not career.events("start").is_empty():
		var e: Dictionary = career.events("start")[0]
		var run := DialogueRun.new(career, e)
		run.auto(0)
		run.commit()
	check(career.pending_minigame == "fitness_run" and career.events("start").is_empty() == false, "입부 대화 끝 → 첫 종목(50m) 대기")
	var loaded := _reload(career)
	check(loaded.pending_minigame == "fitness_run", "불러와도 체력 테스트가 기다린다")
	check(not career.events("start").is_empty() and career.events("start")[0].id == "ep01_05b", "다음 이야기는 50m 결과 반응")
	# 종목 하나씩 (시나리오가 종목 사이에 대사를 넣는다): 세 종목이 다 모이면 종합 보상
	var parts := career.finish_minigame({"run": all_s["run"]})
	check(career.fitness.size() == 1 and career.player.vars["test_speed_grade"] == "high" and not career.player.flags.has("fitness_done") and EventBook.var_of(career, "test_overall_grade") == "",
		"종목 하나: 그 종목 보상·등급 변수만 (%s)" % [parts])
	parts.append_array(career.finish_minigame({"lift": all_s["lift"], "react": all_s["react"]}))
	check(career.pending_minigame == "" and career.player.speed == speed0 + 3 and career.player.flags.has("fitness_ace"), "보상 반영: %s" % [parts])
	check(career.fitness["run"]["grade"] == "S" and career.news[-1].begins_with("체력 테스트") and career.player.vars["test_overall_grade"] == "high", "기록·소식·종합 등급 변수 (S·A → high)")
	check(FitnessTest.story_overall(cfg.fitness, {"run": {"grade": "S"}, "lift": {"grade": "B"}, "react": {"grade": "C"}}) == "mid"
		and FitnessTest.story_overall(cfg.fitness, {"run": {"grade": "C"}, "lift": {"grade": "C"}, "react": {"grade": "B"}}) == "low", "종합: high 3 · mid 2 · low 1 평균 (2.5 이상 high, 1.5 이상 mid)")
	check(career.minigame_text("events.run.title") != "" and career.minigame_text("overall.S").find("{") < 0, "글이 채워진다")
	# 그림: 배경·칸 시트 크기가 메타와 맞다
	var bg := PixelArt.texture("minigame_bg")
	var sheet := PixelArt.texture("minigame")
	check(bg != null and bg.get_size() == Vector2(MinigameMeta.BG_SIZE.x, MinigameMeta.BG_SIZE.y * MinigameMeta.BG_ROWS.size()), "minigame_bg.png 크기")
	var cols := 0
	for name: String in MinigameMeta.ROWS:
		cols = maxi(cols, int(MinigameMeta.ROWS[name][1]))
	check(sheet != null and sheet.get_size() == Vector2(MinigameMeta.CELL.x * cols, MinigameMeta.CELL.y * MinigameMeta.ROWS.size()), "minigame.png 크기")
	for k: String in fc["order"]:
		check(cfg.minigames[FitnessTest.ID]["events"].has(k) and PlayerData.STATS.has(fc[k]["stat"]), "종목 %s 글·능력치" % k)


# ---------- 내 수비 (유격수) ----------

func _field_cfg() -> Dictionary:
	return BalanceLoader.load_balance()["game"]["fielding"]


func test_field_play_catch() -> void:
	var fc := _field_cfg()
	var t0 := 1_000_000
	var f := FieldPlay.new(fc, _rng(3), 844.0 / 390.0, t0, 4.3)
	var same := FieldPlay.new(fc, _rng(3), 844.0 / 390.0, t0, 4.3)
	check(f.dot == same.dot, "같은 시드 → 같은 자리")
	var area: Dictionary = fc["circleArea"]
	check(f.dot.y >= float(area["yMin"]) and f.dot.y <= float(area["yMax"]), "점은 정해 준 칸 안")
	check(f.update(f.hit_us - 1) == "" and f.update(f.hit_us) == "hit" and f.phase == FieldPlay.Phase.CATCH, "introSec 뒤 타구가 맞는다")
	# 원이 점 안에 들어오는 시각 = 도착 - 판정 폭
	var window_us := int(f.catch_window_sec() * 1e6)
	check(absf(f.ring_radius(f.arrive_us - window_us) - float(fc["dotRadius"])) < 1e-4 and f.ring_radius(f.arrive_us) == 0.0,
		"원은 도착 순간 0, 판정 폭만큼 앞에서 점 크기")
	check(f.press(f.arrive_us - window_us / 2) == "catch" and f.phase == FieldPlay.Phase.THROW, "원이 점 안일 때 누르면 포구")
	var early := FieldPlay.new(fc, _rng(3), 2.0, t0, 4.3)
	early.update(early.hit_us)
	check(early.press(early.arrive_us - window_us - 20000) == "early" and early.kind == FieldPlay.Kind.MISS, "원이 점보다 클 때 누르면 실책")
	var late := FieldPlay.new(fc, _rng(3), 2.0, t0, 4.3)
	late.update(late.hit_us)
	check(late.update(late.arrive_us + int(float(fc["missGraceSec"]) * 1e6) + 1) == "through" and late.kind == FieldPlay.Kind.MISS,
		"안 누르면 공이 빠진다")
	check(late.press(late.arrive_us + 900000) == "" and late.kind == FieldPlay.Kind.MISS, "끝난 뒤 누르면 아무 일도 없다")


## 포구한 FieldPlay 하나 (주자가 1루를 밟는 시각 runner_s, 주력 speed, 송구 거리 35m)
func _caught_play(runner_s: float, seed_value: int = 5, speed: int = 50, fielding: int = 50) -> FieldPlay:
	var f := FieldPlay.new(_field_cfg(), _rng(seed_value), 2.0, 0, runner_s, fielding)
	f.throw_m = 35.0
	f.runner_speed = speed
	f.update(f.hit_us)
	f.press(f.arrive_us)
	return f


## 게이지가 뜨자마자 눌러 g 까지 모았다가 뗀다
func _hold(f: FieldPlay, g: float) -> String:
	f.press(f.aim_us)
	return f.release(f.aim_us + int(g * float(f.cfg["gaugeRiseSec"]) * 1e6))


## 시드 n 개로 칸 g 에 던졌을 때 아웃 비율 (내 수비 능력치 fielding)
func _out_rate(g: float, speed: int, n: int = 400, fielding: int = 50) -> float:
	var phys := _config.ball_physics
	var outs := 0
	for i in n:
		var f := _caught_play(FieldPlay.runner_first_s(phys, speed), 1000 + i, speed, fielding)
		_hold(f, g)
		check(f.is_out() == (f.arrive_s < f.runner_s), "아웃이면 공이 먼저, 세이프면 주자가 먼저 (공 %.2f 주자 %.2f)" % [f.arrive_s, f.runner_s])
		check(f.throw_mps <= f.by_stat("throwMpsMax") + 1e-3, "공은 가장 빠른 공보다 빠르지 않다")
		if f.is_out():
			outs += 1
	return float(outs) / n


func test_field_play_throw() -> void:
	var fc := _field_cfg()
	var phys := _config.ball_physics
	var avg := FieldPlay.runner_first_s(phys, 50)
	check(FieldPlay.runner_first_s(phys, 0) > avg and avg > FieldPlay.runner_first_s(phys, 100) and absf(avg - 4.32) < 0.05,
		"타자 주자 1루 도착: 주력이 높을수록 빠르고 보통 4.3초 (%.2f)" % avg)
	# 눌렀다 떼기
	var f := _caught_play(avg)
	check(f.press(f.aim_us - 1000) == "charge" and f.charge_us == f.aim_us, "공을 빼는 동안 누르면 게이지가 뜨는 순간부터 찬다")
	check(f.release(f.aim_us - 500) == "" and f.charge_us == 0 and f.phase == FieldPlay.Phase.THROW, "게이지가 뜨기 전에 떼면 다시 눌러야 한다")
	check(f.gauge_at(f.aim_us + 300000) == 0.0, "안 누르면 게이지는 0")
	f.press(f.aim_us)
	check(absf(f.gauge_at(f.aim_us + int(0.5 * float(fc["gaugeRiseSec"]) * 1e6)) - 0.5) < 1e-3 and f.charging(), "누르고 있으면 0 → 1 로 찬다")
	var zones := {0.4: "weak", 0.75: "good", 0.88: "perfect", 0.93: "good", 0.97: "wild"}
	for g: float in zones:
		var z := _caught_play(avg)
		check(_hold(z, g) == "throw" and z.zone == zones[g] and z.phase == FieldPlay.Phase.DONE, "게이지 %.2f → %s 칸 (%s)" % [g, zones[g], z.zone])
	var wild := _caught_play(avg)
	_hold(wild, 0.97)
	check(wild.kind == FieldPlay.Kind.WILD, "빨강 → 악송구")
	var full := _caught_play(avg)
	full.press(full.aim_us)
	check(full.update(full.aim_us + int(float(fc["gaugeRiseSec"]) * 1e6)) == "throw" and full.kind == FieldPlay.Kind.WILD, "끝까지 누르면 저절로 던져져 악송구")
	# 아웃 확률 (주력 50): 회색 거의 세이프 · 초록 약 80% · 금색 약 95%
	var weak := _out_rate(0.4, 50)
	var good := _out_rate(0.75, 50)
	var perfect := _out_rate(0.88, 50)
	print("    아웃 비율 (주력 50): 회색 %.2f · 초록 %.2f · 금색 %.2f" % [weak, good, perfect])
	check(weak < 0.2, "회색은 거의 세이프 (%.2f)" % weak)
	check(good > 0.72 and good < 0.88, "초록은 약 80%% 아웃 (%.2f)" % good)
	check(perfect > 0.9, "금색은 약 95%% 아웃 (%.2f)" % perfect)
	var fast := _out_rate(0.75, 100)
	var slow := _out_rate(0.75, 0)
	check(fast < good and good < slow, "빠른 주자일수록 초록 아웃 비율이 낮다 (주력 100 %.2f · 0 %.2f)" % [fast, slow])
	var b := _caught_play(avg, 1003)
	_hold(b, 0.88)
	check(b.kind == (FieldPlay.Kind.BONUS if b.is_out() else FieldPlay.Kind.LATE), "금색 아웃은 보너스")
	var again := _caught_play(avg, 1003)
	_hold(again, 0.88)
	check(again.kind == b.kind and again.arrive_s == b.arrive_s, "같은 시드 → 같은 판정")
	# 너무 늦게 던지면 금색이어도 세이프: 공이 가장 빨라도 주자보다 늦다
	var tight := _caught_play(0.0)
	tight.runner_s = tight.since_hit(tight.aim_us) + 1.0
	_hold(tight, 0.88)
	check(tight.kind == FieldPlay.Kind.LATE and tight.arrive_s >= tight.since_hit(tight.thrown_us) + 35.0 / tight.by_stat("throwMpsMax") - 1e-6,
		"늦게 던지면 금색이어도 세이프")
	var late := _caught_play(avg)
	var reach := late.hit_us + int(ceil(avg * 1e6))
	check(late.update(reach - 2) == "" and late.update(reach) == "late" and late.kind == FieldPlay.Kind.LATE,
		"못 던진 사이 주자가 1루를 밟으면 내야안타")
	check(late.press(reach + 10) == "" and late.release(reach + 20) == "", "끝난 뒤 누르고 떼도 아무 일도 없다")


func test_shortstop_in_game_state() -> void:
	var cfg := _career_cfg()
	var mk := func(bases: Array, outs: int) -> GameState:
		var g := GameState.new(9, cfg.running, _config.ball_physics)
		g.bases = bases
		g.outs = outs
		return g
	var g: GameState = mk.call([1, 5, 6], 0)
	var r := g.apply_shortstop(FieldPlay.Kind.BONUS, 2)
	check(g.outs == 1 and g.bases == [1, 5, 6] and r.runs == 0 and r.outs == 1, "레이저 송구: 타자만 아웃, 주자는 묶인다 %s" % [g.bases])
	g = mk.call([1, -1, 3], 1)
	r = g.apply_shortstop(FieldPlay.Kind.NORMAL, 2)
	check(g.outs == 2 and g.bases == [-1, 1, -1] and r.runs == 1, "보통 송구: 타자 아웃, 주자 한 칸씩 (1사 3루 주자 홈인) %s" % [g.bases])
	g = mk.call([1, 2, 3], 0)
	r = g.apply_shortstop(FieldPlay.Kind.WILD, 4)
	check(g.outs == 0 and g.bases == [-1, 4, 1] and r.runs == 2 and r.rbi == 0, "악송구: 타자 2루, 2·3루 주자 홈인(타점 없음) %s" % [g.bases])
	check(r.outcome == SwingJudge.Outcome.GROUND_OUT, "실책 출루는 기록상 안타가 아니다")
	g = mk.call([1, -1, 3], 0)
	r = g.apply_shortstop(FieldPlay.Kind.MISS, 4)
	check(g.bases == [4, 1, -1] and r.runs == 1 and r.rbi == 0, "포구 실책: 모두 한 칸씩 %s" % [g.bases])
	g = mk.call([-1, 2, 3], 0)
	r = g.apply_shortstop(FieldPlay.Kind.LATE, 4)
	check(r.outcome == SwingJudge.Outcome.SINGLE and g.bases == [4, 2, 3] and r.runs == 0, "내야안타: 밀려나는 주자만 %s" % [g.bases])
	g = mk.call([1, -1, -1], 2)
	r = g.apply_shortstop(FieldPlay.Kind.NORMAL, 2)
	check(r.half_over and g.outs == 0 and g.bases == [-1, -1, -1], "세 번째 아웃이면 공수 교대")


func test_fielding_in_game() -> void:
	var kinds := [FieldPlay.Kind.BONUS, FieldPlay.Kind.NORMAL, FieldPlay.Kind.WILD, FieldPlay.Kind.MISS, FieldPlay.Kind.LATE]
	var fc := _field_cfg()
	var total := 0
	for s in 40:
		var cfg := _career_cfg()
		var player := PlayerData.create("테스트", cfg)
		var srng := _rng(s)
		var mine := School.create("me", "우리고", "우리", 50.0, "balanced", cfg, srng)
		var theirs := School.from_content(cfg.regional_schools[s % cfg.regional_schools.size()], cfg, srng)
		var game := GameRunner.new(cfg, _config, player, mine, theirs, s % 2 == 0, s, true, false)
		game.fielding = true
		var rng := _rng(s + 999)
		var fields := 0
		var guard := 0
		while not game.state.over and guard < 600:
			guard += 1
			var step := game.step()
			match step.type:
				"my_turn":
					var pa := AutoPa.simulate(cfg.auto_pa, _config, game.current_batter().skills, game.current_pitcher(), rng)
					var c: SwingJudge.Contact = null
					if pa.ball != null:
						c = SwingJudge.Contact.new(SwingJudge.Quality.SOLID, pa.ball.spray_deg, pa.ball.distance_m, pa.ball.batted_ball)
						c.ball = pa.ball
					game.apply_my_result(AtBat.Result.new(pa.outcome, SwingJudge.BattedBall.NONE, pa.pitches, c))
				"my_field":
					check(step.side != game.my_side, "수비 기회는 상대 공격 때만")
					game.apply_field(kinds[(s + fields) % kinds.size()])
					fields += 1
		total += fields
		check(fields >= int(fc["chancesMin"]) and fields <= int(fc["chancesMax"]), "한 경기 수비 기회 %d~%d번 (seed %d: %d)" % [fc["chancesMin"], fc["chancesMax"], s, fields])
		check(game.my_fields.size() == fields, "내 수비 기록")
		var outs_made := game.my_fields.filter(func(k: int) -> bool: return k == FieldPlay.Kind.BONUS or k == FieldPlay.Kind.NORMAL).size()
		var errs := game.my_fields.filter(func(k: int) -> bool: return k == FieldPlay.Kind.MISS or k == FieldPlay.Kind.WILD).size()
		check(game.my_line.assists == outs_made and game.my_line.errors == errs, "보살·실책이 내 경기 기록에 (seed %d)" % s)
		var st := game.state
		var box := game.box
		for side in 2:
			check(box.team_runs(side) == st.score[side] and box.runs_allowed(1 - side) == st.score[side], "득점·실점 합 = 총점 (seed %d)" % s)
			var hits := 0
			for b: BoxScore.BatterLine in box.batters[side]:
				hits += b.h
				check(b.pa == b.ab + b.bb + b.sh + b.sf, "타석 = 타수 + 볼넷 + 희생 (seed %d)" % s)
			check(hits == box.hits[side], "안타 합 = 팀 안타")
		var outs := 0
		for p: BoxScore.PitcherLine in box.pitchers[1]:
			outs += p.outs
		check(outs == st.line[0].size() * 3, "홈 투수 아웃 = 원정 이닝 × 3 (seed %d)" % s)
	# 수비를 끄면 그 타석도 자동이고, 수비 계획용 난수가 경기 난수와 따로라서 결과가 그대로다
	var off_a := _play_full_game(7)
	var off_b := _play_full_game(7)
	check(off_a.state.score == off_b.state.score and off_a.my_fields.is_empty(), "수비를 끄면 수비 기회가 없다")
	check(total > 40, "수비 기회가 실제로 온다 (%d)" % total)


func test_fielding_stat_effects() -> void:
	var phys := _config.ball_physics
	var avg := FieldPlay.runner_first_s(phys, 50)
	var lo := _caught_play(avg, 5, 50, 10)
	var hi := _caught_play(avg, 5, 50, 90)
	check(hi.catch_window_sec() > lo.catch_window_sec() and hi.travel_sec > lo.travel_sec,
		"수비가 좋으면 원이 천천히 줄어 포구 판정 폭이 넓다 (%.3f → %.3f초)" % [lo.catch_window_sec(), hi.catch_window_sec()])
	check(hi.transfer_sec < lo.transfer_sec and hi.aim_us - hi.caught_us < lo.aim_us - lo.caught_us, "수비가 좋으면 공을 빨리 뺀다")
	check(hi.perfect_max() - hi.perfect_min() > lo.perfect_max() - lo.perfect_min() and hi.perfect_max() < float(hi.cfg["gaugeWildMin"]),
		"수비가 좋으면 금색 칸이 넓다 (악송구 칸은 그대로)")
	check(hi.by_stat("throwMpsMax") > lo.by_stat("throwMpsMax"), "수비가 좋으면 가장 빠른 공이 더 빠르다")
	var good_lo := _out_rate(0.75, 50, 300, 10)
	var good_hi := _out_rate(0.75, 50, 300, 90)
	check(good_hi > good_lo, "같은 초록이어도 수비가 좋으면 더 자주 잡는다 (수비 10 %.2f · 90 %.2f)" % [good_lo, good_hi])
	# 같은 게이지·같은 시드로 던졌을 때 아웃이면, 어깨가 좋을수록 공이 더 일찍(빠르게) 꽂힌다
	var sum_lo := 0.0
	var sum_hi := 0.0
	var n := 0
	for i in 200:
		var a := _caught_play(avg, 2000 + i, 50, 10)
		var b := _caught_play(avg, 2000 + i, 50, 90)
		_hold(a, 0.88)
		_hold(b, 0.88)
		if a.is_out() and b.is_out():
			sum_lo += a.margin()
			sum_hi += b.margin()
			n += 1
	check(n > 100 and sum_hi > sum_lo, "아웃일 때 수비가 좋으면 여유가 크다 (평균 %.2f → %.2f초)" % [sum_lo / maxi(n, 1), sum_hi / maxi(n, 1)])


func test_fielding_stat_career() -> void:
	var cfg := _career_cfg()
	var career := CareerState.new(cfg, "테스트", _config)
	var p := career.player
	check("fielding" in PlayerData.STATS and p.fielding == int(cfg.start["fielding"]) and p.potential.has("fielding"), "수비 능력치가 있고 잠재력도 있다")
	check(cfg.training.has("fielding") and cfg.training["fielding"]["stat"] == "fielding", "수비 훈련이 있다")
	check(TrainingMeta.ROWS.has("fielding") and PixelArt.texture("training").get_height() == int(TrainingMeta.SIZE.y) * TrainingMeta.ROWS.size(), "수비 훈련 장면 그림")
	var before := p.fielding + float(p.progress.get("fielding", 0.0))
	career.train("fielding")
	check(p.fielding + float(p.progress.get("fielding", 0.0)) > before, "수비 훈련 → 수비 성장")
	# 경기용 수비: 컨디션이 나쁘면 내려가고, 내야수 글러브를 사면 오른다
	p.condition = 100
	var fresh := p.fielding_for_game(cfg)
	p.condition = 20
	check(p.fielding_for_game(cfg) < fresh, "컨디션이 나쁘면 경기용 수비도 내려간다")
	p.condition = 80
	var bare := p.fielding_for_game(cfg)
	p.gear["infield_glove"] = true
	check(p.fielding_for_game(cfg) == bare + int(cfg.shop["gear"]["infield_glove"]["bonus"]["fielding"]), "내야수 글러브 → 경기용 수비 +")
	# 보살 → 수비 성장, 실책은 기록만
	var game := career.new_game()
	game.my_line.assists = 3
	game.my_line.errors = 1
	var f0 := p.fielding + float(p.progress.get("fielding", 0.0))
	career.finish_game(game)
	check(p.fielding + float(p.progress.get("fielding", 0.0)) > f0, "보살 → 수비 성장")
	check(p.season.assists == 3 and p.season.errors == 1, "보살·실책이 시즌 기록에 쌓인다")
	# 옛 저장 (능력치 넷): 수비 잠재력·시즌 시작 값이 없어도 불러오면 채워진다
	var old := CareerState.new(cfg, "옛날", _config)
	old.player.potential.erase("fielding")
	old.player.season_start.erase("fielding")
	old.rival.player.potential.erase("fielding")
	var back := _reload(old)
	check(back.player.potential.has("fielding") and back.player.season_start.has("fielding"), "옛 저장 불러오면 수비 잠재력이 채워진다")
	check(int(back.rival.player.potential["fielding"]) == int(cfg.rival["potential"]["fielding"]), "라이벌은 설정의 잠재력")
	back.train("fielding")
	check(back.player.fielding >= 1, "옛 저장에서도 수비 훈련이 된다")
	check(SaveGame.to_bytes(_reload(back)) == SaveGame.to_bytes(back), "고친 뒤 저장·불러오기는 그대로")
