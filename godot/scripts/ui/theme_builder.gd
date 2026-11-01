class_name ThemeBuilder
extends RefCounted
## 앱 전체 Theme 을 토큰(Tokens)에서 만든다. 색·간격·글자 크기의 원본은 tokens.gd 하나이고, 여기서 Theme 으로 옮겨 담는다.
## 새로 만드는 화면(홈의 TopBar · PlayerCard · BottomNav · NavButton)은 노드마다 색을 덮어쓰지 않고
## theme_type_variation 이름만 붙인다 — 아래 "홈" 부분의 변형들. 옛 화면은 아직 UiKit 의 개별 지정을 쓴다.
##
## 변형 이름
##   PanelContainer: Card(흰 카드) · Bar(남색 바)
##   Label: Caption · Body · BodyBold · Title · Number · Kicker (밝은 바탕) / BarText · BarTextSoft (남색 바 위)
##   Button: NavButton · NavButtonOn (아래 탭) · CtaButton (주황, 화면에 하나) · SecondaryButton (흰 바탕 남색 글자) · BarIconButton (바 위 ⚙)


static func build() -> Theme:
	var t := Theme.new()
	_base(t)
	_home(t)
	return t


## 기본: Godot 기본 테마는 어두운 회색이라 슬라이더·버튼·입력칸을 토큰 색으로 바꾼다 (모든 화면 공통)
static func _base(t: Theme) -> void:
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
	var button := StyleBoxFlat.new()
	button.bg_color = Tokens.PRIMARY
	button.set_corner_radius_all(Tokens.RADIUS_CARD)
	button.content_margin_left = Tokens.SPACE_MD
	button.content_margin_right = Tokens.SPACE_MD
	button.content_margin_top = Tokens.SPACE_XS
	button.content_margin_bottom = Tokens.SPACE_XS
	var pressed := button.duplicate() as StyleBoxFlat
	pressed.bg_color = Tokens.PRIMARY.darkened(0.2)
	for state in ["normal", "hover", "focus"]:
		t.set_stylebox(state, "Button", button)
	t.set_stylebox("pressed", "Button", pressed)
	for state in ["font_color", "font_hover_color", "font_pressed_color", "font_focus_color"]:
		t.set_color(state, "Button", Tokens.SURFACE)
	t.set_font("font", "Button", Tokens.FONT_BOLD)
	t.set_font_size("font_size", "Button", Tokens.FONT_CAPTION)
	var edit := StyleBoxFlat.new()
	edit.bg_color = Tokens.SURFACE
	edit.border_color = Tokens.PRIMARY
	edit.set_border_width_all(2)
	edit.set_corner_radius_all(Tokens.SPACE_SM)
	edit.set_content_margin_all(Tokens.SPACE_SM)
	t.set_stylebox("normal", "LineEdit", edit)
	t.set_stylebox("focus", "LineEdit", edit)
	t.set_color("font_color", "LineEdit", Tokens.INK)
	t.set_font_size("font_size", "LineEdit", Tokens.FONT_TITLE)


