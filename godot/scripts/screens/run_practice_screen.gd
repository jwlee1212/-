class_name RunPracticeScreen
extends Control
## 주루 연습 (손맛 튜닝용): 경기를 돌리지 않고 도루·한 베이스 더·태그업 상황만 되풀이한다.
## 경기와 같은 로직(Steal, PlaySimulator + RunControl)과 같은 화면(StealView, 주루 장면)을 쓴다.
## 상황은 번호로 정해지는 시드 난수로 만든다 (원칙 2) — 능력치·기록에는 아무 영향이 없다.

## 연습 종류 [id, 이름]
const MODES := [["steal2", "2루 도루"], ["steal3", "3루 도루"], ["extra", "한 베이스 더"], ["tag", "태그업"]]
## 가상의 내 타순 번호와 타자 타순 번호
const ME := 4
const BATTER := 5
## 결과를 띄워 두는 시간 (ms), 상황을 찾는 최대 횟수
const RESULT_HOLD_MS := 1100.0
const MAX_TRIES := 200
## 위쪽 메뉴 줄의 높이 (px) — 연습 화면은 그 아래에 놓는다
const BAR_HEIGHT := 44
## 타구 범위: 타구 속도(km/h)·발사각(도) — 한 베이스 더는 낮은 타구, 태그업은 뜬공
const EV_RANGE := Vector2(118, 162)
const EXTRA_LAUNCH := Vector2(4, 20)
const TAG_LAUNCH := Vector2(24, 40)

var _app
var _mode := "steal2"
var _speed := 50
## 수동으로 연습하나 (아니면 보조). 설정을 따라 시작하고, 메뉴 줄에서 바꾼다 — 설정이 자동이어도 연습은 보조로 한다
var _manual := false
var _manual_button: Button
## 몇 번째 상황인가 (시드)
var _round := 0
var _good := 0
var _bad := 0
var _stage: Control
var _view: Control
var _control: RunControl
var _tally: Label
var _result: Label
var _result_chip: PanelContainer
var _speed_label: Label
var _mode_buttons := {}
var _next_at := INF


func _init(app) -> void:
	_app = app


func _ready() -> void:
	# 위: 메뉴 줄 (연습 종류 · 주력 · 기록 · 나가기), 그 아래 전체가 연습 화면 — 화면의 버튼·안내와 겹치지 않는다
	var back := ColorRect.new()
	back.color = Tokens.SCOREBOARD
	add_child(UiKit.fill_parent(back))
	_stage = Control.new()
	add_child(UiKit.fill_parent(_stage))
	_stage.offset_top = BAR_HEIGHT
	var bar := UiKit.glass_panel()
	bar.mouse_filter = Control.MOUSE_FILTER_STOP
	add_child(bar)
	var row := UiKit.hbox(2)
	bar.add_child(row)
	for m: Array in MODES:
		var id: String = m[0]
		var b := UiKit.button(m[1], func() -> void: _set_mode(id), Tokens.INK_SOFT, Tokens.FONT_CAPTION)
		row.add_child(b)
		_mode_buttons[id] = b
	_speed_label = UiKit.label("", Tokens.FONT_CAPTION, Tokens.HUD_TEXT, Tokens.FONT_BOLD)
	row.add_child(UiKit.button("−", func() -> void: _set_speed(_speed - 10), Tokens.INK_SOFT, Tokens.FONT_CAPTION))
	row.add_child(_speed_label)
	row.add_child(UiKit.button("+", func() -> void: _set_speed(_speed + 10), Tokens.INK_SOFT, Tokens.FONT_CAPTION))
	_manual = _app.settings.running == RunControl.MANUAL
	_manual_button = UiKit.button("", func() -> void:
		_manual = not _manual
		_set_mode(_mode), Tokens.INK_SOFT, Tokens.FONT_CAPTION)
	row.add_child(_manual_button)
	_tally = UiKit.label("", Tokens.FONT_CAPTION, Tokens.HUD_TEXT_SOFT)
	row.add_child(_tally)
	row.add_child(UiKit.button("나가기", _app.show_title, Tokens.BAD, Tokens.FONT_CAPTION))
	bar.set_anchors_and_offsets_preset(Control.PRESET_CENTER_TOP, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_XS)
	bar.grow_horizontal = Control.GROW_DIRECTION_BOTH
	# 가운데: 결과
	_result_chip = UiKit.glass_panel()
	add_child(_result_chip)
	_result = UiKit.label("", Tokens.FONT_TITLE, Tokens.HUD_TEXT, Tokens.FONT_BOLD)
	_result_chip.add_child(_result)
	_result_chip.set_anchors_and_offsets_preset(Control.PRESET_CENTER, Control.PRESET_MODE_MINSIZE)
	_result_chip.grow_horizontal = Control.GROW_DIRECTION_BOTH
	_result_chip.visible = false
	_set_speed(_speed)
	_set_mode(_mode)


func _process(_delta: float) -> void:
	if _now() >= _next_at:
		_next_at = INF
		_start()


