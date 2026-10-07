class_name BoxScoreView
extends PanelContainer
## 박스스코어 창: 양 팀 타자 기록(타수·안타·홈런·타점·득점·볼넷·삼진)과 투수 기록(이닝·피안타·실점·볼넷·삼진·투구 수).
## 아무 데나 누르면 닫힌다.

signal closed

var _game: GameRunner


func _init(game: GameRunner) -> void:
	_game = game


func _ready() -> void:
	var s := StyleBoxFlat.new()
	s.bg_color = Tokens.SURFACE
	s.set_corner_radius_all(Tokens.RADIUS_CARD)
	s.set_content_margin_all(Tokens.SPACE_SM)
	add_theme_stylebox_override("panel", s)
	mouse_filter = Control.MOUSE_FILTER_STOP
	var row := UiKit.hbox(Tokens.SPACE_MD)
	add_child(row)
	for side in [GameState.AWAY, GameState.HOME]:
		row.add_child(_team_table(side))


func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.pressed:
		accept_event()
		closed.emit()


func _team_table(side: int) -> Control:
	var col := UiKit.vbox(2)
	var team: Team = _game.teams[side]
	var mine := side == _game.my_side
	col.add_child(UiKit.label("%s  %d점" % [team.name, _game.state.score[side]], Tokens.FONT_LABEL, Tokens.PRIMARY if mine else Tokens.INK, Tokens.FONT_BOLD))
	var grid := GridContainer.new()
	grid.columns = 8
	grid.add_theme_constant_override("h_separation", Tokens.SPACE_SM)
	grid.add_theme_constant_override("v_separation", 0)
	for h in ["타자", "타수", "안타", "홈런", "타점", "득점", "볼넷", "삼진"]:
		grid.add_child(_cell(h, Tokens.INK_SOFT, true))
	for i in 9:
		var b: Team.Batter = team.lineup[i]
		var l: BoxScore.BatterLine = _game.box.batters[side][i]
		var color := Tokens.PRIMARY if b.is_me else Tokens.INK
		grid.add_child(_cell("%d %s" % [i + 1, b.name], color, b.is_me))
		for v in [l.ab, l.h, l.hr, l.rbi, l.r, l.bb, l.so]:
			grid.add_child(_cell(str(v), color, b.is_me))
	col.add_child(grid)
	var pgrid := GridContainer.new()
	pgrid.columns = 7
	pgrid.add_theme_constant_override("h_separation", Tokens.SPACE_SM)
	pgrid.add_theme_constant_override("v_separation", 0)
	for h in ["투수", "이닝", "피안타", "실점", "볼넷", "삼진", "투구"]:
		pgrid.add_child(_cell(h, Tokens.INK_SOFT, true))
	for i in team.pitchers.size():
		var pl: BoxScore.PitcherLine = _game.box.pitchers[side][i]
		if pl.bf == 0:
			continue
		pgrid.add_child(_cell(team.pitchers[i].name, Tokens.INK, false))
		for v in [pl.innings_text(), pl.h, pl.r, pl.bb, pl.so, pl.pitches]:
			pgrid.add_child(_cell(str(v), Tokens.INK, false))
	col.add_child(pgrid)
	return col


func _cell(text: String, color: Color, bold: bool) -> Label:
	return UiKit.label(text, Tokens.FONT_CAPTION, color, Tokens.FONT_BOLD if bold else Tokens.FONT_REGULAR)
