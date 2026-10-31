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
	# 유저 저장을 건드리지 않게 따로 저장하고, 매번 빈 저장에서 시작한다
	_app.save_path = "user://shots.save"
	SaveGame.delete(_app.save_path)
	add_child(_app)
	await _frames(10)
	await _shot("menu-lobby-new")
	_app.start_career("김도윤")
	await _frames(10)
	var view: DialogueView = _app._current.story_view()
	await _wait(1.5)
	await _shot("menu-hub-story")
	view.ask_skip()  # [건너뛰기] → 확인 창
	await _frames(5)
	await _shot("menu-hub-story-skip-ask")
	view._close_confirm()
	view.advance()
	await _wait(0.2)
	await _shot("menu-hub-story-choices")
	view.choose(0)
	await _wait(1.5)
	await _shot("menu-hub-story-result")
	view.skip()
	await _frames(10)
	await _shot("menu-hub")
	# 남은 주 시작 이야기(입학식 날 등)를 첫 선택지로 넘긴다
	var scenes := 0
	while _app._current.story_view() != null:
		var sv: DialogueView = _app._current.story_view()
		sv.ask_skip()
		sv.skip_scene()  # 확인 창에서 [건너뛰기]
		await _wait(0.2)
		if scenes == 0:
			await _shot("menu-hub-story-skipped")
		scenes += 1
		if is_instance_valid(sv) and sv.is_chosen() and sv == _app._current.story_view():
			sv.advance()  # 변화를 보여 주는 중이면 닫는다
		await _wait(0.2)
	print("건너뛴 장면 %d개" % scenes)
	await _frames(10)
	await _shot("menu-hub-room")
	# 스케줄: 칸 고르기 판 → 칸 채우기 → 시작 (도장이 하나씩 찍히는 중) → 다 찍힘
	_app._current._open_picker(0)
	await _frames(10)
	await _shot("menu-hub-picker")
	_app.career.set_plan(0, "contact")
	_app.career.set_plan(1, "power")
	_app.career.set_plan(2, "rest")
	_app._current._close_picker()
	_app._current._rebuild()
	await _frames(10)
	await _shot("menu-hub-plan")
	_app._current._start_schedule()
	await _wait(float(_app.career.cfg.schedule["revealSec"]) * 1.2)
	await _shot("menu-hub-revealing")
	await _wait(float(_app.career.cfg.schedule["revealSec"]) * 3.0)
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
	_app._current._open_popup(_app._current._team_popup())
	await _frames(10)
	await _shot("menu-hub-team")
	_app._current._close_picker()
	career.player.money = 320000
	career.people.meet("manager")
	career.people.by_id["manager"].relation = 62
	for tab in ["상점", "스카우트"]:
		_app._current._show_tab(tab)
		await _frames(10)
		await _shot("menu-hub-" + {"상점": "shop", "스카우트": "scout"}[tab])
	await _phone_shots(career)
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
	# 앱을 다시 켠 것처럼: 메모리의 커리어를 버리고 저장에서 불러와 로비 → 이어하기
	var before := "%d학년 %d주 · 돈 %d" % [career.player.grade, career.week, career.player.money]
	_app.career = null
	_app._load_save()
	career = _app.career
	print("불러오기: %s → %s" % [before, "%d학년 %d주 · 돈 %d" % [career.player.grade, career.week, career.player.money]])
	_app.show_title()
	await _frames(10)
	await _shot("menu-lobby-loaded")
	_app.continue_career()
	await _frames(10)
	career.socialize("manager", "hangout")
	_app._current.open_phone()
	_app._current.phone_view().open_person("manager")
	await _frames(10)
	await _shot("menu-phone-person-after")
	_app._current.phone_view().closed.emit()
	await _frames(5)
	# 연애 이벤트 대화 (돈 드는 선택지 포함)
	career.people.partner = "manager"
	career.player.money = 20000
	var anniv: Dictionary = {}
	for e: Dictionary in career.cfg.events:
		if e.id == "date_anniversary":
			anniv = e
	# (date_anniversary 는 테스트 전용 이벤트로 옮겨져 지금 게임 콘텐츠에는 없다)
	if not anniv.is_empty():
		var dv := DialogueView.new(career, anniv)
		_app._current.add_child(UiKit.fill_parent(dv))
		await _wait(1.5)
		dv.advance()
		await _wait(0.2)
		await _shot("menu-dialogue-date")
		dv.queue_free()

	# 경기를 마친 주에 껐다 켜면: 허브가 남은 경기 뒤 이야기와 [다음 주]를 보여 준다
	_app.show_home()
	await _frames(5)
	_resolve_start(career)
	career.train("rest")
	var g2 := career.new_game()
	var rng2 := RandomNumberGenerator.new()
	rng2.seed = 77
	while not g2.state.over:
		if g2.step().type == "my_turn":
			var pa2 := AutoPa.simulate(career.cfg.auto_pa, career.batting, g2.current_batter().skills, g2.current_pitcher(), rng2)
			g2.apply_my_result(AtBat.Result.new(pa2.outcome, SwingJudge.BattedBall.NONE, pa2.pitches, null))
	career.finish_game(g2)
	_app.save_career()
	_app.career = null
	_app._load_save()
	career = _app.career
	_app.continue_career()
	await _wait(1.5)
	await _shot("menu-hub-after-game")
	if _app._current.story_view() != null:
		for e: Dictionary in career.events("after").duplicate():
			career.resolve_event(e, _ok(career, e))
		_app.show_home()
		await _frames(10)
		await _shot("menu-hub-after-game-next")

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
		(sv[0] as DialogueView).skip()
		await _frames(10)
	await _shot("menu-season-graduate")
	get_tree().quit()


