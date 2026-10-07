class_name TitleScreen
extends Control
## 로비: 로고(가제) + 번호 메뉴 (이어하기 · 새 커리어 · 타격 연습 · 기록실 · 설정) + 오른쪽 타자 실루엣과 내 선수 요약.
## 레이아웃은 유저가 준 시안(2026-10-07)을 따르고 색은 밝은 톤 토큰 그대로

var _app


func _init(app) -> void:
	_app = app


func _ready() -> void:
	var title: Dictionary = _app.career_config.game_title
	var career: CareerState = _app.career

	# 오른쪽 위 타자 실루엣 (메뉴 뒤에 깔린다)
	var art := Shapes.BatterSilhouette.new()
	art.number = career.player.lineup_slot if career != null else 7
	art.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(art)
	art.anchor_left = 0.55
	art.anchor_right = 0.8
	art.anchor_top = 0.04
	art.anchor_bottom = 0.6

	var row := UiKit.hbox(Tokens.SPACE_MD)
	add_child(UiKit.fill_parent(UiKit.margin(row, Tokens.SPACE_LG)))

	# 왼쪽: 로고 + 메뉴
	var left := UiKit.vbox(0)
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(left)
	left.add_child(UiKit.kicker(title["kicker"], Tokens.ACCENT))
	var logo := Shapes.SlantText.new(title["ko"], Tokens.FONT_DISPLAY)
	logo.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	left.add_child(logo)
	left.add_child(UiKit.kicker(title["en"], Tokens.INK_SOFT, Tokens.FONT_BODY, Tokens.TRACKING_WIDE))
	var gap := Control.new()
	gap.custom_minimum_size.y = Tokens.SPACE_MD
	left.add_child(gap)

	var resume := "저장 기능 준비 중"
	if career != null:
		resume = "%s · %d학년 · %s %s" % [career.player.name, career.player.grade, career.phase_name(), career.round_text()]
	var items := [
		["이어하기", _app.show_home if career != null else Callable(), resume],
		["새 커리어", _app.show_create, ""],
		["타격 연습", _app.show_practice, ""],
		["기록실", _app.show_records if career != null else Callable(), "커리어를 시작하면 열려요"],
		["설정", Callable(), "준비 중"],
	]
	# 강조는 할 수 있는 첫 항목 (커리어가 있으면 이어하기, 없으면 새 커리어)
	var highlighted := 0 if career != null else 1
	for i in items.size():
		var it: Array = items[i]
		left.add_child(_menu_item(i + 1, it[0], it[1], it[2], i == highlighted))
	left.add_child(UiKit.spacer())
	left.add_child(UiKit.label(title["version"], Tokens.FONT_CAPTION, Tokens.INK_SOFT))

	# 오른쪽 아래: 내 선수 (커리어가 있을 때)
	var right := UiKit.vbox(0)
	right.size_flags_horizontal = Control.SIZE_SHRINK_END
	row.add_child(right)
	right.add_child(UiKit.spacer())
	if career != null:
		var p := career.player
		for l: Label in [UiKit.kicker("PLAYER"), UiKit.title(p.name, Tokens.FONT_HERO),
				UiKit.label("외야수 · %s %d학년" % [career.my_school.name, p.grade], Tokens.FONT_BODY, Tokens.INK_SOFT)]:
			l.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
			right.add_child(l)


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
		stack.custom_minimum_size = Vector2(320, 34)
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
