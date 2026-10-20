class_name BattingHud
extends Control
## 타석 HUD: 실제 야구 중계 그래픽처럼 (2026-10-15, 시안 docs/mockups/broadcast-batting.svg — 시점은 타자 뒤 그대로)
##   왼쪽 위: 스코어 버그(팀·점수·이닝·주자·B/S/O) + 투수 줄 (+ 연습 정보 칩)
##   오른쪽 위: 메뉴 · 구속 패널
##   왼쪽: 스윙 종류 (세로) / 오른쪽 아래: 큰 스윙 버튼 (선택 시간 링)
##   왼쪽 아래: 타자 자막 — 타석이 시작되면 미끄러져 나왔다가 들어간다
##   위 가운데: 투구 전 "스윙을 고르세요" 안내
## 경기 상황은 game(GameRunner)이 있으면 그것을, 없으면(연습) 상대 투수 이름만 보여 준다.

signal menu_pressed

## 배치 (px): 화면 가장자리 여백, 왼쪽 위 스코어 버그·투수 줄 아래(정보 칩), 오른쪽 위 메뉴·구속 패널 아래(연습 화면의 능력치 버튼),
## 위 가운데 안내 높이, 왼쪽 아래 ⚙ 손맛 버튼 자리, 스윙 버튼을 띄우는 높이
const MARGIN := Tokens.SPACE_SM
const LEFT_BELOW_Y := 96
const RIGHT_BELOW_Y := 112
const TOAST_Y := 72
const GEAR_CLEARANCE := 34
const SWING_LIFT := 12
## 스윙 종류 칩 크기·첫 칩 높이
const CHIP_SIZE := Vector2(84, 38)
const CHIPS_Y := 126

var session: BattingSession
var game: GameRunner = null
## 왼쪽 위 정보 칩에 보여 줄 글 (매 프레임 부른다, 비면 칩을 숨긴다)
var info_text: Callable = func() -> String: return ""
## 타자 자막에 보여 줄 값 (타석이 시작될 때 한 번 부른다): {"number", "name", "sub", "today", "season"}. 비면 자막을 띄우지 않는다
var batter_card: Callable = func() -> Dictionary: return {}
var show_menu := true

var _bug: ScoreBug
var _strip: PitcherStrip
var _speed: SpeedPanel
var _card: BatterCard
var _info: Label
var _toast: PanelContainer
var _toast_label: Label
var _toast_bar: ProgressBar
var _swing_buttons := {}
var _shown_type := ""
var _swing: SwingButton
## 자막을 띄운 타석, 그 타석이 시작된 때, 들어가기 시작하는 때 (타석 시작부터 ms)
var _card_at_bat: AtBat = null
var _card_since := 0.0
var _card_out_at := INF


func _init(p_session: BattingSession) -> void:
	session = p_session


func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	_build_score_bug()
	_build_right()
	_build_info()
	_build_swing_types()
	_build_swing_button()
	_build_card()
	_build_toast()


func _process(_delta: float) -> void:
	_refresh_bug()
	_info.text = info_text.call()
	_info.get_parent().visible = _info.text != ""
	_refresh_speed()
	_refresh_card()
	_refresh_toast()
	if _shown_type != session.swing_type:
		_refresh_swing_types()
	_swing.label = _swing_label()
	_swing.ring = session.select_progress()
	# 베이스 버튼(BasePad)이 뜨는 동안은 같은 자리의 스윙 버튼을 치운다
	_swing.visible = not (session.phase == BattingSession.Phase.HIT and session.run_control != null and not session.run_control.pending().is_empty())


# ---------- 왼쪽 위: 스코어 버그 · 투수 줄 ----------

func _build_score_bug() -> void:
	_bug = ScoreBug.new()
	add_child(_bug)
	_bug.position = Vector2(MARGIN, MARGIN)
	_strip = PitcherStrip.new()
	add_child(_strip)
	_strip.position = Vector2(MARGIN, MARGIN + ScoreBug.SIZE.y + Tokens.SPACE_XS + 2)


