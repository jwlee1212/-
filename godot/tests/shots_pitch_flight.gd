extends Node
## 확인용 스크린샷: 실제 타석 화면(타격 연습)에서 구종마다 공이 날아오는 막판 한 장 → godot/build/shots/flight-<구종>.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --always-on-top --path godot res://tests/shots_pitch_flight.tscn
## 구종 고정(개발 빌드 버튼과 같은 forced_pitch_type)으로 던지게 하고, 비행 25(커브가 튀어 오른 꼭대기 근처)·60·85·97% 와 도착 직후(108%, 공이 미트에 멈춰 있는 모습)를 찍는다 → flight-<구종>-<%>.png
## (잔상으로 휘어 온 길이 보인다). 앞 공의 판정 말풍선이 사라진 뒤에 던진다

const AT := [0.25, 0.6, 0.85, 0.97, 1.08]


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(844, 390)
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	var app: Control = load("res://scenes/batting.tscn").instantiate()
	app.save_path = "user://shots.save"
	add_child(app)
	await _until(func() -> bool: return app._current != null)
	app.show_practice()
	await _wait_ms(300)
	var s: BattingSession = app._current._session
	for id: String in PitchCatalog.IDS:
		s.config.forced_pitch_type = id
		# 지금 공이 끝날 때까지 기다렸다가 새 공 (타석이 끝났으면 넘긴다)
		var start := Time.get_ticks_msec()
		while s.phase != BattingSession.Phase.WAITING and Time.get_ticks_msec() - start < 20000:
			if s.phase == BattingSession.Phase.OVER:
				s.tap()
			await get_tree().process_frame
		await _until(func() -> bool: return s.callout == null, 3000)
		s.ready_now()
		await _until(func() -> bool: return s.phase == BattingSession.Phase.FLIGHT and s.pitch.type == id)
		for at: float in AT:
			await _until(func() -> bool: return s.since(s.phase_since_ms) >= s.pitch.flight_ms * at)
			await _shot("flight-%s-%d" % [id, roundi(at * 100)])
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
