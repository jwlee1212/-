extends Node
## 주루 연습 화면 확인: 타이틀의 버튼과 네 가지 연습을 찍는다 → godot/build/shots/runprac-*.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --path godot res://tests/shots_run_practice.tscn


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(844, 390)
	await get_tree().process_frame
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	var app: Control = load("res://scenes/batting.tscn").instantiate()
	app.save_path = "user://shots.save"
	SaveGame.delete(app.save_path)
	add_child(app)
	await _wait_ms(300)
	await _shot("runprac-0-title")
	app.show_settings()
	await _wait_ms(200)
	await _shot("runprac-0-settings")
	app.show_run_practice()
	var screen: RunPracticeScreen = app._current
	await _wait_ms(400)
	await _shot("runprac-1-steal2")
	# 도루: 투구 0.2초 뒤 스타트 → 판정 → 다음 상황으로 넘어가는지
	var sv: StealView = screen._view
	while sv.elapsed_ms() < sv._plan.go_ms + 200.0:
		await get_tree().process_frame
	sv._on_go()
	var round_before := screen._round
	while screen._round == round_before:
		await get_tree().process_frame
	print("도루 뒤: %s" % screen._tally.text)
	for mode: String in ["extra", "tag"]:
		screen._set_mode(mode)
		var d: Dictionary = screen._control.play.decisions[0]
		var h: HighlightView = screen._view
		while h._t() < float(d.t) - 0.6:
			await get_tree().process_frame
		await _shot("runprac-2-%s" % mode)
		screen._control.set_target(4)
		while not screen._result_chip.visible:
			await get_tree().process_frame
		await _shot("runprac-3-%s-result" % mode)
		print("%s: %s / %s" % [mode, screen._result.text, screen._tally.text])
	SaveGame.delete(app.save_path)
	get_tree().quit()


func _wait_ms(ms: float) -> void:
	var start := Time.get_ticks_usec()
	while (Time.get_ticks_usec() - start) / 1000.0 < ms:
		await get_tree().process_frame


func _shot(name: String) -> void:
	await get_tree().process_frame
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % name)