func _now() -> float:
	return Time.get_ticks_usec() / 1000.0


func _set_mode(id: String) -> void:
	_mode = id
	_manual_button.text = RunControl.MODE_LABELS[RunControl.MANUAL if _manual else RunControl.ASSIST]
	_good = 0
	_bad = 0
	for k: String in _mode_buttons:
		(_mode_buttons[k] as Button).modulate.a = 1.0 if k == id else 0.55
	_start()


func _set_speed(v: int) -> void:
	_speed = clampi(v, 0, 100)
	_speed_label.text = "주력 %d" % _speed


## 다음 상황을 띄운다
func _start() -> void:
	if _view != null:
		_view.queue_free()
		_view = null
	_control = null
	_next_at = INF
	_result_chip.visible = false
	_round += 1
	_refresh_tally()
	if _mode.begins_with("steal"):
		_start_steal(1 if _mode == "steal2" else 2)
	else:
		_start_play(_mode == "tag")


func _start_steal(from: int) -> void:
	var bases := [-1, -1, -1]
	bases[from - 1] = ME
	var v := StealView.new(_app.batting_config.ball_physics, _app.career_config.steal, bases, ME, from, _speed,
		hash(["run-practice", _mode, _round]), _app.batting_config.input_latency_ms)
	v.sound_requested.connect(func(sfx: SoundSynth.Sfx, volume: float, pitch_scale: float) -> void:
		_app.sound.play(sfx, volume * _app.presentation.sfx_volume, pitch_scale))
	v.finished.connect(func(r: Steal.Result) -> void:
		if r.kind == Steal.Kind.SAFE:
			_good += 1
		elif r.is_out():
			_bad += 1
		_refresh_tally()
		_next_at = _now() + 300.0)
	_view = v
	_stage.add_child(UiKit.fill_parent(v))


## 타구 상황: 내 주자에게 판단이 생기는 타구를 시드 난수로 찾는다
func _start_play(tag: bool) -> void:
	var phys: BattedBallSim.Config = _app.batting_config.ball_physics
	var rng := RandomNumberGenerator.new()
	rng.seed = hash(["run-practice", _mode, _round])
	var my_speed := _speed
	var speed_of := func(i: int) -> int: return my_speed if i == ME else 50
	var launch := TAG_LAUNCH if tag else EXTRA_LAUNCH
	for i in MAX_TRIES:
		var ball := BattedBallSim.simulate(phys, rng.randf_range(EV_RANGE.x, EV_RANGE.y), rng.randf_range(launch.x, launch.y), rng.randf_range(-40.0, 40.0), 50)
		var outs := rng.randi_range(0, 1 if tag else 2)
		var from := 3 if tag else rng.randi_range(1, 2)
		var bases := [-1, -1, -1]
		bases[from - 1] = ME
		var play_seed := rng.randi()
		var resim := func(choices: Array) -> PlaySimulator.PlayResult:
			var r := RandomNumberGenerator.new()
			r.seed = play_seed
			return PlaySimulator.new(phys, ball, BATTER, bases, speed_of, outs, r, ME, choices).run()
		var control := RunControl.new(resim.call([]), resim, _manual)
		if not control.has_decisions():
			continue
		_control = control
		var caption := "★ 주루 연습 · %s %d루 주자" % ["%d사" % outs if outs > 0 else "무사", from]
		var h := HighlightView.new(phys, control.play, caption, _app.presentation.broadcast_speed, false)
		h.control = control
		h.mark_id = ME
		h.finished.connect(_on_play_finished)
		_view = h
		_stage.add_child(UiKit.fill_parent(h))
		return
	_show_result("상황을 만들지 못했다", Tokens.HUD_TEXT)


func _on_play_finished() -> void:
	if _control == null or _next_at < INF:
		return
	_control.finish()
	var play := _control.play
	var me: RunnerAI = play.runners.filter(func(r: RunnerAI) -> bool: return r.id == ME)[0]
	# 코치 사인을 따랐는지와 결과
	var followed := true
	for d: Dictionary in play.decisions:
		followed = followed and d.go == d.ai
	var note := "" if _control.manual else (" (코치 사인대로)" if followed else " (사인을 거슬러)")
	if me.state == RunnerAI.State.OUT:
		_bad += 1
		_show_result("아웃…" + note, Tokens.BAD)
	elif ME in play.scorers:
		_good += 1
		_show_result("득점!" + note, Tokens.GOOD)
	else:
		_good += 1
		_show_result("%s 세이프%s" % [BasePad.base_name(me.base), note], Tokens.HUD_TEXT)


func _show_result(text: String, color: Color) -> void:
	_result.text = text
	_result.add_theme_color_override("font_color", color)
	_result_chip.visible = true
	_refresh_tally()
	_next_at = _now() + RESULT_HOLD_MS


func _refresh_tally() -> void:
	_tally.text = "성공 %d · 실패 %d" % [_good, _bad]
