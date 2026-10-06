class_name SeasonScreen
extends Control
## 시즌 결산 → 다음 시즌 / 타이틀

var _app


func _init(app) -> void:
	_app = app


func _ready() -> void:
	var career: CareerState = _app.career
	var p := career.player
	var s := p.season
	var center := CenterContainer.new()
	add_child(UiKit.fill_parent(center))
	var card := UiKit.card()
	card.custom_minimum_size.x = 560
	center.add_child(card)
	var col := UiKit.vbox(Tokens.SPACE_SM)
	card.add_child(col)
	col.add_child(UiKit.label("%d학년 봄 리그 끝" % p.grade, Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	col.add_child(UiKit.title("%s 시즌 결산" % p.name, Tokens.FONT_TITLE))
	col.add_child(UiKit.label("팀 %d승 %d패 %d무" % [career.wins(), career.losses(), career.results.count(0)], Tokens.FONT_LABEL, Tokens.INK, Tokens.FONT_BOLD))
	col.add_child(UiKit.label("타율 %s · %d안타 %d홈런 %d타점 · %d볼넷 %d삼진 (%d경기 %d타석)" % [
		s.average(), s.hits, s.home_runs, s.rbi, s.walks, s.strikeouts, s.games, s.pa], Tokens.FONT_BODY, Tokens.PRIMARY, Tokens.FONT_BOLD))
	col.add_child(UiKit.stat_bar("컨택", p.contact, 99, Tokens.PRIMARY))
	col.add_child(UiKit.stat_bar("파워", p.power, 99, Tokens.ACCENT))
	col.add_child(UiKit.stat_bar("선구안", p.eye, 99, Tokens.GOOD))
	col.add_child(UiKit.stat_bar("스카우트", p.scout_interest, 100, Tokens.BAD))
	var verdict := "스카우트들이 이름을 적어 가기 시작했다." if p.scout_interest >= 30 else "아직은 무명. 다음 시즌이 진짜다."
	col.add_child(UiKit.label(verdict, Tokens.FONT_BODY, Tokens.INK_SOFT))
	var row := UiKit.hbox(Tokens.SPACE_SM)
	row.add_child(UiKit.button("타이틀로", _app.show_title, Tokens.INK_SOFT))
	if p.grade < 3:
		var next := UiKit.button("%d학년 시즌 시작 ▶" % (p.grade + 1), func() -> void:
			career.start_next_season()
			_app.show_home(), Tokens.ACCENT, Tokens.FONT_LABEL)
		next.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(next)
	else:
		row.add_child(UiKit.label("고교 졸업! 드래프트는 다음 업데이트에서.", Tokens.FONT_BODY, Tokens.ACCENT, Tokens.FONT_BOLD))
	col.add_child(row)
