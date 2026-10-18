extends Node
## 확인용 스크린샷: 타자 포즈 그림을 한 장에 늘어놓고(batter-poses.png), 실제 타석 화면 다섯 장(batter-1 ~ 5)을 찍는다.
##   ~/Applications/Godot.app/Contents/MacOS/Godot --path godot res://tests/shots_batter.tscn
## 포즈 그림이 아직 없는 칸은 준비 자세가 대신 나오고 이름 옆에 "(없음)" 이 붙는다


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(1680, 780)
	var back := ColorRect.new()
	back.color = Tokens.GRASS
	back.size = Vector2(1680, 900)
	add_child(back)
	var frames: Array = BatterSprite.FRAMES.keys()
	for i in frames.size():
		var batter := BatterSprite.new()
		# 창은 크지만 화면 좌표는 844 폭 기준으로 늘어난다
		batter.scale = Vector2.ONE * 0.3
		batter.position = Vector2(55 + i * 170, 320)
		add_child(batter)
		batter.show_frame(frames[i])
		var label := Label.new()
		label.text = frames[i] + ("" if batter.has_frame(frames[i]) else " (없음)")
		label.position = Vector2(35 + i * 170, 330)
		add_child(label)
	await get_tree().create_timer(0.3).timeout
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	get_viewport().get_texture().get_image().save_png("res://build/shots/batter-poses.png")
	# 투수: 와인드업 0.7초 + 던진 뒤 0.35초를 15칸으로 나눠 한 장에 (포즈가 넘어가는 모습을 본다)
	var strip := _PitcherStrip.new()
	add_child(strip)
	await get_tree().create_timer(0.3).timeout
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/pitcher-strip.png")
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


class _PitcherStrip extends Control:
	func _draw() -> void:
		draw_rect(Rect2(0, 0, 844, 400), Tokens.GRASS)
		texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS
		for i in 24:
			var ms := i * 50.0
			var windup := minf(ms / 700.0, 1.0)
			var since := ms - 700.0 if ms >= 700.0 else -1.0
			var feet := Vector2(60 + (i % 8) * 100, 112 + (i / 8) * 127)
			PitcherPoses.draw(self, windup, since, 700.0, feet, 92.0)
			draw_string(Tokens.FONT_REGULAR, feet + Vector2(-20, 14), "%dms" % ms, HORIZONTAL_ALIGNMENT_LEFT, -1, 10, Tokens.CHALK)