## 홈 화면 변형 (2026-11-01 재구성: 60-30-10, 8px 격자, 터치 48)
static func _home(t: Theme) -> void:
	t.set_stylebox("panel", "Card", card_box())
	t.set_type_variation("Card", "PanelContainer")
	t.set_stylebox("panel", "Bar", _box(Tokens.NAVY, Tokens.RADIUS_PANEL, Tokens.SPACE_MD, 0))
	t.set_type_variation("Bar", "PanelContainer")
	# 글자: 굵기와 크기로 위계 (글꼴은 Pretendard Regular·Bold 두 가지만)
	_label(t, "Caption", Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_REGULAR)
	_label(t, "Body", Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_REGULAR)
	_label(t, "BodyBold", Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD)
	_label(t, "Title", Tokens.FONT_TITLE, Tokens.INK, Tokens.FONT_BOLD)
	_label(t, "Number", Tokens.FONT_NUMBER - 6, Tokens.INK, Tokens.FONT_BOLD)
	_label(t, "Kicker", Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.spaced(Tokens.FONT_BOLD, Tokens.TRACKING_KICKER))
	_label(t, "BarText", Tokens.FONT_BODY, Tokens.SURFACE, Tokens.FONT_BOLD)
	_label(t, "BarTextSoft", Tokens.FONT_CAPTION, Tokens.BAR_TEXT_SOFT, Tokens.FONT_REGULAR)
	# 아래 탭: 고르지 않은 탭은 흐린 글자만, 고른 탭은 한 톤 밝은 남색 판 + 흰 글자
	_button(t, "NavButton", Color(0, 0, 0, 0), Tokens.BAR_TEXT_SOFT, Tokens.FONT_LABEL, Tokens.SPACE_SM)
	_button(t, "NavButtonOn", Tokens.NAVY_SOFT, Tokens.SURFACE, Tokens.FONT_LABEL, Tokens.SPACE_SM)
	_button(t, "CtaButton", Tokens.ACCENT, Tokens.SURFACE, Tokens.FONT_TITLE - 4, Tokens.SPACE_LG)
	_button(t, "SecondaryButton", Tokens.SURFACE, Tokens.NAVY, Tokens.FONT_LABEL, Tokens.SPACE_MD, Tokens.DIVIDER)
	_button(t, "BarIconButton", Color(0, 0, 0, 0), Tokens.SURFACE, Tokens.FONT_TITLE, Tokens.SPACE_SM)


## Card 변형의 판 (흰 카드). 테마 밖에서 고쳐 쓸 때(왼쪽 띠 등)도 이것에서 시작한다
static func card_box() -> StyleBoxFlat:
	return _box(Tokens.ROOM_PANEL, Tokens.RADIUS_PANEL, Tokens.SPACE_MD, Tokens.SPACE_SM + Tokens.SPACE_XS)


static func _box(color: Color, radius: int, pad_x: int, pad_y: int) -> StyleBoxFlat:
	var s := StyleBoxFlat.new()
	s.bg_color = color
	s.set_corner_radius_all(radius)
	s.content_margin_left = pad_x
	s.content_margin_right = pad_x
	s.content_margin_top = pad_y
	s.content_margin_bottom = pad_y
	s.anti_aliasing = true
	return s


static func _label(t: Theme, name: String, size: int, color: Color, font: Font) -> void:
	t.set_type_variation(name, "Label")
	t.set_font_size("font_size", name, size)
	t.set_color("font_color", name, color)
	t.set_font("font", name, font)


## 버튼 변형: 바탕·글자색, 눌림은 바탕을 조금 어둡게, 꺼짐은 흐리게. border 를 주면 1px 테두리
static func _button(t: Theme, name: String, bg: Color, fg: Color, size: int, pad_x: int, border: Color = Color(0, 0, 0, 0)) -> void:
	t.set_type_variation(name, "Button")
	var normal := _box(bg, Tokens.RADIUS_PANEL, pad_x, Tokens.SPACE_XS)
	if border.a > 0.0:
		normal.border_color = border
		normal.set_border_width_all(1)
	var pressed := normal.duplicate() as StyleBoxFlat
	pressed.bg_color = bg.darkened(0.15) if bg.a > 0.0 else Color(Tokens.SURFACE, 0.08)
	var disabled := normal.duplicate() as StyleBoxFlat
	disabled.bg_color = Color(Tokens.INK_SOFT, 0.35) if bg.a > 0.0 else bg
	for state in ["normal", "hover", "focus"]:
		t.set_stylebox(state, name, normal)
	t.set_stylebox("pressed", name, pressed)
	t.set_stylebox("disabled", name, disabled)
	for state in ["font_color", "font_hover_color", "font_pressed_color", "font_focus_color", "font_hover_pressed_color"]:
		t.set_color(state, name, fg)
	t.set_color("font_disabled_color", name, Color(fg, 0.6))
	t.set_font("font", name, Tokens.FONT_BOLD)
	t.set_font_size("font_size", name, size)
