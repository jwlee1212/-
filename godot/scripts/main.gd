extends Control
## 앱 루트: 수치·콘텐츠를 읽고 화면을 바꿔 끼운다.
##
## 화면 흐름: 타이틀 → 선수 만들기 → [홈(훈련) → 경기 → 결과·이벤트] × 시즌 주차 → 시즌 결산
## 타이틀의 "타격 연습"은 능력치 슬라이더가 있는 연습 화면 (손맛 튜닝용).
## 개발 빌드면 어느 화면에서나 왼쪽 아래 "⚙ 손맛" 패널을 쓸 수 있다 (CLAUDE.md §3-7).

var batting_config: BattingConfig
var presentation: Presentation
var career_config: CareerConfig
var sound: SoundPlayer
var career: CareerState

var _source: BalanceSource
var _screen_root: Control
var _current: Control
var _tuning: Tuning
var _panel: TuningPanel
var _rotate_hint: Control
var _fps_label: Label


func _ready() -> void:
	theme = _make_theme()
	var bg := ColorRect.new()
	bg.color = Tokens.BACKGROUND
	bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(UiKit.fill_parent(bg))
	_screen_root = UiKit.fill_parent(Control.new())
	add_child(_screen_root)
	_source = BalanceSource.new()
	add_child(_source)
	sound = SoundPlayer.new()
	add_child(sound)
	await _load_configs()
	if OS.is_debug_build():
		_add_tuning_panel()
		_add_fps()
	_add_rotate_hint()
	show_title()


func _process(_delta: float) -> void:
	# 세로로 들고 있으면 가로로 돌리라는 안내
	if _rotate_hint != null:
		_rotate_hint.visible = size.x < size.y
	if _fps_label != null:
		_fps_label.text = "%d FPS" % Engine.get_frames_per_second()


# ---------- 화면 전환 ----------

func show_screen(screen: Control) -> void:
	if _current != null:
		_current.queue_free()
	_current = screen
	_screen_root.add_child(UiKit.fill_parent(screen))


func show_title() -> void:
	show_screen(TitleScreen.new(self))


func show_create() -> void:
	show_screen(CreateScreen.new(self))


func start_career(player_name: String) -> void:
	career = CareerState.new(career_config, player_name, batting_config)
	show_home()


func show_home() -> void:
	show_screen(HomeScreen.new(self))


func show_game() -> void:
	show_screen(GameScreen.new(self))


func show_result(game: GameRunner) -> void:
	show_screen(ResultScreen.new(self, game))


func show_season() -> void:
	show_screen(SeasonScreen.new(self))


func show_practice() -> void:
	show_screen(PracticeScreen.new(self))


## 새 타격 세션 (설정은 앱이 들고 있는 것을 같이 쓴다 → 손맛 패널 조절이 바로 반영)
func new_batting_session() -> BattingSession:
	var s := BattingSession.new(batting_config, presentation)
	s.sound_requested.connect(sound.play)
	return s


# ---------- 수치 ----------

func _load_configs() -> void:
	var balance: Dictionary = await _source.fetch("balance.json")
	var content: Dictionary = await _source.fetch("content.json")
	batting_config = BattingConfig.from_balance(balance)
	presentation = Presentation.from_balance(balance)
	career_config = CareerConfig.from(balance, content)
	if career != null:
		career.cfg = career_config
		career.batting = batting_config


func _add_tuning_panel() -> void:
	_panel = TuningPanel.new()
	add_child(_panel)
	_reset_tuning()
	_panel.changed.connect(func(id: String, v: float) -> void:
		_tuning.set_value(id, v, batting_config, presentation))
	_panel.reset_requested.connect(func() -> void:
		_tuning.reset(batting_config, presentation)
		_panel.show_values(_tuning.values)
		_panel.set_status("처음 값으로 되돌림"))
	_panel.save_requested.connect(_save_tuning)
	_panel.reload_requested.connect(_reload)


func _reset_tuning() -> void:
	_tuning = Tuning.new(batting_config, presentation)
	_panel.show_values(_tuning.values)
	_panel.set_status("수치 출처: %s" % _source.last_origin)


func _save_tuning() -> void:
	var patch := _tuning.to_patch(batting_config, presentation)
	if patch.is_empty():
		_panel.set_status("바뀐 값이 없어요")
		return
	_panel.set_status("저장 중…")
	var error: String = await _source.save(patch)
	if error == "":
		await _reload()
		_panel.set_status("저장했어요 (%d개 항목) → config/balance.json" % patch.size())
	else:
		_panel.set_status("저장 실패: " + error)


## 수치를 다시 읽는다. 진행 중인 화면이 있으면 새 설정으로 바꿔 끼운다
func _reload() -> void:
	await _load_configs()
	_reset_tuning()
	if _current != null and _current.has_method("on_config_reloaded"):
		_current.on_config_reloaded()


# ---------- 공통 겹침 ----------

func _add_fps() -> void:
	_fps_label = UiKit.label("", Tokens.FONT_CAPTION, Tokens.CHALK)
	var bg := PanelContainer.new()
	var style := StyleBoxFlat.new()
	style.bg_color = Tokens.SCRIM
	style.set_corner_radius_all(Tokens.SPACE_XS)
	style.content_margin_left = Tokens.SPACE_XS
	style.content_margin_right = Tokens.SPACE_XS
	bg.add_theme_stylebox_override("panel", style)
	bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	bg.add_child(_fps_label)
	add_child(bg)
	bg.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_RIGHT, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_XS)
	bg.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	bg.grow_vertical = Control.GROW_DIRECTION_BEGIN


func _add_rotate_hint() -> void:
	var cover := ColorRect.new()
	cover.color = Tokens.INK
	_rotate_hint = UiKit.fill_parent(cover)
	var text := UiKit.title("폰을 가로로 돌려 주세요", Tokens.FONT_TITLE, Tokens.SURFACE)
	text.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	text.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	cover.add_child(UiKit.fill_parent(text))
	add_child(_rotate_hint)
	_rotate_hint.visible = false


## Godot 기본 테마는 어두운 회색이라 슬라이더·버튼을 토큰 색으로 바꾼다
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
	return t