func _refresh_bug() -> void:
	var st: GameState = game.state if game != null else null
	if st != null:
		var mine := game.my_side
		for side in 2:
			_bug.rows[side] = [game.team_short(side), str(st.score[side]),
				Tokens.UNIFORM_US if side == mine else Tokens.UNIFORM_THEM, side == st.batting_side()]
		_bug.inning = str(st.inning)
		_bug.top = 1 if st.top else 0
		_bug.bases = st.bases.map(func(b: int) -> bool: return b != GameState.EMPTY)
		_bug.outs = st.outs
		var def := 1 - st.batting_side()
		var line: BoxScore.PitcherLine = game.box.pitchers[def][game.pitcher_index[def]]
		_strip.pitcher = game.current_pitcher().name
		_strip.team = game.team_short(def)
		_strip.pitches = line.pitches + session.at_bat.pitch_count
	else:
		_bug.rows[0] = ["상대", "", Tokens.UNIFORM_THEM, false]
		_bug.rows[1] = ["연습", "", Tokens.UNIFORM_US, true]
		_bug.inning = "-"
		_bug.top = -1
		_strip.pitcher = session.pitcher.name
		_strip.team = ""
		_strip.pitches = session.at_bat.pitch_count
	_bug.balls = mini(session.at_bat.balls, 3)
	_bug.strikes = mini(session.at_bat.strikes, 2)


func _build_info() -> void:
	var chip := UiKit.glass_panel(Tokens.RADIUS_BROADCAST)
	add_child(chip)
	chip.position = Vector2(MARGIN, LEFT_BELOW_Y)
	_info = UiKit.label("", Tokens.FONT_CAPTION, Tokens.HUD_TEXT, Tokens.FONT_BOLD)
	chip.add_child(_info)


# ---------- 오른쪽 위: 메뉴 · 구속 ----------

func _build_right() -> void:
	var y := MARGIN
	if show_menu:
		var menu := Button.new()
		menu.text = "Ⅱ"
		menu.focus_mode = Control.FOCUS_NONE
		for state in ["normal", "hover", "pressed"]:
			menu.add_theme_stylebox_override(state, UiKit.glass(Tokens.RADIUS_HUD))
		menu.add_theme_color_override("font_color", Tokens.HUD_TEXT)
		menu.add_theme_font_size_override("font_size", Tokens.FONT_CAPTION)
		menu.pressed.connect(menu_pressed.emit)
		add_child(menu)
		menu.set_anchors_and_offsets_preset(Control.PRESET_TOP_RIGHT, Control.PRESET_MODE_MINSIZE, MARGIN)
		menu.grow_horizontal = Control.GROW_DIRECTION_BEGIN
		y += 34
	_speed = SpeedPanel.new()
	add_child(_speed)
	_speed.set_anchors_and_offsets_preset(Control.PRESET_TOP_RIGHT, Control.PRESET_MODE_MINSIZE, MARGIN)
	_speed.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	_speed.position.y = y


func _refresh_speed() -> void:
	var p := session.pitch
	if p == null or session.phase == BattingSession.Phase.WINDUP:
		_speed.type_label = ""
		_speed.speed = ""
		return
	var flying := session.phase == BattingSession.Phase.FLIGHT
	var progress := session.since(session.phase_since_ms) / p.flight_ms if flying else INF
	# 구종은 선구안 시점이 지나야, 구속은 공이 지나간 뒤에
	var revealed := p.is_revealed_at(progress)
	_speed.type_label = p.label if revealed else ""
	_speed.type_color = Tokens.PITCH_COLORS.get(p.type, Tokens.HUD_TEXT) if revealed else Tokens.HUD_BORDER
	_speed.speed = str(p.speed_kmh) if progress >= 1.0 else ""


# ---------- 위 가운데: 스윙 고르는 시간 ----------

