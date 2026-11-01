extends Control
## 앱 루트: 수치·콘텐츠를 읽고 화면을 바꿔 끼운다.
##
## 화면 흐름: 타이틀 → 선수 만들기 → [홈(훈련) → 경기 → 결과·이벤트] × 시즌 주차 → 시즌 결산
##   → 겨울 캠프 [홈(훈련) → (연습경기) → 이벤트] × 4주 → 캠프 결산 → 다음 시즌
## 타이틀의 "타격 연습"은 능력치 슬라이더가 있는 연습 화면 (손맛 튜닝용).
## 개발 빌드면 어느 화면에서나 왼쪽 아래 "⚙ 손맛" 패널을 쓸 수 있다 (CLAUDE.md §3-7).

var batting_config: BattingConfig
var presentation: Presentation
var career_config: CareerConfig
var sound: SoundPlayer
var music: MusicPlayer
var career: CareerState
## 유저 설정 (주루 설정)
var settings := Settings.new()
## 저장을 못 읽은 이유 (로비에 보여 준다, "" 이면 문제없음)
var save_error := ""
## 저장 파일 (스크린샷 스크립트는 다른 파일을 써서 유저 저장을 건드리지 않는다)
var save_path := SaveGame.PATH

## 테스트 커리어 저장 파일 (개발 빌드의 "테스트 시작" — 유저의 진짜 저장과 따로 둔다)
const TEST_SAVE := "user://test.save"
## 지금 테스트 커리어를 하고 있나
var test_mode := false

var _source: BalanceSource
var _screen_root: Control
var _current: Control
var _tuning: Tuning
var _panel: TuningPanel
var _rotate_hint: Control
var _fps_label: Label


func _ready() -> void:
	theme = ThemeBuilder.build()
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
	music = MusicPlayer.new()
	add_child(music)
	await _load_configs()
	_load_save()
	settings = Settings.load_from()
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
	music.play(_track_for(screen))
	_screen_root.add_child(UiKit.fill_parent(screen))


## 화면별 배경음악: 로비(타이틀·선수 만들기) · 경기(경기·연습) · 그 밖은 홈
static func _track_for(screen: Control) -> String:
	if screen is TitleScreen or screen is CreateScreen:
		return "lobby"
	if screen is GameScreen or screen is PracticeScreen or screen is RunPracticeScreen:
		return "game"
	return "home"


func show_title() -> void:
	show_screen(TitleScreen.new(self))


func show_create() -> void:
	show_screen(CreateScreen.new(self))


func start_career(player_name: String) -> void:
	career = CareerState.new(career_config, player_name, batting_config)
	save_career()
	show_home()


## 이어하기: 시즌이 끝났으면 시즌 결산, 아니면 허브 (경기를 마친 주면 허브가 남은 이야기와 [다음 주]를 보여 준다)
func continue_career() -> void:
	_show_current()


## 지금 커리어 상태에 맞는 화면: 캠프 중이면 허브, 캠프를 마쳤으면 캠프 결산, 시즌이 끝났으면 시즌 결산
func _show_current() -> void:
	if career.in_camp():
		show_home()
	elif career.camp_finished():
		show_camp_end()
	elif career.is_season_over():
		show_season()
	else:
		show_home()


## 경기 뒤 다음 주로 (결과 화면·허브의 [다음 주])
func next_week() -> void:
	career.advance_week()
	_show_current()


func show_home() -> void:
	show_screen(HomeScreen.new(self))


func show_game() -> void:
	if career.game_done:
		show_home()  # 이번 주 경기는 이미 했다 (불러온 뒤 같은 경기를 다시 하지 않는다)
		return
	if career.next_role() == "bench":
		# 벤치에서 대타로도 안 나가는 날: 지켜볼 타석이 없으니 경기를 건너뛰고 결과만 본다
		var game := career.new_game()
		game.run_to_end()
		show_result(game)
		return
	show_screen(GameScreen.new(self))


func show_result(game: GameRunner) -> void:
	show_screen(ResultScreen.new(self, game))


func show_season() -> void:
	show_screen(SeasonScreen.new(self))


func show_camp_end() -> void:
	show_screen(CampScreen.new(self))


# ---------- 테스트 시작 (개발 빌드) ----------

func show_jump() -> void:
	show_screen(JumpScreen.new(self))


## 빨리 감은 테스트 커리어로 시작한다. 저장은 TEST_SAVE 로 가서 진짜 저장을 덮지 않는다
func start_test_career(test_career: CareerState) -> void:
	if not test_mode:
		save_path = TEST_SAVE
		test_mode = true
	career = test_career
	save_career()
	_show_current()


