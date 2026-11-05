class_name ProHubScreen
extends Control
## 프로 편 허브. 탭: 이번 주 · 순위 · 개인 기록 · 뉴스 · 피치톡(SNS) · 구단·기록.
## 이번 주: 훈련 하나 고르기 → [경기 시작] (내가 직접 하는 1경기 — 고교와 같은 경기 화면) → 결과 화면 → 다음 주.
##   직접 할 경기가 없는 주(부상·가을야구 탈락)는 [이번 주 진행] 으로 자동 진행한다.
## 이야기 카드(주 시작·경기 뒤)와 기념 순간(첫 안타·첫 홈런 … — 컷신, 그림이 없으면 "컷신 자리" 카드)은 허브 위에 차례로 뜬다.
## 화면 코드는 로직(ProCareer·ProLeague)을 읽어 그리기만 한다 (원칙 1). 색·간격은 Tokens.

const TABS := ["week", "standings", "leaders", "news", "sns", "club"]
const TAB_LABELS := {"week": "이번 주", "standings": "순위", "leaders": "개인 기록", "news": "뉴스", "sns": "피치톡", "club": "구단·기록"}

var _app
var _tab := "week"
var _body: VBoxContainer
var _overlay: Control
var _phone: PhoneView
var _notes: Array[String] = []


func _init(app) -> void:
	_app = app


func _ready() -> void:
	_rebuild()
	_next_story()


func _career() -> CareerState:
	return _app.career


func _pro() -> ProCareer:
	return _app.career.pro


func _rebuild() -> void:
	for c in get_children():
		if c != _overlay and c != _phone:
			c.queue_free()
	var bg := ColorRect.new()
	bg.color = Tokens.BACKGROUND
	add_child(UiKit.fill_parent(bg))
	move_child(bg, 0)
	var root := UiKit.vbox(Tokens.SPACE_SM)
	var m := UiKit.margin(root, Tokens.SPACE_MD)
	add_child(UiKit.fill_parent(m))
	move_child(m, 1)
	root.add_child(_top_bar())
	root.add_child(_tab_bar())
	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	_body = UiKit.vbox(Tokens.SPACE_SM)
	_body.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(_body)
	root.add_child(scroll)
	match _tab:
		"week": _week_page()
		"standings": _standings_page()
		"leaders": _leaders_page()
		"news": _feed_page("news")
		"sns": _feed_page("sns")
		"club": _club_page()


func _top_bar() -> Control:
	var career := _career()
	var pro := _pro()
	var bar := UiKit.hbox(Tokens.SPACE_MD)
	bar.add_child(UiKit.button("‹ 타이틀", _app.show_title, Tokens.INK_SOFT, Tokens.FONT_CAPTION))
	var col := UiKit.vbox(0)
	col.add_child(UiKit.kicker("%s %s · 프로 %d년 차" % [pro.team().city, pro.team().name, pro.year()]))
	col.add_child(UiKit.title("%s · %s" % [career.player.name, pro.phase_text(career.cfg)], Tokens.FONT_LABEL))
	col.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	bar.add_child(col)
	bar.add_child(UiKit.label("인기 %d · 지갑 %s" % [pro.fame, ProCareer.money_text(career.player.money)], Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD))
	var unread := career.phone.total_unread()
	bar.add_child(UiKit.button("폰%s" % (" (%d)" % unread if unread > 0 else ""), open_phone, Tokens.PRIMARY, Tokens.FONT_CAPTION))
	return bar


func _tab_bar() -> Control:
	var row := UiKit.hbox(Tokens.SPACE_XS)
	for t: String in TABS:
		var label: String = TAB_LABELS[t]
		var pro := _pro()
		if t == "news" and pro.feed.news().any(func(it: Dictionary) -> bool: return not it["read"]):
			label += " •"
		if t == "sns" and pro.feed.sns().any(func(it: Dictionary) -> bool: return not it["read"]):
			label += " •"
		var b := UiKit.button(label, _show_tab.bind(t), Tokens.ACCENT if t == _tab else Tokens.NAVY_SOFT, Tokens.FONT_CAPTION)
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(b)
	return row


