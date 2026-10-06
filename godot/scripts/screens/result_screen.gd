class_name ResultScreen
extends Control
## 경기 결과 → 성장 → 돌발 이벤트(있으면) → 다음 주

var _app
var _game: GameRunner
var _right: VBoxContainer


func _init(app, game: GameRunner) -> void:
	_app = app
	_game = game


func _ready() -> void:
	var career: CareerState = _app.career
	var notes := career.finish_game(_game)
	var row := UiKit.hbox(Tokens.SPACE_MD)
	add_child(UiKit.fill_parent(UiKit.margin(row)))

	var left := UiKit.card()
	left.custom_minimum_size.x = 360
	row.add_child(left)
	var col := UiKit.vbox(Tokens.SPACE_SM)
	left.add_child(col)
	var r := _game.my_result()
	col.add_child(UiKit.title(["패배…", "무승부", "승리!"][r + 1], Tokens.FONT_HERO, [Tokens.BAD, Tokens.INK_SOFT, Tokens.GOOD][r + 1]))
	var st := _game.state
	col.add_child(UiKit.label("%s %d : %d %s" % [_game.team_name(GameState.AWAY), st.score[0], st.score[1], _game.team_name(GameState.HOME)], Tokens.FONT_LABEL, Tokens.INK, Tokens.FONT_BOLD))
	var line := _game.my_line
	col.add_child(UiKit.label("%s: %s" % [career.player.name, _line_text(line)], Tokens.FONT_BODY, Tokens.PRIMARY, Tokens.FONT_BOLD))
	col.add_child(UiKit.label("훈련: %s" % career.last_training, Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	if notes.is_empty():
		col.add_child(UiKit.label("이번 경기 성장 없음", Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	else:
		col.add_child(UiKit.label("성장: " + ", ".join(notes), Tokens.FONT_BODY, Tokens.GOOD, Tokens.FONT_BOLD))

	_right = UiKit.vbox(Tokens.SPACE_SM)
	_right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(_right)
	var event := career.pick_event()
	if event.is_empty():
		var coach := UiKit.card()
		var cc := UiKit.vbox(Tokens.SPACE_XS)
		coach.add_child(cc)
		cc.add_child(UiKit.title("감독", Tokens.FONT_LABEL))
		var coach_text := UiKit.label(career.coach_line(_game), Tokens.FONT_BODY)
		coach_text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		cc.add_child(coach_text)
		_right.add_child(coach)
		_show_next()
	else:
		_show_event(event)


func _show_event(event: Dictionary) -> void:
	var card := UiKit.card(Tokens.SURFACE)
	var col := UiKit.vbox(Tokens.SPACE_SM)
	card.add_child(col)
	col.add_child(UiKit.label("돌발 이벤트", Tokens.FONT_CAPTION, Tokens.ACCENT, Tokens.FONT_BOLD))
	col.add_child(UiKit.title(str(event["speaker"]), Tokens.FONT_LABEL))
	var text := UiKit.label(str(event["text"]), Tokens.FONT_BODY)
	text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	col.add_child(text)
	var buttons := UiKit.vbox(Tokens.SPACE_XS)
	var choices: Array = event["choices"]
	for i in choices.size():
		var b := UiKit.button(str(choices[i]["label"]), func() -> void: _choose(event, i, card), Tokens.PRIMARY, Tokens.FONT_LABEL)
		buttons.add_child(b)
	col.add_child(buttons)
	_right.add_child(card)


func _choose(event: Dictionary, index: int, card: Control) -> void:
	var result: String = _app.career.resolve_event(event, index)
	card.queue_free()
	var res := UiKit.card()
	var t := UiKit.label(result, Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD)
	t.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	res.add_child(t)
	_right.add_child(res)
	_show_next()


func _show_next() -> void:
	var spacer := Control.new()
	spacer.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_right.add_child(spacer)
	_right.add_child(UiKit.button("다음 주 ▶", _next, Tokens.ACCENT, Tokens.FONT_TITLE))


func _next() -> void:
	var career: CareerState = _app.career
	career.advance_week()
	if career.is_season_over():
		_app.show_season()
	else:
		_app.show_home()


static func _line_text(l: PlayerData.SeasonStats) -> String:
	var parts := ["%d타수 %d안타" % [l.ab, l.hits]]
	if l.home_runs > 0:
		parts.append("%d홈런" % l.home_runs)
	if l.rbi > 0:
		parts.append("%d타점" % l.rbi)
	if l.walks > 0:
		parts.append("%d볼넷" % l.walks)
	if l.strikeouts > 0:
		parts.append("%d삼진" % l.strikeouts)
	return " ".join(parts)