func _build_toast() -> void:
	_toast = UiKit.glass_panel(Tokens.RADIUS_BROADCAST)
	add_child(_toast)
	_toast.set_anchors_and_offsets_preset(Control.PRESET_CENTER_TOP, Control.PRESET_MODE_MINSIZE)
	_toast.position.y = TOAST_Y
	var col := UiKit.vbox(Tokens.SPACE_XS)
	col.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_toast.add_child(col)
	_toast_label = UiKit.label("스윙을 고르세요", Tokens.FONT_CAPTION, Tokens.HUD_TEXT, Tokens.FONT_BOLD)
	_toast_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	col.add_child(_toast_label)
	_toast_bar = ProgressBar.new()
	_toast_bar.show_percentage = false
	_toast_bar.max_value = 1.0
	_toast_bar.custom_minimum_size = Vector2(160, 4)
	var bg := StyleBoxFlat.new()
	bg.bg_color = Tokens.HUD_BORDER
	bg.set_corner_radius_all(2)
	var fill := StyleBoxFlat.new()
	fill.bg_color = Tokens.ACCENT
	fill.set_corner_radius_all(2)
	_toast_bar.add_theme_stylebox_override("background", bg)
	_toast_bar.add_theme_stylebox_override("fill", fill)
	col.add_child(_toast_bar)


func _refresh_toast() -> void:
	var p := session.select_progress()
	_toast.visible = p >= 0.0
	if p >= 0.0:
		_toast_bar.value = 1.0 - p
		_toast_label.text = "%s 스윙 · 바꾸려면 왼쪽" % session.config.swing_type(session.swing_type).label
		_toast.position.x = (size.x - _toast.size.x) / 2.0


# ---------- 왼쪽 아래: 타자 자막 ----------

func _build_card() -> void:
	_card = BatterCard.new()
	add_child(_card)
	_card.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_LEFT, Control.PRESET_MODE_MINSIZE, MARGIN)
	_card.grow_vertical = Control.GROW_DIRECTION_BEGIN
	_card.position.y -= GEAR_CLEARANCE
	_card.visible = false


## 타석이 바뀌면 자막을 다시 띄운다. 나와서 batterCardMs 동안 있다가 들어가고, 그 전에 첫 공을 던지면 바로 들어간다
func _refresh_card() -> void:
	var pr := session.presentation
	var t := BattingSession.now_ms() - _card_since
	if session.at_bat != _card_at_bat:
		_card_at_bat = session.at_bat
		_card_since = BattingSession.now_ms()
		_card_out_at = pr.batter_card_ms
		_card.data = batter_card.call()
		t = 0.0
	if session.phase != BattingSession.Phase.WAITING:
		_card_out_at = minf(_card_out_at, t)
	var slide := maxf(pr.batter_card_slide_ms, 1.0)
	var k_in := clampf(t / slide, 0.0, 1.0)
	var k_out := clampf((t - _card_out_at) / slide, 0.0, 1.0)
	# 나올 때는 빠르게 멈추고(ease out), 들어갈 때는 천천히 출발한다(ease in)
	var k := (1.0 - pow(1.0 - k_in, 3.0)) * (1.0 - pow(k_out, 3.0))
	_card.visible = not _card.data.is_empty() and k > 0.0
	_card.position.x = lerpf(-_card.size.x - Tokens.SPACE_XS, MARGIN, k)


# ---------- 왼쪽 · 오른쪽 아래: 조작 ----------

func _build_swing_types() -> void:
	# 왼쪽에 세로로: 왼손 엄지 자리, 타자 캐릭터 몸통을 가리지 않는다
	var col := UiKit.vbox(Tokens.SPACE_XS)
	add_child(col)
	col.position = Vector2(MARGIN, CHIPS_Y)
	for id: String in session.config.swing_types:
		var st: BattingConfig.SwingType = session.config.swing_types[id]
		var b := Button.new()
		b.text = st.label
		b.focus_mode = Control.FOCUS_NONE
		b.custom_minimum_size = CHIP_SIZE
		b.add_theme_font_override("font", Tokens.FONT_BOLD)
		b.add_theme_font_size_override("font_size", Tokens.FONT_CAPTION)
		b.pressed.connect(func() -> void: session.set_swing_type(id))
		col.add_child(b)
		_swing_buttons[id] = b
	_refresh_swing_types()


