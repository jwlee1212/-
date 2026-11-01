extends Node
## 입부 체력 테스트 확인용 스크린샷 → godot/build/shots/fitness-*.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --always-on-top --path godot res://tests/shots_fitness.tscn
## 새 커리어 → 입부 대화까지 넘기고 홈에 뜬 체력 테스트를 종목마다 가짜 입력으로 진행한다 (진짜 시계로 돈다)

var _app: Control
var _view: FitnessView


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(844, 390)
	await get_tree().process_frame
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	_app = load("res://scenes/batting.tscn").instantiate()
	_app.save_path = "user://shots.save"
	SaveGame.delete(_app.save_path)
	add_child(_app)
	await _frames(10)
	_app.start_career("김도윤")
	await _frames(5)
	var career: CareerState = _app.career
	# 입부 대화까지 화면 없이 넘긴다 (미니게임은 대기로 남긴다)
	while career.pending_minigame == "" and not career.events("start").is_empty():
		var run := DialogueRun.new(career, career.events("start")[0])
		run.auto(0)
		run.commit()
	_app.show_home()
	await _frames(10)
	_view = (_app._current as HomeScreen).minigame_view()
	assert(_view != null, "홈에 체력 테스트가 떠야 한다")
	await _shot("fitness-1-intro-run")
	# ① 50m: 땅! 까지 기다린 뒤 1초에 10번 번갈아
	_press_card()
	await _frames(3)
	await _shot("fitness-2-run-ready")
	while _view._run.phase == RunTest.Phase.READY:
		await _frames(1)
	var foot := 0
	var shot_mid := false
	while _view._run.phase != RunTest.Phase.DONE:
		_view._run_tap(foot, Time.get_ticks_usec())
		foot = 1 - foot
		await _wait(0.1)
		if not shot_mid and _view._run.x > 22.0:
			shot_mid = true
			await _shot("fitness-3-run-mid")
	await _wait(0.9)
	await _shot("fitness-4-run-finish")
	await _until_card()
	await _shot("fitness-5-run-result")
	_press_card()
	await _frames(5)
	await _shot("fitness-6-intro-lift")
	# ② 스쿼트: 가운데서 떼기 두 번, 세 번째는 끝까지 참는 모습을 찍고 떼기
	_press_card()
	await _wait(0.8)
	for rep in 3:
		var now := Time.get_ticks_usec()
		_view._lift_press(now)
		var target := float(_view._fc["lift"]["zoneCenter"]) * _view._lift.fill_sec()
		await _wait(target * (0.55 if rep == 0 else 1.0))
		if rep == 0:
			await _shot("fitness-7-lift-hold")
			await _wait(target * 0.45)
		if rep == 2:
			await _wait(_view._lift.fill_sec() * 0.12)
			await _shot("fitness-8-lift-struggle")
		_view._lift_release(Time.get_ticks_usec())
		await _wait(0.12)
		if rep == 0:
			await _shot("fitness-9-lift-success")
		while _view._next_what != "":
			await _frames(1)
	# 나머지는 일찍 떼서 끝낸다
	while _view._lift.phase != LiftTest.Phase.DONE:
		_view._lift_press(Time.get_ticks_usec())
		await _wait(0.1)
		_view._lift_release(Time.get_ticks_usec())
		await _wait(0.05)
		while _view._next_what == "next_rep":
			await _frames(1)
	await _until_card()
	await _shot("fitness-10-lift-result")
	_press_card()
	await _frames(5)
	# ③ 반응: 초록은 0.25초 뒤 탭, 빨강은 참기
	_press_card()
	var shot_green := false
	var shot_red := false
	while _view._react.phase != ReactTest.Phase.DONE:
		if _view._react.phase == ReactTest.Phase.LIT:
			if _view._react.is_red():
				if not shot_red:
					shot_red = true
					await _frames(2)
					await _shot("fitness-12-react-red")
			else:
				if not shot_green:
					shot_green = true
					await _frames(2)
					await _shot("fitness-11-react-green")
				await _wait(0.2)
				_view._react_tap(Time.get_ticks_usec())
				await _wait(0.03)
				if shot_green and not has_meta("hit"):
					set_meta("hit", true)
					await _shot("fitness-13-react-hit")
		await _frames(1)
	await _until_card()
	await _shot("fitness-14-react-result")
	_press_card()
	await _frames(10)
	await _shot("fitness-15-summary")
	_press_card()
	await _frames(10)
	await _shot("fitness-16-after")
	SaveGame.delete(_app.save_path)
	get_tree().quit()


func _press_card() -> void:
	var b := _view.card_button()
	if b != null:
		b.pressed.emit()


func _until_card() -> void:
	while _view.card_button() == null:
		await _frames(1)
	await _wait(0.25)


func _wait(sec: float) -> void:
	var until := Time.get_ticks_usec() + int(sec * 1e6)
	while Time.get_ticks_usec() < until:
		await get_tree().process_frame


func _frames(n: int) -> void:
	for i in n:
		await get_tree().process_frame


func _shot(name: String) -> void:
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % name)
	print("shot ", name)
