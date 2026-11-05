class_name CalendarView
extends VBoxContainer
## 달력 한 달 (더쇼 RTTS 의 일정 달력처럼). 화면만 — 칸 내용은 로직이 만든다 (ProCareer.calendar_cells · HsSim.calendar_cells).
## 칸: {"title": 상대·"휴식", "sub": 점수·홈/원정, "state": win | loss | draw | future | off | today | other, "pick": 고를 수 있나, "me": 내 기록 한 줄}
## 고를 수 있는 칸을 누르면 그 날짜가 선택되고 picked(날짜) 를 보낸다 — 허브가 "그날 경기 전까지 시뮬레이션" 버튼을 띄운다.
## ‹ › 로 달을 넘긴다. 색·간격·글자 크기는 Tokens 만 쓴다 (원칙 5).

signal picked(day: int)

## (y, m) → 칸 사전
var _cells_of: Callable
var _y := 2030
var _m := 3
## 고른 날짜 (-1 없음)
var selected := -1


func _init(cells_of: Callable, today: int) -> void:
	_cells_of = cells_of
	var c := GameCalendar.civil_from_days(today)
	_y = int(c["y"])
	_m = int(c["m"])
	add_theme_constant_override("separation", Tokens.SPACE_XS)
	size_flags_horizontal = Control.SIZE_EXPAND_FILL


func _ready() -> void:
	_build()


## 지금 보이는 달 (테스트·스크린샷용)
func month() -> Vector2i:
	return Vector2i(_y, _m)


func shift(months: int) -> void:
	var n := _y * 12 + (_m - 1) + months
	_y = n / 12
	_m = n % 12 + 1
	_build()


func _build() -> void:
	for c in get_children():
		c.queue_free()
	var head := UiKit.hbox(Tokens.SPACE_SM)
	head.add_child(UiKit.button("‹", shift.bind(-1), Tokens.NAVY_SOFT, Tokens.FONT_CAPTION))
	var t := UiKit.title("%d년 %d월" % [_y, _m], Tokens.FONT_LABEL)
	t.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	t.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	head.add_child(t)
	head.add_child(UiKit.button("›", shift.bind(1), Tokens.NAVY_SOFT, Tokens.FONT_CAPTION))
	add_child(head)
	var grid := GridContainer.new()
	grid.columns = 7
	grid.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	grid.add_theme_constant_override("h_separation", Tokens.SPACE_XS)
	grid.add_theme_constant_override("v_separation", Tokens.SPACE_XS)
	for wd: String in GameCalendar.WEEKDAYS:
		var l := UiKit.label(wd, Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD)
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		grid.add_child(l)
	var first := GameCalendar.days_from_civil(_y, _m, 1)
	for i in GameCalendar.weekday(first):
		grid.add_child(Control.new())
	var cells: Dictionary = _cells_of.call(_y, _m)
	for d in GameCalendar.days_in_month(_y, _m):
		var z := first + d
		grid.add_child(_cell(z, d + 1, cells.get(z, {})))
	add_child(grid)


func _cell(z: int, dnum: int, c: Dictionary) -> Control:
	var b := Button.new()
	b.focus_mode = Control.FOCUS_NONE
	b.custom_minimum_size = Vector2(0, Tokens.SPACE_XL + Tokens.SPACE_MD)
	b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	b.clip_text = true
	var lines := [str(dnum)]
	if str(c.get("title", "")) != "":
		lines.append(str(c["title"]))
	if str(c.get("sub", "")) != "":
		lines.append(str(c["sub"]))
	b.text = "\n".join(lines)
	b.tooltip_text = str(c.get("me", ""))
	b.add_theme_font_size_override("font_size", Tokens.FONT_CAPTION)
	b.add_theme_font_override("font", Tokens.FONT_BOLD if c.get("state", "") == "today" else Tokens.FONT_REGULAR)
	var bg := _bg(str(c.get("state", "")))
	var border := Tokens.ACCENT if z == selected else (Tokens.NAVY if c.get("state", "") == "today" else Color(Tokens.INK, 0.0))
	for kind: String in ["normal", "hover", "pressed", "disabled"]:
		b.add_theme_stylebox_override(kind, _style(bg, border))
	for kind: String in ["font_color", "font_hover_color", "font_pressed_color", "font_disabled_color"]:
		b.add_theme_color_override(kind, Tokens.INK if not c.is_empty() else Tokens.INK_SOFT)
	b.disabled = not bool(c.get("pick", false))
	b.pressed.connect(func() -> void:
		selected = z
		picked.emit(z)
		_build())
	return b


func _bg(state: String) -> Color:
	match state:
		"win":
			return Color(Tokens.GOOD, 0.22)
		"loss":
			return Color(Tokens.BAD, 0.18)
		"draw":
			return Color(Tokens.INK_SOFT, 0.18)
		"today":
			return Color(Tokens.ACCENT, 0.2)
		"off":
			return Color(Tokens.NAVY_SOFT, 0.12)
		"future":
			return Tokens.SURFACE
		"other":
			return Color(Tokens.NAVY_SOFT, 0.08)
	return Color(Tokens.SURFACE, 0.5)


func _style(bg: Color, border: Color) -> StyleBoxFlat:
	var s := StyleBoxFlat.new()
	s.bg_color = bg
	s.border_color = border
	s.set_border_width_all(2)
	s.set_corner_radius_all(Tokens.RADIUS_BUBBLE / 2)
	s.content_margin_left = Tokens.SPACE_XS / 2
	s.content_margin_right = Tokens.SPACE_XS / 2
	s.content_margin_top = Tokens.SPACE_XS / 2
	s.content_margin_bottom = Tokens.SPACE_XS / 2
	return s
