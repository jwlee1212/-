extends Node
## 확인용 스크린샷: 게임 흐름을 처음부터 따라가며 화면마다 찍어 godot/build/shots/ 에 남긴다.
##   ~/Applications/Godot.app/Contents/MacOS/Godot --path godot res://tests/shots.tscn
## 타이틀 → 선수 만들기 → 홈(훈련) → 경기(빠르게) → 내 타석(스윙) → 경기 끝 → 결과·이벤트 → 시즌 결산 → 타격 연습

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
	await _until(func() -> bool: return _app._current != null)
	await _wait_ms(200)
	await _shot("1-title")

	_app.show_create()
	await _wait_ms(200)
	await _shot("2-create")

	_app.start_career("김도윤")
	await _wait_ms(200)
	await _shot("3a-home-story")
	# 주 시작 이야기(입부)를 고르고 넘긴다
	while _app._current.story_view() != null:
		var view: DialogueView = _app._current.story_view()
		view.choose(0)
		await _wait_ms(200)
		view.skip()
		await _wait_ms(200)
	await _shot("3-home")
	_app._current._train("contact")
	await _wait_ms(200)
	await _shot("4-home-trained")

	# 1학년 1주차의 나는 벤치라 경기 화면 없이 결과로 넘어간다. 경기 화면을 찍으려고 여기서만 선발 5번에 넣는다
	if _app.career.next_role() == "bench":
		_app.career.player.lineup_slot = 5
	_app.show_game()
	if not _app._current is GameScreen:
		push_error("경기 화면이 아니다: %s" % _app._current)
		get_tree().quit(1)
		return
	var game_screen: GameScreen = _app._current
	await _wait_ms(100)
	await _wait_ms(1500)
	await _shot("5-game")
	# 보통 속도로 두고 하이라이트가 나오면 찍는다 (최대 40초), 그다음 빠르게
	await _until(func() -> bool:
		_skip_dialogue(game_screen)
		if game_screen._session != null and game_screen._session.phase == BattingSession.Phase.OVER:
			game_screen._session.tap()
		elif game_screen._session != null and game_screen._session.phase == BattingSession.Phase.WAITING:
			game_screen._session.ready_now()
		elif game_screen._session != null and game_screen._session.phase == BattingSession.Phase.FLIGHT and not game_screen._session.swung:
			game_screen._session.tap()
		return game_screen._highlight != null or game_screen._game.state.over, 40000)
	if game_screen._highlight != null:
		await _wait_ms(1500)
		await _shot("5b-highlight")
		if game_screen._highlight != null:
			game_screen._highlight.finished.emit()  # 화면을 탭한 것과 같다
	game_screen._fast = true

	var turn := 0
	var started := Time.get_ticks_msec()
	var last_print := 0
	while not game_screen._game.state.over and Time.get_ticks_msec() - started < 120000:
		_skip_dialogue(game_screen)
		if Time.get_ticks_msec() - last_print > 3000:
			last_print = Time.get_ticks_msec()
			var gs = game_screen._game.state
			print("진행: %s 아웃 %d, 점수 %s, 내 타석 %d, 세션 %s, 대기 %s, 로그 %d" % [gs.half_text(), gs.outs, gs.score, turn,
				game_screen._session != null, game_screen._waiting_for_turn, game_screen._game.log.size()])
		if game_screen._session != null:
			turn += 1
			var s: BattingSession = game_screen._session
			await _until(func() -> bool:
				s.ready_now()  # 공 사이 대기(스윙 고르는 시간)를 건너뛴다
				return s.phase == BattingSession.Phase.WINDUP)
			if turn == 1:
				await _wait_ms(s.presentation.windup_ms * 0.6)
				await _shot("6-my-turn")
			# 볼은 거르고 스트라이크는 공이 도착하는 순간에 친다
			var t0 := Time.get_ticks_msec()
			while game_screen._session == s and s.phase != BattingSession.Phase.OVER and Time.get_ticks_msec() - t0 < 30000:
				_skip_dialogue(game_screen)
				if turn > 1:
					s.ready_now()
				if s.phase == BattingSession.Phase.FLIGHT and not s.swung and s.pitch.is_strike() \
						and s.since(s.phase_since_ms) >= s.pitch.flight_ms - 4.0:
					s.tap()
					if turn == 1:
						await _wait_ms(40)
						await _shot("7-my-swing")
				await get_tree().process_frame
			if turn == 1:
				await _wait_ms(400)
				await _shot("8-my-result")
			s.tap()
		await get_tree().process_frame
	await _wait_ms(300)
	await _shot("9-game-over")

	_app.show_result(game_screen._game)
	await _wait_ms(300)
	await _shot("10-result")
	_app._current._show_box()
	await _wait_ms(300)
	await _shot("10b-box")
	# 박스스코어 창(맨 뒤에 붙은 어두운 막)을 닫는다
	var screen: Control = _app._current
	screen.get_child(screen.get_child_count() - 1).queue_free()
	await _wait_ms(200)
	# 이벤트가 있으면 첫 선택지
	var result_screen: Control = _app._current
	if result_screen._story_button != null:
		result_screen._show_event()
		await _wait_ms(1500)
		await _shot("11-dialogue")
		var dv: DialogueView = result_screen.story_view()
		for i in (dv._event.choices as Array).size():
			if EventBook.choice_block(_app.career, dv._event.choices[i]) == "":
				dv.choose(i)
				break
		await _wait_ms(1500)
	await _shot("11-after-event")

	# 시즌 끝 화면: 남은 단계를 기록만 채워 넘긴다
	var career: CareerState = _app.career
	career.phase_log.append({"name": "새봄기 전국고교야구대회", "text": "8강 탈락"})
	career.phase_index = career.phases().size()
	_app.show_season()
	await _wait_ms(300)
	await _shot("12-season")

	_app.show_practice()
	await _wait_ms(1800)
	await _shot("13-practice")
	get_tree().quit()


## 경기 중 이야기(대타 호출 등)가 뜨면 첫 선택지로 넘긴다 — 안 넘기면 경기가 멈춘다
func _skip_dialogue(game_screen: GameScreen) -> void:
	var view := game_screen.story_view()
	if view != null:
		view.choose(0)
		view.skip()


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
