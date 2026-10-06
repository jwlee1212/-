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
	add_child(_app)
	await _until(func() -> bool: return _app._current != null)
	await _wait_ms(200)
	await _shot("1-title")

	_app.show_create()
	await _wait_ms(200)
	await _shot("2-create")

	_app.start_career("김도윤")
	await _wait_ms(200)
	await _shot("3-home")
	_app._current._train("contact")
	await _wait_ms(200)
	await _shot("4-home-trained")

	_app.show_game()
	var game_screen: Control = _app._current
	await _wait_ms(100)
	game_screen._fast = true
	await _wait_ms(1500)
	await _shot("5-game")

	var turn := 0
	var started := Time.get_ticks_msec()
	var last_print := 0
	while not game_screen._game.state.over and Time.get_ticks_msec() - started < 120000:
		if Time.get_ticks_msec() - last_print > 3000:
			last_print = Time.get_ticks_msec()
			var gs = game_screen._game.state
			print("진행: %s 아웃 %d, 점수 %s, 내 타석 %d, 세션 %s, 대기 %s, 로그 %d" % [gs.half_text(), gs.outs, gs.score, turn,
				game_screen._session != null, game_screen._waiting_for_turn, game_screen._game.log.size()])
		if game_screen._session != null:
			turn += 1
			var s: BattingSession = game_screen._session
			await _until(func() -> bool: return s.phase == BattingSession.Phase.WINDUP)
			if turn == 1:
				await _wait_ms(s.presentation.windup_ms * 0.6)
				await _shot("6-my-turn")
			# 볼은 거르고 스트라이크는 공이 도착하는 순간에 친다
			var t0 := Time.get_ticks_msec()
			while game_screen._session == s and s.phase != BattingSession.Phase.OVER and Time.get_ticks_msec() - t0 < 30000:
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
	# 이벤트가 있으면 첫 선택지
	var result_screen: Control = _app._current
	for c in result_screen.find_children("*", "Button", true, false):
		if (c as Button).text != "다음 주 ▶":
			(c as Button).pressed.emit()
			break
	await _wait_ms(300)
	await _shot("11-after-event")

	_app.career.week = _app.career.cfg.season_weeks + 1
	_app.show_season()
	await _wait_ms(300)
	await _shot("12-season")

	_app.show_practice()
	await _wait_ms(1800)
	await _shot("13-practice")
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
