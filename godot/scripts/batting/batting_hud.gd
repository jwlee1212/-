class_name BattingHud
extends Control
## 타석 HUD (반투명 유리 패널):
##   위: 미니 다이아몬드·이닝·점수·B/S/O·메뉴 / 왼쪽 위 아래: 타자 정보
##   왼쪽 아래: 스윙 종류 캡슐 (밸런스·컨택·파워·번트) / 오른쪽 아래: 큰 스윙 버튼 (선택 시간 링)
##   아래 가운데: 구종·구속 표시 / 위 가운데: 투구 전 "스윙을 고르세요" 안내
## 경기 상황은 game(GameRunner)이 있으면 그것을, 없으면(연습) practice_text 를 보여 준다.

signal menu_pressed

## 배치 (px): 위쪽 바 아래 정보 칩·안내가 놓이는 높이, 왼쪽 아래 ⚙ 손맛 버튼 자리, 스윙 버튼을 띄우는 높이
const BELOW_BAR_Y := 56
const GEAR_CLEARANCE := 34
const SWING_LIFT := 12

var session: BattingSession
var game: GameRunner = null
## 왼쪽 위 정보 칩에 보여 줄 글 (매 프레임 부른다)
var info_text: Callable = func() -> String: return ""
var show_menu := true

var _inning: Label
var _score: Label
var _info: Label
var _lcd_type: Label
var _lcd_speed: Label
var _toast: PanelContainer
var _toast_label: Label
var _toast_bar: ProgressBar
var _swing_buttons := {}
var _shown_type := ""
var _diamond: Diamond
var _count: CountDots
var _swing: SwingButton


func _init(p_session: BattingSession) -> void:
	session = p_session


func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	_build_top_bar()
	_build_info()
	_build_swing_types()
	_build_swing_button()
	_build_lcd()
	_build_toast()


func _process(_delta: float) -> void:
	var st: GameState = game.state if game != null else null
	if st != null:
		_inning.text = st.half_text()
		_score.text = "%s %d  :  %d %s" % [game.team_short(GameState.AWAY), st.score[0], st.score[1], game.team_short(GameState.HOME)]
		_diamond.bases = st.bases.map(func(b: int) -> bool: return b != GameState.EMPTY)
	else:
		_inning.text = "연습"
		_score.text = "상대 " + session.pitcher.name
	_count.balls = mini(session.at_bat.balls, 3)
	_count.strikes = mini(session.at_bat.strikes, 2)
	_count.outs = st.outs if st != null else 0
	_info.text = info_text.call()
	_info.get_parent().visible = _info.text != ""
	_refresh_lcd()
	_refresh_toast()
	if _shown_type != session.swing_type:
		_refresh_swing_types()
	_swing.label = _swing_label()
	_swing.ring = session.select_progress()


# ---------- 위 ----------

func _build_top_bar() -> void:
	var bar := UiKit.glass_panel()
	add_child(bar)
	bar.set_anchors_and_offsets_preset(Control.PRESET_TOP_WIDE, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_SM)
	var row := UiKit.hbox(Tokens.SPACE_MD)
	row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	bar.add_child(row)
	_diamond = Diamond.new()
	row.add_child(_diamond)
	_inning = UiKit.label("", Tokens.FONT_LABEL, Tokens.HUD_TEXT, Tokens.FONT_BOLD)
	row.add_child(_inning)
	_score = UiKit.label("", Tokens.FONT_LABEL, Tokens.HUD_TEXT, Tokens.FONT_BOLD)
	row.add_child(_score)
	var spacer := Control.new()
	spacer.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	spacer.mouse_filter = Control.MOUSE_FILTER_IGNORE
	row.add_child(spacer)
	_count = CountDots.new()
	row.add_child(_count)
	if show_menu:
		var menu := Button.new()
		menu.text = "Ⅱ"
		menu.focus_mode = Control.FOCUS_NONE
		menu.add_theme_stylebox_override("normal", UiKit.glass(Tokens.RADIUS_HUD))
		menu.add_theme_stylebox_override("hover", UiKit.glass(Tokens.RADIUS_HUD))
		menu.add_theme_stylebox_override("pressed", UiKit.glass(Tokens.RADIUS_HUD))
		menu.add_theme_color_override("font_color", Tokens.HUD_TEXT)
		menu.add_theme_font_size_override("font_size", Tokens.FONT_LABEL)
		menu.pressed.connect(menu_pressed.emit)
		row.add_child(menu)


func _build_info() -> void:
	var chip := UiKit.glass_panel()
	add_child(chip)
	chip.position = Vector2(Tokens.SPACE_SM, BELOW_BAR_Y)
	_info = UiKit.label("", Tokens.FONT_CAPTION, Tokens.HUD_TEXT, Tokens.FONT_BOLD)
	chip.add_child(_info)


