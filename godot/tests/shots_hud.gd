extends Node
## 타석 중계 그래픽 확인: 실제 경기의 내 첫 타석에서 타자 자막이 나오는 중·떠 있는 동안·들어간 뒤, 구속 패널을 찍는다
## → godot/build/shots/hud-1-card-in.png, hud-2-card.png, hud-3-select.png, hud-4-speed.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --path godot res://tests/shots_hud.tscn

const MAX_MS := 120000

var _app: Control


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(844, 390)
	await get_tree().process_frame
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	_app = load("res://scenes/batting.tscn").instantiate()
	_app.save_path = "user://shots.save"
	SaveGame.delete(_app.save_path)
	add_child(_app)
	await _wait_ms(300)
	_app.start_career("김도윤")
	# 2학년 시즌 몇 주 뒤: 자막에 시즌 기록이 들어가게
	FastForward.run(_app.career, 2, "season_start")
	for i in 3:
		FastForward.step(_app.career)
	var career: CareerState = _app.career
	FastForward._resolve(career, "start")
	FastForward._train(career)
	_app.show_game()
	var screen: GameScreen = _app._current
	screen._fast = true
	var started := Time.get_ticks_msec()
	while screen._session == null and not screen._game.state.over and Time.get_ticks_msec() - started < MAX_MS:
		if screen._dialogue != null:
			screen._dialogue.choose(0)
			screen._dialogue.skip()
		await get_tree().process_frame
	var s: BattingSession = screen._session
	if s == null:
		push_error("내 타석이 오지 않았다")
		get_tree().quit(1)
		return
	var p := s.presentation
	await _wait_ms(p.batter_card_slide_ms * 0.5)
	await _shot("hud-1-card-in")
	await _wait_ms(p.batter_card_slide_ms + 300)
	await _shot("hud-2-card")
	await _until(func() -> bool: return s.select_progress() >= 0.3)
	await _shot("hud-3-select")
	# 공이 지나가 구속이 뜬 뒤
	await _until(func() -> bool: return s.phase == BattingSession.Phase.FLIGHT)
	await _until(func() -> bool: return s.phase != BattingSession.Phase.FLIGHT or s.since(s.phase_since_ms) > s.pitch.flight_ms + 120.0)
	await _shot("hud-4-speed")
	get_tree().quit()


func _until(cond: Callable) -> void:
	var start := Time.get_ticks_msec()
	while not cond.call() and Time.get_ticks_msec() - start < 20000:
		await get_tree().process_frame


func _wait_ms(ms: float) -> void:
	var start := Time.get_ticks_usec()
	while (Time.get_ticks_usec() - start) / 1000.0 < ms:
		await get_tree().process_frame


func _shot(name: String) -> void:
	await get_tree().process_frame
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % name)
