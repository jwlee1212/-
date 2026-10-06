class_name TuningPanel
extends Control
## 개발 빌드 전용 손맛 조절 패널 (CLAUDE.md §3-7). 왼쪽 아래 ⚙ 버튼으로 연다.
## 슬라이더를 움직이면 다음 공부터 바로 반영되고, [저장]하면 맥의 config/balance.json 에 기록된다.

signal changed(id: String, value: float)
signal save_requested
signal reload_requested
signal reset_requested

var _panel: PanelContainer
var _sliders := {}
var _value_labels := {}
var _status: Label


func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE

	var gear := Button.new()
	gear.text = "⚙ 손맛"
	gear.focus_mode = Control.FOCUS_NONE
	gear.pressed.connect(func() -> void: _panel.visible = not _panel.visible)
	add_child(gear)
	gear.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_LEFT, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_XS)
	gear.grow_vertical = Control.GROW_DIRECTION_BEGIN

	_panel = PanelContainer.new()
	_panel.visible = false
	var style := StyleBoxFlat.new()
	style.bg_color = Color(Tokens.SURFACE, 0.96)
	style.set_corner_radius_all(Tokens.RADIUS_CARD)
	style.set_content_margin_all(Tokens.SPACE_MD)
	_panel.add_theme_stylebox_override("panel", style)
	add_child(_panel)
	_panel.set_anchors_and_offsets_preset(Control.PRESET_CENTER_BOTTOM, Control.PRESET_MODE_KEEP_WIDTH)
	_panel.anchor_left = 0.0
	_panel.anchor_right = 1.0
	_panel.offset_left = Tokens.SPACE_SM
	_panel.offset_right = -Tokens.SPACE_SM
	_panel.offset_bottom = -48
	_panel.grow_vertical = Control.GROW_DIRECTION_BEGIN

	var col := VBoxContainer.new()
	col.add_theme_constant_override("separation", Tokens.SPACE_XS)
	_panel.add_child(col)
	col.add_child(_label("손맛 조절 (다음 공부터 반영)", Tokens.FONT_LABEL, Tokens.INK, Tokens.FONT_BOLD))
	for knob: Dictionary in Tuning.KNOBS:
		col.add_child(_knob_row(knob))
	var buttons := HBoxContainer.new()
	buttons.add_theme_constant_override("separation", Tokens.SPACE_XS)
	for b in [["저장", save_requested], ["다시 읽기", reload_requested], ["되돌리기", reset_requested]]:
		var btn := Button.new()
		btn.text = b[0]
		btn.focus_mode = Control.FOCUS_NONE
		btn.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		btn.pressed.connect((b[1] as Signal).emit)
		buttons.add_child(btn)
	col.add_child(buttons)
	_status = _label("", Tokens.FONT_CAPTION, Tokens.INK_SOFT)
	_status.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	col.add_child(_status)


## 패널 값을 다시 맞춘다 (다시 읽기·되돌리기 뒤)
func show_values(values: Dictionary) -> void:
	for knob: Dictionary in Tuning.KNOBS:
		var s: HSlider = _sliders[knob.id]
		s.set_value_no_signal(values[knob.id])
		_value_labels[knob.id].text = knob.fmt % values[knob.id]


func set_status(text: String) -> void:
	_status.text = text


func _knob_row(knob: Dictionary) -> Control:
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", Tokens.SPACE_SM)
	var names := VBoxContainer.new()
	names.custom_minimum_size.x = 112
	names.add_theme_constant_override("separation", 0)
	names.add_child(_label(knob.label, Tokens.FONT_CAPTION, Tokens.INK, Tokens.FONT_BOLD))
	names.add_child(_label(knob.hint, Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	row.add_child(names)
	var slider := HSlider.new()
	slider.min_value = knob.min
	slider.max_value = knob.max
	slider.step = knob.step
	slider.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	slider.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	var value_label := _label("", Tokens.FONT_CAPTION, Tokens.INK, Tokens.FONT_BOLD)
	value_label.custom_minimum_size.x = 52
	value_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	slider.value_changed.connect(func(v: float) -> void:
		value_label.text = knob.fmt % v
		changed.emit(knob.id, v))
	row.add_child(slider)
	row.add_child(value_label)
	_sliders[knob.id] = slider
	_value_labels[knob.id] = value_label
	return row


func _label(text: String, size: int, color: Color, font: Font = Tokens.FONT_REGULAR) -> Label:
	var l := Label.new()
	l.text = text
	l.add_theme_font_override("font", font)
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", color)
	return l