func _build_toast() -> void:
	_toast = UiKit.glass_panel()
	add_child(_toast)
	_toast.set_anchors_and_offsets_preset(Control.PRESET_CENTER_TOP, Control.PRESET_MODE_MINSIZE)
	_toast.position.y = BELOW_BAR_Y
	var col := UiKit.vbox(Tokens.SPACE_XS)
	col.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_toast.add_child(col)
	_toast_label = UiKit.label("스윙을 고르세요", Tokens.FONT_BODY, Tokens.HUD_TEXT, Tokens.FONT_BOLD)
	_toast_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	col.add_child(_toast_label)
	_toast_bar = ProgressBar.new()
	_toast_bar.show_percentage = false
	_toast_bar.max_value = 1.0
	_toast_bar.custom_minimum_size = Vector2(180, 5)
	var bg := StyleBoxFlat.new()
	bg.bg_color = Tokens.HUD_BORDER
	bg.set_corner_radius_all(3)
	var fill := StyleBoxFlat.new()
	fill.bg_color = Tokens.ACCENT
	fill.set_corner_radius_all(3)
	_toast_bar.add_theme_stylebox_override("background", bg)
	_toast_bar.add_theme_stylebox_override("fill", fill)
	col.add_child(_toast_bar)


func _refresh_toast() -> void:
	var p := session.select_progress()
	_toast.visible = p >= 0.0
	if p >= 0.0:
		_toast_bar.value = 1.0 - p
		_toast_label.text = "%s 스윙 · 바꾸려면 왼쪽 아래" % session.config.swing_type(session.swing_type).label
		_toast.position.x = (size.x - _toast.size.x) / 2.0


# ---------- 아래 ----------

func _build_swing_types() -> void:
	var capsule := UiKit.glass_panel(36)
	add_child(capsule)
	capsule.mouse_filter = Control.MOUSE_FILTER_STOP
	var row := UiKit.hbox(Tokens.SPACE_XS)
	capsule.add_child(row)
	for id: String in session.config.swing_types:
		var st: BattingConfig.SwingType = session.config.swing_types[id]
		var b := Button.new()
		b.text = st.label
		b.focus_mode = Control.FOCUS_NONE
		b.custom_minimum_size = Vector2(58, 50)
		b.add_theme_font_override("font", Tokens.FONT_BOLD)
		b.add_theme_font_size_override("font_size", Tokens.FONT_CAPTION)
		b.pressed.connect(func() -> void: session.set_swing_type(id))
		row.add_child(b)
		_swing_buttons[id] = b
	capsule.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_LEFT, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_SM)
	capsule.grow_vertical = Control.GROW_DIRECTION_BEGIN
	capsule.position.y -= GEAR_CLEARANCE
	_refresh_swing_types()


func _refresh_swing_types() -> void:
	_shown_type = session.swing_type
	for id: String in _swing_buttons:
		var b: Button = _swing_buttons[id]
		var on := id == session.swing_type
		var s := StyleBoxFlat.new()
		s.bg_color = Tokens.PRIMARY if on else Color(1, 1, 1, 0.08)
		s.border_color = Tokens.HUD_TEXT if on else Tokens.HUD_BORDER
		s.set_border_width_all(2 if on else 1)
		s.set_corner_radius_all(25)
		s.anti_aliasing = true
		for state in ["normal", "hover", "pressed", "focus"]:
			b.add_theme_stylebox_override(state, s)
		for state in ["font_color", "font_hover_color", "font_pressed_color", "font_focus_color"]:
			b.add_theme_color_override(state, Tokens.HUD_TEXT if on else Tokens.HUD_TEXT_SOFT)


func _build_swing_button() -> void:
	_swing = SwingButton.new()
	add_child(_swing)
	_swing.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_RIGHT, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_MD)
	_swing.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	_swing.grow_vertical = Control.GROW_DIRECTION_BEGIN
	_swing.position -= Vector2(0, SWING_LIFT)
	_swing.pressed.connect(_on_swing)


## 스윙 버튼: 선택 시간이면 바로 던지게, 공이 오고 있으면 스윙, 타석이 끝났으면 다음
func _on_swing() -> void:
	match session.phase:
		BattingSession.Phase.WAITING:
			session.ready_now()
		BattingSession.Phase.FLIGHT, BattingSession.Phase.OVER:
			session.tap()


func _swing_label() -> String:
	match session.phase:
		BattingSession.Phase.WAITING:
			return "던져!" if session.select_progress() >= 0.0 else "대기"
		BattingSession.Phase.OVER:
			return "다음"
		BattingSession.Phase.HIT:
			return ""
	return "스윙"


func _build_lcd() -> void:
	var lcd := PanelContainer.new()
	var s := StyleBoxFlat.new()
	s.bg_color = Tokens.LCD_BG
	s.border_color = Tokens.HUD_BORDER
	s.set_border_width_all(1)
	s.set_corner_radius_all(10)
	s.content_margin_left = Tokens.SPACE_MD
	s.content_margin_right = Tokens.SPACE_MD
	s.content_margin_top = 2
	s.content_margin_bottom = 2
	lcd.add_theme_stylebox_override("panel", s)
	lcd.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(lcd)
	var row := UiKit.hbox(Tokens.SPACE_SM)
	row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	lcd.add_child(row)
	_lcd_type = UiKit.label("", Tokens.FONT_CAPTION, Tokens.HUD_TEXT_SOFT, Tokens.FONT_BOLD)
	_lcd_type.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	row.add_child(_lcd_type)
	_lcd_speed = UiKit.label("--- km/h", Tokens.FONT_TITLE, Tokens.LCD_TEXT, Tokens.FONT_BOLD)
	row.add_child(_lcd_speed)
	lcd.set_anchors_and_offsets_preset(Control.PRESET_CENTER_BOTTOM, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_SM)
	lcd.grow_vertical = Control.GROW_DIRECTION_BEGIN
	lcd.grow_horizontal = Control.GROW_DIRECTION_BOTH


