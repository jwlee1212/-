class_name HomeScreen
extends Control
## 홈 (이번 주): 내 선수 카드 / 다음 상대 / 훈련 하나 고르기 → 경기 시작

var _app
var _result_label: Label
var _start_button: Button
var _training_buttons: Array[Button] = []
var _player_card: PanelContainer
var _left: VBoxContainer


func _init(app) -> void:
	_app = app


func _ready() -> void:
	var career: CareerState = _app.career
	var row := UiKit.hbox(Tokens.SPACE_MD)
	add_child(UiKit.fill_parent(UiKit.margin(row)))

	_left = UiKit.vbox()
	_left.custom_minimum_size.x = 330
	row.add_child(_left)
	_refresh_player_card()

	var right := UiKit.vbox(Tokens.SPACE_SM)
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(right)
	var opp := career.opponent()
	right.add_child(UiKit.title("%d학년 봄 리그 · %d주차 / %d" % [career.player.grade, career.week, career.cfg.season_weeks]))
	var opp_card := UiKit.card()
	var oc := UiKit.vbox(Tokens.SPACE_XS)
	opp_card.add_child(oc)
	var home_away := "홈" if career.is_home() else "원정"
	oc.add_child(UiKit.label("다음 경기 (%s)" % home_away, Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	oc.add_child(UiKit.title("vs %s" % opp["name"], Tokens.FONT_TITLE, Tokens.BAD))
	var pitcher: BattingConfig.PitcherProfile = _app.batting_config.pitchers[opp["pitcher"]]
	oc.add_child(UiKit.label("전력 %s · 선발 %s 투수" % [_stars(float(opp["strength"])), pitcher.name], Tokens.FONT_BODY))
	right.add_child(opp_card)

	right.add_child(UiKit.label("감독: 이번 주는 뭘 할래? (하나만)", Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD))
	var grid := GridContainer.new()
	grid.columns = 4
	grid.add_theme_constant_override("h_separation", Tokens.SPACE_XS)
	for id: String in career.cfg.training:
		var t: Dictionary = career.cfg.training[id]
		var color := Tokens.GOOD if id == "rest" else Tokens.PRIMARY
		var b := UiKit.button(t["label"], func() -> void: _train(id), color, Tokens.FONT_CAPTION)
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		grid.add_child(b)
		_training_buttons.append(b)
	right.add_child(grid)
	_result_label = UiKit.label("", Tokens.FONT_BODY, Tokens.GOOD, Tokens.FONT_BOLD)
	right.add_child(_result_label)
	var spacer := Control.new()
	spacer.size_flags_vertical = Control.SIZE_EXPAND_FILL
	right.add_child(spacer)
	_start_button = UiKit.button("경기 시작 ▶", _app.show_game, Tokens.ACCENT, Tokens.FONT_TITLE)
	_start_button.disabled = true
	right.add_child(_start_button)


func _train(id: String) -> void:
	var text: String = _app.career.train(id)
	_result_label.text = text
	for b in _training_buttons:
		b.disabled = true
	_start_button.disabled = false
	_refresh_player_card()


func _refresh_player_card() -> void:
	if _player_card != null:
		_player_card.queue_free()
	var career: CareerState = _app.career
	var p := career.player
	_player_card = UiKit.card()
	_player_card.size_flags_vertical = Control.SIZE_EXPAND_FILL
	var col := UiKit.vbox(Tokens.SPACE_XS)
	_player_card.add_child(col)
	col.add_child(UiKit.title(p.name))
	col.add_child(UiKit.label("%s %d학년 · 외야수 · %d번 타자" % [career.cfg.my_school["name"], p.grade, p.lineup_slot], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	col.add_child(UiKit.stat_bar("컨택", p.contact, 99, Tokens.PRIMARY))
	col.add_child(UiKit.stat_bar("파워", p.power, 99, Tokens.ACCENT))
	col.add_child(UiKit.stat_bar("선구안", p.eye, 99, Tokens.GOOD))
	col.add_child(UiKit.stat_bar("컨디션", p.condition, 100, Tokens.WARN))
	col.add_child(UiKit.stat_bar("스카우트", p.scout_interest, 100, Tokens.BAD))
	var s := p.season
	col.add_child(UiKit.label("시즌 %d승 %d패 · 타율 %s · %d홈런 %d타점" % [career.wins(), career.losses(), s.average(), s.home_runs, s.rbi], Tokens.FONT_CAPTION, Tokens.INK))
	_left.add_child(_player_card)


func _stars(strength: float) -> String:
	var n := clampi(roundi((strength - 30.0) / 8.0), 1, 5)
	return "★".repeat(n) + "☆".repeat(5 - n)
