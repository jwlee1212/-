class_name HomeScreen
extends Control
## 커리어 허브 (이번 주). 레이아웃은 유저가 준 시안(2026-10-07)을 따르고 색은 밝은 톤 토큰 그대로.
## 위: 로고 · 탭(홈 · 선수 · 훈련 · 인물 · 스카우트 · 기록) · 학년/주차
## 홈 탭: 다음 경기 | 종합 능력치 + 시즌 기록 | 스카우트 리포트 + 받은 소식, 아래: 컨디션 · 폼 · 평판 · 행동 / 다음 주
## 주 시작 이야기(EventBook timing start)가 있으면 화면 가운데 겹쳐 먼저 보여 준다

const TABS := ["홈", "선수", "훈련", "인물", "상점", "스카우트", "기록"]
## 지금 있는 탭
const READY_TABS := TABS

var _app
var _tab := "홈"
var _tab_buttons := {}
var _page_host: Control
var _bottom: HBoxContainer
## 훈련 버튼 (스크린샷 스크립트가 _train 을 직접 부른다)
var _training_buttons: Array[Button] = []
## 주 시작 이야기 대화 화면 (없으면 null)
var _overlay: Control
## 상점에서 마지막으로 쓴 서비스 결과
var _last_shop_note := ""


func _init(app) -> void:
	_app = app


func _ready() -> void:
	var root := UiKit.vbox(Tokens.SPACE_SM)
	add_child(UiKit.fill_parent(UiKit.margin(root, Tokens.SPACE_MD)))
	root.add_child(_top_bar())
	root.add_child(UiKit.divider())
	_page_host = PanelContainer.new()
	_page_host.add_theme_stylebox_override("panel", StyleBoxEmpty.new())
	_page_host.size_flags_vertical = Control.SIZE_EXPAND_FILL
	root.add_child(_page_host)
	_bottom = UiKit.hbox(Tokens.SPACE_LG)
	root.add_child(_bottom)
	_rebuild()
	_next_story()


## 주 시작 이야기가 남았으면 대화 화면을 위에 띄운다 (끝나면 다음 것, 다 끝나면 허브를 다시 그린다)
func _next_story() -> void:
	var events: Array = _app.career.events("start")
	if events.is_empty():
		return
	var view := DialogueView.new(_app.career, events[0])
	_overlay = view
	add_child(UiKit.fill_parent(view))
	view.finished.connect(func() -> void:
		_overlay.queue_free()
		_overlay = null
		_rebuild()
		_next_story())


## 지금 떠 있는 대화 화면 (스크린샷 스크립트용, 없으면 null)
func story_view() -> DialogueView:
	return _overlay as DialogueView


# ---------- 위 막대 ----------

