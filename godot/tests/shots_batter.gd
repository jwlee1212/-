extends Node
## 확인용 스크린샷: 타자 컷아웃 캐릭터의 자세를 한 장에 늘어놓는다 → godot/build/shots/batter-poses.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --path godot res://tests/shots_batter.tscn
## 준비 → 힘 모으기 → 스윙(0.2, 맞는 순간, 0.7, 끝) → 번트. 이어서 실제 타석 화면 다섯 장(batter-1 ~ 5)


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(1680, 900)
	var back := ColorRect.new()
	back.color = Tokens.GRASS
	back.size = Vector2(1680, 900)
	add_child(back)
	# [힘 모으기, 스윙 진행률, 번트, 배트 각도]
	var poses := [[0.0, -1.0, false, -30.0], [1.0, -1.0, false, -42.0], [1.0, 0.2, false, 35.0], [1.0, BatterRig.CONTACT_SWING, false, 95.0],
		[1.0, 0.7, false, 134.0], [1.0, 1.0, false, 150.0], [0.0, -1.0, true, 88.0]]
	for i in poses.size():
		var rig := BatterRig.new()
		# 창은 크지만 화면 좌표는 844 폭 기준으로 늘어난다
		rig.scale = Vector2.ONE * 0.3
		rig.position = Vector2(80 + (i % 4) * 210, 150 + (i / 4) * 220)
		add_child(rig)
		rig.pose(poses[i][0], poses[i][1], poses[i][2], poses[i][3])
	await get_tree().create_timer(0.3).timeout
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	get_viewport().get_texture().get_image().save_png("res://build/shots/batter-poses.png")
	for child in get_children():
		child.queue_free()

	# 실제 타석 화면(타격 연습)에서: 준비 → 와인드업 → 공이 오는 중 → 맞는 순간 → 팔로스루
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