func _refresh_swing_types() -> void:
	_shown_type = session.swing_type
	for id: String in _swing_buttons:
		var b: Button = _swing_buttons[id]
		var on := id == session.swing_type
		var s := StyleBoxFlat.new()
		s.bg_color = Tokens.PRIMARY if on else Tokens.HUD_GLASS
		# 고른 칩은 왼쪽 주황 띠, 나머지는 얇은 테두리
		s.border_color = Tokens.ACCENT if on else Tokens.HUD_BORDER
		s.set_border_width_all(0 if on else 1)
		s.border_width_left = 4 if on else 1
		s.set_corner_radius_all(Tokens.RADIUS_BROADCAST)
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


# ---------- 그리는 부품 ----------

static func _text(ci: CanvasItem, pos: Vector2, text: String, font_size: int, color: Color, bold := true,
		align := HORIZONTAL_ALIGNMENT_LEFT, width := -1.0) -> float:
	var font: Font = Tokens.FONT_BOLD if bold else Tokens.FONT_REGULAR
	ci.draw_string(font, pos, text, align, width, font_size, color)
	return font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size).x


## 스코어 버그: [팀 두 줄 | 이닝 | 주자 | B·S·O]
class ScoreBug extends Control:
	const SIZE := Vector2(300, 56)
	## 칸 경계 (팀 | 이닝 | 주자 | 카운트)
	const COLS := [120.0, 160.0, 212.0]
	## [이름, 점수 글자, 팀 색, 공격 중인가] × (원정, 홈)
	var rows := [["", "", Color.WHITE, false], ["", "", Color.WHITE, false]]
	var inning := ""
	## 1 초, 0 말, -1 없음 (연습)
	var top := -1
	var bases := [false, false, false]
	var balls := 0
	var strikes := 0
	var outs := 0

	func _init() -> void:
		custom_minimum_size = SIZE
		size = SIZE
		mouse_filter = Control.MOUSE_FILTER_IGNORE

	func _process(_d: float) -> void:
		queue_redraw()

	func _draw() -> void:
		var r := float(Tokens.RADIUS_BROADCAST)
		var bg := StyleBoxFlat.new()
		bg.bg_color = Tokens.BROADCAST_BG
		bg.border_color = Tokens.HUD_BORDER
		bg.set_border_width_all(1)
		bg.set_corner_radius_all(int(r))
		bg.anti_aliasing = true
		draw_style_box(bg, Rect2(Vector2.ZERO, SIZE))
		var h := SIZE.y / 2.0
		for i in 2:
			var row: Array = rows[i]
			var y := h * i
			if row[3]:
				draw_rect(Rect2(6, y, COLS[0] - 6, h), Tokens.BROADCAST_ROW_HI)
			draw_rect(Rect2(0, y + (1 if i == 0 else 0), 6, h - 1), row[2])
			var w := BattingHud._text(self, Vector2(16, y + 19.5), row[0], Tokens.FONT_LABEL, Tokens.HUD_TEXT)
			if row[3]:
				var a := Vector2(16 + w + 7, y + h / 2.0)
				draw_colored_polygon(PackedVector2Array([a + Vector2(0, -4), a + Vector2(6, 0), a + Vector2(0, 4)]), Tokens.ACCENT)
			BattingHud._text(self, Vector2(0, y + 21), row[1], Tokens.FONT_TITLE - 4, Tokens.HUD_TEXT, true, HORIZONTAL_ALIGNMENT_RIGHT, COLS[0] - 10)
		for x: float in COLS:
			draw_line(Vector2(x, 6), Vector2(x, SIZE.y - 6), Tokens.HUD_BORDER, 1.0)
		# 이닝: 숫자 위아래 삼각형, 지금 쪽(초 ▲ / 말 ▼)만 주황
		var cx: float = (COLS[0] + COLS[1]) / 2.0
		BattingHud._text(self, Vector2(COLS[0], 36), inning, Tokens.FONT_TITLE, Tokens.HUD_TEXT, true, HORIZONTAL_ALIGNMENT_CENTER, COLS[1] - COLS[0])
		if top >= 0:
			var up := Tokens.ACCENT if top == 1 else Tokens.BROADCAST_EMPTY
			var down := Tokens.ACCENT if top == 0 else Tokens.BROADCAST_EMPTY
			draw_colored_polygon(PackedVector2Array([Vector2(cx, 9), Vector2(cx + 5, 15), Vector2(cx - 5, 15)]), up)
			draw_colored_polygon(PackedVector2Array([Vector2(cx, 50), Vector2(cx + 5, 44), Vector2(cx - 5, 44)]), down)
		# 주자 (1·2·3루)
		var c := Vector2((COLS[1] + COLS[2]) / 2.0, SIZE.y / 2.0 + 2)
		var g := 12.0
		var spots := [c + Vector2(g, 0), c + Vector2(0, -g), c + Vector2(-g, 0)]
		for i in 3:
			var p: Vector2 = spots[i]
			var d := 7.0
			draw_colored_polygon(PackedVector2Array([p + Vector2(0, -d), p + Vector2(d, 0), p + Vector2(0, d), p + Vector2(-d, 0)]),
				Tokens.COUNT_STRIKE if bases[i] else Tokens.BROADCAST_EMPTY)
		# B · S · O 세 줄
		var x0: float = COLS[2] + 10
		var groups := [["B", balls, 3, Tokens.COUNT_BALL], ["S", strikes, 2, Tokens.COUNT_STRIKE], ["O", outs, 2, Tokens.COUNT_OUT]]
		for i in 3:
			var gr: Array = groups[i]
			var y := 13.0 + 15.0 * i
			BattingHud._text(self, Vector2(x0, y + 4.5), gr[0], Tokens.FONT_CAPTION - 1, Tokens.HUD_TEXT_SOFT)
			for j in int(gr[2]):
				draw_circle(Vector2(x0 + 19 + 14 * j, y), 4.5, gr[3] if j < gr[1] else Tokens.BROADCAST_EMPTY, true, -1.0, true)


