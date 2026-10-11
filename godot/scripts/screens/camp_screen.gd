class_name CampScreen
extends Control
## 겨울 캠프 결산 → 다음 시즌. 캠프 4주 성장 + 겨울 마무리 성장(한 번만), 집중 과제, 연습경기 결과.

var _app


func _init(app) -> void:
	_app = app


func _ready() -> void:
	var career: CareerState = _app.career
	var p := career.player
	# 겨울 마무리 성장 (다시 열어도 한 번만 — CareerState.winter_gains)
	var gains := career.winter_training()
	_app.save_career()
	var center := CenterContainer.new()
	add_child(UiKit.fill_parent(center))
	var card := UiKit.card()
	card.custom_minimum_size.x = 680
	center.add_child(card)
	var col := UiKit.vbox(Tokens.SPACE_XS)
	card.add_child(col)
	col.add_child(UiKit.kicker("WINTER CAMP · %d학년 겨울" % p.grade))
	col.add_child(UiKit.title("%s 겨울 캠프 결산" % p.name, Tokens.FONT_TITLE))
	var focus := "집중 과제: %s" % career.camp_focus_label() if career.camp_focus != "" else "집중 과제 없이 보냈다"
	col.add_child(UiKit.label(focus, Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD))
	var growth := []
	for st in PlayerData.STATS:
		var from: int = career.camp_start.get(st, p.stat(st))
		growth.append("%s %d→%d" % [PlayerData.STAT_LABELS[st], from, p.stat(st)])
	col.add_child(UiKit.label("겨울 성장: " + " · ".join(growth), Tokens.FONT_BODY, Tokens.GOOD, Tokens.FONT_BOLD))
	var parts := []
	for st: String in gains:
		if gains[st] > 0:
			parts.append("%s +%d" % [PlayerData.STAT_LABELS[st], gains[st]])
	col.add_child(UiKit.label("그중 겨울 마무리 훈련: " + (", ".join(parts) if not parts.is_empty() else "큰 변화 없음"), Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	for g: Dictionary in career.camp_games:
		var r: int = g["result"]
		col.add_child(UiKit.label("연습경기 vs %s  %d : %d %s · %s" % [g["opponent"], g["my"], g["opp"], ["패", "무", "승"][r + 1], g["line"]],
			Tokens.FONT_CAPTION, Tokens.INK))
	col.add_child(UiKit.label("유망주 랭킹 %d위 · %s" % [career.prospect_rank(), career.projection()["text"]], Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD))
	col.add_child(UiKit.spacer())
	var row := UiKit.hbox(Tokens.SPACE_SM)
	row.add_child(UiKit.button("타이틀로", _app.show_title, Tokens.INK_SOFT))
	var next := UiKit.button("%d학년 시즌 시작 ▶" % (p.grade + 1), func() -> void:
		career.start_next_season()
		_app.show_home(), Tokens.ACCENT, Tokens.FONT_LABEL)
	next.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(next)
	col.add_child(row)
