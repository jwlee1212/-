extends Node
## 내 주루를 실제 경기 화면에서 확인: 2학년 시즌을 몇 경기 돌리며 도루 화면·루상 주루 장면·내 타석의 주루 버튼이
## 경기 화면에 제대로 뜨고 경기가 끝까지 가는지 본다 → godot/build/shots/game-steal.png, game-run.png, game-atbat-run.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --path godot res://tests/shots_run_game.tscn

const MAX_GAMES := 8
const MAX_MS := 280000

var _app: Control
var _shots := {}


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(844, 390)
	await get_tree().process_frame
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	_app = load("res://scenes/batting.tscn").instantiate()
	_app.save_path = "user://shots.save"
	SaveGame.delete(_app.save_path)
	add_child(_app)
	await _wait_ms(300)
	_app.start_career("김도윤")
	_app.settings.running = RunControl.ASSIST  # 저장하지 않는다 (유저 설정을 건드리지 않게)
	FastForward.run(_app.career, 2, "season_start")
	var started := Time.get_ticks_msec()
	var steals := 0
	var runs := 0
	for g in MAX_GAMES:
		if _shots.size() >= 3 or Time.get_ticks_msec() - started > MAX_MS:
			break
		var career: CareerState = _app.career
		if career.is_season_over():
			break
		# 한 주를 FastForward.step 과 같은 순서로: 주 시작 이야기 → 훈련 → (경기는 화면에서 직접) → 경기 뒤 이야기 → 다음 주
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
		while not screen._game.state.over and Time.get_ticks_msec() - started < MAX_MS:
			if screen._dialogue != null:
				screen._dialogue.choose(0)
				screen._dialogue.skip()
			var sv := screen.steal_view()
			if sv != null and sv._result == null and sv.elapsed_ms() >= sv._plan.go_ms + 200.0:
				await _shot_once("game-steal")
				sv._on_go()
				steals += 1
			var rv := screen.run_view()
			if rv != null and not rv.control.pending().is_empty() and rv.control.target < 0:
				await _wait_ms(250)
				await _shot_once("game-run")
				rv.control.set_target(4)
				runs += 1
			var s: BattingSession = screen._session
			if s != null:
				match s.phase:
					BattingSession.Phase.WAITING:
						s.ready_now()
					BattingSession.Phase.FLIGHT:
						if not s.swung and s.pitch.is_strike() and s.since(s.phase_since_ms) >= s.pitch.flight_ms - 4.0:
							s.tap()
					BattingSession.Phase.HIT:
						if s.run_control != null and not s.run_control.pending().is_empty() and s.run_control.target < 0 \
								and s.broadcast_time() > 0.5:
							await _wait_ms(250)
							await _shot_once("game-atbat-run")
							s.run_control.set_target(2)
					BattingSession.Phase.OVER:
						s.tap()
			await get_tree().process_frame
		var game: GameRunner = screen._game
		print("경기 %d: %s, 내 기록 %s (도루 %d, 실패 %d)" % [g + 1, "끝" if game.state.over else "시간 초과", game.my_line.line_text(),
			game.my_line.steals, game.my_line.caught_stealing])
		for line: String in game.log:
			if line.contains("도루") or line.contains("견제사"):
				print("  ", line)
		if not game.state.over:
			break
		career.finish_game(game)
		FastForward._resolve(career, "after")
		career.advance_week()
	print("도루 시도 %d번, 루상 주루 판단 %d번, 찍은 화면 %s" % [steals, runs, _shots.keys()])
	SaveGame.delete(_app.save_path)
	get_tree().quit()


func _wait_ms(ms: float) -> void:
	var start := Time.get_ticks_usec()
	while (Time.get_ticks_usec() - start) / 1000.0 < ms:
		await get_tree().process_frame


func _shot_once(name: String) -> void:
	if _shots.has(name):
		return
	_shots[name] = true
	await get_tree().process_frame
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % name)