## 테스트를 끝내고 진짜 저장으로 돌아간다
func end_test() -> void:
	SaveGame.delete(TEST_SAVE)
	save_path = SaveGame.PATH
	test_mode = false
	career = null
	save_error = ""
	_load_save()
	show_title()


func show_records() -> void:
	show_screen(RecordsScreen.new(self))


func show_practice() -> void:
	show_screen(PracticeScreen.new(self))


## 설정 (back: [돌아가기]로 갈 화면, 없으면 로비)
func show_settings(back: Callable = Callable()) -> void:
	show_screen(SettingsScreen.new(self, back if back.is_valid() else show_title))


## 체력 테스트 연습 (개발 빌드): 입부 첫날 미니게임 세 종목을 보상 없이 (폰에서 손맛 맞추기용)
func show_fitness_practice() -> void:
	var screen := Control.new()
	var view := FitnessView.new(career_config, null, sound)
	screen.add_child(UiKit.fill_parent(view))
	view.finished.connect(show_title)
	show_screen(screen)


## 수비 연습 (개발 빌드): 유격수 포구·송구만 되풀이 (끝날 때마다 새 타구, 타자 주력 50, 경기·저장과 상관없다).
## 내 수비 능력치는 커리어가 있으면 그 선수 것, 없으면 50
func show_field_practice(round_no: int = 0) -> void:
	var screen := Control.new()
	var fielding: int = career.player.fielding_for_game(career_config) if career != null else 50
	var view := FieldView.new(career_config.fielding, batting_config.ball_physics, hash("field_practice%d" % round_no), "연습 타구 %d" % (round_no + 1), 50, fielding, batting_config.input_latency_ms)
	view.sound_requested.connect(func(sfx: SoundSynth.Sfx, volume: float, pitch_scale: float) -> void:
		sound.play(sfx, volume * presentation.sfx_volume, pitch_scale))
	screen.add_child(UiKit.fill_parent(view))
	view.finished.connect(func(_kind: FieldPlay.Kind) -> void: show_field_practice(round_no + 1))
	var back := UiKit.button("그만 ›", show_title, Tokens.INK_SOFT, Tokens.FONT_CAPTION)
	screen.add_child(back)
	back.set_anchors_and_offsets_preset(Control.PRESET_TOP_RIGHT, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_SM)
	back.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	show_screen(screen)


## 주루 연습 (개발 빌드): 도루·한 베이스 더·태그업만 되풀이
func show_run_practice() -> void:
	show_screen(RunPracticeScreen.new(self))


## 새 타격 세션 (설정은 앱이 들고 있는 것을 같이 쓴다 → 손맛 패널 조절이 바로 반영)
func new_batting_session() -> BattingSession:
	var s := BattingSession.new(batting_config, presentation)
	s.sound_requested.connect(sound.play)
	return s


# ---------- 저장 ----------

## 지금 커리어를 저장한다 (허브·결과·시즌 결산 화면이 그릴 때와 선택할 때마다 부른다)
func save_career() -> void:
	if career == null:
		return
	var err := SaveGame.save(career, save_path)
	if err != "":
		push_warning("저장 실패: " + err)


func _load_save() -> void:
	if not SaveGame.exists(save_path):
		return
	var r := SaveGame.load_career(career_config, batting_config, save_path)
	career = r["career"]
	save_error = r["error"]
	if save_error != "":
		push_warning("불러오기 실패: " + save_error)


# ---------- 수치 ----------

func _load_configs() -> void:
	var balance: Dictionary = await _source.fetch("balance.json")
	var content: Dictionary = await _source.fetch("content.json")
	batting_config = BattingConfig.from_balance(balance)
	presentation = Presentation.from_balance(balance)
	music.configure(presentation)
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
	_panel.pitch_forced.connect(func(id: String) -> void:
		batting_config.forced_pitch_type = id
		_panel.set_status("구종: %s" % ("볼배합대로" if id == "" else "랜덤" if id == "random" else batting_config.catalog.spec(id).name_ko)))
	_panel.save_requested.connect(_save_tuning)
	_panel.reload_requested.connect(_reload)


func _reset_tuning() -> void:
	_tuning = Tuning.new(batting_config, presentation)
	_panel.show_values(_tuning.values)
	# 구종 버튼: 수치를 새로 읽으면 config 가 새것이라 고른 구종을 다시 넣는다
	batting_config.forced_pitch_type = _panel.forced_pitch if batting_config.pitches.has(_panel.forced_pitch) or _panel.forced_pitch == "random" else ""
	var names := []
	for id: String in batting_config.pitches:
		names.append([id, batting_config.catalog.spec(id).name_ko])
	_panel.set_pitches(names)
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
