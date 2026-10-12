extends Node
## 대화가 이어지는 스토리 확인용 스크린샷 → godot/build/shots/story-*.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --path godot res://tests/shots_story.tscn
## 입부 이야기를 탭·선택으로 끝까지 따라가며 줄마다 찍는다 (말하는 사람이 바뀌는지, 선택 뒤 대사가 이어지는지), 그리고 폰 연락처

var _app: Control
var _n := 0


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
	await _frames(10)
	var guard := 0
	while _app._current.story_view() != null and guard < 120:
		guard += 1
		var view: DialogueView = _app._current.story_view()
		if view._text.visible_ratio < 1.0:
			view.advance()  # 글자를 다 보여 준다
		await _frames(3)
		_n += 1
		await _shot("story-%02d" % _n)
		if not is_instance_valid(view) or view.is_queued_for_deletion():
			continue
		if view._stage == DialogueView.Stage.CHOICE:
			view.choose(0)
		else:
			view.advance()
		await _frames(3)
	# 일러스트 확인: 동기 · 매니저가 말하는 장면
	for id: String in ["s1_buddy", "love_manager_meet", "d1_dawn", "y1_inj_morning", "s1_rival_meet", "y5_home_6"]:
		for e: Dictionary in _app.career.cfg.events:
			if e.id == id:
				var dv := DialogueView.new(_app.career, e)
				_app._current.add_child(UiKit.fill_parent(dv))
				await _frames(5)
				for k in 2:
					dv.advance()
					dv.advance()
				await _frames(3)
				await _shot("story-portrait-" + id)
				dv.queue_free()
				await _frames(3)
	await _frames(10)
	_app._current.open_phone()
	_app._current.phone_view().show_tab(PhoneView.TAB_CONTACTS)
	await _frames(10)
	await _shot("story-people")
	_app._current.phone_view().closed.emit()
	await _frames(5)
	# 경기 중 대화: 첫 경기 첫 타석 직전에 데뷔 이야기가 뜬다
	_app._current._train("contact")
	await _frames(5)
	_app.show_game()
	var waited := 0
	while _app._current.story_view() == null and waited < 600:
		waited += 1
		await get_tree().create_timer(0.1).timeout
	var gv: DialogueView = _app._current.story_view()
	if gv != null:
		for k in 3:
			if gv._text.visible_ratio < 1.0:
				gv.advance()
			await _frames(3)
			await _shot("story-game-%d" % (k + 1))
			if gv._stage == DialogueView.Stage.CHOICE:
				gv.choose(0)
			else:
				gv.advance()
			await _frames(3)
		gv.skip()
		await get_tree().create_timer(2.5).timeout
		await _shot("story-game-batting")
	print("game event log: ", _app.career.book.log[-1].title, " boost ", _app.career.player.game_boost)
	print("log: ", _app.career.book.log)
	SaveGame.delete("user://shots.save")
	get_tree().quit()


func _frames(n: int) -> void:
	for i in n:
		await get_tree().process_frame


func _shot(name: String) -> void:
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % name)
	print("shot ", name)
