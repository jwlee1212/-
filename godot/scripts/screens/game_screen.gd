class_name GameScreen
extends Control
## 경기: 점수판 + 상황(주자·아웃·타자) + 문자 중계가 자동으로 흘러가다가,
## 내 타순이 오면 타석 화면(BattingView)으로 바뀌어 직접 친다. 결과는 주자·점수에 그대로 반영된다.

var _app
var _game: GameRunner
var _next_step_at := 0.0
var _fast := false
var _scoreboard: GridContainer
var _situation: Label
var _batter: Label
var _bases: BasesView
var _log_box: VBoxContainer
var _fast_button: Button
var _done_button: Button
var _banner: Label

# 내 타석
var _batting: Control
var _session: BattingSession
var _view: BattingView
var _banner_until := 0.0
var _waiting_for_turn := false
var _highlight: HighlightView


func _init(app) -> void:
	_app = app


func _ready() -> void:
	_game = _app.career.new_game()
	var col := UiKit.vbox(Tokens.SPACE_SM)
	add_child(UiKit.fill_parent(UiKit.margin(col, Tokens.SPACE_SM)))

	_scoreboard = GridContainer.new()
	_scoreboard.columns = _game.state.innings + 3
	_scoreboard.add_theme_constant_override("h_separation", Tokens.SPACE_SM)
	var sb_card := UiKit.card()
	sb_card.add_child(_scoreboard)
	col.add_child(sb_card)

	var mid := UiKit.hbox(Tokens.SPACE_SM)
	mid.size_flags_vertical = Control.SIZE_EXPAND_FILL
	col.add_child(mid)
	var sit_card := UiKit.card()
	sit_card.custom_minimum_size.x = 250
	var sit := UiKit.vbox(Tokens.SPACE_XS)
	sit_card.add_child(sit)
	_situation = UiKit.title("", Tokens.FONT_TITLE)
	sit.add_child(_situation)
	_bases = BasesView.new()
	sit.add_child(_bases)
	_batter = UiKit.label("", Tokens.FONT_BODY)
	_batter.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	sit.add_child(_batter)
	mid.add_child(sit_card)

	var log_card := UiKit.card()
	log_card.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_log_box = UiKit.vbox(2)
	log_card.add_child(_log_box)
	mid.add_child(log_card)

	var bottom := UiKit.hbox(Tokens.SPACE_SM)
	_banner = UiKit.title("", Tokens.FONT_TITLE, Tokens.ACCENT)
	_banner.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	bottom.add_child(_banner)
	_fast_button = UiKit.button("빠르게 ▶▶", _toggle_fast, Tokens.INK_SOFT, Tokens.FONT_CAPTION)
	bottom.add_child(_fast_button)
	_done_button = UiKit.button("경기 결과 ▶", func() -> void: _app.show_result(_game), Tokens.ACCENT, Tokens.FONT_LABEL)
	_done_button.visible = false
	bottom.add_child(_done_button)
	col.add_child(bottom)
	_refresh()
	_next_step_at = _now() + 800.0


func _process(_delta: float) -> void:
	if _session != null:
		_session.tick()
		return
	if _game.state.over or _highlight != null:
		return
	if _waiting_for_turn:
		if _now() >= _banner_until:
			_waiting_for_turn = false
			_open_batting()
		return
	if _now() < _next_step_at:
		return
	var step := _game.step()
	match step.type:
		"my_turn":
			# "★ 내 타석!" 을 잠깐 보여 주고 타석 화면으로
			_banner.text = "★ %s의 타석! %s %s" % [_app.career.player.name, _game.state.half_text(), _outs_runners()]
			_banner_until = _now() + 1200.0
			_waiting_for_turn = true
		"auto":
			_next_step_at = _now() + (_app.career_config.fast_step_ms if _fast else _app.career_config.auto_step_ms)
			# 점수가 난 안타·홈런은 중계 화면 하이라이트로 (빠르게 모드면 건너뜀)
			if step.highlight != null and not _fast:
				_show_highlight(step.highlight, step.text, step.side != _game.my_side)
	_refresh()


func _toggle_fast() -> void:
	_fast = not _fast
	_fast_button.text = "보통 속도 ▶" if _fast else "빠르게 ▶▶"


## us_fielding: 우리 팀이 수비였는가 (수비수 유니폼 색)
func _show_highlight(play: PlaySimulator.PlayResult, text: String, us_fielding: bool) -> void:
	_highlight = HighlightView.new(_app.batting_config.ball_physics, play, text.substr(text.find("]") + 2), _app.presentation.broadcast_speed, us_fielding)
	add_child(UiKit.fill_parent(_highlight))
	_highlight.finished.connect(func() -> void:
		if _highlight != null:
			_highlight.queue_free()
			_highlight = null
			_next_step_at = _now() + 300.0)


