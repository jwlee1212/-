extends Node
## 테스트 시작(원하는 시점부터) 확인용 스크린샷 → godot/build/shots/jump-*.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --path godot res://tests/shots_jump.tscn

var _app: Control


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(844, 390)
	await get_tree().process_frame
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	_app = load("res://scenes/batting.tscn").instantiate()
	# 유저 저장을 건드리지 않게 따로 저장하고, 매번 빈 저장에서 시작한다
	_app.save_path = "user://shots.save"
	SaveGame.delete(_app.save_path)
	add_child(_app)
	await _frames(10)
	await _shot("jump-1-lobby")
	_app.show_jump()
	await _frames(10)
	var screen: JumpScreen = _app._current
	screen._grade = 2
	screen._point = "camp_game"
	screen._money = 300000
	screen._partner = "manager"
	screen._build()
	await _frames(10)
	await _shot("jump-2-pick")
	screen.start()
	await _frames(20)
	await _shot("jump-3-running")
	while _app._current is JumpScreen:
		await get_tree().process_frame
	await _frames(10)
	await _shot("jump-4-arrived")
	_app.show_title()
	await _frames(10)
	await _shot("jump-5-lobby-test")
	_app.end_test()
	await _frames(10)
	await _shot("jump-6-lobby-back")
	SaveGame.delete("user://shots.save")
	get_tree().quit()


func _frames(n: int) -> void:
	for i in n:
		await get_tree().process_frame


func _shot(name: String) -> void:
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % name)
	print("shot ", name)
