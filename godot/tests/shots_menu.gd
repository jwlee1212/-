extends Node
## 로비·커리어 허브·이야기 확인용 스크린샷 → godot/build/shots/menu-*.png (경기는 자동으로 빠르게 돌린다)
##   ~/Applications/Godot.app/Contents/MacOS/Godot --path godot res://tests/shots_menu.tscn

var _app: Control


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(844, 390)
	await get_tree().process_frame
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	_app = load("res://scenes/batting.tscn").instantiate()
	add_child(_app)
	await _frames(10)
	await _shot("menu-lobby-new")
	_app.start_career("김도윤")
	await _frames(10)
	var view: DialogueView = _app._current.story_view()
	await _wait(1.5)
	await _shot("menu-hub-story")
	view.advance()
	await _wait(0.2)
	await _shot("menu-hub-story-choices")
	view.choose(0)
	await _wait(1.5)
	await _shot("menu-hub-story-result")
	view.finished.emit()
	await _frames(10)
	await _shot("menu-hub")
	_app._current._show_tab("훈련")
	await _frames(10)
	await _shot("menu-hub-training")
	_app._current._train("contact")
	await _frames(10)
	await _shot("menu-hub-trained")
	_app._current._show_tab("선수")
	await _frames(10)
	await _shot("menu-hub-player")
	_app.show_title()
	await _frames(10)
	await _shot("menu-lobby-career")
	_app.show_home()
	await _frames(10)
	check_trained()

	# 몇 주 자동 진행 (이벤트는 첫 선택지) → 소식·라이벌·스카우트가 쌓인 허브
	var career: CareerState = _app.career
	for w in 7:
		_auto_week(career)
	_app.show_home()
	await _frames(10)
	if _app._current.story_view() != null:
		await _shot("menu-hub-story-week")
		_resolve_start(career)
		_app.show_home()
		await _frames(10)
	await _shot("menu-hub-week8")
	career.player.money = 320000
	career.people.meet("manager")
	career.people.by_id["manager"].relation = 62
	for tab in ["인물", "상점", "스카우트"]:
		_app._current._show_tab(tab)
		await _frames(10)
		await _shot("menu-hub-" + {"인물": "people", "상점": "shop", "스카우트": "scout"}[tab])
	# 기록 탭: 작은 탭마다
	_app._current._show_tab("기록")
	await _frames(10)
	var rv: RecordsView = _app._current.find_children("*", "RecordsView", true, false)[0]
	for t: String in RecordsView.TABS:
		rv.show_tab(t)
		await _frames(10)
		await _shot("menu-records-" + {"요약": "summary", "시즌": "seasons", "경기": "games", "대회": "phases", "분할": "splits", "이야기": "story"}[t])
	# 로비 기록실 (분할 기록 탭)
	_app.show_records()
	await _frames(10)
	(_app._current as RecordsScreen).view.show_tab("분할")
	await _frames(10)
	await _shot("menu-lobby-records")
	_app.show_home()
	await _frames(10)
	career.socialize("manager", "hangout")
	_app._current._show_tab("인물")
	await _frames(10)
	await _shot("menu-hub-people-after")
	# 연애 이벤트 대화 (돈 드는 선택지 포함)
	career.people.partner = "manager"
	career.player.money = 20000
	var anniv: Dictionary = {}
	for e: Dictionary in career.cfg.events:
		if e.id == "date_anniversary":
			anniv = e
	var dv := DialogueView.new(career, anniv)
	_app._current.add_child(UiKit.fill_parent(dv))
	await _wait(1.5)
	dv.advance()
	await _wait(0.2)
	await _shot("menu-dialogue-date")
	dv.queue_free()

	# 3학년 시즌 끝: 진로 선택
	career.player.grade = 3
	career.phase_index = career.phases().size()
	_app.show_season()
	await _frames(10)
	await _shot("menu-season-path")
	var sv: Array = _app._current.find_children("*", "DialogueView", true, false)
	if not sv.is_empty():
		(sv[0] as DialogueView).choose(0)
		await _frames(10)
		(sv[0] as DialogueView).finished.emit()
		await _frames(10)
	await _shot("menu-season-graduate")
	get_tree().quit()


func _resolve_start(career: CareerState) -> void:
	for e: Dictionary in career.events("start").duplicate():
		career.resolve_event(e, _ok(career, e))


## 한 주를 화면 없이 진행 (내 타석도 자동)
func _auto_week(career: CareerState) -> void:
	_resolve_start(career)
	career.train("rest" if career.player.condition < 50 else "contact")
	var game := career.new_game()
	var rng := RandomNumberGenerator.new()
	rng.seed = career.week
	while not game.state.over:
		if game.step().type == "my_turn":
			var pa := AutoPa.simulate(career.cfg.auto_pa, career.batting, game.current_batter().skills, game.current_pitcher(), rng)
			# 기록실 분할 기록이 차도록 타구와 카운트도 붙인다
			var c: SwingJudge.Contact = null
			if pa.ball != null:
				c = SwingJudge.Contact.new(SwingJudge.Quality.SOLID, pa.ball.spray_deg, pa.ball.distance_m, pa.ball.batted_ball)
				c.ball = pa.ball
			var res := AtBat.Result.new(pa.outcome, SwingJudge.BattedBall.NONE, pa.pitches, c)
			res.balls = rng.randi_range(0, 3)
			res.strikes = rng.randi_range(0, 2)
			game.apply_my_result(res)
	career.finish_game(game)
	for e: Dictionary in career.events("after").duplicate():
		career.resolve_event(e, _ok(career, e))
	career.advance_week()


## 로비에 갔다 와도 이번 주 훈련은 한 번뿐
func check_trained() -> void:
	var trained: bool = _app._current._trained()
	print("다시 연 허브: 훈련함=%s, 훈련 버튼 %s" % [trained, "없음(홈 탭)" if _app._current._training_buttons.is_empty() else "있음"])


## 고를 수 있는 첫 선택지
func _ok(career: CareerState, e: Dictionary) -> int:
	for i in (e.choices as Array).size():
		if EventBook.choice_block(career, e.choices[i]) == "":
			return i
	return 0


func _wait(sec: float) -> void:
	await get_tree().create_timer(sec).timeout


func _frames(n: int) -> void:
	for i in n:
		await get_tree().process_frame


func _shot(name: String) -> void:
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % name)
