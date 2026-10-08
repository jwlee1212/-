class_name SeasonScreen
extends Control
## 시즌 결산 → (시즌 끝 이야기) → 겨울 훈련 → 다음 시즌 / 3학년이면 진로 선택 뒤 졸업

var _app
var _overlay: Control


func _init(app) -> void:
	_app = app


func _ready() -> void:
	_build()
	_next_story()


func _build() -> void:
	for c in get_children():
		if c != _overlay:
			c.queue_free()
	var career: CareerState = _app.career
	_app.save_career()
	var p := career.player
	var s := p.season
	var center := CenterContainer.new()
	add_child(UiKit.fill_parent(center))
	move_child(center, 0)
	var card := UiKit.card()
	card.custom_minimum_size.x = 680
	center.add_child(card)
	var col := UiKit.vbox(Tokens.SPACE_XS)
	card.add_child(col)
	col.add_child(UiKit.kicker(EventBook.chapter(career)))
	col.add_child(UiKit.title("%s %d학년 시즌 결산" % [p.name, p.grade], Tokens.FONT_TITLE))
	for entry: Dictionary in career.phase_log:
		col.add_child(UiKit.label("%s — %s" % [entry["name"], entry["text"]], Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD))
	col.add_child(UiKit.label("팀 %d승 %d패 %d무" % [career.wins(), career.losses(), career.results.count(0)], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	col.add_child(UiKit.label("타율 %s · %d안타 %d홈런 %d타점 · %d볼넷 %d삼진 (%d경기 %d타석)" % [
		s.average(), s.hits, s.home_runs, s.rbi, s.walks, s.strikeouts, s.games, s.pa], Tokens.FONT_BODY, Tokens.PRIMARY, Tokens.FONT_BOLD))
	if career.people.is_met("rival"):
		var rs := career.rival.player.season
		col.add_child(UiKit.label("라이벌 %s: 타율 %s · %d홈런 %d타점 (유망주 %d위)" % [career.rival.player.name, rs.average(), rs.home_runs, rs.rbi, career.rival_rank()],
			Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	var growth := []
	for st in PlayerData.STATS:
		growth.append("%s %d→%d" % [PlayerData.STAT_LABELS[st], p.season_start.get(st, p.stat(st)), p.stat(st)])
	col.add_child(UiKit.label("이번 시즌 성장: " + " · ".join(growth), Tokens.FONT_CAPTION, Tokens.GOOD, Tokens.FONT_BOLD))
	col.add_child(UiKit.stat_bar("스카우트", p.scout_interest, 100, Tokens.BAD))
	col.add_child(UiKit.label("유망주 랭킹 %d위 · %s" % [career.prospect_rank(), career.projection()["text"]], Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD))
	var winter := UiKit.label("", Tokens.FONT_BODY, Tokens.GOOD, Tokens.FONT_BOLD)
	col.add_child(winter)
	var row := UiKit.hbox(Tokens.SPACE_SM)
	row.add_child(UiKit.button("타이틀로", _app.show_title, Tokens.INK_SOFT))
	if p.grade < 3:
		var next: Button
		next = UiKit.button("겨울 훈련 ▶", func() -> void:
			if career.winter_gains.is_empty():
				career.winter_training()
				_app.save_career()
				_show_winter(winter, next)
			else:
				career.start_next_season()
				_app.show_home(), Tokens.ACCENT, Tokens.FONT_LABEL)
		# 겨울 훈련을 이미 했으면 (불러온 커리어) 결과와 다음 시즌 버튼
		if not career.winter_gains.is_empty():
			_show_winter(winter, next)
		next.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(next)
	else:
		var text := "고교 졸업!"
		match career.path:
			"draft":
				text += " 드래프트 신청 — 지명 결과는 다음 업데이트(G7)에서."
			"college":
				text += " 대학 진학 — 대학 리그는 다음 업데이트(G7)에서."
		var l := UiKit.label(text, Tokens.FONT_BODY, Tokens.ACCENT, Tokens.FONT_BOLD)
		l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(l)
	col.add_child(row)


func _show_winter(winter: Label, next: Button) -> void:
	var career: CareerState = _app.career
	var gains: Dictionary = career.winter_gains
	var parts := []
	for st: String in gains:
		if gains[st] > 0:
			parts.append("%s +%d" % [PlayerData.STAT_LABELS[st], gains[st]])
	winter.text = "겨울 훈련: " + (", ".join(parts) if not parts.is_empty() else "큰 변화 없음")
	next.text = "%d학년 시즌 시작 ▶" % (career.player.grade + 1)


## 시즌 끝 이야기가 남았으면 대화 화면을 띄우고, 다 고르면 결산을 다시 그린다 (진로가 반영되게)
func _next_story() -> void:
	var career: CareerState = _app.career
	# 시즌 마지막 경기 뒤에 껐다 켰으면 남은 경기 뒤 이야기부터, 다 보면 그 주를 마친다
	if career.game_done and career.events("after").is_empty():
		career.advance_week()
		_app.save_career()
	var events: Array = career.events("after" if career.game_done else "seasonEnd")
	if events.is_empty():
		return
	var view := DialogueView.new(_app.career, events[0])
	_overlay = view
	add_child(UiKit.fill_parent(view))
	view.finished.connect(func() -> void:
		_overlay.queue_free()
		_overlay = null
		_build()
		_next_story())
