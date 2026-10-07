class_name RecordsView
extends VBoxContainer
## 기록 화면 (허브 "기록" 탭과 로비 "기록실"이 같이 쓴다).
## 위: 작은 탭 (요약 · 시즌 · 경기 · 대회 · 분할 · 이야기), 경기·분할은 시즌 고르기 칩 (통산 · N학년)
## 요약: 고교 통산 큰 숫자 + 최고 기록 | 시즌: 시즌별 세부 기록 + 통산 합계 | 경기: 경기마다 내 성적
## 대회: 대회별 결과와 내 성적 | 분할: 투수 성향 · 카운트 · 타구 종류 · 타구 방향 | 이야기: 지난 이벤트

const TABS := ["요약", "시즌", "경기", "대회", "분할", "이야기"]

var _career: CareerState
## 투구 성향 id -> BattingConfig.PitcherProfile (분할 기록 칸 이름과 순서)
var _pitchers: Dictionary
var _tab := "요약"
## 고른 시즌 (0 = 통산 / 전체)
var _season := 0
var _host: Control


func _init(career: CareerState, pitchers: Dictionary, start_tab: String = "요약") -> void:
	_career = career
	_pitchers = pitchers
	_tab = start_tab
	add_theme_constant_override("separation", Tokens.SPACE_SM)
	size_flags_vertical = Control.SIZE_EXPAND_FILL
	size_flags_horizontal = Control.SIZE_EXPAND_FILL


func _ready() -> void:
	_rebuild()


## 탭 바꾸기 (스크린샷 스크립트도 부른다)
func show_tab(t: String, season_no: int = 0) -> void:
	_tab = t
	_season = season_no
	_rebuild()


func _rebuild() -> void:
	for c in get_children():
		c.queue_free()
	var bar := UiKit.hbox(Tokens.SPACE_XS)
	for t: String in TABS:
		bar.add_child(_chip(t, t == _tab, func() -> void: show_tab(t)))
	if _tab in ["경기", "분할"]:
		var gap := Control.new()
		gap.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		bar.add_child(gap)
		for pair: Array in _season_choices():
			var sn: int = pair[0]
			bar.add_child(_chip(pair[1], sn == _season, func() -> void: show_tab(_tab, sn), Tokens.ACCENT))
	add_child(bar)
	_host = PanelContainer.new()
	_host.add_theme_stylebox_override("panel", StyleBoxEmpty.new())
	_host.size_flags_vertical = Control.SIZE_EXPAND_FILL
	add_child(_host)
	match _tab:
		"시즌": _host.add_child(_seasons_page())
		"경기": _host.add_child(_games_page())
		"대회": _host.add_child(_phases_page())
		"분할": _host.add_child(_splits_page())
		"이야기": _host.add_child(_story_page())
		_: _host.add_child(_summary_page())


## [[0, "통산"], [1, "1학년"], ...] — 지난 시즌 + 이번 시즌
func _season_choices() -> Array:
	var out := [[0, "통산" if _tab == "분할" else "전체"]]
	for h: Dictionary in _career.history:
		out.append([int(h["season_no"]), "%d학년" % int(h["grade"])])
	out.append([_career.season_no, "%d학년" % _career.player.grade])
	return out


# ---------- 요약 ----------