func _refresh_lcd() -> void:
	var p := session.pitch
	if p == null:
		return
	var flying := session.phase == BattingSession.Phase.FLIGHT
	var progress := session.since(session.phase_since_ms) / p.flight_ms if flying else INF
	if session.phase == BattingSession.Phase.WINDUP:
		_lcd_type.text = ""
		_lcd_speed.text = "--- km/h"
		return
	# 구종은 선구안 시점이 지나야, 구속은 공이 지나간 뒤에
	_lcd_type.text = p.label if p.is_revealed_at(progress) else ""
	_lcd_type.add_theme_color_override("font_color", Tokens.PITCH_COLORS.get(p.type, Tokens.HUD_TEXT))
	_lcd_speed.text = "%d km/h" % session.config.speed_kmh(p.flight_ms) if progress >= 1.0 else "--- km/h"


# ---------- 그리는 부품 ----------

## 미니 다이아몬드 (주자)
class Diamond extends Control:
	var bases := [false, false, false]

	func _init() -> void:
		custom_minimum_size = Vector2(34, 28)
		mouse_filter = Control.MOUSE_FILTER_IGNORE

	func _process(_d: float) -> void:
		queue_redraw()

	func _draw() -> void:
		var c := Vector2(size.x / 2.0, size.y / 2.0 + 2)
		var g := 9.0
		var spots := [c + Vector2(g, 0), c + Vector2(0, -g), c + Vector2(-g, 0)]
		for i in 3:
			var p: Vector2 = spots[i]
			var r := 5.5
			var pts := PackedVector2Array([p + Vector2(0, -r), p + Vector2(r, 0), p + Vector2(0, r), p + Vector2(-r, 0)])
			draw_colored_polygon(pts, Tokens.COUNT_STRIKE if bases[i] else Color(1, 1, 1, 0.22))


## B/S/O 점
class CountDots extends Control:
	var balls := 0
	var strikes := 0
	var outs := 0

	func _init() -> void:
		custom_minimum_size = Vector2(150, 26)
		mouse_filter = Control.MOUSE_FILTER_IGNORE

	func _process(_d: float) -> void:
		queue_redraw()

	func _draw() -> void:
		var x := 0.0
		for group in [["B", balls, 3, Tokens.COUNT_BALL], ["S", strikes, 2, Tokens.COUNT_STRIKE], ["O", outs, 2, Tokens.COUNT_OUT]]:
			draw_string(Tokens.FONT_BOLD, Vector2(x, 18), group[0], HORIZONTAL_ALIGNMENT_LEFT, -1, Tokens.FONT_CAPTION, Tokens.HUD_TEXT_SOFT)
			x += 12
			for i in group[2]:
				draw_circle(Vector2(x + 6, 13), 5.0, group[3] if i < group[1] else Color(1, 1, 1, 0.2), true, -1.0, true)
				x += 13
			x += 8


## 큰 스윙 버튼: 원 + 배트 그림 + 글자, 선택 시간이면 남은 시간 링
class SwingButton extends Control:
	signal pressed
	var label := "스윙"
	var ring := -1.0

	func _init() -> void:
		custom_minimum_size = Vector2(88, 88)
		mouse_filter = Control.MOUSE_FILTER_STOP

	func _gui_input(event: InputEvent) -> void:
		# 손가락이 닿는 순간 (타이밍이 생명)
		if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
			pressed.emit()
			accept_event()

	func _process(_d: float) -> void:
		queue_redraw()

	func _draw() -> void:
		var c := size / 2.0
		var r := size.x / 2.0
		draw_circle(c, r, Tokens.HUD_GLASS, true, -1.0, true)
		draw_circle(c, r - 6, Tokens.PRIMARY, true, -1.0, true)
		draw_circle(c, r - 1, Tokens.HUD_BORDER, false, 2.0, true)
		if ring >= 0.0:
			draw_arc(c, r - 2, -PI / 2.0, -PI / 2.0 + TAU * (1.0 - ring), 48, Tokens.ACCENT, 4.0, true)
		# 배트 그림
		draw_line(c + Vector2(-r * 0.38, r * 0.2), c + Vector2(r * 0.32, -r * 0.32), Tokens.HUD_TEXT, r * 0.14, true)
		draw_circle(c + Vector2(-r * 0.4, r * 0.22), r * 0.08, Tokens.HUD_TEXT, true, -1.0, true)
		draw_string(Tokens.FONT_BOLD, Vector2(0, c.y + r * 0.62), label, HORIZONTAL_ALIGNMENT_CENTER, size.x, Tokens.FONT_CAPTION, Tokens.HUD_TEXT)
