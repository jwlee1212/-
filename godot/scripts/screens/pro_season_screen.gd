class_name ProSeasonScreen
extends Control
## 프로 시즌 결산: 최종 순위 · 포스트시즌 · 내 시즌 기록 · 시상 · 연봉 → (시즌 끝 이야기·기념 순간) → [다음 시즌]

var _app
var _overlay: Control


func _init(app) -> void:
	_app = app


func _ready() -> void:
	var career: CareerState = _app.career
	career.pro.close_season(career)
	_app.save_career()
	_build()
	_next_story()


func _build() -> void:
	for c in get_children():
		if c != _overlay:
			c.queue_free()
	var career: CareerState = _app.career
	var pro := career.pro
	var lg := pro.league
	var bg := ColorRect.new()
	bg.color = Tokens.BACKGROUND
	add_child(UiKit.fill_parent(bg))
	move_child(bg, 0)
	var scroll := ScrollContainer.new()
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	add_child(UiKit.fill_parent(UiKit.margin(scroll, Tokens.SPACE_MD)))
	var col := UiKit.vbox(Tokens.SPACE_XS)
	col.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(col)
	col.add_child(UiKit.kicker("KBA %d년 차 시즌 결산" % pro.year()))
	col.add_child(UiKit.title("%s · %s" % [career.player.name, pro.team().name], Tokens.FONT_TITLE))
	var t := pro.team()
	var rank := lg.final_order.find(t.id) + 1
	var post := lg.post_text(t.id)
	col.add_child(UiKit.label("팀 %d위 (%d승 %d패 %d무)%s" % [rank, t.w, t.l, t.d, " · " + post if post != "" else " · 가을야구 실패"], Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD))
	if not lg.champions.is_empty() and int(lg.champions[-1]["year"]) == lg.year:
		col.add_child(UiKit.label("한국시리즈 우승: %s" % lg.team(lg.champions[-1]["team"]).name, Tokens.FONT_BODY, Tokens.ACCENT, Tokens.FONT_BOLD))
	col.add_child(UiKit.label("1군: " + pro.me.line_text(), Tokens.FONT_BODY, Tokens.PRIMARY, Tokens.FONT_BOLD))
	if pro.futures_season.pa > 0:
		var f := pro.futures_season
		col.add_child(UiKit.label("퓨처스: 타율 %s · %d홈런 (%d타석)" % [f.average(), f.home_runs, f.pa], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	if pro.me.post.pa > 0:
		var ps := pro.me.post
		col.add_child(UiKit.label("포스트시즌: %s" % ps.line_text(), Tokens.FONT_CAPTION, Tokens.INK))
	var growth := []
	for st in PlayerData.STATS:
		growth.append("%s %d→%d" % [PlayerData.STAT_LABELS[st], career.player.season_start.get(st, career.player.stat(st)), career.player.stat(st)])
	col.add_child(UiKit.label("성장: " + " · ".join(growth), Tokens.FONT_CAPTION, Tokens.GOOD, Tokens.FONT_BOLD))
	col.add_child(UiKit.label("내 수상: " + (", ".join(pro.season_awards) if not pro.season_awards.is_empty() else "없음"), Tokens.FONT_BODY, Tokens.ACCENT, Tokens.FONT_BOLD))
	col.add_child(UiKit.label(pro.season_note, Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD))
	col.add_child(UiKit.divider())
	col.add_child(UiKit.kicker("올해의 상"))
	for a: Dictionary in lg.awards:
		if int(a["year"]) == lg.year:
			col.add_child(UiKit.label("%s — %s (%s) · %s" % [a["award"], a["name"], a["team"], a["line"]], Tokens.FONT_CAPTION,
				Tokens.PRIMARY if a["id"] == pro.me.id else Tokens.INK))
	var row := UiKit.hbox(Tokens.SPACE_SM)
	row.add_child(UiKit.button("타이틀로", _app.show_title, Tokens.INK_SOFT))
	var next := UiKit.button("다음 시즌 ▶", func() -> void:
		career.start_next_pro_season()
		_app.save_career()
		_app.show_home(), Tokens.ACCENT, Tokens.FONT_LABEL)
	next.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(next)
	col.add_child(row)


## 시상으로 생긴 기념 순간(신인왕 …)을 먼저, 그다음 시즌 끝 이야기
func _next_story() -> void:
	var career: CareerState = _app.career
	var pro := career.pro
	if not pro.pending_scenes.is_empty():
		var m: Dictionary = pro.pending_scenes.pop_front()
		if Milestones.has_cutscene(career.cfg, m):
			var cs := CutsceneView.new(career, m["cutscene"])
			_show(cs)
			cs.finished.connect(_closed)
		else:
			var card := UiKit.card()
			var c := UiKit.vbox(Tokens.SPACE_SM)
			card.add_child(c)
			c.add_child(UiKit.kicker("컷신 자리 · %s" % m["cutscene"]))
			c.add_child(UiKit.title(m["title"], Tokens.FONT_TITLE))
			var l := UiKit.label(m["line"], Tokens.FONT_BODY)
			l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
			c.add_child(l)
			c.add_child(UiKit.button("확인", _closed, Tokens.ACCENT))
			var center := CenterContainer.new()
			center.add_child(card)
			card.custom_minimum_size.x = 560
			_show(center)
		return
	var events: Array = career.events("seasonEnd")
	if events.is_empty():
		return
	var view := DialogueView.new(career, events[0])
	_show(view)
	view.finished.connect(_closed)


func _show(c: Control) -> void:
	_overlay = c
	add_child(UiKit.fill_parent(c))


func _closed() -> void:
	if _overlay != null:
		_overlay.queue_free()
		_overlay = null
	_app.save_career()
	_build()
	_next_story()