## 고교 통산 큰 숫자 (타율·홈런·타점·OPS·OPS+) | 최고 기록
func _summary_page() -> Control:
	var total := _career.career_line()
	var row := UiKit.hbox(Tokens.SPACE_SM)
	var left := _card(true)
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	left.size_flags_stretch_ratio = 1.3
	var lc := UiKit.vbox(Tokens.SPACE_XS)
	left.add_child(lc)
	lc.add_child(UiKit.kicker("고교 통산 · %d시즌 %d경기" % [_career.history.size() + 1, total.games]))
	var big := UiKit.hbox(Tokens.SPACE_LG)
	for item: Array in [[total.average(), "타율"], [str(total.home_runs), "홈런"], [str(total.rbi), "타점"], [total.ops(), "OPS"],
			[total.ops_plus_text(_career.cfg.league_avg), "OPS+"]]:
		var c := UiKit.vbox(0)
		c.add_child(UiKit.title(item[0], Tokens.FONT_HERO, Tokens.ACCENT if item[1] == "OPS+" else Tokens.INK))
		c.add_child(UiKit.label(item[1], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
		big.add_child(c)
	lc.add_child(big)
	lc.add_child(UiKit.label("%d타석 %d타수 %d안타 · 2루타 %d · 3루타 %d · 볼넷 %d · 삼진 %d" % [total.pa, total.ab, total.hits, total.doubles,
		total.triples, total.walks, total.strikeouts], Tokens.FONT_CAPTION, Tokens.INK))
	lc.add_child(UiKit.label("출루율 %s · 장타율 %s" % [total.obp_text(), total.slg_text()], Tokens.FONT_CAPTION, Tokens.INK))
	row.add_child(left)

	var right := _card()
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var rc := UiKit.vbox(Tokens.SPACE_XS)
	right.add_child(rc)
	rc.add_child(UiKit.kicker("최고 기록"))
	var b := _career.records.bests()
	for pair: Array in [["hits", "한 경기 최다 안타", "%d안타"], ["rbi", "한 경기 최다 타점", "%d타점"], ["home_runs", "한 경기 최다 홈런", "%d홈런"]]:
		var g: CareerRecords.GameEntry = b[pair[0]]
		var v := "-" if g == null else "%s  (%d학년 %d주 vs %s)" % [pair[2] % int(g.line.get(pair[0])), g.grade, g.week, g.opponent]
		rc.add_child(_best_line(pair[1], v))
	rc.add_child(_best_line("멀티히트 경기", "%d경기" % b["multi_hit"]))
	rc.add_child(_best_line("최장 연속 경기 안타", "%d경기%s" % [b["streak"], " (지금 %d경기째)" % b["current_streak"] if b["current_streak"] > 0 else ""]))
	row.add_child(right)
	return row


func _best_line(name: String, value: String) -> Control:
	var line := UiKit.hbox(Tokens.SPACE_SM)
	var n := UiKit.label(name, Tokens.FONT_CAPTION, Tokens.INK_SOFT)
	n.custom_minimum_size.x = 120
	line.add_child(n)
	line.add_child(UiKit.label(value, Tokens.FONT_CAPTION, Tokens.INK, Tokens.FONT_BOLD))
	return line


# ---------- 시즌 ----------

const SEASON_HEADERS := ["", "경기", "타석", "타수", "안타", "2루타", "3루타", "홈런", "타점", "볼넷", "삼진", "타율", "출루율", "장타율", "OPS", "OPS+"]


## 시즌별 세부 기록 + 고교 통산 줄, 아래에 시즌별 팀 성적
func _seasons_page() -> Control:
	var card := _card()
	var col := UiKit.vbox(Tokens.SPACE_XS)
	card.add_child(col)
	col.add_child(UiKit.kicker("SEASONS · 시즌별 기록"))
	var rows := []
	var styles := []
	var teams := []
	for h: Dictionary in _career.history:
		rows.append(_season_cells("%d학년" % h["grade"], h["line"]))
		styles.append(0)
		teams.append("%d학년 %s · 유망주 %d위" % [h["grade"], h["record"], h["rank"]])
	rows.append(_season_cells("%d학년" % _career.player.grade, _career.player.season))
	styles.append(1)
	teams.append("%d학년 %d승 %d패 %d무 (진행 중)" % [_career.player.grade, _career.wins(), _career.losses(), _career.results.count(0)])
	rows.append(_season_cells("통산", _career.career_line()))
	styles.append(2)
	col.add_child(_table(SEASON_HEADERS, rows, styles))
	col.add_child(UiKit.divider())
	col.add_child(UiKit.label("팀 성적 · " + "  |  ".join(teams), Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	return card


func _season_cells(name: String, l: PlayerData.SeasonStats) -> Array:
	return [name, str(l.games), str(l.pa), str(l.ab), str(l.hits), str(l.doubles), str(l.triples), str(l.home_runs), str(l.rbi),
		str(l.walks), str(l.strikeouts), l.average(), l.obp_text(), l.slg_text(), l.ops(), l.ops_plus_text(_career.cfg.league_avg)]


# ---------- 경기 ----------

## 경기마다 내 성적 (최근 경기부터)
func _games_page() -> Control:
	var card := _card()
	var col := UiKit.vbox(Tokens.SPACE_XS)
	var log := _career.records.game_log(_season)
	col.add_child(UiKit.kicker("GAME LOG · %d경기" % log.size()))
	if log.is_empty():
		col.add_child(UiKit.label("아직 치른 경기가 없다", Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	var rows := []
	var styles := []
	for g: CareerRecords.GameEntry in log:
		var l := g.line
		rows.append(["%d학년 %d주" % [g.grade, g.week], "%s %s" % [g.phase_name, g.round], "%s %s" % ["vs" if g.home else "@", g.opponent],
			g.score_text(), g.line_text(), str(l.doubles + l.triples) if g.playing else "", str(l.strikeouts) if g.playing else ""])
		styles.append(3 if g.result > 0 else (4 if g.result < 0 else 0))
	if not rows.is_empty():
		col.add_child(_table(["경기", "대회", "상대", "결과", "내 기록", "2·3루타", "삼진"], rows, styles, 3, 5))
	card.add_child(_scroll(col))
	return card


# ---------- 대회 ----------

## 대회(시즌 단계)마다 결과와 내 성적 (최근 것부터)
func _phases_page() -> Control:
	var card := _card()
	var col := UiKit.vbox(Tokens.SPACE_XS)
	var phases := _career.records.phase_log()
	col.add_child(UiKit.kicker("COMPETITIONS · 대회별 기록"))
	if phases.is_empty():
		col.add_child(UiKit.label("아직 치른 대회가 없다", Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	var rows := []
	var styles := []
	for ph: CareerRecords.PhaseEntry in phases:
		var l := ph.line
		var result := ph.result if ph.result != "" else "진행 중 (%d승 %d패 %d무)" % [ph.wins, ph.losses, ph.draws]
		rows.append(["%d학년" % ph.grade, ph.name, result, str(l.games), l.average(), str(l.hits), str(l.home_runs), str(l.rbi), l.ops()])
		styles.append(3 if ph.result == "우승!" else (1 if ph.result == "" else 0))
	if not rows.is_empty():
		col.add_child(_table(["", "대회", "결과", "경기", "타율", "안타", "홈런", "타점", "OPS"], rows, styles, 2, 3))
	card.add_child(_scroll(col))
	return card


# ---------- 분할 ----------

## 왼쪽: 투수 성향 · 카운트 | 오른쪽: 타구 종류 · 타구 방향 (타구 속도 포함)
func _splits_page() -> Control:
	var rec := _career.records
	var row := UiKit.hbox(Tokens.SPACE_SM)
	var pitcher_labels := {}
	for id: String in _pitchers:
		pitcher_labels[id] = "%s 투수" % _pitchers[id].name
	var left := _card()
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var lc := UiKit.vbox(Tokens.SPACE_XS)
	lc.add_child(UiKit.kicker("투수 성향별"))
	lc.add_child(_split_table(rec.split_rows(_season, "pitcher", _pitchers.keys()), pitcher_labels, false))
	lc.add_child(UiKit.kicker("카운트별 (결정구 때)"))
	lc.add_child(_split_table(rec.split_rows(_season, "count", CareerRecords.COUNT_KEYS), CareerRecords.COUNT_LABELS, false))
	left.add_child(_scroll(lc))
	row.add_child(left)
	var right := _card()
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var rc := UiKit.vbox(Tokens.SPACE_XS)
	rc.add_child(UiKit.kicker("타구 종류별"))
	rc.add_child(_split_table(rec.split_rows(_season, "batted", CareerRecords.BATTED_KEYS), CareerRecords.BATTED_LABELS, true))
	rc.add_child(UiKit.kicker("타구 방향별 (우타자 기준)"))
	rc.add_child(_split_table(rec.split_rows(_season, "direction", CareerRecords.DIRECTION_KEYS), CareerRecords.DIRECTION_LABELS, true))
	right.add_child(_scroll(rc))
	row.add_child(right)
	return row


## rows: [[칸, SeasonStats]]. batted 면 타석·삼진 대신 타구 속도
func _split_table(rows: Array, labels: Dictionary, batted: bool) -> Control:
	var headers := ["", "타수", "안타", "홈런", "타율", "장타율", "평균 타구"] if batted else ["", "타석", "안타", "홈런", "삼진", "타율", "OPS"]
	var cells := []
	for r: Array in rows:
		var l: PlayerData.SeasonStats = r[1]
		var name: String = labels.get(r[0], r[0])
		if batted:
			var ev := l.avg_ev()
			cells.append([name, str(l.ab), str(l.hits), str(l.home_runs), l.average(), l.slg_text(), "%d km/h" % roundi(ev) if ev >= 0 else "-"])
		else:
			cells.append([name, str(l.pa), str(l.hits), str(l.home_runs), str(l.strikeouts), l.average(), l.ops()])
	return _table(headers, cells, [])


# ---------- 이야기 ----------

func _story_page() -> Control:
	var card := _card()
	var col := UiKit.vbox(Tokens.SPACE_XS)
	col.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var log: Array = _career.book.log
	col.add_child(UiKit.kicker("STORY · %d" % log.size()))
	if log.is_empty():
		col.add_child(UiKit.label("아직 이야기가 없다", Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	for i in range(log.size() - 1, -1, -1):
		var e: Dictionary = log[i]
		var line := UiKit.label("%d학년 %d주 · %s — %s" % [e["grade"], e["week"], e["title"], e["choice"]], Tokens.FONT_CAPTION,
			Tokens.ACCENT if e["story"] else Tokens.INK, Tokens.FONT_BOLD)
		line.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		col.add_child(line)
	card.add_child(_scroll(col))
	return card


# ---------- 공통 ----------

## 표. styles[i]: 0 보통, 1 지금(파랑 굵게), 2 합계(굵게), 3 좋음(초록), 4 나쁨(빨강).
## result_col: 좋음·나쁨 색을 이 칸에만 칠한다 (경기 결과·대회 결과 칸). text_cols: 앞에서부터 이만큼은 글자 칸 (왼쪽 정렬), 나머지 숫자 칸은 오른쪽 정렬
func _table(headers: Array, rows: Array, styles: Array, result_col: int = -1, text_cols: int = 1) -> GridContainer:
	var grid := GridContainer.new()
	grid.columns = headers.size()
	grid.add_theme_constant_override("h_separation", Tokens.SPACE_SM + Tokens.SPACE_XS)
	grid.add_theme_constant_override("v_separation", 2)
	for i in headers.size():
		grid.add_child(_cell(headers[i], i < text_cols, Tokens.INK_SOFT, true))
	for r in rows.size():
		var st: int = styles[r] if r < styles.size() else 0
		var color: Color = [Tokens.INK, Tokens.PRIMARY, Tokens.INK, Tokens.GOOD, Tokens.BAD][st]
		var cells: Array = rows[r]
		for i in cells.size():
			var c := color if st < 3 or result_col < 0 or i == result_col else Tokens.INK
			grid.add_child(_cell(cells[i], i < text_cols, c, st in [1, 2] or i == 0))
	return grid


func _cell(text: String, left: bool, color: Color, bold: bool) -> Label:
	var l := UiKit.label(text, Tokens.FONT_CAPTION, color, Tokens.FONT_BOLD if bold else Tokens.FONT_REGULAR)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT if left else HORIZONTAL_ALIGNMENT_RIGHT
	return l


func _scroll(content: Control) -> ScrollContainer:
	var scroll := ScrollContainer.new()
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	content.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(content)
	return scroll


## 흰 카드 (허브 카드와 같은 모양). stripe 면 왼쪽에 강조 띠
func _card(stripe: bool = false) -> PanelContainer:
	var card := UiKit.card()
	var s := card.get_theme_stylebox("panel").duplicate() as StyleBoxFlat
	s.set_corner_radius_all(Tokens.SPACE_SM)
	s.set_content_margin_all(Tokens.SPACE_SM + Tokens.SPACE_XS)
	if stripe:
		s.border_color = Tokens.PRIMARY
		s.border_width_left = 4
	card.add_theme_stylebox_override("panel", s)
	card.size_flags_vertical = Control.SIZE_EXPAND_FILL
	return card


## 작은 탭·시즌 칩 (고른 것은 색 바탕, 나머지는 옅은 바탕)
func _chip(text: String, on: bool, on_press: Callable, color: Color = Tokens.PRIMARY) -> Button:
	var b := UiKit.button(text, on_press, color if on else Color(Tokens.INK_SOFT, 0.35), Tokens.FONT_CAPTION)
	for state in ["normal", "hover", "focus", "pressed"]:
		var s := (b.get_theme_stylebox(state) as StyleBoxFlat).duplicate() as StyleBoxFlat
		s.content_margin_left = Tokens.SPACE_MD
		s.content_margin_right = Tokens.SPACE_MD
		s.content_margin_top = 2
		s.content_margin_bottom = 2
		b.add_theme_stylebox_override(state, s)
	return b