func _show_tab(t: String) -> void:
	_tab = t
	if t == "news" or t == "sns":
		for it: Dictionary in _pro().feed.items:
			if it["kind"] == t:
				it["read"] = true
	_rebuild()


# ---------- 이번 주 ----------

func _week_page() -> void:
	var career := _career()
	var pro := _pro()
	var cfg := career.cfg
	var p := career.player
	var card := UiKit.card()
	var col := UiKit.vbox(Tokens.SPACE_XS)
	card.add_child(col)
	_body.add_child(card)
	var t := pro.team()
	col.add_child(UiKit.kicker("%s %d위 · %d승 %d패 %d무 (승률 %s)" % [t.name, pro.league.rank_of(t.id), t.w, t.l, t.d, t.pct_text()]))
	var level := "1군 %d번 유격수" % p.lineup_slot if pro.is_first() else pro.level_text()
	if pro.level == ProCareer.LEVEL_MILITARY:
		level += " — 전역까지 %d주" % pro.service_weeks_left
	col.add_child(UiKit.label("%s · 컨디션 %d%s" % [level, p.condition, " · " + p.injury_text() + " %d주" % p.injury_weeks if p.is_injured() else ""],
		Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD))
	# 감독 신뢰: 1군은 강등 기준, 퓨처스는 콜업 기준을 보여 주고, 기준을 넘은 채 지켜보는 중이면 몇 경기째인지
	if pro.level != ProCareer.LEVEL_MILITARY:
		var tc: Dictionary = cfg.pro["trust"]
		col.add_child(UiKit.stat_bar("감독 신뢰", int(pro.trust), 100, Tokens.GOOD if pro.trust >= float(tc["callupAbove"]) else (Tokens.BAD if pro.trust < float(tc["demoteBelow"]) else Tokens.WARN)))
		var ws := pro.watch_state(cfg)
		var watch := ""
		match ws["kind"]:
			"demote":
				watch = "감독이 지켜보는 중 — %d/%d경기 (신뢰 %d 아래면 퓨처스행)" % [ws["games"], ws["need"], int(tc["demoteBelow"])]
			"callup":
				watch = "1군 코치진이 지켜보는 중 — %d/%d경기 (신뢰 %d 이상 유지하면 콜업)" % [ws["games"], ws["need"], int(tc["callupAbove"])]
			_:
				watch = "강등 기준 %d · 콜업 기준 %d" % [int(tc["demoteBelow"]), int(tc["callupAbove"])]
		col.add_child(UiKit.label(watch, Tokens.FONT_CAPTION, Tokens.BAD if ws["kind"] == "demote" else (Tokens.GOOD if ws["kind"] == "callup" else Tokens.INK_SOFT), Tokens.FONT_BOLD))
	col.add_child(UiKit.label("1군 시즌: " + pro.me.line_text(), Tokens.FONT_BODY, Tokens.PRIMARY, Tokens.FONT_BOLD))
	if pro.service_line.pa > 0 and pro.level == ProCareer.LEVEL_MILITARY:
		var sl := pro.service_line
		col.add_child(UiKit.label("상무: 타율 %s · %d홈런 (%d타석)" % [sl.average(), sl.home_runs, sl.pa], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	if not pro.is_first() or pro.futures_season.pa > 0:
		var f := pro.futures_season
		col.add_child(UiKit.label("퓨처스: 타율 %s · %d홈런 · OPS %s (%d타석)" % [f.average(), f.home_runs, f.ops(), f.pa], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	col.add_child(UiKit.label("연봉 %s · FA 등록 %d/%d시즌%s · 병역 %s" % [ProCareer.money_text(pro.salary), pro.fa_seasons, ProFA.required(cfg, pro),
		(" · 계약 %d년 남음" % pro.contract_years_left) if pro.contract_years_left > 0 else "", _military_text(pro)], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	# 경기 결과 (이번 주를 마쳤으면)
	if career.game_done:
		var res := UiKit.card()
		var rc := UiKit.vbox(Tokens.SPACE_XS)
		res.add_child(rc)
		rc.add_child(UiKit.kicker("이번 주 경기"))
		for g: Dictionary in pro.week_games:
			var ln: PlayerData.SeasonStats = g["line"]
			var mark: String = ["패", "무", "승"][int(g["result"]) + 1]
			rc.add_child(UiKit.label("%s %s %s %d:%d%s%s" % ["★" if g["direct"] else "·", "홈" if g["home"] else "원정", g["opp"], g["my"], g["their"],
				" " + mark, "  — " + ln.line_text() if ln != null else ""], Tokens.FONT_CAPTION,
				Tokens.GOOD if int(g["result"]) > 0 else (Tokens.BAD if int(g["result"]) < 0 else Tokens.INK_SOFT)))
		for n: String in _notes:
			rc.add_child(UiKit.label(n, Tokens.FONT_CAPTION, Tokens.ACCENT, Tokens.FONT_BOLD))
		_body.add_child(res)
		var next := UiKit.button("다음 주 ▶", _app.next_week, Tokens.ACCENT, Tokens.FONT_LABEL)
		_body.add_child(next)
		return
	# 훈련 (한 주에 하나 — 고교 스케줄과 같은 규칙, 칸 셋을 모두 같은 훈련으로)
	if career.week_training == "":
		col.add_child(UiKit.kicker("이번 주 훈련"))
		var row := UiKit.hbox(Tokens.SPACE_XS)
		for id: String in cfg.training:
			var b := UiKit.button(str(cfg.training[id]["label"]), func() -> void:
				career.train(id)
				_app.save_career()
				_rebuild(), Tokens.NAVY_SOFT, Tokens.FONT_CAPTION)
			b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			row.add_child(b)
		col.add_child(row)
	else:
		col.add_child(UiKit.label("훈련: " + career.week_training, Tokens.FONT_CAPTION, Tokens.GOOD, Tokens.FONT_BOLD))
	var opp := pro.next_opponent(cfg)
	if pro.has_direct_game(career) and opp != null:
		var vs := "%s (%d위)" % [opp.name, pro.league.rank_of(opp.id)]
		var label := "퓨처스 경기 시작 ▶" if not pro.is_first() and pro.phase == ProCareer.PHASE_REGULAR else "경기 시작 ▶ vs " + vs
		var go := UiKit.button(label, _app.show_game, Tokens.ACCENT, Tokens.FONT_LABEL)
		go.disabled = career.week_training == ""
		_body.add_child(go)
		_body.add_child(UiKit.label("이번 주 %d경기 중 첫 경기를 직접 한다. 나머지는 능력치대로 자동 진행된다." % int(cfg.pro["gamesPerWeek"]),
			Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	else:
		var why := "부상으로 이번 주는 쉰다" if career.player.is_injured() else "이번 주는 내가 나갈 경기가 없다"
		_body.add_child(UiKit.label(why, Tokens.FONT_CAPTION, Tokens.INK_SOFT))
		var go := UiKit.button("이번 주 진행 ▶", _sim_week, Tokens.ACCENT, Tokens.FONT_LABEL)
		_body.add_child(go)


## 직접 할 경기가 없는 주
func _sim_week() -> void:
	var career := _career()
	if career.game_done:
		return
	career.game_done = true
	_notes = career.pro.finish_week(career, null)
	_app.save_career()
	_rebuild()
	_next_story()


# ---------- 순위 ----------

func _standings_page() -> void:
	var pro := _pro()
	var card := UiKit.card()
	var col := UiKit.vbox(Tokens.SPACE_XS)
	card.add_child(col)
	_body.add_child(card)
	col.add_child(UiKit.kicker("KBA 정규시즌 순위 · %d년" % pro.year()))
	var rows := pro.league.standings()
	for i in rows.size():
		var t: ProTeam = rows[i]
		var streak := ("%d연승" % t.streak) if t.streak > 1 else (("%d연패" % -t.streak) if t.streak < -1 else "")
		var gb := pro.league.games_behind(t)
		var text := "%2d  %s  %d승 %d패 %d무  %s  %s  %s" % [i + 1, t.name, t.w, t.l, t.d, t.pct_text(), "-" if gb == 0.0 else "%.1f" % gb, streak]
		var mine := t.id == pro.team_id
		col.add_child(UiKit.label(text, Tokens.FONT_CAPTION, Tokens.PRIMARY if mine else Tokens.INK, Tokens.FONT_BOLD if mine or i < 5 else Tokens.FONT_REGULAR))
		if i == 4:
			col.add_child(UiKit.divider())
	if not pro.league.post_results.is_empty():
		var pc := UiKit.card()
		var pcol := UiKit.vbox(Tokens.SPACE_XS)
		pc.add_child(pcol)
		pcol.add_child(UiKit.kicker("포스트시즌"))
		for r: Dictionary in pro.league.post_results:
			var name: String = _app.career.cfg.pro["postseason"][r["round"]]["name"]
			pcol.add_child(UiKit.label("%s — %s 승 (%d:%d) vs %s" % [name, pro.league.team(r["winner"]).name, maxi(r["wins"][0], r["wins"][1]), mini(r["wins"][0], r["wins"][1]),
				pro.league.team(r["lo"] if r["winner"] == r["hi"] else r["hi"]).name], Tokens.FONT_CAPTION))
		_body.add_child(pc)


# ---------- 개인 기록 ----------

func _leaders_page() -> void:
	var career := _career()
	var pro := _pro()
	var names: Dictionary = career.cfg.pro_content["statNames"]
	var grid := GridContainer.new()
	grid.columns = 2
	grid.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_body.add_child(grid)
	for stat: String in ["avg", "hr", "rbi", "hits", "sb", "era", "wins", "so"]:
		var card := UiKit.card()
		card.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var col := UiKit.vbox(0)
		card.add_child(col)
		col.add_child(UiKit.kicker(names[stat] + (" (규정 타석)" if stat == "avg" else (" (규정 이닝)" if stat == "era" else ""))))
		var rows := pro.league.leaders(career.cfg, stat, 5)
		for i in rows.size():
			var p: ProPlayer = rows[i]["player"]
			var color := Tokens.PRIMARY if p.is_me else (Tokens.ACCENT if p.is_rival else Tokens.INK)
			col.add_child(UiKit.label("%d. %s (%s) %s" % [i + 1, p.name, pro.league.team(p.team_id).short, rows[i]["text"]], Tokens.FONT_CAPTION, color,
				Tokens.FONT_BOLD if p.is_me or p.is_rival else Tokens.FONT_REGULAR))
		if not stat in ["era", "wins", "so"]:
			var r := pro.league.leader_rank(career.cfg, stat, pro.me)
			if r > 5:
				col.add_child(UiKit.label("… %d. %s (나)" % [r, career.player.name], Tokens.FONT_CAPTION, Tokens.PRIMARY, Tokens.FONT_BOLD))
		grid.add_child(card)
	var rv := pro.league.rival_player()
	if rv != null:
		_body.add_child(UiKit.label("라이벌 %s (%s): %s" % [rv.name, pro.league.team(rv.team_id).name, rv.line_text()], Tokens.FONT_CAPTION, Tokens.ACCENT, Tokens.FONT_BOLD))


# ---------- 뉴스 · SNS ----------

func _feed_page(kind: String) -> void:
	var pro := _pro()
	var list: Array = pro.feed.news() if kind == "news" else pro.feed.sns()
	if list.is_empty():
		_body.add_child(UiKit.label("아직 소식이 없다", Tokens.FONT_CAPTION, Tokens.INK_SOFT))
		return
	for it: Dictionary in list.slice(0, 30):
		var card := UiKit.card()
		var col := UiKit.vbox(0)
		card.add_child(col)
		col.add_child(UiKit.kicker("%s · %d년 차" % [it["source"], int(it["year"])]))
		var title := UiKit.label(it["title"], Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD)
		title.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		col.add_child(title)
		if kind == "sns":
			col.add_child(UiKit.label("♥ %d" % int(it["likes"]), Tokens.FONT_CAPTION, Tokens.BAD, Tokens.FONT_BOLD))
			for c: String in it["comments"]:
				col.add_child(UiKit.label("  └ " + c, Tokens.FONT_CAPTION, Tokens.INK_SOFT))
		_body.add_child(card)


# ---------- 구단 · 기록 ----------

func _club_page() -> void:
	var career := _career()
	var pro := _pro()
	var t := pro.team()
	var card := UiKit.card()
	var col := UiKit.vbox(Tokens.SPACE_XS)
	card.add_child(col)
	_body.add_child(card)
	col.add_child(UiKit.kicker("%s · %s · 감독 %s · 주장 %s" % [t.name, t.style, _team_def(t.id).get("coach", ""), _team_def(t.id).get("captain", "")]))
	col.add_child(UiKit.label("내 계약: %s · 계약금 %s · 연봉 %s" % [pro.pick_text, ProCareer.money_text(pro.bonus), ProCareer.money_text(pro.salary)], Tokens.FONT_CAPTION))
	col.add_child(UiKit.label("구단 역사", Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD))
	if t.history.is_empty():
		col.add_child(UiKit.label("올해가 첫 시즌이다", Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	for h: Dictionary in t.history:
		col.add_child(UiKit.label("%d년: %d승 %d패 %d무 · %d위 %s" % [h["year"], h["w"], h["l"], h["d"], h["rank"], h["post"]], Tokens.FONT_CAPTION))
	var labels := {"hr": "한 시즌 최다 홈런", "hits": "한 시즌 최다 안타", "avg": "한 시즌 최고 타율", "wins": "한 시즌 최다승", "so": "한 시즌 최다 탈삼진"}
	for k: String in labels:
		if t.bests.has(k):
			var b: Dictionary = t.bests[k]
			var v := PlayerData.SeasonStats.rate_text(float(b["value"])) if k == "avg" else str(int(b["value"]))
			col.add_child(UiKit.label("%s: %s %s (%d년)" % [labels[k], b["name"], v, b["year"]], Tokens.FONT_CAPTION))
	var me := UiKit.card()
	var mc := UiKit.vbox(Tokens.SPACE_XS)
	me.add_child(mc)
	mc.add_child(UiKit.label("내 프로 기록", Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD))
	for h: Dictionary in pro.me.history:
		mc.add_child(UiKit.label("%d년 (%s): %s" % [h["year"], h["team"], h["line"]], Tokens.FONT_CAPTION))
	mc.add_child(UiKit.label("올해: " + pro.me.line_text(), Tokens.FONT_CAPTION, Tokens.PRIMARY))
	var total := PlayerData.SeasonStats.new()
	total.merge(pro.me.career)
	total.merge(pro.me.season)
	mc.add_child(UiKit.label("통산: 타율 %s · %d안타 %d홈런 %d타점 %d도루 (%d경기)" % [total.average(), total.hits, total.home_runs, total.rbi, total.steals, total.games],
		Tokens.FONT_CAPTION, Tokens.INK, Tokens.FONT_BOLD))
	var mine := pro.league.awards.filter(func(a: Dictionary) -> bool: return a["id"] == pro.me.id)
	if not mine.is_empty():
		mc.add_child(UiKit.label("수상: " + ", ".join(mine.map(func(a: Dictionary) -> String: return "%d년 %s" % [a["year"], a["award"]])), Tokens.FONT_CAPTION, Tokens.ACCENT, Tokens.FONT_BOLD))
	for n: Dictionary in pro.national:
		mc.add_child(UiKit.label("%d년 %s 국가대표 — %s · %s" % [n["year"], n["name"], n["result"], (n["line"] as PlayerData.SeasonStats).line_text()], Tokens.FONT_CAPTION, Tokens.ACCENT))
	mc.add_child(UiKit.label("기념 순간: %d개" % pro.milestones_done.size(), Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	_body.add_child(me)
	var lg := UiKit.card()
	var lc := UiKit.vbox(Tokens.SPACE_XS)
	lg.add_child(lc)
	lc.add_child(UiKit.label("KBA 역대 우승 · 주요 시상", Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD))
	for c: Dictionary in pro.league.champions:
		lc.add_child(UiKit.label("%d년 우승 %s (준우승 %s)" % [c["year"], pro.league.team(c["team"]).name, pro.league.team(c["runner"]).name], Tokens.FONT_CAPTION))
	for a: Dictionary in pro.league.awards:
		if a["award"] in ["MVP", "신인왕", "한국시리즈 MVP"]:
			lc.add_child(UiKit.label("%d년 %s — %s (%s) %s" % [a["year"], a["award"], a["name"], a["team"], a["line"]], Tokens.FONT_CAPTION))
	_body.add_child(lg)


static func _military_text(pro: ProCareer) -> String:
	var names := {ProService.ENLISTING: "다음 시즌 입대", ProService.SANGMU: "상무 복무 중", ProService.ACTIVE: "현역 복무 중",
		ProService.DONE: "복무 마침", ProService.EXEMPT: "면제"}
	return names.get(pro.military, "미필")


func _team_def(id: String) -> Dictionary:
	for d: Dictionary in _career().cfg.pro_teams:
		if d.get("id", "") == id:
			return d
	return {}


# ---------- 위에 뜨는 것: 기념 순간 · 이야기 카드 · 폰 ----------

## 기념 순간(컷신)이 먼저, 그다음 이야기 카드 (주 시작 또는 경기 뒤). 다 끝나면 허브를 다시 그린다
func _next_story() -> void:
	if _overlay != null:
		return
	var career := _career()
	var pro := _pro()
	if not pro.pending_scenes.is_empty():
		var m: Dictionary = pro.pending_scenes.pop_front()
		_app.save_career()
		if Milestones.has_cutscene(career.cfg, m):
			var cs := CutsceneView.new(career, m["cutscene"])
			_overlay = cs
			add_child(UiKit.fill_parent(cs))
			cs.finished.connect(_close_overlay)
		else:
			_overlay = _placeholder(m)
			add_child(UiKit.fill_parent(_overlay))
		return
	var events: Array = career.events("after" if career.game_done else "start")
	if events.is_empty():
		return
	var view := DialogueView.new(career, events[0])
	_overlay = view
	add_child(UiKit.fill_parent(view))
	view.finished.connect(_close_overlay)


func _close_overlay() -> void:
	if _overlay != null:
		_overlay.queue_free()
		_overlay = null
	_app.save_career()
	_rebuild()
	_next_story()


## 그림이 아직 없는 컷신의 자리: 검은 화면 + 장면 설명 한 줄 + 컷신 id (docs/scenario/pro-cutscene-list.md)
func _placeholder(m: Dictionary) -> Control:
	var veil := ColorRect.new()
	veil.color = Tokens.STAGE_FRAME
	var center := CenterContainer.new()
	veil.add_child(UiKit.fill_parent(center))
	var col := UiKit.vbox(Tokens.SPACE_SM)
	col.custom_minimum_size.x = 560
	center.add_child(col)
	col.add_child(UiKit.kicker("컷신 자리 · %s" % m["cutscene"], Tokens.HUD_TEXT_SOFT))
	col.add_child(UiKit.title(m["title"], Tokens.FONT_TITLE, Tokens.HUD_TEXT))
	var line := UiKit.label(m["line"], Tokens.FONT_BODY, Tokens.HUD_TEXT)
	line.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	col.add_child(line)
	col.add_child(UiKit.button("확인", _close_overlay, Tokens.ACCENT, Tokens.FONT_LABEL))
	return veil


## 지금 떠 있는 것 (스크린샷·테스트용)
func overlay() -> Control:
	return _overlay


func open_phone() -> void:
	if _phone != null or _overlay != null:
		return
	_phone = PhoneView.new(_career())
	add_child(UiKit.fill_parent(_phone))
	_phone.changed.connect(_app.save_career)
	_phone.closed.connect(func() -> void:
		_phone.queue_free()
		_phone = null
		_rebuild())
