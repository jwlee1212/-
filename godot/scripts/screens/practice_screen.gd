class_name PracticeScreen
extends Control
## 타격 연습 (손맛 튜닝용): 전체 화면 타석 + HUD. 오른쪽 위 "능력치" 버튼으로 능력치 슬라이더·상대 투수를 연다

var _app
var _session: BattingSession
var _view: BattingView
var _hud: BattingHud
var _drawer: PanelContainer
var _sliders := {}
var _pitcher_button: Button


func _init(app) -> void:
	_app = app


func _ready() -> void:
	_session = _app.new_batting_session()
	_view = BattingView.new()
	_view.session = _session
	_view.tapped.connect(_session.tap)
	add_child(UiKit.fill_parent(_view))
	_hud = BattingHud.new(_session)
	_hud.info_text = func() -> String:
		var t := _session.tally
		var last := "" if is_nan(_session.last_timing_ms) else " · 직전 " + BattingSession.timing_text(_session.last_timing_ms)
		return "%d타석 %d안타 (%s) · 홈런 %d · 삼진 %d%s" % [t.plate_appearances, t.hits, t.average(), t.home_runs, t.strikeouts, last]
	_hud.menu_pressed.connect(_app.show_title)
	add_child(_hud)
	_build_drawer()


func _process(_delta: float) -> void:
	_session.tick()
	if _pitcher_button != null:
		_pitcher_button.text = "상대: %s ▸" % _session.pitcher.name


func on_config_reloaded() -> void:
	_session.replace_config(_app.batting_config, _app.presentation)


## 능력치 서랍 (오른쪽 위 버튼으로 열고 닫는다)
func _build_drawer() -> void:
	var toggle := UiKit.button("능력치 ▾", func() -> void: _drawer.visible = not _drawer.visible, Tokens.INK_SOFT, Tokens.FONT_CAPTION)
	add_child(toggle)
	toggle.set_anchors_and_offsets_preset(Control.PRESET_TOP_RIGHT, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_SM)
	toggle.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	toggle.position.y = BattingHud.BELOW_BAR_Y
	_drawer = UiKit.glass_panel()
	_drawer.mouse_filter = Control.MOUSE_FILTER_STOP
	_drawer.visible = false
	add_child(_drawer)
	_drawer.set_anchors_and_offsets_preset(Control.PRESET_TOP_RIGHT, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_SM)
	_drawer.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	_drawer.position.y = BattingHud.BELOW_BAR_Y + 40
	_drawer.custom_minimum_size.x = 260
	var col := UiKit.vbox(Tokens.SPACE_XS)
	_drawer.add_child(col)
	_add_slider(col, "contact", "컨택", "판정 폭")
	_add_slider(col, "power", "파워", "타구 속도")
	_add_slider(col, "eye", "선구안", "구종 보이는 시점")
	_add_slider(col, "speed", "주력", "내야안타·장타")
	_pitcher_button = UiKit.button("", func() -> void: _session.cycle_pitcher(), Tokens.PRIMARY, Tokens.FONT_CAPTION)
	col.add_child(_pitcher_button)
	if _session.config.aim_enabled:
		var hint := UiKit.label("투구 전에 존 칸을 탭하면 노려치기", Tokens.FONT_CAPTION, Tokens.HUD_TEXT_SOFT)
		col.add_child(hint)


func _add_slider(parent: Container, key: String, title: String, effect: String) -> void:
	var names := UiKit.hbox(Tokens.SPACE_XS)
	var value_label := UiKit.label("%s 50" % title, Tokens.FONT_CAPTION, Tokens.HUD_TEXT, Tokens.FONT_BOLD)
	names.add_child(value_label)
	names.add_child(UiKit.label(effect, Tokens.FONT_CAPTION, Tokens.HUD_TEXT_SOFT))
	parent.add_child(names)
	var slider := HSlider.new()
	slider.min_value = 0
	slider.max_value = 100
	slider.step = 1
	slider.value = 50
	slider.value_changed.connect(func(v: float) -> void:
		value_label.text = "%s %d" % [title, int(v)]
		_session.update_skills(BatterSkills.new(int(_sliders["contact"].value), int(_sliders["power"].value),
			int(_sliders["eye"].value), int(_sliders["speed"].value))))
	parent.add_child(slider)
	_sliders[key] = slider
