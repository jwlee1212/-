class_name UiKit
extends RefCounted
## 화면 조립용 도우미. 색·간격·글꼴은 모두 Tokens 에서 (원칙 5).


static func label(text: String, size: int = Tokens.FONT_BODY, color: Color = Tokens.INK, font: Font = Tokens.FONT_REGULAR) -> Label:
	var l := Label.new()
	l.text = text
	l.add_theme_font_override("font", font)
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", color)
	return l


static func title(text: String, size: int = Tokens.FONT_TITLE, color: Color = Tokens.INK) -> Label:
	return label(text, size, color, Tokens.FONT_BOLD)


## 둥근 카드 (흰 바탕)
static func card(color: Color = Tokens.SURFACE) -> PanelContainer:
	var p := PanelContainer.new()
	var s := StyleBoxFlat.new()
	s.bg_color = color
	s.set_corner_radius_all(Tokens.RADIUS_CARD)
	s.set_content_margin_all(Tokens.SPACE_MD)
	s.anti_aliasing = true
	p.add_theme_stylebox_override("panel", s)
	return p


## 큰 알약 버튼
static func button(text: String, on_press: Callable, color: Color = Tokens.PRIMARY, size: int = Tokens.FONT_LABEL) -> Button:
	var b := Button.new()
	b.text = text
	b.focus_mode = Control.FOCUS_NONE
	var normal := StyleBoxFlat.new()
	normal.bg_color = color
	normal.set_corner_radius_all(Tokens.RADIUS_CARD)
	normal.content_margin_left = Tokens.SPACE_LG
	normal.content_margin_right = Tokens.SPACE_LG
	normal.content_margin_top = Tokens.SPACE_SM
	normal.content_margin_bottom = Tokens.SPACE_SM
	normal.anti_aliasing = true
	var pressed := normal.duplicate() as StyleBoxFlat
	pressed.bg_color = color.darkened(0.2)
	var disabled := normal.duplicate() as StyleBoxFlat
	disabled.bg_color = Color(Tokens.INK_SOFT, 0.4)
	for state in ["normal", "hover", "focus"]:
		b.add_theme_stylebox_override(state, normal)
	b.add_theme_stylebox_override("pressed", pressed)
	b.add_theme_stylebox_override("disabled", disabled)
	for state in ["font_color", "font_hover_color", "font_pressed_color", "font_focus_color", "font_disabled_color"]:
		b.add_theme_color_override(state, Tokens.SURFACE)
	b.add_theme_font_override("font", Tokens.FONT_BOLD)
	b.add_theme_font_size_override("font_size", size)
	b.pressed.connect(on_press)
	return b


## 능력치 막대: "컨택  ■■■■■□□□  52"
static func stat_bar(name: String, value: int, max_value: int, color: Color) -> HBoxContainer:
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", Tokens.SPACE_SM)
	var n := label(name, Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD)
	n.custom_minimum_size.x = 64
	row.add_child(n)
	var bar := ProgressBar.new()
	bar.max_value = max_value
	bar.value = value
	bar.show_percentage = false
	bar.custom_minimum_size = Vector2(120, 12)
	bar.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	bar.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	var bg := StyleBoxFlat.new()
	bg.bg_color = Color(color, 0.15)
	bg.set_corner_radius_all(6)
	var fill := StyleBoxFlat.new()
	fill.bg_color = color
	fill.set_corner_radius_all(6)
	bar.add_theme_stylebox_override("background", bg)
	bar.add_theme_stylebox_override("fill", fill)
	row.add_child(bar)
	var v := label(str(value), Tokens.FONT_LABEL, Tokens.INK, Tokens.FONT_BOLD)
	v.custom_minimum_size.x = 28
	v.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	row.add_child(v)
	return row


static func vbox(separation: int = Tokens.SPACE_SM) -> VBoxContainer:
	var b := VBoxContainer.new()
	b.add_theme_constant_override("separation", separation)
	return b


static func hbox(separation: int = Tokens.SPACE_SM) -> HBoxContainer:
	var b := HBoxContainer.new()
	b.add_theme_constant_override("separation", separation)
	return b


static func margin(child: Control, px: int = Tokens.SPACE_MD) -> MarginContainer:
	var m := MarginContainer.new()
	for side in ["left", "right", "top", "bottom"]:
		m.add_theme_constant_override("margin_" + side, px)
	m.add_child(child)
	return m


static func fill_parent(c: Control) -> Control:
	c.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	return c


static func expand(c: Control) -> Control:
	c.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	c.size_flags_vertical = Control.SIZE_EXPAND_FILL
	return c


## 반투명 유리 패널 (HUD)
static func glass(radius: int = Tokens.RADIUS_HUD) -> StyleBoxFlat:
	var s := StyleBoxFlat.new()
	s.bg_color = Tokens.HUD_GLASS
	s.border_color = Tokens.HUD_BORDER
	s.set_border_width_all(1)
	s.set_corner_radius_all(radius)
	s.content_margin_left = Tokens.SPACE_SM + Tokens.SPACE_XS
	s.content_margin_right = Tokens.SPACE_SM + Tokens.SPACE_XS
	s.content_margin_top = Tokens.SPACE_XS
	s.content_margin_bottom = Tokens.SPACE_XS
	s.anti_aliasing = true
	return s


static func glass_panel(radius: int = Tokens.RADIUS_HUD) -> PanelContainer:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", glass(radius))
	p.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return p


## 자간 넓은 영문·소제목 ("NEXT MATCH", "KOREAN BASEBALL CAREER")
static func kicker(text: String, color: Color = Tokens.INK_SOFT, size: int = Tokens.FONT_CAPTION, tracking: int = Tokens.TRACKING_KICKER) -> Label:
	var l := label(text, size, color, Tokens.FONT_BOLD)
	l.add_theme_font_override("font", Tokens.spaced(Tokens.FONT_BOLD, tracking))
	return l


## 가로 구분선
static func divider() -> ColorRect:
	var r := ColorRect.new()
	r.color = Tokens.DIVIDER
	r.custom_minimum_size.y = 1
	return r


## 아래로 남는 자리를 채우는 빈칸
static func spacer() -> Control:
	var c := Control.new()
	c.size_flags_vertical = Control.SIZE_EXPAND_FILL
	c.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	return c
