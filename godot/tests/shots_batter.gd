extends Node
## 확인용 스크린샷: 실제 타석 화면(batter-1 ~ 8)을 찍는다. 도트 칸을 늘어놓은 그림은 make_pixel_art.py 가 만든다
## (build/shots/pixel-batter.png · pixel-pitcher.png, 4배 확대).
##   ~/Applications/Godot.app/Contents/MacOS/Godot --always-on-top --path godot res://tests/shots_batter.tscn


func _ready() -> void:
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	# 실제 타석 화면(타격 연습)에서: 준비 → 와인드업 → 공이 오는 중 → 맞는 순간 → 팔로스루
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(844, 390)
	await get_tree().process_frame
	var app: Control = load("res://scenes/batting.tscn").instantiate()
	app.save_path = "user://shots.save"
	add_child(app)
	await _until(func() -> bool: return app._current != null)
	app.show_practice()
	await _wait_ms(300)
	var s: BattingSession = app._current._session
	await _shot("batter-1-ready")
	# 주자가 있을 때 투수 뒤로 보이는 모습 (화면만 — 판정과 무관하게 잠깐 바꿨다가 되돌린다)
	var saved := s.situation
	s.situation = {"bases": [1, 2, 3]}
	await _shot("batter-1-runners")
	s.situation = saved
	s.ready_now()
	await _until(func() -> bool: return s.phase == BattingSession.Phase.WINDUP)
	await _wait_ms(s.presentation.windup_ms * 0.8)
	await _shot("batter-2-windup")
	# 스트라이크가 올 때까지 기다렸다가 공이 도착하는 순간에 친다
	var t0 := Time.get_ticks_msec()
	var shot_flight := false
	while Time.get_ticks_msec() - t0 < 60000:
		if s.phase == BattingSession.Phase.WAITING:
			s.ready_now()
		elif s.phase == BattingSession.Phase.OVER:
			s.tap()
		elif s.phase == BattingSession.Phase.FLIGHT and not s.swung:
			var elapsed := s.since(s.phase_since_ms)
			if not shot_flight and elapsed >= s.pitch.flight_ms * 0.5:
				shot_flight = true
				await _shot("batter-3-flight")
			if s.pitch.is_strike() and elapsed >= s.pitch.flight_ms - 4.0:
				s.tap()
				if s.phase == BattingSession.Phase.HIT:
					break
		await get_tree().process_frame
	await _wait_ms(30)
	await _shot("batter-4-contact")
	await _wait_ms(s.presentation.hit_stop_ms + s.presentation.swing_ms * 0.5)
	await _shot("batter-5-follow")
	# 공이 끝난 뒤: 팔로스루로 멈춰 있다가(6) 사이 칸(7)을 거쳐 준비 자세(8)로 — 다음 타석에서 일찍 휘둘러 헛스윙·파울을 만든다
	await _until(func() -> bool: return s.phase == BattingSession.Phase.OVER, 30000)
	s.tap()
	await _until(func() -> bool: return s.phase == BattingSession.Phase.WAITING)
	s.ready_now()
	await _until(func() -> bool: return s.phase == BattingSession.Phase.FLIGHT)
	await _wait_ms(s.pitch.flight_ms * 0.3)
	s.tap()
	await _until(func() -> bool: return s.phase != BattingSession.Phase.FLIGHT)
	if s.phase == BattingSession.Phase.WAITING:
		await _wait_ms(300)
		await _shot("batter-6-hold")
		await _wait_ms(s.presentation.pitch_interval_ms - 300 + s.presentation.batter_recover_ms * 0.5)
		await _shot("batter-7-recover")
		await _wait_ms(s.presentation.batter_recover_ms)
		await _shot("batter-8-ready")
	get_tree().quit()


func _until(cond: Callable, timeout_ms: int = 20000) -> void:
	var start := Time.get_ticks_msec()
	while not cond.call() and Time.get_ticks_msec() - start < timeout_ms:
		await get_tree().process_frame


func _wait_ms(ms: float) -> void:
	var start := Time.get_ticks_usec()
	while (Time.get_ticks_usec() - start) / 1000.0 < ms:
		await get_tree().process_frame


func _shot(shot_name: String) -> void:
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % shot_name)

