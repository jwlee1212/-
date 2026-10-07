class_name ResultScreen
extends Control
## 경기 결과 → 성장·들어온 돈 → [이번 주 이야기] 대화 화면(0~2개, 차례로) → 다음 주

var _app
var _game: GameRunner
var _right: VBoxContainer
## 아직 남은 이번 경기 뒤 이벤트, 그걸 여는 버튼
var _queue: Array = []
var _story_button: Button


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
	var my_text := _line_text(line) if _game.me_playing else "부상으로 결장"
	col.add_child(UiKit.label("%s: %s" % [career.player.name, my_text], Tokens.FONT_BODY, Tokens.PRIMARY, Tokens.FONT_BOLD))
	col.add_child(UiKit.label("훈련: %s" % career.last_training, Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	if notes.is_empty():
		col.add_child(UiKit.label("이번 경기 성장 없음", Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	else:
		col.add_child(UiKit.label("성장: " + ", ".join(notes), Tokens.FONT_BODY, Tokens.GOOD, Tokens.FONT_BOLD))
	col.add_child(UiKit.button("박스스코어 보기", _show_box, Tokens.INK_SOFT, Tokens.FONT_CAPTION))

	_right = UiKit.vbox(Tokens.SPACE_SM)
	_right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(_right)
	_queue = career.events("after").duplicate()
	var coach := UiKit.card()
	var cc := UiKit.vbox(Tokens.SPACE_XS)
	coach.add_child(cc)
	cc.add_child(UiKit.title(career.people.person("coach").call, Tokens.FONT_LABEL))
	var coach_text := UiKit.label(career.coach_line(_game), Tokens.FONT_BODY)
	coach_text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	cc.add_child(coach_text)
	_right.add_child(coach)
	_right.add_child(_income_card())
	if _queue.is_empty():
		_show_next()
	else:
		var spacer := Control.new()
		spacer.size_flags_vertical = Control.SIZE_EXPAND_FILL
		_right.add_child(spacer)
		_story_button = UiKit.button("이번 주 이야기 ▶ (%d)" % _queue.size(), _show_event, Tokens.ACCENT, Tokens.FONT_TITLE)
		_right.add_child(_story_button)


## 들어온 돈 (용돈 · 칭찬 용돈 · 장학금)
func _income_card() -> Control:
	var career: CareerState = _app.career
	var card := UiKit.card()
	var col := UiKit.vbox(2)
	card.add_child(col)
	var parts := []
	for pair: Array in career.last_income:
		parts.append("%s %s" % [pair[0], PlayerData.money_text(int(pair[1]))])
	col.add_child(UiKit.label("지갑 %s" % PlayerData.money_text(career.player.money), Tokens.FONT_LABEL, Tokens.INK, Tokens.FONT_BOLD))
	var l := UiKit.label(" · ".join(parts), Tokens.FONT_CAPTION, Tokens.GOOD, Tokens.FONT_BOLD)
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	col.add_child(l)
	return card


func _show_box() -> void:
	var dim := ColorRect.new()
	dim.color = Tokens.SCRIM
	add_child(UiKit.fill_parent(dim))
	var center := CenterContainer.new()
	dim.add_child(UiKit.fill_parent(center))
	var view := BoxScoreView.new(_game)
	center.add_child(view)
	view.closed.connect(dim.queue_free)


## 남은 이야기 중 첫 번째를 화면 전체 대화로 띄운다. 끝나면 다음 것, 다 끝나면 짧은 기록과 [다음 주]
func _show_event() -> void:
	if _story_button != null:
		_story_button.queue_free()
		_story_button = null
	var event: Dictionary = _queue.pop_front()
	var view := DialogueView.new(_app.career, event)
	add_child(UiKit.fill_parent(view))
	view.finished.connect(func() -> void:
		var entry: Dictionary = _app.career.book.log[-1]
		view.queue_free()
		var done := UiKit.label("%s · %s" % [entry["title"], entry["choice"]], Tokens.FONT_CAPTION, Tokens.INK_SOFT)
		done.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		_right.add_child(done)
		if _queue.is_empty():
			_show_next()
		else:
			_show_event())


## 지금 떠 있는 대화 화면 (스크린샷 스크립트용, 없으면 null)
func story_view() -> DialogueView:
	var found := find_children("*", "DialogueView", true, false)
	return found[0] if not found.is_empty() else null


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
