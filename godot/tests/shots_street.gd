extends Node
## 로비 골목 확인용 스크린샷 → godot/build/shots/street-*.png
## 아침 집 앞 → 밀어서 길 끝(야구장) → 운동장으로 걸어가는 중 → 들어간 훈련 메뉴 → 훈련 뒤(야구장 말풍선)
## → 경기 뒤 노을(야구장 앞) → 겨울 캠프 눈
##   ~/Applications/Godot.app/Contents/MacOS/Godot --always-on-top --path godot res://tests/shots_street.tscn

var _app: Control


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(844, 390)
	await get_tree().process_frame
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	_app = load("res://scenes/batting.tscn").instantiate()
	_app.save_path = "user://shots.save"
	_app.show_tips = false
	SaveGame.delete(_app.save_path)
	add_child(_app)
	await _frames(10)
	_app.start_career("김도윤")
	var career: CareerState = _app.career
	FastForward.run(career, 1, "phase:1")
	_quiet(career)
	_app.show_home()
	await _frames(10)
	await _shot("street-1-morning")
	var st: StreetView = _app._current.street()
	st.cam = st.map.clamp_camera(st.map.width, st.size.x / st.scale_px())
	await _frames(5)
	await _shot("street-2-end")
	st.cam = st.map.camera_left(st.player_x, st.size.x / st.scale_px())
	await _frames(5)
	await _shot("street-1b-near-door")
	# 방향키로 오른쪽 (화면 › 버튼을 누르고 있는 것과 같다) → 문 사이 빈 땅
	st.hold_dir = 1
	await _wait(0.45)
	await _shot("street-1c-moving")
	st.hold_dir = 0
	await _frames(5)
	st.go_to("ground")
	await _wait(0.55)
	await _shot("street-3-walking")
	st.finish_walk()
	await _frames(10)
	await _shot("street-4-training")
	FastForward._train(career)
	_app._current._show_tab("lobby")
	await _frames(10)
	await _shot("street-5-ready")
	_app._current._show_tab("game")
	await _frames(10)
	await _shot("street-6-game-menu")
	FastForward._play(career)
	_app.lobby_spot = "ballpark"
	_quiet(career)
	_app.show_home()
	await _frames(10)
	await _shot("street-7-dusk")
	FastForward._resolve(career, "after")
	career.advance_week()
	FastForward.run(career, 1, "camp")
	_quiet(career)
	_app.lobby_spot = ""
	_app.show_home()
	await _frames(10)
	await _shot("street-8-snow")
	get_tree().quit()


## 화면을 덮는 이야기·미니게임을 미리 넘긴다 (골목만 보려고)
func _quiet(career: CareerState) -> void:
	FastForward._resolve(career, "after" if career.game_done else "start")
	career.pending_minigame = ""


func _frames(n: int) -> void:
	for i in n:
		await get_tree().process_frame


func _wait(sec: float) -> void:
	await get_tree().create_timer(sec).timeout


func _shot(name: String) -> void:
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % name)
	print("찍음 ", name)