# ---------- 내 타석 ----------

func _open_batting() -> void:
	_session = _app.new_batting_session()
	# 지금 마운드에 있는 투수의 성향 (선발이 내려가면 불펜 투수)
	var opp_pitcher: BattingConfig.PitcherProfile = _app.batting_config.pitchers[_game.current_pitcher().profile]
	var player: PlayerData = _app.career.player
	_session.start_single(player.skills_for_game(_app.career_config), opp_pitcher, _app.career.at_bat_seed(_game), _game.situation())
	_session.at_bat_finished.connect(_on_at_bat_finished)
	_batting = Control.new()
	add_child(UiKit.fill_parent(_batting))
	_view = BattingView.new()
	_view.session = _session
	_view.jersey_number = player.lineup_slot
	_view.tapped.connect(_session.tap)
	_batting.add_child(UiKit.fill_parent(_view))
	var hud := BattingHud.new(_session)
	hud.game = _game
	hud.show_menu = false
	# 타석이 끝나면 _session 이 비워지므로 필요한 값은 미리 담아 둔다
	var pitcher_name := opp_pitcher.name
	hud.info_text = func() -> String:
		var l := _game.my_line
		return "%d번 %s · 오늘 %d타수 %d안타 %d타점 · 상대 %s" % [player.lineup_slot, player.name, l.ab, l.hits, l.rbi, pitcher_name]
	_batting.add_child(hud)


func _on_at_bat_finished(result: AtBat.Result) -> void:
	_game.apply_my_result(result)
	_batting.queue_free()
	_batting = null
	_session = null
	_view = null
	_banner.text = ""
	_next_step_at = _now() + 900.0
	_refresh()


# ---------- 표시 ----------

func _refresh() -> void:
	var st := _game.state
	_situation.text = "경기 종료" if st.over else "%s %s" % [st.half_text(), "%d사" % st.outs if st.outs > 0 else "무사"]
	_bases.bases = st.bases.map(func(b: int) -> bool: return b != GameState.EMPTY)
	_bases.outs = st.outs
	_bases.queue_redraw()
	if st.over:
		var r := _game.my_result()
		_batter.text = ["패배…", "무승부", "승리!"][r + 1]
		_done_button.visible = true
		_fast_button.visible = false
	else:
		_batter.text = "타석: %s" % _game.current_batter_name()
	for c in _log_box.get_children():
		c.queue_free()
	var lines := _game.log.slice(maxi(0, _game.log.size() - 9))
	for line: String in lines:
		var mine := line.contains("★")
		_log_box.add_child(UiKit.label(line, Tokens.FONT_CAPTION, Tokens.PRIMARY if mine else Tokens.INK, Tokens.FONT_BOLD if mine else Tokens.FONT_REGULAR))
	_refresh_scoreboard()


func _refresh_scoreboard() -> void:
	for c in _scoreboard.get_children():
		c.queue_free()
	var st := _game.state
	_scoreboard.add_child(UiKit.label("", Tokens.FONT_CAPTION))
	for i in st.innings:
		_scoreboard.add_child(_cell(str(i + 1), Tokens.INK_SOFT))
	_scoreboard.add_child(_cell("R", Tokens.INK_SOFT))
	_scoreboard.add_child(_cell("H", Tokens.INK_SOFT))
	for side in [GameState.AWAY, GameState.HOME]:
		var mine: bool = side == _game.my_side
		_scoreboard.add_child(UiKit.label(_game.team_short(side), Tokens.FONT_LABEL, Tokens.PRIMARY if mine else Tokens.INK, Tokens.FONT_BOLD))
		for i in st.innings:
			var v := ""
			if i < (st.line[side] as Array).size():
				v = str(st.line[side][i])
			_scoreboard.add_child(_cell(v, Tokens.INK))
		_scoreboard.add_child(_cell(str(st.score[side]), Tokens.INK, true))
		_scoreboard.add_child(_cell(str(_game.box.hits[side]), Tokens.INK_SOFT))


func _cell(text: String, color: Color, bold: bool = false) -> Label:
	var l := UiKit.label(text, Tokens.FONT_LABEL, color, Tokens.FONT_BOLD if bold else Tokens.FONT_REGULAR)
	l.custom_minimum_size.x = 22
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	return l


func _outs_runners() -> String:
	var st := _game.state
	return "%s %s" % ["%d사" % st.outs if st.outs > 0 else "무사", st.runners_text()]


func _now() -> float:
	return Time.get_ticks_usec() / 1000.0
