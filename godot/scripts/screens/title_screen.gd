class_name TitleScreen
extends Control
## 게임을 열면 보이는 시작 메뉴. 메뉴 판과 개발용 버튼은 같은 4px 그리드를 쓴다.

const ART := "res://art/backgrounds/start_menu.png"
const FALLBACK := "res://art/backgrounds/field.png"
const MENU_ICONS := [
	preload("res://art/ui/start_menu/continue.svg"),
	preload("res://art/ui/start_menu/career.svg"),
	preload("res://art/ui/start_menu/bat.svg"),
	preload("res://art/ui/start_menu/records.svg"),
	preload("res://art/ui/start_menu/settings.svg"),
]
const BASEBALL := preload("res://art/ui/start_menu/baseball.svg")

var _app


func _init(app) -> void:
	_app = app


func _ready() -> void:
	var title: Dictionary = _app.career_config.game_title
	var career: CareerState = _app.career
	_add_art()

	var panel := PanelContainer.new()
	var panel_style := _box(Tokens.LOBBY_PANEL, Tokens.SURFACE, Tokens.RADIUS_PANEL)
	panel_style.shadow_color = Tokens.START_MENU_PANEL_SHADOW
	panel_style.shadow_size = Tokens.SPACE_SM
	panel_style.shadow_offset.y = Tokens.SPACE_XS
	panel.add_theme_stylebox_override("panel", panel_style)
	var layout := UiKit.hbox(Tokens.SPACE_MD)
	layout.add_child(UiKit.spacer())
	panel.custom_minimum_size = Tokens.START_MENU_SIZE
	panel.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	layout.add_child(panel)
	var inset := UiKit.margin(layout, Tokens.SPACE_SM)
	inset.add_theme_constant_override("margin_top", Tokens.SPACE_XS)
	inset.add_theme_constant_override("margin_bottom", Tokens.SPACE_XS)
	add_child(UiKit.fill_parent(inset))

	var col := UiKit.vbox(Tokens.SPACE_XS)
	panel.add_child(UiKit.margin(col, Tokens.START_MENU_PAD))
	col.add_child(_heading(title))

	var resume := "저장된 커리어 없음"
	if career != null:
		resume = ("[테스트] " if _app.test_mode else "") + SaveGame.summary(career)
	elif _app.save_error != "":
		resume = "불러오지 못함: " + _app.save_error

	var items := [
		["이어하기", _app.continue_career if career != null else Callable(), resume],
		["새 커리어", _app.show_create, ""],
		["타격 연습", _app.show_practice, ""],
		["기록실", _app.show_records if career != null else Callable(), "커리어를 시작하면 열려요" if career == null else ""],
		["설정", _app.show_settings, ""],
	]
	var highlighted := 0 if career != null else 1
	var rows: Array[Button] = []
	for i in items.size():
		var item: Array = items[i]
		var color := Tokens.START_MENU_ACCENT if i == highlighted else (Tokens.START_MENU_PRACTICE if i == 2 else Tokens.SURFACE)
		var row := _menu_item(i + 1, item[0], item[1], item[2], color)
		col.add_child(row)
		rows.append(row)

	var spacer := Control.new()
	spacer.size_flags_vertical = Control.SIZE_EXPAND_FILL
	spacer.mouse_filter = Control.MOUSE_FILTER_IGNORE
	col.add_child(spacer)
	var foot := UiKit.hbox(Tokens.SPACE_SM)
	col.add_child(foot)
	foot.add_child(UiKit.label(title["version"], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	foot.add_child(UiKit.spacer())
	if career != null:
		var p := career.player
		var who := UiKit.label("%s · %s %d학년" % [p.name, career.my_school.name, p.grade], Tokens.FONT_CAPTION, Tokens.INK, Tokens.FONT_BOLD)
		who.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		who.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
		who.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		foot.add_child(who)

	if OS.is_debug_build():
		_add_dev_buttons()
	_fade_rows(rows, _app.presentation)


func _add_art() -> void:
	var path := ART if ResourceLoader.exists(ART) else FALLBACK
	if ResourceLoader.exists(path):
		var art := TextureRect.new()
		art.texture = load(path)
		art.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		art.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
		art.mouse_filter = Control.MOUSE_FILTER_IGNORE
		add_child(UiKit.fill_parent(art))


func _heading(title: Dictionary) -> Control:
	var col := UiKit.vbox(0)
	var kicker := UiKit.label(title["kicker"], Tokens.FONT_CAPTION, Tokens.NAVY_SOFT, Tokens.FONT_BOLD)
	var english := UiKit.label(title["en"], Tokens.FONT_CAPTION, Tokens.START_MENU_PRACTICE, Tokens.spaced(Tokens.FONT_BOLD, Tokens.START_MENU_TRACKING))
	for label: Label in [kicker, english]:
		label.custom_minimum_size.y = Tokens.START_MENU_CAPTION_HEIGHT
		label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
		label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	col.add_child(kicker)

	var mark := UiKit.hbox(Tokens.SPACE_XS)
	mark.alignment = BoxContainer.ALIGNMENT_CENTER
	mark.custom_minimum_size.y = Tokens.START_MENU_LOGO_HEIGHT
	var logo := Shapes.SlantText.new(title["ko"], Tokens.START_MENU_LOGO_FONT, Tokens.NAVY)
	mark.add_child(logo)
	var ball := _icon(BASEBALL, Tokens.START_MENU_PRACTICE)
	mark.add_child(ball)
	col.add_child(mark)
	var signoff := UiKit.hbox(Tokens.SPACE_SM)
	signoff.alignment = BoxContainer.ALIGNMENT_CENTER
	signoff.add_child(_logo_rule())
	signoff.add_child(english)
	signoff.add_child(_logo_rule())
	col.add_child(signoff)
	return col


func _logo_rule() -> ColorRect:
	var rule := ColorRect.new()
	rule.color = Tokens.START_MENU_PRACTICE
	rule.custom_minimum_size = Tokens.START_MENU_RULE_SIZE
	rule.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	rule.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return rule


func _icon(texture: Texture2D, ink: Color) -> TextureRect:
	var icon := TextureRect.new()
	icon.texture = texture
	icon.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	icon.custom_minimum_size = Tokens.START_MENU_ICON_SIZE
	icon.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	icon.mouse_filter = Control.MOUSE_FILTER_IGNORE
	icon.self_modulate = ink
	return icon


## 번호, 아이콘, 이름, 상태, 화살표는 버튼 종류와 상관없이 같은 열을 사용한다.
func _menu_item(n: int, label: String, on_press: Callable, caption: String, color: Color) -> Button:
	var enabled := on_press.is_valid()
	var solid := color != Tokens.SURFACE and enabled
	var ink := Tokens.SURFACE if solid else (Tokens.NAVY if enabled else Tokens.START_MENU_DISABLED_INK)
	var bg := color if enabled else Tokens.START_MENU_DISABLED
	var edge := Tokens.NAVY if enabled and not solid else bg
	var button := Button.new()
	button.disabled = not enabled
	button.accessibility_name = label
	button.tooltip_text = caption
	button.custom_minimum_size.y = Tokens.TOUCH_MIN
	var normal := _box(bg, edge, Tokens.START_MENU_ROW_RADIUS)
	if solid:
		normal.border_color = bg.darkened(0.25)
		normal.border_width_bottom = Tokens.START_MENU_BUTTON_DEPTH
	button.add_theme_stylebox_override("normal", normal)
	button.add_theme_stylebox_override("disabled", normal)
	var hover := normal.duplicate() as StyleBoxFlat
	hover.bg_color = bg.lightened(0.08)
	button.add_theme_stylebox_override("hover", hover)
	button.add_theme_stylebox_override("pressed", _box(bg.darkened(0.14), edge, Tokens.START_MENU_ROW_RADIUS))
	var focus := _box(Tokens.START_MENU_CLEAR, Tokens.NAVY, Tokens.START_MENU_ROW_RADIUS)
	focus.set_border_width_all(Tokens.START_MENU_FOCUS_BORDER)
	button.add_theme_stylebox_override("focus", focus)
	if enabled:
		button.pressed.connect(on_press)

	var line := UiKit.hbox(Tokens.SPACE_SM)
	var number := UiKit.label("%02d" % n, Tokens.FONT_BODY, ink, Tokens.FONT_BOLD)
	number.custom_minimum_size.x = Tokens.SPACE_XL
	number.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	line.add_child(number)
	line.add_child(_icon(MENU_ICONS[n - 1], ink))
	line.add_child(UiKit.label(label, Tokens.FONT_TITLE, ink, Tokens.FONT_BOLD))
	var state := UiKit.label(caption, Tokens.FONT_CAPTION, ink)
	state.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	state.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	state.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	line.add_child(state)
	var arrow := UiKit.label("›" if enabled else "", Tokens.FONT_TITLE, ink, Tokens.FONT_BOLD)
	arrow.custom_minimum_size.x = Tokens.SPACE_MD
	arrow.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	line.add_child(arrow)
	for part: Control in line.get_children():
		if part is Label:
			part.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	var pad := UiKit.margin(line, 0)
	pad.add_theme_constant_override("margin_left", Tokens.SPACE_SM)
	pad.add_theme_constant_override("margin_right", Tokens.SPACE_SM)
	pad.mouse_filter = Control.MOUSE_FILTER_IGNORE
	line.mouse_filter = Control.MOUSE_FILTER_IGNORE
	button.add_child(UiKit.fill_parent(pad))
	return button


func _add_dev_buttons() -> void:
	var dev := UiKit.vbox(Tokens.SPACE_XS)
	var grid := GridContainer.new()
	grid.columns = 2
	grid.add_theme_constant_override("h_separation", Tokens.SPACE_XS)
	grid.add_theme_constant_override("v_separation", Tokens.SPACE_XS)
	dev.add_child(grid)
	for item: Array in [["테스트 시작 ›", _app.show_jump], ["주루 연습 ›", _app.show_run_practice], ["체력 테스트 ›", _app.show_fitness_practice], ["수비 연습 ›", _app.show_field_practice]]:
		var button := UiKit.button(item[0], item[1], Tokens.NAVY, Tokens.FONT_CAPTION)
		button.custom_minimum_size = Tokens.START_MENU_DEV_SIZE
		grid.add_child(button)
	if _app.test_mode:
		dev.add_child(UiKit.button("테스트 끝내기 (내 저장으로)", _app.end_test, Tokens.BAD, Tokens.FONT_CAPTION))
	var bottom := VBoxContainer.new()
	var space := Control.new()
	space.mouse_filter = Control.MOUSE_FILTER_IGNORE
	space.size_flags_vertical = Control.SIZE_EXPAND_FILL
	bottom.add_child(space)
	dev.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	bottom.add_child(dev)
	var inset := UiKit.margin(bottom, Tokens.SPACE_SM)
	inset.add_theme_constant_override("margin_bottom", Tokens.SPACE_XL)
	inset.mouse_filter = Control.MOUSE_FILTER_IGNORE
	bottom.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(UiKit.fill_parent(inset))


func _fade_rows(rows: Array[Button], pres: Presentation) -> void:
	var duration := pres.lobby_menu_slide_ms / 1000.0
	var tween := create_tween().set_parallel()
	for i in rows.size():
		rows[i].modulate.a = 0.0
		tween.tween_property(rows[i], "modulate:a", 1.0, duration).set_delay(i * pres.lobby_menu_stagger_ms / 1000.0)


func _box(color: Color, border: Color, radius: int) -> StyleBoxFlat:
	var style := StyleBoxFlat.new()
	style.bg_color = color
	style.border_color = border
	style.set_border_width_all(Tokens.START_MENU_BORDER)
	style.set_corner_radius_all(radius)
	style.set_content_margin_all(0)
	return style
