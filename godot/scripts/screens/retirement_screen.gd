class_name RetirementScreen
extends Control
## 은퇴식 — 커리어의 마지막 화면 (worldview 7장: 은퇴식 → 은하식당).
## 은퇴식 글 → 커리어 요약(프로 시즌 · 구단 · 통산 기록 · 역대 순위 · 수상 · 국가대표 · FA · 최고 연봉 · 기념 순간)
## → 만난 사람들의 한마디(지금 연인이 먼저) → 마지막 장면 은하식당(컷신 자리) → [타이틀로] · [새 선수로 시작].
## 화면은 로직(ProCareer.career_summary · content.json pro.retireText)을 읽어 그리기만 한다 (원칙 1).

var _app


func _init(app) -> void:
	_app = app


func _ready() -> void:
	var career: CareerState = _app.career
	var pro := career.pro
	var cfg := career.cfg
	var rt: Dictionary = cfg.pro_content.get("retireText", {})
	var sum := pro.career_summary(career)
	var bg := ColorRect.new()
	bg.color = Tokens.BACKGROUND
	add_child(UiKit.fill_parent(bg))
	var scroll := ScrollContainer.new()
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	add_child(UiKit.fill_parent(UiKit.margin(scroll, Tokens.SPACE_MD)))
	var col := UiKit.vbox(Tokens.SPACE_SM)
	col.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(col)

	# 은퇴식
	var head := UiKit.card()
	var hc := UiKit.vbox(Tokens.SPACE_XS)
	head.add_child(hc)
	hc.add_child(UiKit.kicker("은퇴식 · 프로 %d시즌 · 만 %d세 · %d–%d" % [int(sum["years"]), int(sum["age"]), int(sum["first_year"]), int(sum["last_year"])]))
	hc.add_child(UiKit.title("%s, 그라운드를 떠나다" % career.player.name, Tokens.FONT_TITLE))
	for ln: String in rt.get("ceremony", []):
		hc.add_child(_wrap(EventBook.fill(career, ln), Tokens.FONT_BODY, Tokens.INK))
	if bool(sum["released"]):
		hc.add_child(_wrap("마지막 계약은 이어지지 않았다. 그래도 마지막 인사는 홈 플레이트에서 했다.", Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	col.add_child(head)

	# 커리어 요약
	var card := UiKit.card()
	var cc := UiKit.vbox(Tokens.SPACE_XS)
	card.add_child(cc)
	cc.add_child(UiKit.kicker("커리어"))
	var line: PlayerData.SeasonStats = sum["line"]
	cc.add_child(UiKit.label("통산 %d경기 · 타율 %s · %d안타 · %d홈런 · %d타점 · %d도루 · OPS %s" % [line.games, line.average(), line.hits,
		line.home_runs, line.rbi, line.steals, line.ops()], Tokens.FONT_BODY, Tokens.PRIMARY, Tokens.FONT_BOLD))
	var ranks: Dictionary = sum["ranks"]
	var rank_parts := []
	for pair: Array in [["hits", "안타"], ["hr", "홈런"], ["rbi", "타점"], ["sb", "도루"]]:
		if int(ranks.get(pair[0], 0)) > 0:
			rank_parts.append("%s %d위" % [pair[1], int(ranks[pair[0]])])
	if not rank_parts.is_empty():
		cc.add_child(UiKit.label("KBA 역대 " + " · ".join(rank_parts), Tokens.FONT_BODY, Tokens.ACCENT, Tokens.FONT_BOLD))
	cc.add_child(UiKit.label("구단: " + " → ".join(sum["teams"]), Tokens.FONT_CAPTION, Tokens.INK))
	var awards: Array = sum["awards"]
	cc.add_child(_wrap("수상: " + (", ".join(awards) if not awards.is_empty() else "없음"), Tokens.FONT_CAPTION, Tokens.INK))
	for n: Dictionary in sum["national"]:
		cc.add_child(UiKit.label("%d년 차 %s 국가대표 — %s" % [n["year"], n["name"], n["result"]], Tokens.FONT_CAPTION, Tokens.ACCENT))
	cc.add_child(UiKit.label("FA 계약 %d번 · 최고 연봉 %s · 기념 순간 %d개" % [int(sum["fa_count"]), ProCareer.money_text(int(sum["best_salary"])),
		int(sum["milestones"])], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	var seasons := UiKit.vbox(0)
	for h: Dictionary in pro.me.history:
		seasons.add_child(UiKit.label("%d년 차 (%s): %s" % [h["year"], h["team"], h["line"]], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	seasons.add_child(UiKit.label("%d년 차 (%s): %s" % [pro.year(), pro.team().name, pro.me.line_text()], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	cc.add_child(seasons)
	col.add_child(card)

	# 한마디 (만난 사람만, 지금 연인이 먼저)
	var voices: Dictionary = rt.get("voices", {})
	var vcard := UiKit.card()
	var vc := UiKit.vbox(Tokens.SPACE_XS)
	vcard.add_child(vc)
	vc.add_child(UiKit.kicker("한마디"))
	var order: Array = voices.keys()
	if career.people.partner != "" and order.has(career.people.partner):
		order.erase(career.people.partner)
		order.push_front(career.people.partner)
	for id: String in order:
		if not career.people.has(id) or not career.people.is_met(id):
			continue
		var person := career.people.person(id)
		var mark := " ♥" if id == career.people.partner else ""
		vc.add_child(_wrap("%s%s — %s" % [person.call, mark, EventBook.fill(career, voices[id])], Tokens.FONT_BODY, Tokens.INK))
	col.add_child(vcard)

	# 마지막 장면: 은하식당 (컷신 자리 — 그림이 들어오면 컷신으로)
	var last := UiKit.card(Tokens.STAGE_FRAME)
	var lc := UiKit.vbox(Tokens.SPACE_XS)
	last.add_child(lc)
	lc.add_child(UiKit.kicker("마지막 장면 · 컷신 자리 · pro_restaurant", Tokens.HUD_TEXT_SOFT))
	for ln: String in rt.get("restaurant", []):
		lc.add_child(_wrap(EventBook.fill(career, ln), Tokens.FONT_BODY, Tokens.HUD_TEXT))
	lc.add_child(UiKit.label("— 끝 —", Tokens.FONT_LABEL, Tokens.HUD_TEXT, Tokens.FONT_BOLD))
	col.add_child(last)

	var row := UiKit.hbox(Tokens.SPACE_SM)
	row.add_child(UiKit.button("타이틀로", _app.show_title, Tokens.INK_SOFT))
	var again := UiKit.button("새 선수로 시작 ▶", _app.show_create, Tokens.ACCENT, Tokens.FONT_LABEL)
	again.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(again)
	col.add_child(row)


func _wrap(text: String, size: int, color: Color) -> Label:
	var l := UiKit.label(text, size, color)
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	return l
