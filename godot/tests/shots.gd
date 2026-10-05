extends Node
## 확인용 스크린샷: 메인 화면을 띄워 공이 날아오는 장면, 헛스윙, 정타 순간을 찍어 godot/build/shots/ 에 남긴다.
##   ~/Applications/Godot.app/Contents/MacOS/Godot --path godot res://tests/shots.tscn

var _main: Control


func _ready() -> void:
	# 폰 세로 화면 크기로 찍는다
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(390, 844)
	await get_tree().process_frame
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	_main = load("res://scenes/batting.tscn").instantiate()
	add_child(_main)
	await get_tree().process_frame
	var s: BattingSession = _main._session

	await _until(func() -> bool: return s.phase == BattingSession.Phase.FLIGHT)
	await _wait_ms(s.pitch.flight_ms * 0.6)
	await _shot("flight")
	s.tap()
	await _wait_ms(200)
	await _shot("after-swing")

	# 컨택·파워 100 으로 공이 도착하는 순간에 맞춰 탭
	_main._sliders["contact"].value = 100
	_main._sliders["power"].value = 100
	await _until(func() -> bool:
		if s.phase == BattingSession.Phase.OVER:
			s.tap()
		return s.phase == BattingSession.Phase.FLIGHT and not s.swung)
	while s.since(s.phase_since_ms) < s.pitch.flight_ms - 8.0:
		await get_tree().process_frame
	s.tap()
	await _wait_ms(40)
	await _shot("impact")
	await _wait_ms(220)
	await _shot("launch")
	await _wait_ms(700)
	await _shot("result")
	get_tree().quit()


func _until(cond: Callable) -> void:
	var start := Time.get_ticks_msec()
	while not cond.call() and Time.get_ticks_msec() - start < 8000:
		await get_tree().process_frame


func _wait_ms(ms: float) -> void:
	var start := Time.get_ticks_usec()
	while (Time.get_ticks_usec() - start) / 1000.0 < ms:
		await get_tree().process_frame


func _shot(name: String) -> void:
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % name)
