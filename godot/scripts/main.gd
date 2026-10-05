extends Control
## 타격 프로토타입 화면 — "내 선수의 타석 하나를 직접 조작하는 게 재밌는가" 검증용.
##
## 위: 능력치 슬라이더·카운트·기록 / 아래: BattingView (타구 분포도 + 투구 화면, 탭 = 스윙).
## 화면은 코드로 만든다 (장면 파일을 손으로 고치지 않아도 되게).

var _session: BattingSession
var _sound: SoundPlayer
var _view: BattingView
var _sliders := {}  # "contact" | "power" | "eye" -> HSlider
var _value_labels := {}
var _count_label: Label
var _timing_label: Label
var _tally_label: Label
var _fps_label: Label


func _ready() -> void:
	var balance := BalanceLoader.load_balance()
	_session = BattingSession.new(BattingConfig.from_balance(balance), Presentation.from_balance(balance))
	_sound = SoundPlayer.new()
	add_child(_sound)
	_session.sound_requested.connect(_sound.play)
	theme = _make_theme()
	_build()


func _process(_delta: float) -> void:
	_session.tick()
	_refresh_labels()


func _build() -> void:
	var bg := ColorRect.new()
	bg.color = Tokens.BACKGROUND
	bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(bg)

	var column := VBoxContainer.new()
	column.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	column.add_theme_constant_override("separation", 0)
	add_child(column)

	var panel := MarginContainer.new()
	for side in ["left", "right"]:
		panel.add_theme_constant_override("margin_" + side, Tokens.SPACE_MD)
	panel.add_theme_constant_override("margin_top", Tokens.SPACE_SM)
	panel.add_theme_constant_override("margin_bottom", Tokens.SPACE_XS)
	column.add_child(panel)
	var rows := VBoxContainer.new()
	rows.add_theme_constant_override("separation", 2)
	panel.add_child(rows)
	_add_slider(rows, "contact", "컨택", "판정 폭")
	_add_slider(rows, "power", "파워", "타구 거리")
	_add_slider(rows, "eye", "선구안", "구종 보이는 시점")

	var count_row := HBoxContainer.new()
	_count_label = _label("", Tokens.FONT_LABEL, Tokens.INK, Tokens.FONT_BOLD)
	_count_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_timing_label = _label("", Tokens.FONT_CAPTION, Tokens.INK_SOFT)
	count_row.add_child(_count_label)
	count_row.add_child(_timing_label)
	rows.add_child(count_row)
	_tally_label = _label("", Tokens.FONT_CAPTION, Tokens.INK_SOFT)
	rows.add_child(_tally_label)

	_view = BattingView.new()
	_view.session = _session
	_view.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_view.tapped.connect(_session.tap)
	column.add_child(_view)

	# FPS (오른쪽 아래 구석)
	_fps_label = _label("", Tokens.FONT_CAPTION, Tokens.CHALK)
	var fps_bg := PanelContainer.new()
	var style := StyleBoxFlat.new()
	style.bg_color = Tokens.SCRIM
	style.set_corner_radius_all(Tokens.SPACE_XS)
	style.content_margin_left = Tokens.SPACE_XS
	style.content_margin_right = Tokens.SPACE_XS
	fps_bg.add_theme_stylebox_override("panel", style)
	fps_bg.add_child(_fps_label)
	fps_bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(fps_bg)
	fps_bg.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_RIGHT, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_XS)
	# 글자가 늘어나면 왼쪽·위로 자라게 (오른쪽 끝에서 잘리지 않게)
	fps_bg.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	fps_bg.grow_vertical = Control.GROW_DIRECTION_BEGIN


func _add_slider(parent: Container, key: String, title: String, effect: String) -> void:
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", Tokens.SPACE_SM)
	var names := VBoxContainer.new()
	names.custom_minimum_size.x = 104
	names.add_theme_constant_override("separation", 0)
	var value_label := _label("%s 50" % title, Tokens.FONT_LABEL, Tokens.INK, Tokens.FONT_BOLD)
	names.add_child(value_label)
	names.add_child(_label(effect, Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	row.add_child(names)
	var slider := HSlider.new()
	slider.min_value = 0
	slider.max_value = 100
	slider.step = 1
	slider.value = 50
	slider.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	slider.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	slider.value_changed.connect(func(v: float) -> void:
		value_label.text = "%s %d" % [title, int(v)]
		_on_skills_changed())
	row.add_child(slider)
	parent.add_child(row)
	_sliders[key] = slider


## 슬라이더를 움직이면 다음 공부터 반영된다
func _on_skills_changed() -> void:
	_session.update_skills(BatterSkills.new(int(_sliders["contact"].value), int(_sliders["power"].value), int(_sliders["eye"].value)))


func _refresh_labels() -> void:
	# 볼넷·삼진으로 끝나면 4볼·3스트라이크가 되므로 표시 칸(3·2)에 맞춰 자른다
	var b := mini(_session.at_bat.balls, 3)
	var s := mini(_session.at_bat.strikes, 2)
	_count_label.text = "B %s%s  S %s%s" % ["●".repeat(b), "○".repeat(3 - b), "●".repeat(s), "○".repeat(2 - s)]
	_timing_label.text = "공이 오면 화면을 탭!" if is_nan(_session.last_timing_ms) else "직전 스윙 " + BattingSession.timing_text(_session.last_timing_ms)
	var t := _session.tally
	_tally_label.text = "%d타석 %d타수 %d안타 (%s) · 홈런 %d · 삼진 %d · 볼넷 %d · 시드 #%d" % [
		t.plate_appearances, t.at_bats, t.hits, t.average(), t.home_runs, t.strikeouts, t.walks, _session.seed_value]
	_fps_label.text = "%d FPS" % Engine.get_frames_per_second()


func _label(text: String, font_size: int, color: Color, font: Font = Tokens.FONT_REGULAR) -> Label:
	var l := Label.new()
	l.text = text
	l.add_theme_font_override("font", font)
	l.add_theme_font_size_override("font_size", font_size)
	l.add_theme_color_override("font_color", color)
	return l


## 슬라이더 모양 (Godot 기본 테마는 어두운 회색이라 토큰 색으로 바꾼다)
func _make_theme() -> Theme:
	var t := Theme.new()
	t.default_font = Tokens.FONT_REGULAR
	t.default_font_size = Tokens.FONT_BODY
	var track := StyleBoxFlat.new()
	track.bg_color = Color(Tokens.PRIMARY, 0.15)
	track.set_corner_radius_all(4)
	track.content_margin_top = 4
	track.content_margin_bottom = 4
	var filled := track.duplicate() as StyleBoxFlat
	filled.bg_color = Tokens.PRIMARY
	t.set_stylebox("slider", "HSlider", track)
	t.set_stylebox("grabber_area", "HSlider", filled)
	t.set_stylebox("grabber_area_highlight", "HSlider", filled)
	return t