## 투수 줄: "P 이름 팀 … N구"
class PitcherStrip extends Control:
	const SIZE := Vector2(212, 20)
	var pitcher := ""
	var team := ""
	var pitches := 0

	func _init() -> void:
		custom_minimum_size = SIZE
		size = SIZE
		mouse_filter = Control.MOUSE_FILTER_IGNORE

	func _process(_d: float) -> void:
		queue_redraw()

	func _draw() -> void:
		var bg := StyleBoxFlat.new()
		bg.bg_color = Tokens.BROADCAST_BG
		bg.set_corner_radius_all(4)
		draw_style_box(bg, Rect2(Vector2.ZERO, SIZE))
		var base := 14.5
		var x := 9.0
		x += BattingHud._text(self, Vector2(x, base), "P", Tokens.FONT_CAPTION - 1, Tokens.ACCENT) + 6
		x += BattingHud._text(self, Vector2(x, base), pitcher, Tokens.FONT_CAPTION, Tokens.HUD_TEXT) + 6
		BattingHud._text(self, Vector2(x, base), team, Tokens.FONT_CAPTION - 1, Tokens.HUD_TEXT_SOFT, false)
		BattingHud._text(self, Vector2(0, base), "%d구" % pitches, Tokens.FONT_CAPTION, Tokens.HUD_TEXT, true, HORIZONTAL_ALIGNMENT_RIGHT, SIZE.x - 9)