func _top_bar() -> Control:
	var career: CareerState = _app.career
	var bar := UiKit.hbox(Tokens.SPACE_LG)
	# 로고를 누르면 로비로 (기울인 글자 위에 투명 버튼을 겹친다)
	var logo := PanelContainer.new()
	logo.add_theme_stylebox_override("panel", StyleBoxEmpty.new())
	logo.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	logo.add_child(Shapes.SlantText.new(_app.career_config.game_title["ko"], Tokens.FONT_TITLE))
	var hit := Button.new()
	hit.flat = true
	hit.focus_mode = Control.FOCUS_NONE
	hit.pressed.connect(_app.show_title)
	logo.add_child(hit)
	bar.add_child(logo)
	var tabs := UiKit.hbox(Tokens.SPACE_MD)
	tabs.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	for t: String in TABS:
		var b := Button.new()
		b.text = t
		b.flat = true
		b.focus_mode = Control.FOCUS_NONE
		b.disabled = not (t in READY_TABS)
		b.add_theme_font_override("font", Tokens.FONT_BOLD)
		b.add_theme_font_size_override("font_size", Tokens.FONT_BODY)
		b.pressed.connect(func() -> void: _show_tab(t))
		tabs.add_child(b)
		_tab_buttons[t] = b
	bar.add_child(tabs)
	bar.add_child(UiKit.label("%d학년 · %d주차" % [career.player.grade, career.week], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	return bar


func _show_tab(t: String) -> void:
	_tab = t
	_rebuild()


## 탭 글자색과 밑줄 (선택된 탭만 강조색 밑줄)
func _style_tabs() -> void:
	for t: String in _tab_buttons:
		var b: Button = _tab_buttons[t]
		var on: bool = t == _tab
		var color := Tokens.INK if on else (Tokens.INK_SOFT if t in READY_TABS else Color(Tokens.INK_SOFT, 0.4))
		for state in ["font_color", "font_hover_color", "font_pressed_color", "font_focus_color", "font_disabled_color"]:
			b.add_theme_color_override(state, color)
		var line := StyleBoxFlat.new()
		line.bg_color = Color(0, 0, 0, 0)
		line.border_color = Tokens.ACCENT
		line.border_width_bottom = 3 if on else 0
		line.content_margin_bottom = Tokens.SPACE_XS
		for state in ["normal", "hover", "pressed", "disabled", "focus"]:
			b.add_theme_stylebox_override(state, line)


# ---------- 페이지 ----------

func _rebuild() -> void:
	for c in _page_host.get_children():
		c.queue_free()
	_training_buttons.clear()
	match _tab:
		"선수":
			_page_host.add_child(_player_page())
		"훈련":
			_page_host.add_child(_training_page())
		"인물":
			_page_host.add_child(_people_page())
		"상점":
			_page_host.add_child(_shop_page())
		"스카우트":
			_page_host.add_child(_scout_page())
		"기록":
			_page_host.add_child(_records_page())
		_:
			_page_host.add_child(_home_page())
	_style_tabs()
	_rebuild_bottom()


func _home_page() -> Control:
	var row := UiKit.hbox(Tokens.SPACE_SM)
	var c1 := UiKit.vbox(0)
	c1.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	c1.size_flags_stretch_ratio = 1.15
	c1.add_child(_match_card())
	c1.add_child(_go_button())
	row.add_child(c1)
	for pair: Array in [[_overall_card(), _season_card()], [_scout_card(), _inbox_card()]]:
		var col := UiKit.vbox(Tokens.SPACE_SM)
		col.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		for c: Control in pair:
			c.size_flags_vertical = Control.SIZE_EXPAND_FILL
			col.add_child(c)
		row.add_child(col)
	return row


## 다음 경기: 대회·라운드, 우리 학교 vs 상대 (방패 + 순위), 상대 선발
func _match_card() -> Control:
	var career: CareerState = _app.career
	var card := _card()
	card.size_flags_vertical = Control.SIZE_EXPAND_FILL
	var col := UiKit.vbox(Tokens.SPACE_XS)
	card.add_child(col)
	var kind := "주말리그" if career.is_league() else "전국대회"
	var opp_head := career.opponent()
	var rival_tag := " · 라이벌 학교" if opp_head.id == career.rival.school_id and career.people.is_met("rival") else ""
	col.add_child(UiKit.kicker("NEXT MATCH · %d주차 · %s%s" % [career.week, "홈" if career.is_home() else "원정", rival_tag]))
	col.add_child(UiKit.title("%s %s" % [career.phase_name(), career.round_text()], Tokens.FONT_TITLE))
	col.add_child(UiKit.spacer())
	var opp := career.opponent()
	var vs := UiKit.hbox(Tokens.SPACE_MD)
	vs.alignment = BoxContainer.ALIGNMENT_CENTER
	vs.add_child(_team(career.my_school.short, career.my_school.name, _rank_text(career.my_school.name, kind), Tokens.UNIFORM_US))
	var v := UiKit.label("VS", Tokens.FONT_HERO, Color(Tokens.INK_SOFT, 0.6), Tokens.FONT_BOLD)
	v.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	vs.add_child(v)
	vs.add_child(_team(opp.short, opp.name, _rank_text(opp.name, kind), Tokens.UNIFORM_THEM))
	col.add_child(vs)
	col.add_child(UiKit.spacer())
	var pitcher: BattingConfig.PitcherProfile = _app.batting_config.pitchers[opp.pitcher_profile]
	col.add_child(UiKit.label("상대 선발 · %s 투수 · 전력 %s" % [pitcher.name, _stars(opp.strength)], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	return card


func _team(short: String, full: String, rank: String, color: Color) -> Control:
	var col := UiKit.vbox(2)
	var sh := Shapes.Shield.new(color, short)
	sh.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	col.add_child(sh)
	for l: Label in [UiKit.label(full, Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD), UiKit.label(rank, Tokens.FONT_CAPTION, Tokens.INK_SOFT)]:
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		col.add_child(l)
	return col


## 주말리그면 순위표의 순위, 전국대회면 "전국대회"
func _rank_text(school_name: String, kind: String) -> String:
	var career: CareerState = _app.career
	if not career.is_league():
		return kind
	var rows := career.standings_rows()
	for i in rows.size():
		if rows[i][0] == school_name or (rows[i][4] and school_name == career.my_school.name):
			return "%d위" % (i + 1)
	return "-"


## 경기 준비 버튼: 이번 주 훈련을 아직 안 골랐으면 훈련 탭으로, 골랐으면 경기로
func _go_button() -> Button:
	var p := _app.career.player as PlayerData
	if not _trained():
		return _wide_button("훈련 고르고 경기 준비  ›", func() -> void: _show_tab("훈련"))
	return _wide_button("경기 보기 (결장)  ›" if p.is_injured() else "경기 준비  ›", _app.show_game)


func _wide_button(text: String, on_press: Callable) -> Button:
	var b := UiKit.button(text, on_press, Tokens.ACCENT, Tokens.FONT_TITLE)
	b.alignment = HORIZONTAL_ALIGNMENT_LEFT
	b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	return b


## 종합 능력치 (네 능력치 평균) + 잠재력 범위(임시) + 능력치 막대
func _overall_card() -> Control:
	var career: CareerState = _app.career
	var p: PlayerData = career.player
	var card := _card(true)
	var col := UiKit.vbox(2)
	card.add_child(col)
	var head := UiKit.hbox()
	var l := UiKit.vbox(0)
	l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	l.add_child(UiKit.kicker("OVERALL"))
	l.add_child(UiKit.title(str(p.overall()), Tokens.FONT_NUMBER))
	head.add_child(l)
	var r := UiKit.vbox(0)
	var pk := UiKit.kicker("POTENTIAL")
	pk.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	r.add_child(pk)
	var pv := UiKit.title(career.scouting.potential_range(career.cfg, p), Tokens.FONT_TITLE, Tokens.INK_SOFT)
	pv.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	r.add_child(pv)
	head.add_child(r)
	col.add_child(head)
	var weeks := int(career.cfg.scouting["deltaWeeks"])
	var d := Scouting.delta(career.scouting.overall_history, weeks)
	var dt := ("▲ %d" % d if d > 0 else ("▼ %d" % -d if d < 0 else "― 0")) + " 최근 %d주" % weeks
	col.add_child(UiKit.label(dt, Tokens.FONT_CAPTION, Tokens.GOOD if d > 0 else (Tokens.BAD if d < 0 else Tokens.INK_SOFT), Tokens.FONT_BOLD))
	for s: String in PlayerData.STATS:
		col.add_child(_mini_bar(PlayerData.STAT_LABELS[s], p.stat(s)))
	return card


func _mini_bar(name: String, value: int) -> Control:
	var row := UiKit.hbox(Tokens.SPACE_SM)
	var n := UiKit.label(name, Tokens.FONT_CAPTION, Tokens.INK_SOFT)
	n.custom_minimum_size.x = 40
	row.add_child(n)
	var bar := ProgressBar.new()
	bar.max_value = 99
	bar.value = value
	bar.show_percentage = false
	bar.custom_minimum_size.y = 5
	bar.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	bar.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	var bg := StyleBoxFlat.new()
	bg.bg_color = Tokens.DIVIDER
	var fill := StyleBoxFlat.new()
	fill.bg_color = Tokens.PRIMARY
	bar.add_theme_stylebox_override("background", bg)
	bar.add_theme_stylebox_override("fill", fill)
	row.add_child(bar)
	var v := UiKit.label(str(value), Tokens.FONT_CAPTION, Tokens.INK, Tokens.FONT_BOLD)
	v.custom_minimum_size.x = 20
	v.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	row.add_child(v)
	return row


## 시즌 기록: 타율 · OPS · OPS+ (고교 평균 = 100) · 유망주 랭킹 (최근 변화)
func _season_card() -> Control:
	var career: CareerState = _app.career
	var st: PlayerData.SeasonStats = career.player.season
	var card := _card()
	var col := UiKit.vbox(2)
	card.add_child(col)
	col.add_child(UiKit.kicker("SEASON"))
	var row := UiKit.hbox(Tokens.SPACE_LG)
	var plus := st.ops_plus(career.cfg.league_avg)
	for item: Array in [[st.average(), "AVG", Tokens.INK], [st.ops(), "OPS", Tokens.INK], [str(plus) if plus >= 0 else "-", "OPS+", Tokens.ACCENT]]:
		var c := UiKit.vbox(0)
		c.add_child(UiKit.title(item[0], Tokens.FONT_TITLE, item[2]))
		c.add_child(UiKit.label(item[1], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
		row.add_child(c)
	col.add_child(row)
	var rank := UiKit.hbox(Tokens.SPACE_XS)
	rank.add_child(UiKit.label("유망주 랭킹 %d위" % career.prospect_rank(), Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	var rd := -Scouting.delta(career.scouting.rank_history, int(career.cfg.scouting["deltaWeeks"]))
	if rd != 0:
		rank.add_child(UiKit.label("%s%d" % ["▲" if rd > 0 else "▼", absi(rd)], Tokens.FONT_CAPTION, Tokens.GOOD if rd > 0 else Tokens.BAD, Tokens.FONT_BOLD))
	col.add_child(rank)
	return card


## 스카우트 리포트: 예상 지명·주목 구단·스카우트 관심도
func _scout_card() -> Control:
	var career: CareerState = _app.career
	var p: PlayerData = career.player
	var proj := career.projection()
	var teams := career.scouting.interested_teams(career.cfg, p.scout_interest)
	var card := _card()
	var col := UiKit.vbox(Tokens.SPACE_XS)
	card.add_child(col)
	col.add_child(UiKit.kicker("SCOUT REPORT"))
	var head := UiKit.hbox(Tokens.SPACE_XS)
	head.add_child(UiKit.title(proj["big"], Tokens.FONT_NUMBER - 8))
	var suffix := UiKit.label(proj["suffix"], Tokens.FONT_BODY, Tokens.INK_SOFT)
	suffix.size_flags_vertical = Control.SIZE_SHRINK_END
	head.add_child(suffix)
	col.add_child(head)
	var boxes := UiKit.hbox(Tokens.SPACE_XS)
	for t: Dictionary in teams.slice(0, 5):
		boxes.add_child(_letter_box(t["short"]))
	if teams.size() > 5:
		boxes.add_child(UiKit.label("+%d" % (teams.size() - 5), Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD))
	col.add_child(boxes)
	var note := "%d개 구단 주목 중" % teams.size() if not teams.is_empty() else "아직 지켜보는 구단 없음"
	col.add_child(UiKit.label("%s · 관심도 %d" % [note, p.scout_interest], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	return card


func _letter_box(t: String) -> Control:
	var box := PanelContainer.new()
	var s := StyleBoxFlat.new()
	s.bg_color = Color(0, 0, 0, 0)
	s.border_color = Tokens.INK_SOFT
	s.set_border_width_all(1)
	s.set_corner_radius_all(Tokens.SPACE_XS)
	s.content_margin_left = Tokens.SPACE_SM
	s.content_margin_right = Tokens.SPACE_SM
	box.add_theme_stylebox_override("panel", s)
	box.add_child(UiKit.label(t, Tokens.FONT_CAPTION, Tokens.INK, Tokens.FONT_BOLD))
	return box


## 받은 소식: 이번 주 훈련 + 지난 경기 뒤 소식 (라이벌 기록·유망주 랭킹·구단 관심)
func _inbox_card() -> Control:
	var career: CareerState = _app.career
	var items: Array = ["훈련 · " + career.week_training if _trained() else "%s · 이번 주는 뭘 할래? (훈련 하나)" % career.people.person("coach").call]
	items.append_array(career.news)
	var card := _card()
	var col := UiKit.vbox(Tokens.SPACE_XS)
	card.add_child(col)
	col.add_child(UiKit.kicker("INBOX · %d" % items.size()))
	for i in items.size():
		col.add_child(_inbox_line(items[i], i == 0))
	return card


func _inbox_line(text: String, fresh: bool) -> Control:
	var row := UiKit.hbox(Tokens.SPACE_SM)
	var stripe := ColorRect.new()
	stripe.color = Tokens.ACCENT if fresh else Tokens.DIVIDER
	stripe.custom_minimum_size.x = 3
	row.add_child(stripe)
	var l := UiKit.label(text, Tokens.FONT_CAPTION, Tokens.INK if fresh else Tokens.INK_SOFT, Tokens.FONT_BOLD if fresh else Tokens.FONT_REGULAR)
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(l)
	return row


## 아래 막대: 컨디션 · 폼 · 평판 · 행동(이번 주 훈련 0/1) / 다음 주(아직 경기를 해야 넘어간다)
func _rebuild_bottom() -> void:
	for c in _bottom.get_children():
		c.queue_free()
	var p: PlayerData = _app.career.player
	for item: Array in [["컨디션", str(p.condition)], ["폼", _form_text(p.form)], ["평판", str(p.reputation)], ["지갑", PlayerData.money_text(p.money)], ["행동", "%d/1" % (1 if _trained() else 0)]]:
		var c := UiKit.vbox(0)
		c.add_child(UiKit.label(item[0], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
		c.add_child(UiKit.label(item[1], Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD))
		_bottom.add_child(c)
	var gap := Control.new()
	gap.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_bottom.add_child(gap)
	var next := UiKit.button("다음 주", func() -> void: pass, Tokens.ACCENT)
	next.disabled = true
	next.tooltip_text = "경기를 마치면 다음 주로 넘어간다"
	next.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	_bottom.add_child(next)


# ---------- 훈련 탭 ----------

func _training_page() -> Control:
	var career: CareerState = _app.career
	var p := career.player
	var card := _card()
	var col := UiKit.vbox(Tokens.SPACE_SM)
	card.add_child(col)
	col.add_child(UiKit.kicker("TRAINING · 이번 주 하나"))
	if p.is_injured():
		col.add_child(UiKit.label("부상 중 (%d주 남음) — 이번 주는 쉬어야 한다" % p.injury_weeks, Tokens.FONT_BODY, Tokens.BAD, Tokens.FONT_BOLD))
	else:
		col.add_child(UiKit.label("감독: 이번 주는 뭘 할래? (하나만)", Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD))
	var grid := GridContainer.new()
	grid.columns = career.cfg.training.size()
	grid.add_theme_constant_override("h_separation", Tokens.SPACE_XS)
	for id: String in career.cfg.training:
		var t: Dictionary = career.cfg.training[id]
		var b := UiKit.button(t["label"], func() -> void: _train(id), Tokens.GOOD if id == "rest" else Tokens.PRIMARY, Tokens.FONT_CAPTION)
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		b.disabled = _trained() or (p.is_injured() and id != "rest")
		grid.add_child(b)
		_training_buttons.append(b)
	col.add_child(grid)
	if _trained():
		col.add_child(UiKit.label(_app.career.week_training, Tokens.FONT_BODY, Tokens.GOOD, Tokens.FONT_BOLD))
	col.add_child(UiKit.spacer())
	if _trained():
		col.add_child(_go_button())
	return card


func _train(id: String) -> void:
	_app.career.train(id)
	_rebuild()


## 이번 주 훈련을 했나 (CareerState 가 기억한다)
func _trained() -> bool:
	return _app.career.week_training != ""


# ---------- 선수 탭 ----------

func _player_page() -> Control:
	var career: CareerState = _app.career
	var p := career.player
	var card := _card()
	var col := UiKit.vbox(2)
	card.add_child(col)
	var head := UiKit.hbox(Tokens.SPACE_SM)
	head.add_child(UiKit.title(p.name))
	head.add_child(UiKit.label(_form_text(p.form), Tokens.FONT_CAPTION, Tokens.GOOD if p.form > 0.5 else (Tokens.BAD if p.form < -0.5 else Tokens.INK_SOFT), Tokens.FONT_BOLD))
	col.add_child(head)
	col.add_child(UiKit.label("%s %d학년 · 외야수 · %d번 타자" % [career.my_school.name, p.grade, p.lineup_slot], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	var colors := {"contact": Tokens.PRIMARY, "power": Tokens.ACCENT, "eye": Tokens.GOOD, "speed": Tokens.INK_SOFT}
	for s in PlayerData.STATS:
		col.add_child(UiKit.stat_bar(PlayerData.STAT_LABELS[s], p.stat(s), 99, colors[s]))
	col.add_child(UiKit.stat_bar("컨디션", p.condition, 100, Tokens.WARN))
	col.add_child(UiKit.stat_bar("스카우트", p.scout_interest, 100, Tokens.BAD))
	# 코치의 성장 여력 한마디 (정확한 잠재력은 숨김)
	var best := ""
	var best_gap := -999
	for s in PlayerData.STATS:
		var gap: int = p.potential[s] - p.stat(s)
		if gap > best_gap:
			best_gap = gap
			best = s
	col.add_child(UiKit.label("코치: %s은(는) %s" % [PlayerData.STAT_LABELS[best], Growth.room_text(p.stat(best), p.potential[best])], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	var st := p.season
	col.add_child(UiKit.label("시즌 %d승 %d패 · 타율 %s · %d홈런 %d타점" % [career.wins(), career.losses(), st.average(), st.home_runs, st.rbi], Tokens.FONT_CAPTION, Tokens.INK))
	return card


# ---------- 인물 탭 ----------

## 인물: 4열 카드 (역할 · 이름 · 관계 막대 · 한마디), 아래에 교류 버튼(선물 / 만나기, 한 주에 한 번). 못 만난 사람은 ???
func _people_page() -> Control:
	var career: CareerState = _app.career
	var e := career.cfg.economy
	var col := UiKit.vbox(Tokens.SPACE_XS)
	var head := UiKit.hbox(Tokens.SPACE_SM)
	var partner := career.people.person("partner")
	head.add_child(UiKit.kicker("PEOPLE · 교류는 한 주에 한 번 (선물 %s · 만나기 %s)" % [PlayerData.money_text(int(e["giftCost"])), PlayerData.money_text(int(e["hangoutCost"]))]))
	var gap := Control.new()
	gap.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	head.add_child(gap)
	var status := career.week_social if career.week_social != "" else ("♥ %s과(와) 연애 중" % partner.name if partner != null else "")
	head.add_child(UiKit.label(EventBook.josa(status), Tokens.FONT_CAPTION, Tokens.BAD if career.week_social == "" else Tokens.GOOD, Tokens.FONT_BOLD))
	col.add_child(head)
	var grid := GridContainer.new()
	grid.columns = 4
	grid.add_theme_constant_override("h_separation", Tokens.SPACE_SM)
	grid.add_theme_constant_override("v_separation", Tokens.SPACE_SM)
	grid.size_flags_vertical = Control.SIZE_EXPAND_FILL
	for id: String in career.people.by_id:
		grid.add_child(_person_card(career.people.by_id[id]))
	col.add_child(grid)
	return col


func _person_card(person: People.Person) -> Control:
	var career: CareerState = _app.career
	var dating := career.people.partner == person.id
	var card := _card(dating)
	card.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	card.size_flags_vertical = Control.SIZE_EXPAND_FILL
	var col := UiKit.vbox(2)
	card.add_child(col)
	var top := UiKit.hbox(Tokens.SPACE_XS)
	top.add_child(UiKit.label(person.role, Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD))
	if person.met:
		top.add_child(UiKit.label(("♥ 연애 중" if dating else "") if person.romance else "", Tokens.FONT_CAPTION, Tokens.BAD, Tokens.FONT_BOLD))
	col.add_child(top)
	if not person.met:
		col.add_child(UiKit.title("???", Tokens.FONT_LABEL, Tokens.INK_SOFT))
		col.add_child(UiKit.label("아직 만나지 않았다", Tokens.FONT_CAPTION, Tokens.INK_SOFT))
		return card
	col.add_child(UiKit.title(person.name, Tokens.FONT_LABEL))
	col.add_child(_mini_bar(person.rel_label, person.relation))
	var note := People.relation_text(person.relation)
	if person.id == "rival":
		note = "유망주 %d위 · 타율 %s" % [career.rival_rank(), career.rival.player.season.average()]
	elif person.team_name != "":
		note = person.team_name
	col.add_child(UiKit.label(note, Tokens.FONT_CAPTION, Tokens.PRIMARY, Tokens.FONT_BOLD))
	var buttons := UiKit.hbox(Tokens.SPACE_XS)
	if CareerState.can_gift(person):
		buttons.add_child(_social_button("선물", person.id, "gift"))
	if person.romance:
		buttons.add_child(_social_button("데이트" if dating else "만나기", person.id, "hangout"))
	col.add_child(buttons)
	return card


func _social_button(text: String, person_id: String, kind: String) -> Button:
	var b := UiKit.button(text, func() -> void:
		_app.career.socialize(person_id, kind)
		_rebuild(), Tokens.BAD if kind == "hangout" else Tokens.PRIMARY, Tokens.FONT_CAPTION)
	var why: String = _app.career.social_block(person_id, kind)
	b.disabled = why != ""
	b.tooltip_text = why
	b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	return b


# ---------- 상점 탭 ----------

## 장비(한 번 사면 계속, 경기 능력치 보정) | 관리(한 주에 한 번씩) | 지갑(돈 버는 법)
func _shop_page() -> Control:
	var career: CareerState = _app.career
	var p := career.player
	var row := UiKit.hbox(Tokens.SPACE_SM)

	var gear_card := _card(true)
	gear_card.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	gear_card.size_flags_stretch_ratio = 1.4
	var gc := UiKit.vbox(Tokens.SPACE_XS)
	gear_card.add_child(gc)
	var bonus := p.gear_bonus(career.cfg)
	var bonus_parts := []
	for st: String in bonus:
		bonus_parts.append("%s +%d" % [PlayerData.STAT_LABELS[st], bonus[st]])
	gc.add_child(UiKit.kicker("GEAR · 경기 보정 %s" % (" · ".join(bonus_parts) if not bonus_parts.is_empty() else "없음")))
	var gear: Dictionary = career.cfg.shop["gear"]
	for id: String in gear:
		if id.begins_with("_"):
			continue
		var item: Dictionary = gear[id]
		var line := UiKit.hbox(Tokens.SPACE_SM)
		var effects := []
		for st: String in item["bonus"]:
			effects.append("%s +%d" % [PlayerData.STAT_LABELS[st], item["bonus"][st]])
		var name := UiKit.label("%s  ·  %s" % [item["label"], " ".join(effects)], Tokens.FONT_CAPTION, Tokens.INK, Tokens.FONT_BOLD)
		name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		line.add_child(name)
		var owned := p.gear.has(id)
		var b := UiKit.button("보유" if owned else PlayerData.money_text(int(item["price"])), func() -> void:
			career.buy_gear(id)
			_rebuild(), Tokens.INK_SOFT if owned else Tokens.PRIMARY, Tokens.FONT_CAPTION)
		b.disabled = owned or p.money < int(item["price"])
		b.custom_minimum_size.x = 84
		line.add_child(b)
		gc.add_child(line)
	row.add_child(gear_card)

	var care := _card()
	care.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var cc := UiKit.vbox(Tokens.SPACE_XS)
	care.add_child(cc)
	cc.add_child(UiKit.kicker("CARE · 한 주에 한 번씩"))
	var services: Dictionary = career.cfg.shop["services"]
	var desc := {"lesson": "이번 주 훈련 한 번 더", "care": "컨디션↑ 부상 회복 당김", "meal": "컨디션↑ 폼↑"}
	for id: String in services:
		if id.begins_with("_"):
			continue
		var sv: Dictionary = services[id]
		var why := career.service_block(id)
		var b := UiKit.button("%s · %s" % [sv["label"], PlayerData.money_text(int(sv["price"]))], func() -> void:
			_last_shop_note = career.use_service(id)
			_rebuild(), Tokens.GOOD, Tokens.FONT_CAPTION)
		b.disabled = why != ""
		b.alignment = HORIZONTAL_ALIGNMENT_LEFT
		cc.add_child(b)
		cc.add_child(UiKit.label(why if why != "" else desc.get(id, ""), Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	if _last_shop_note != "" and not _last_shop_note.begins_with("!"):
		var note := UiKit.label(_last_shop_note, Tokens.FONT_CAPTION, Tokens.GOOD, Tokens.FONT_BOLD)
		note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		cc.add_child(note)
	row.add_child(care)

	var wallet := _card()
	wallet.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	wallet.size_flags_stretch_ratio = 0.9
	var wc := UiKit.vbox(Tokens.SPACE_XS)
	wallet.add_child(wc)
	wc.add_child(UiKit.kicker("WALLET"))
	wc.add_child(UiKit.title(PlayerData.money_text(p.money), Tokens.FONT_NUMBER - 8))
	var e := career.cfg.economy
	var how := UiKit.label("돈 버는 법\n· 용돈: 엄마와 사이가 좋을수록 많다\n· 칭찬 용돈: 안타 %s · 홈런 %s\n· 동문회 장학금: 리그 1위 %s · 전국대회 우승 %s" % [
		PlayerData.money_text(int(e["perHit"])), PlayerData.money_text(int(e["perHomeRun"])), PlayerData.money_text(int(e["leagueWinPrize"])), PlayerData.money_text(int(e["championPrize"]))],
		Tokens.FONT_CAPTION, Tokens.INK_SOFT)
	how.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	wc.add_child(how)
	row.add_child(wallet)
	return row


# ---------- 스카우트 탭 ----------

## 예상 지명·평가 | 유망주 랭킹 (내 주변) | 주목 구단
func _scout_page() -> Control:
	var career: CareerState = _app.career
	var p := career.player
	var row := UiKit.hbox(Tokens.SPACE_SM)
	var proj := career.projection()
	var left := _card(true)
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var lc := UiKit.vbox(Tokens.SPACE_XS)
	left.add_child(lc)
	lc.add_child(UiKit.kicker("DRAFT PROJECTION"))
	var head := UiKit.hbox(Tokens.SPACE_XS)
	head.add_child(UiKit.title(proj["big"], Tokens.FONT_NUMBER))
	var suffix := UiKit.label(proj["suffix"], Tokens.FONT_BODY, Tokens.INK_SOFT)
	suffix.size_flags_vertical = Control.SIZE_SHRINK_END
	head.add_child(suffix)
	lc.add_child(head)
	lc.add_child(UiKit.label("같은 학년 유망주 %d위 · 잠재력 %s" % [career.prospect_rank(), career.scouting.potential_range(career.cfg, p)], Tokens.FONT_CAPTION, Tokens.INK, Tokens.FONT_BOLD))
	lc.add_child(UiKit.stat_bar("관심도", p.scout_interest, 100, Tokens.BAD))
	lc.add_child(UiKit.stat_bar("평판", p.reputation, 100, Tokens.GOOD))
	var hint := UiKit.label("평가 = 종합 능력치 + 관심도 + 평판 + 성장 여력. 전국대회 활약이 관심도를 두 배로 올린다.", Tokens.FONT_CAPTION, Tokens.INK_SOFT)
	hint.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	lc.add_child(hint)
	row.add_child(left)

	var mid := _card()
	mid.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var mc := UiKit.vbox(2)
	mid.add_child(mc)
	mc.add_child(UiKit.kicker("PROSPECT RANKING · %d학년" % p.grade))
	var board := career.prospect_board()
	var me_rank := career.prospect_rank()
	var shown := {}
	for r: Dictionary in board:
		var rank: int = r["rank"]
		if rank <= 3 or absi(rank - me_rank) <= 2 or r["rival"]:
			shown[rank] = r
	var prev := 0
	for rank: int in shown:
		if rank > prev + 1:
			mc.add_child(UiKit.label("…", Tokens.FONT_CAPTION, Tokens.INK_SOFT))
		prev = rank
		var r: Dictionary = shown[rank]
		var color := Tokens.PRIMARY if r["me"] else (Tokens.ACCENT if r["rival"] else Tokens.INK)
		var tag := " (나)" if r["me"] else (" (라이벌)" if r["rival"] and career.people.is_met("rival") else "")
		mc.add_child(UiKit.label("%d. %s%s" % [rank, r["name"], tag], Tokens.FONT_CAPTION, color, Tokens.FONT_BOLD if r["me"] or r["rival"] else Tokens.FONT_REGULAR))
	row.add_child(mid)

	var right := _card()
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var rc := UiKit.vbox(Tokens.SPACE_XS)
	right.add_child(rc)
	var teams := career.scouting.interested_teams(career.cfg, p.scout_interest)
	rc.add_child(UiKit.kicker("WATCHING · %d/%d" % [teams.size(), career.cfg.pro_teams.size()]))
	if teams.is_empty():
		rc.add_child(UiKit.label("아직 지켜보는 구단이 없다", Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	var scout := career.people.person("scout")
	var grid := GridContainer.new()
	grid.columns = 2
	grid.add_theme_constant_override("h_separation", Tokens.SPACE_SM)
	for t: Dictionary in teams:
		var mine: bool = scout.met and t["name"] == scout.team_name
		grid.add_child(UiKit.label(t["name"] + (" ★" if mine else ""), Tokens.FONT_CAPTION, Tokens.ACCENT if mine else Tokens.INK, Tokens.FONT_BOLD))
	rc.add_child(grid)
	row.add_child(right)
	return row


# ---------- 기록 탭 ----------

## 기록실과 같은 화면 (요약 · 시즌 · 경기 · 대회 · 분할 · 이야기)
func _records_page() -> Control:
	return RecordsView.new(_app.career, _app.batting_config.pitchers)


# ---------- 공통 ----------

## 흰 카드 (모서리 작게). stripe 면 왼쪽에 강조 띠
func _card(stripe: bool = false) -> PanelContainer:
	var card := UiKit.card()
	var s := card.get_theme_stylebox("panel").duplicate() as StyleBoxFlat
	s.set_corner_radius_all(Tokens.SPACE_SM)
	s.set_content_margin_all(Tokens.SPACE_SM + Tokens.SPACE_XS)
	if stripe:
		s.border_color = Tokens.PRIMARY
		s.border_width_left = 4
	card.add_theme_stylebox_override("panel", s)
	return card


func _form_text(f: float) -> String:
	if f > 1.5:
		return "▲▲ 불타는 중"
	if f > 0.5:
		return "▲ 상승세"
	if f < -1.5:
		return "▼▼ 깊은 슬럼프"
	if f < -0.5:
		return "▼ 슬럼프"
	return "― 보통"


func _stars(strength: float) -> String:
	var n := clampi(roundi((strength - 30.0) / 8.0), 1, 5)
	return "★".repeat(n) + "☆".repeat(5 - n)
