class_name TitleScreen
extends Control
## 로비: 화면 전체에 일러스트(art/backgrounds/lobby.png, 없으면 학교 운동장 임시 그림), 오른쪽 판에 로고(가제) + 번호 메뉴
## (이어하기 · 새 커리어 · 타격 연습 · 기록실 · 설정) + 내 선수 요약.
## 메뉴 구성은 유저가 준 시안(2026-10-07)을 따르고, 2026-10-21 에 도형 실루엣 대신 일러스트를 깔았다 (유저 결정).
## 판은 오른쪽에서 미끄러져 들어오고 메뉴 줄은 하나씩 나타난다.

## 로비 일러스트. 없으면 FALLBACK 을 쓴다
const ART := "res://art/backgrounds/lobby.png"
const FALLBACK := "res://art/backgrounds/field.png"
## 오른쪽 판이 시작하는 가로 자리 (화면 너비 비율) — 일러스트 왼쪽 60% 가량이 보인다
const PANEL_LEFT := 0.57
## 판 왼쪽 모서리를 비스듬히 자르는 폭 (위쪽이 이만큼 오른쪽으로 들어간다)
const PANEL_SLANT := 36.0

var _app


func _init(app) -> void:
	_app = app


func _ready() -> void:
	var title: Dictionary = _app.career_config.game_title
	var career: CareerState = _app.career
	var pres: Presentation = _app.presentation

	var path := ART if ResourceLoader.exists(ART) else FALLBACK
	if ResourceLoader.exists(path):
		var art := TextureRect.new()
		art.texture = load(path)
		art.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		art.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
		art.mouse_filter = Control.MOUSE_FILTER_IGNORE
		add_child(UiKit.fill_parent(art))

	# 오른쪽 판 (비스듬한 왼쪽 모서리)
	var panel := Control.new()
	panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(panel)
	panel.anchor_left = PANEL_LEFT
	panel.anchor_right = 1.0
	panel.anchor_bottom = 1.0
	panel.draw.connect(func() -> void:
		var r := panel.size
		panel.draw_colored_polygon(PackedVector2Array([Vector2(PANEL_SLANT, 0), Vector2(r.x, 0), r, Vector2(0, r.y)]), Tokens.LOBBY_PANEL))

	var col := UiKit.vbox(0)
	var pad := UiKit.margin(col, Tokens.SPACE_LG)
	pad.add_theme_constant_override("margin_left", int(PANEL_SLANT) + Tokens.SPACE_MD)
	panel.add_child(UiKit.fill_parent(pad))
	var fade_in: Array[Control] = []

	col.add_child(UiKit.kicker(title["kicker"], Tokens.ACCENT))
	var logo := Shapes.SlantText.new(title["ko"], Tokens.FONT_DISPLAY)
	logo.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	col.add_child(logo)
	col.add_child(UiKit.kicker(title["en"], Tokens.INK_SOFT, Tokens.FONT_BODY, Tokens.TRACKING_WIDE))
	var gap := Control.new()
	gap.custom_minimum_size.y = Tokens.SPACE_SM
	col.add_child(gap)

	var resume := "저장된 커리어 없음"
	if career != null:
		resume = ("[테스트] " if _app.test_mode else "") + SaveGame.summary(career)
	elif _app.save_error != "":
		resume = "불러오지 못함: " + _app.save_error
	var items := [
		["이어하기", _app.continue_career if career != null else Callable(), resume],
		["새 커리어", _app.show_create, ""],
		["타격 연습", _app.show_practice, ""],
		["기록실", _app.show_records if career != null else Callable(), "커리어를 시작하면 열려요"],
		["설정", _app.show_settings, ""],
	]
	# 강조는 할 수 있는 첫 항목 (커리어가 있으면 이어하기, 없으면 새 커리어)
	var highlighted := 0 if career != null else 1
	for i in items.size():
		var it: Array = items[i]
		var item := _menu_item(i + 1, it[0], it[1], it[2], i == highlighted)
		col.add_child(item)
		fade_in.append(item)
	col.add_child(UiKit.spacer())
	# 아래: 내 선수 (커리어가 있을 때) · 버전
	var foot := UiKit.hbox(Tokens.SPACE_SM)
	col.add_child(foot)
	foot.add_child(UiKit.label(title["version"], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	foot.add_child(UiKit.spacer())
	if career != null:
		var p := career.player
		var who := UiKit.label("%s · %s %d학년" % [p.name, career.my_school.name, p.grade], Tokens.FONT_CAPTION, Tokens.INK, Tokens.FONT_BOLD)
		who.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
		foot.add_child(who)

	# 개발 빌드: 원하는 시점부터 시작하는 테스트 (진짜 저장은 건드리지 않는다) — 왼쪽 위 일러스트 위
	if OS.is_debug_build():
		var dev := UiKit.vbox(Tokens.SPACE_XS)
		add_child(dev)
		dev.position = Vector2(Tokens.SPACE_SM, Tokens.SPACE_SM)
		for b: Array in [["테스트 시작 ›", _app.show_jump, Tokens.INK_SOFT], ["주루 연습 ›", _app.show_run_practice, Tokens.INK_SOFT], ["체력 테스트 ›", _app.show_fitness_practice, Tokens.INK_SOFT], ["수비 연습 ›", _app.show_field_practice, Tokens.INK_SOFT]] \
				+ ([["테스트 끝내기 (내 저장으로)", _app.end_test, Tokens.BAD]] if _app.test_mode else []):
			var button := UiKit.button(b[0], b[1], b[2], Tokens.FONT_CAPTION)
			button.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
			dev.add_child(button)

	_slide_in(panel, fade_in, pres)


## 판은 오른쪽 밖에서 미끄러져 들어오고(빠르게 출발해 천천히 멈춤), 메뉴 줄은 위에서부터 하나씩 나타난다
func _slide_in(panel: Control, rows: Array[Control], pres: Presentation) -> void:
	var away := get_viewport_rect().size.x * (1.0 - PANEL_LEFT)
	panel.offset_left = away
	panel.offset_right = away
	var slide := pres.lobby_menu_slide_ms / 1000.0
	var tween := create_tween().set_parallel().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
	tween.tween_property(panel, "offset_left", 0.0, slide)
	tween.tween_property(panel, "offset_right", 0.0, slide)
	for i in rows.size():
		rows[i].modulate.a = 0.0
		tween.tween_property(rows[i], "modulate:a", 1.0, slide * 0.6).set_delay(slide * 0.5 + i * pres.lobby_menu_stagger_ms / 1000.0)


## 메뉴 한 줄: "01  이어하기". 강조된 항목은 비스듬한 막대 위에 흰 글자 + 아래 한 줄 설명.
## on_press 가 비어 있으면 아직 없는 기능 (흐리게, 이름 옆에 "준비 중")
func _menu_item(n: int, text: String, on_press: Callable, caption: String, highlighted: bool) -> Control:
	var enabled := on_press.is_valid()
	var ink := Tokens.SURFACE if highlighted else (Tokens.INK if enabled else Color(Tokens.INK_SOFT, 0.6))
	var line := UiKit.hbox(Tokens.SPACE_SM)
	line.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	var num := UiKit.label("%02d" % n, Tokens.FONT_CAPTION, ink, Tokens.FONT_BOLD)
	num.custom_minimum_size.x = 22
	line.add_child(num)
	line.add_child(UiKit.label(text, Tokens.FONT_TITLE if highlighted else Tokens.FONT_BODY + 3, ink, Tokens.FONT_BOLD if highlighted else Tokens.FONT_REGULAR))
	if not enabled and caption != "":
		line.add_child(UiKit.label(caption, Tokens.FONT_CAPTION, Color(Tokens.INK_SOFT, 0.6)))
	var pad := UiKit.margin(line, 0)
	pad.add_theme_constant_override("margin_left", Tokens.SPACE_MD)

	var col := UiKit.vbox(0)
	if highlighted:
		# 막대와 글자를 겹친다 (PanelContainer 는 자식을 모두 같은 칸에 겹쳐 놓는다)
		var stack := _overlay()
		stack.custom_minimum_size = Vector2(280, 34)
		stack.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
		stack.add_child(Shapes.SlantBar.new())
		stack.add_child(pad)
		col.add_child(stack)
		if caption != "":
			var cap := UiKit.margin(UiKit.label(caption, Tokens.FONT_CAPTION, Tokens.INK_SOFT), 0)
			cap.add_theme_constant_override("margin_left", Tokens.SPACE_MD + 30)
			cap.add_theme_constant_override("margin_top", 2)
			cap.add_theme_constant_override("margin_bottom", Tokens.SPACE_XS)
			col.add_child(cap)
	else:
		pad.add_theme_constant_override("margin_top", 3)
		pad.add_theme_constant_override("margin_bottom", 3)
		col.add_child(pad)
	if not enabled:
		return col
	# 줄 전체를 누르는 투명 버튼을 겹친다
	var item := _overlay()
	item.add_child(col)
	var hit := Button.new()
	hit.flat = true
	hit.focus_mode = Control.FOCUS_NONE
	hit.pressed.connect(on_press)
	item.add_child(hit)
	return item


## 자식들을 같은 칸에 겹쳐 놓는 투명 상자
func _overlay() -> PanelContainer:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", StyleBoxEmpty.new())
	return p