## 구속 패널: 왼쪽 띠는 구종 색, 위에 구종, 아래 큰 구속 (모르면 ---)
class SpeedPanel extends Control:
	const SIZE := Vector2(128, 58)
	var type_label := ""
	var type_color := Tokens.HUD_BORDER
	var speed := ""

	func _init() -> void:
		custom_minimum_size = SIZE
		size = SIZE
		mouse_filter = Control.MOUSE_FILTER_IGNORE

	func _process(_d: float) -> void:
		queue_redraw()

	func _draw() -> void:
		var bg := StyleBoxFlat.new()
		bg.bg_color = Tokens.BROADCAST_BG
		bg.border_color = Tokens.HUD_BORDER
		bg.set_border_width_all(1)
		bg.set_corner_radius_all(Tokens.RADIUS_BROADCAST)
		bg.anti_aliasing = true
		draw_style_box(bg, Rect2(Vector2.ZERO, SIZE))
		draw_rect(Rect2(0, 0, 5, SIZE.y), type_color)
		BattingHud._text(self, Vector2(14, 18), type_label if type_label != "" else "구속", Tokens.FONT_CAPTION - 1,
			type_color if type_label != "" else Tokens.HUD_TEXT_SOFT)
		var w := BattingHud._text(self, Vector2(14, 49), speed if speed != "" else "---", Tokens.FONT_HERO - 4, Tokens.HUD_TEXT)
		BattingHud._text(self, Vector2(14 + w + 5, 49), "km/h", Tokens.FONT_CAPTION, Tokens.HUD_TEXT_SOFT, false)


## 타자 자막: [번호 블록 / 이름 · 소개 / 오늘 · 시즌 기록], 오른쪽 끝을 비스듬히
class BatterCard extends Control:
	const SIZE := Vector2(330, 59)
	## 번호 블록 너비, 비스듬한 끝이 밀리는 정도, 위 주황 줄 두께
	const BLOCK := 54.0
	const SLANT := 8.0
	const LINE := 3.0
	var data := {}

	func _init() -> void:
		custom_minimum_size = SIZE
		size = SIZE
		mouse_filter = Control.MOUSE_FILTER_IGNORE

	func _process(_d: float) -> void:
		queue_redraw()

	func _draw() -> void:
		if data.is_empty():
			return
		var w := SIZE.x
		var t := LINE
		var b := SIZE.y
		draw_colored_polygon(PackedVector2Array([Vector2(BLOCK, t), Vector2(w, t), Vector2(w - SLANT, b), Vector2(BLOCK - SLANT, b)]), Tokens.BROADCAST_BG)
		draw_colored_polygon(PackedVector2Array([Vector2(0, t), Vector2(BLOCK, t), Vector2(BLOCK - SLANT, b), Vector2(0, b)]), Tokens.UNIFORM_US)
		draw_rect(Rect2(0, 0, w, LINE), Tokens.ACCENT)
		var number: String = data.get("number", "")
		var big := number.length() <= 2
		BattingHud._text(self, Vector2(0, t + (40.0 if big else 34.0)), number, Tokens.FONT_HERO - 4 if big else Tokens.FONT_LABEL,
			Tokens.HUD_TEXT, true, HORIZONTAL_ALIGNMENT_CENTER, BLOCK - SLANT / 2.0)
		var x := BLOCK + 8.0
		var nw := BattingHud._text(self, Vector2(x, t + 23), data.get("name", ""), Tokens.FONT_TITLE - 4, Tokens.HUD_TEXT)
		BattingHud._text(self, Vector2(x + nw + 8, t + 22), data.get("sub", ""), Tokens.FONT_CAPTION - 1, Tokens.HUD_TEXT_SOFT, false)
		var tw := BattingHud._text(self, Vector2(x - 3, t + 45), data.get("today", ""), Tokens.FONT_CAPTION, Tokens.HUD_TEXT)
		BattingHud._text(self, Vector2(x - 3 + tw + 12, t + 45), data.get("season", ""), Tokens.FONT_CAPTION, Tokens.HUD_TEXT_SOFT, false)


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
