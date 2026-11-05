class_name SeasonScreen
extends Control
## 시즌 결산 → (시즌 끝 이야기) → 겨울 캠프 시작 (허브로) / 3학년이면 진로 선택 뒤 졸업

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
	var row := UiKit.hbox(Tokens.SPACE_SM)
	row.add_child(UiKit.button("타이틀로", _app.show_title, Tokens.INK_SOFT))
	if p.grade < 3:
		var camp := career.cfg.camp
		col.add_child(UiKit.label("겨울 캠프 %d주: 훈련 효과 ×%.1f (집중 과제 ×%.1f) · 연습경기 %d번" % [
			int(camp["weeks"]), float(camp["trainingMultiplier"]), float(camp["focusMultiplier"]), (camp["gameWeeks"] as Array).size()],
			Tokens.FONT_CAPTION, Tokens.INK_SOFT))
		var next := UiKit.button("겨울 캠프 시작 ▶", func() -> void:
			career.start_camp()
			_app.save_career()
			_app.show_home(), Tokens.ACCENT, Tokens.FONT_LABEL)
		next.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(next)
	else:
		# 고교 졸업 → 프로 입단 (드래프트 결과 · 대학을 고르면 4년 요약 뒤 대졸 드래프트)
		var text := "고교 졸업!"
		if not career.draft.is_empty():
			text += " " + str(career.draft["text"])
		elif career.path == "college":
			text += " 대학 진학 — 4년 뒤 대졸 드래프트"
		var l := UiKit.label(text, Tokens.FONT_BODY, Tokens.ACCENT, Tokens.FONT_BOLD)
		l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(l)
		var go := UiKit.button("프로 입단 ▶" if career.path != "college" else "대학 4년 → 드래프트 ▶", func() -> void:
			career.start_pro()
			_app.save_career()
			_app.show_home(), Tokens.ACCENT, Tokens.FONT_LABEL)
		go.disabled = _overlay != null
		row.add_child(go)
	col.add_child(row)


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