## 폰: 대화방 목록 → 대화방(답장 전·후) → 단체방 → 연락처 → 인물 카드
func _phone_shots(career: CareerState) -> void:
	_app._current._show_tab("홈")
	await _frames(10)
	await _shot("menu-hub-phone-badge")
	# 새 문자 알림 띠 (오른쪽에서 미끄러져 들어온 뒤)
	career.phone.fresh.append(career.phone.status.keys()[0])
	_app._current._notify_messages()
	await _wait(0.8)
	await _shot("menu-hub-phone-toast")
	# 손맛 버튼을 끌어 옮긴 모습
	var gear: Button = _app._panel._gear if _app._panel != null else null
	if gear != null:
		for e: InputEvent in [_mouse(Vector2(5, 5), true), _motion(Vector2(120, -150)), _mouse(Vector2(120, -150), false)]:
			gear.gui_input.emit(e)
		await _frames(5)
		await _shot("menu-hub-gear-moved")
		gear.position = Vector2(4, 390 - gear.size.y - 4)
	_app._current.open_phone()
	await _frames(10)
	await _shot("menu-phone-rooms")
	var pv: PhoneView = _app._current.phone_view()
	# 자동 진행은 답장을 안 해서 다 놓쳤다 — 아직 안 온 문자 하나를 받아 답장 장면을 찍는다
	for m: Dictionary in career.cfg.messages:
		if not career.phone.status.has(m.id) and m.has("replies") and not m.has("room") and career.phone.open_message(m.speaker) == "":
			career.people.meet(m.speaker)
			career.phone._receive(career, m)
			break
	for room: String in career.phone.order:
		if not career.phone.reply_options(career, room).is_empty() and not Phone.is_group(career, room):
			pv.open_room(room)
			await _frames(10)
			await _shot("menu-phone-room")
			pv.reply(0)
			await _wait(2.0)
			await _shot("menu-phone-room-replied")
			break
	if career.phone.rooms.has("team"):
		pv.open_room("team")
		await _frames(10)
		await _shot("menu-phone-team")
	pv.show_tab(PhoneView.TAB_CONTACTS)
	await _frames(10)
	await _shot("menu-phone-contacts")
	pv.open_person("manager")
	await _frames(10)
	await _shot("menu-phone-person")
	pv.closed.emit()
	await _frames(5)


func _resolve_start(career: CareerState) -> void:
	# 주 시작 이야기는 하나가 끝나면 다음 것이 이어질 수 있다 — 남은 게 없을 때까지
	while not career.events("start").is_empty():
		var e: Dictionary = career.events("start")[0]
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
	var trained: bool = _app._current._ran()
	print("다시 연 허브: 스케줄 함=%s, 칸 결과 %d개" % [trained, _app.career.week_schedule.size()])


## 고를 수 있는 첫 선택지
func _ok(career: CareerState, e: Dictionary) -> int:
	for i in (e.choices as Array).size():
		if EventBook.choice_block(career, e.choices[i]) == "":
			return i
	return 0


func _mouse(at: Vector2, pressed: bool) -> InputEventMouseButton:
	var e := InputEventMouseButton.new()
	e.button_index = MOUSE_BUTTON_LEFT
	e.pressed = pressed
	e.position = at
	return e


func _motion(at: Vector2) -> InputEventMouseMotion:
	var e := InputEventMouseMotion.new()
	e.position = at
	return e


func _wait(sec: float) -> void:
	await get_tree().create_timer(sec).timeout


func _frames(n: int) -> void:
	for i in n:
		await get_tree().process_frame


func _shot(name: String) -> void:
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % name)
