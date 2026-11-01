class_name DrillView
extends Control
## 이야기 속 연습 (이벤트 효과 minigame "drill:<id>", 정의는 CareerState.pending_drill):
##   kind field — 유격수 수비 화면(FieldView)을 balls 번 되풀이. 아웃(레이저·보통 송구)이 성공
##   kind bat   — 타석 화면(BattingSession)을 공 balls 개를 던질 때까지 되풀이. 정타(SOLID)가 성공
## 위에 제목과 "3 / 10 · 성공 2" 를 띄우고, 끝나면 결과 카드 → [확인] → finished(성공 수).
## 판정 폭 보정(drill_boost — 체력 테스트 등급·폼 적응도)은 이 연습에만 붙는다. 로직은 FieldPlay·AtBat 그대로 (원칙 1).

signal finished(successes: int)

var _app
var _drill: Dictionary
var _boost: Dictionary
var _balls := 0
var _done := 0
var _successes := 0
var _round := 0
var _host: Control
var _counter: Label
## 지금 떠 있는 화면 (수비 화면 또는 타석 묶음)
var _current: Control
var _session: BattingSession


func _init(app, drill: Dictionary) -> void:
	_app = app
	_drill = drill
	_balls = int(drill["balls"])
	_boost = app.career.drill_boost(drill)


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_STOP
	var bg := ColorRect.new()
	bg.color = Tokens.STAGE_FRAME
	add_child(UiKit.fill_parent(bg))
	_host = UiKit.fill_parent(Control.new())
	add_child(_host)
	_counter = UiKit.title("", Tokens.FONT_TITLE, Tokens.HUD_TEXT)
	_counter.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_counter)
	_counter.set_anchors_and_offsets_preset(Control.PRESET_CENTER_TOP, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_SM)
	_counter.grow_horizontal = Control.GROW_DIRECTION_BOTH
	_next()


func _process(_delta: float) -> void:
	if _session != null:
		_session.tick()


## 성공 수 (스크린샷·테스트용)
func successes() -> int:
	return _successes


func _refresh_counter() -> void:
	_counter.text = "%s  %d / %d · 성공 %d" % [_drill.get("title", ""), _done, _balls, _successes]


func _next() -> void:
	_refresh_counter()
	if _done >= _balls:
		_open_result()
		return
	if _drill["kind"] == "field":
		_open_field()
	else:
		_open_bat()


func _stat(id: String) -> int:
	return int(_boost.get(id, 0))


func _open_field() -> void:
	var cfg: CareerConfig = _app.career_config
	var fielding := clampi(_app.career.player.fielding_for_game(cfg) + _stat("fielding"), 1, 99)
	var seed_value: int = _app.career.rng_for("drill:%s:%d" % [_drill["id"], _round]).randi()
	_round += 1
	var view := FieldView.new(cfg.fielding, _app.batting_config.ball_physics, seed_value, _drill.get("batter", "펑고"), 50, fielding, _app.batting_config.input_latency_ms)
	view.sound_requested.connect(func(sfx: SoundSynth.Sfx, volume: float, pitch_scale: float) -> void:
		_app.sound.play(sfx, volume * _app.presentation.sfx_volume, pitch_scale))
	_current = UiKit.fill_parent(view)
	_host.add_child(_current)
	view.finished.connect(func(kind: FieldPlay.Kind) -> void:
		_done += 1
		if kind == FieldPlay.Kind.BONUS or kind == FieldPlay.Kind.NORMAL:
			_successes += 1
		_current.queue_free()
		_current = null
		_next())


func _open_bat() -> void:
	var cfg: CareerConfig = _app.career_config
	var base: BatterSkills = _app.career.player.skills_for_game(cfg)
	var skills := BatterSkills.new(clampi(base.contact + _stat("contact"), 1, 99), clampi(base.power + _stat("power"), 1, 99),
		clampi(base.eye + _stat("eye"), 1, 99), clampi(base.speed + _stat("speed"), 1, 99))
	var pitchers: Dictionary = _app.batting_config.pitchers
	var pitcher: BattingConfig.PitcherProfile = pitchers.get(_drill.get("pitcher", "balanced"), pitchers.values()[0])
	_session = _app.new_batting_session()
	_session.start_single(skills, pitcher, _app.career.rng_for("drill:%s:%d" % [_drill["id"], _round]).randi())
	_round += 1
	var box := UiKit.fill_parent(Control.new())
	var view := BattingView.new()
	view.session = _session
	view.tapped.connect(_session.tap)
	box.add_child(UiKit.fill_parent(view))
	var hud := BattingHud.new(_session)
	hud.show_menu = false
	hud.info_text = func() -> String: return "%s · %d / %d구" % [_drill.get("title", ""), _done, _balls]
	box.add_child(hud)
	_current = box
	_host.add_child(box)
	_session.at_bat_finished.connect(func(result: AtBat.Result) -> void:
		_done = mini(_balls, _done + maxi(1, result.pitches))
		if result.contact != null and result.contact.quality == SwingJudge.Quality.SOLID:
			_successes += 1
		_session = null
		_current.queue_free()
		_current = null
		_next())


func _open_result() -> void:
	var parts: Array[String] = _app.career.finish_drill(_successes)
	_app.save_career()
	var card := UiKit.card()
	var col := UiKit.vbox(Tokens.SPACE_SM)
	card.add_child(col)
	col.add_child(UiKit.kicker(_drill.get("title", "")))
	col.add_child(UiKit.title("성공 %d / %d" % [_successes, _balls], Tokens.FONT_TITLE))
	col.add_child(UiKit.label(", ".join(parts) if not parts.is_empty() else "변화 없음", Tokens.FONT_BODY))
	col.add_child(UiKit.button("확인", func() -> void: finished.emit(_successes), Tokens.PRIMARY))
	add_child(card)
	card.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
	card.grow_horizontal = Control.GROW_DIRECTION_BOTH
	card.grow_vertical = Control.GROW_DIRECTION_BOTH
