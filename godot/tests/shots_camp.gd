extends Node
## 겨울 캠프 확인용 스크린샷 → godot/build/shots/camp-*.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --path godot res://tests/shots_camp.tscn
## 시즌을 건너뛰어 결산 → 캠프 시작 → 집중 과제 이야기 → 훈련 주 → 연습경기 주 → 캠프 결산 → 다음 시즌 허브

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
	_app.start_career("김도윤")
	await _frames(5)
	var career: CareerState = _app.career
	# 주 시작 이야기는 하나가 끝나면 다음 것이 이어질 수 있다 — 남은 게 없을 때까지
	while not career.events("start").is_empty():
		var e: Dictionary = career.events("start")[0]
		career.resolve_event(e, 0)
	career.people.meet("rival")
	# 시즌을 건너뛴다 (기록은 비어 있다)
	career.phase_index = career.phases().size()
	_app.continue_career()
	await _frames(10)
	# 시즌 끝 이야기가 뜨면 넘긴다
	while not _app._current.find_children("*", "DialogueView", true, false).is_empty():
		var dv: DialogueView = _app._current.find_children("*", "DialogueView", true, false)[0]
		dv.choose(1)
		await _frames(5)
		dv.skip()
		await _frames(10)
	await _shot("camp-1-season-end")
	career.start_camp()
	_app.show_home()
	await _wait(1.5)
	await _shot("camp-2-focus-story")
	var view: DialogueView = _app._current.story_view()
	view.advance()
	view.advance()
	await _wait(0.3)
	await _shot("camp-3-focus-choices")
	view.choose(1)
	await _wait(1.5)
	await _shot("camp-4-focus-result")
	view.skip()
	await _frames(10)
	await _shot("camp-5-hub-training-week")
	_app._current._show_tab("훈련")
	await _frames(10)
	await _shot("camp-6-training-bonus")
	_app._current._train("power")
	await _frames(10)
	await _shot("camp-7-trained")
	_app._current._show_tab("홈")
	await _frames(5)
	_app._current._finish_camp_week()
	await _wait(1.2)
	await _shot("camp-8-week-done")
	# 남은 이야기를 첫 선택지로 넘기고 다음 주
	while _app._current.story_view() != null:
		var dv2: DialogueView = _app._current.story_view()
		for i in (dv2._event.choices as Array).size():
			if EventBook.choice_block(career, dv2._event.choices[i]) == "":
				dv2.choose(i)
				break
		await _frames(5)
		dv2.skip()
		await _frames(10)
	_app.next_week()
	await _frames(10)
	while _app._current.story_view() != null:
		var dv3: DialogueView = _app._current.story_view()
		dv3.choose(0)
		await _frames(5)
		dv3.skip()
		await _frames(10)
	await _shot("camp-9-hub-practice-game")
	# 캠프 결산 (남은 주는 건너뛴다)
	career.camp_games.append({"opponent": career.opponent().name, "my": 5, "opp": 3, "result": 1, "line": "4타수 2안타 1타점"})
	career.camp_week = int(career.cfg.camp["weeks"]) + 1
	_app.continue_career()
	await _frames(10)
	await _shot("camp-10-camp-end")
	career.start_next_season()
	_app.show_home()
	await _frames(10)
	if _app._current.story_view() != null:
		_app._current.story_view().choose(0)
		await _frames(5)
		_app._current.story_view().skip()
		await _frames(10)
	await _shot("camp-11-next-season")
	get_tree().quit()


func _wait(sec: float) -> void:
	await get_tree().create_timer(sec).timeout


func _frames(n: int) -> void:
	for i in n:
		await get_tree().process_frame


func _shot(name: String) -> void:
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % name)
	print("shot ", name)
