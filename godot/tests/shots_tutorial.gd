extends Node
## 튜토리얼 팁 카드 확인용 스크린샷 → godot/build/shots/tutorial-*.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --always-on-top --path godot res://tests/shots_tutorial.tscn
## 새 커리어 → 이야기 속 펑고 연습 직전의 수비 팁 → 입부 이야기를 넘기면 홈 팁 → 2학년으로 빨리 감아 경기 속 팁(타석·도루·주루)
## → 설정의 [다시 보기]
## 설정은 따로 파일을 써서 유저가 본 팁을 건드리지 않는다

var _app: Control


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(844, 390)
	await get_tree().process_frame
	# 어디서 막혀도 창이 남지 않게
	get_tree().create_timer(150.0).timeout.connect(func() -> void: get_tree().quit(1))
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	_app = load("res://scenes/batting.tscn").instantiate()
	_app.save_path = "user://shots_tutorial.save"
	SaveGame.delete(_app.save_path)
	add_child(_app)
	await _until(func() -> bool: return _app._current is TitleScreen)
	_app.settings = Settings.new()
	_app.settings.path = "user://shots_tutorial_settings.json"

	_app.start_career("김도윤")
	await _wait_ms(200)
	# 입부 이야기는 고르고 넘기고, 체력 테스트는 화면 없이 끝낸다 (홈을 다시 열면 남은 이야기 → 홈 팁)
	for i in 3000:
		var home: HomeScreen = _app._current
		if home.tip_card() != null:
			break
		if home.drill_view() != null:
			# 연습 첫 공 전 수비 팁을 찍고, 연습은 화면 없이 끝낸다
			var tip := _find_tip(home.drill_view())
			if tip != null and not _app.settings.has_seen(Tutorial.FIELDING):
				await _wait_ms(300)
				await _shot("tutorial-0-fielding-drill")
				tip.confirm()
				await _wait_ms(100)
			_app.career.finish_drill(0)
			home.drill_view().finished.emit(0)
		elif home.minigame_view() != null:
			_app.career.auto_minigame()
			_app.show_home()
		elif home.story_view() != null:
			home.story_view().skip()
		await get_tree().process_frame
	await _wait_ms(300)
	await _shot("tutorial-1-home")
	_app._current.tip_card().confirm()
	await _wait_ms(200)

	# 경기 속 팁: 2학년 시즌 초로 빨리 감고(주전), 경기를 빠르게 돌리며 팁 카드(타석·도루·주루·수비)가 뜰 때마다 찍는다
	FastForward.run(_app.career, 2, "season_start")
	var started := Time.get_ticks_msec()
	while Time.get_ticks_msec() - started < 90000:
		var career: CareerState = _app.career
		if career.is_season_over() or Tutorial.IDS.all(func(t: String) -> bool: return _app.settings.has_seen(t)):
			break
		FastForward._resolve(career, "start")
		FastForward._train(career)
		if career.next_role() == "bench":
			FastForward._play(career)
			FastForward._resolve(career, "after")
			career.advance_week()
			continue
		_app.show_game()
		var screen: GameScreen = _app._current
		screen._fast = true
		while not screen._game.state.over and Time.get_ticks_msec() - started < 90000:
			if screen._dialogue != null:
				screen._dialogue.skip()
			var card := screen.tip_card()
			if card != null:
				await _wait_ms(300)
				await _shot("tutorial-game-" + card.id)
				card.confirm()
			# 타석·도루·주루·수비는 아무것도 안 누르고 흘려보낸다 (타석은 공을 다 보낸다)
			if screen._session != null:
				screen._session.ready_now()
				if screen._session.phase == BattingSession.Phase.OVER:
					screen._session.tap()
			await get_tree().process_frame
		if not screen._game.state.over:
			break
		career.finish_game(screen._game)
		FastForward._resolve(career, "after")
		career.advance_week()

	# 경기에서 못 만난 팁은 카드 내용만 본다
	for t: String in Tutorial.IDS:
		if not _app.settings.has_seen(t):
			var c: TipCard = _app.show_tip(_app._current, t)
			await _wait_ms(300)
			await _shot("tutorial-card-" + t)
			c.confirm()
			await _wait_ms(100)
	_app.show_settings()
	await _wait_ms(300)
	await _shot("tutorial-6-settings")
	DirAccess.remove_absolute(_app.settings.path)
	SaveGame.delete(_app.save_path)
	get_tree().quit()


func _find_tip(host: Node) -> TipCard:
	for c in host.get_children():
		if c is TipCard:
			return c
	return null


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
