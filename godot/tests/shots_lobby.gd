extends Node
## 로비 확인용 스크린샷: 판이 들어오는 중 · 다 들어온 뒤 · 이어하기가 있는 로비 → godot/build/shots/lobby-*.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --path godot res://tests/shots_lobby.tscn

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
	await _until(func() -> bool: return _app._current is TitleScreen)
	await _wait_ms(200)
	await _shot("lobby-1-slide")
	await _wait_ms(1200)
	await _shot("lobby-2-menu")
	# 이어하기가 있는 로비 (커리어를 하나 만든 뒤)
	_app.start_career("김도윤")
	_app.show_title()
	await _wait_ms(1500)
	await _shot("lobby-3-continue")
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
	print("shot %s %dms" % [name, Time.get_ticks_msec()])
