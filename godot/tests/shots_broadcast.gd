extends Node
## 타구 중계 화면 확인용: 연습 화면에서 공을 쳐서 중계 화면을 시간대별로 찍는다 → godot/build/shots/bc-*.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --path godot res://tests/shots_broadcast.tscn

var _app: Control


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(844, 390)
	await get_tree().process_frame
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	_app = load("res://scenes/batting.tscn").instantiate()
	add_child(_app)
	await _until(func() -> bool: return _app._current != null)
	_app.show_practice()
	await _wait_ms(300)
	var screen: Control = _app._current
	var s: BattingSession = screen._session
	s.update_skills(BatterSkills.new(90, 90, 60, 50))
	# 투구 전 선택 시간
	await _until(func() -> bool: return s.select_progress() >= 0.3)
	await _shot("bc-00-select")
	var shots := 0
	var started := Time.get_ticks_msec()
	while shots < 3 and Time.get_ticks_msec() - started < 90000:
		if s.phase == BattingSession.Phase.OVER:
			s.tap()
		if s.phase == BattingSession.Phase.WINDUP and shots == 0:
			await _wait_ms(s.presentation.windup_ms * 0.7)
			await _shot("bc-0-stance")
		if s.phase == BattingSession.Phase.FLIGHT and shots == 0 and not s.swung and s.since(s.phase_since_ms) >= s.pitch.flight_ms * 0.7 and not FileAccess.file_exists("res://build/shots/bc-01-flight.png"):
			await _shot("bc-01-flight")
		if s.phase == BattingSession.Phase.FLIGHT and not s.swung and s.pitch.is_strike() and s.since(s.phase_since_ms) >= s.pitch.flight_ms - 3.0:
			s.tap()
			if s.phase == BattingSession.Phase.HIT:
				shots += 1
				var dur: float = s.last_result.contact.ball.duration()
				for k in [0.15, 0.5, 1.0]:
					await _until(func() -> bool: return s.phase != BattingSession.Phase.HIT or s.broadcast_time() >= dur * k)
					await _shot("bc-%d-%d" % [shots, int(k * 100)])
				print("타구 %d: %s, %.0f km/h, %.0f°, 방향 %.0f°, %.0fm, 수비 %s" % [shots, SwingJudge.Outcome.keys()[s.last_result.outcome],
					s.last_result.contact.ev_kmh, s.last_result.contact.launch_deg, s.last_result.contact.angle_deg, s.last_result.contact.distance_m, s.last_result.contact.ball.fielder])
		await get_tree().process_frame
	# 번트 자세
	s.set_swing_type("bunt")
	await _until(func() -> bool:
		if s.phase == BattingSession.Phase.OVER:
			s.tap()
		return s.phase == BattingSession.Phase.WINDUP)
	await _wait_ms(300)
	await _shot("bc-9-bunt")
	get_tree().quit()


func _until(cond: Callable, timeout_ms: int = 20000) -> void:
	var start := Time.get_ticks_msec()
	while not cond.call() and Time.get_ticks_msec() - start < timeout_ms:
		await get_tree().process_frame


func _wait_ms(ms: float) -> void:
	var start := Time.get_ticks_usec()
	while (Time.get_ticks_usec() - start) / 1000.0 < ms:
		await get_tree().process_frame


func _shot(name: String) -> void:
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % name)
