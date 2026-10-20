class_name HomeScreen
extends Control
## 커리어 허브 (이번 주). 탭 레이아웃은 유저가 준 시안(2026-10-07), 홈 탭은 러브딜리버리식 "내 방" (2026-10-21 유저 요청).
## 위: 로고 · 탭(홈 · 폰 · 선수 · 상점 · 스카우트 · 기록) · 학년/주차
## 인물과의 문자·관계·교류는 폰(PhoneView)에 있다. 폰 탭을 누르면 허브 위에 폰이 뜬다.
## 안 읽은 문자가 있으면 폰 탭 오른쪽 아래에 빨간 원 + 흰 숫자, 새 문자가 오면 오른쪽 위에 알림 띠가 미끄러져 들어왔다 나간다
## 홈 탭: 내 방 그림(art/backgrounds/room.png, 없으면 home.png) 위에 내 선수 일러스트,
##   왼쪽 능력치·소식, 오른쪽 주간 달력(평일 칸 3개 + 주말 경기). 칸을 눌러 훈련·휴식을 넣고 [스케줄 시작]을 누르면
##   칸마다 대성공·성공·실패 도장이 하나씩 찍힌다. 아래: 컨디션 · 폼 · 평판 · 지갑 · 스케줄 / 다음 주
## 주 시작 이야기(EventBook timing start)가 있으면 화면 가운데 겹쳐 먼저 보여 준다.
## 경기를 마친 주(불러온 커리어)면 남은 경기 뒤 이야기를 보여 주고 [다음 주]로 넘어간다. 그릴 때마다 저장한다

const TABS := ["홈", "폰", "선수", "상점", "스카우트", "기록"]
## 내 방 그림 (없으면 우리 집 그림), 내 선수 일러스트
const ROOM_ART := "res://art/backgrounds/room.png"
const ROOM_FALLBACK := "res://art/backgrounds/home.png"
const PLAYER_ART := "res://art/portraits/player.png"
## 누르면 페이지가 바뀌는 대신 폰이 뜨는 탭
const PHONE_TAB := "폰"
## 지금 있는 탭
const READY_TABS := TABS

var _app
var _tab := "홈"
var _tab_buttons := {}
var _page_host: Control
var _bottom: HBoxContainer
## 내 방 그림 (홈 탭에서만 보인다)
var _room_bg: Control
## 스케줄 칸을 고르는 판 (없으면 null)
var _picker: Control
## 스케줄 결과를 몇 칸까지 보여 줬나 (-1 = 다 보여 줌). 실행 직후 0 부터 revealSec 마다 하나씩 는다
var _revealed := -1
var _reveal_tween: Tween
## 결과 도장이 뜰 때 일러스트 위로 떠오르는 글자 자리
var _float_host: Control
## 주 시작 이야기 대화 화면 (없으면 null)
var _overlay: Control
## 상점에서 마지막으로 쓴 서비스 결과
var _last_shop_note := ""
## 폰 탭의 알림 배지, 떠 있는 폰 화면 (없으면 null), 새 문자 알림 띠
var _phone_badge: Shapes.Badge
var _phone: PhoneView
var _toast: Control


func _init(app) -> void:
	_app = app


func _ready() -> void:
	var path := ROOM_ART if ResourceLoader.exists(ROOM_ART) else ROOM_FALLBACK
	_room_bg = Control.new()
	_room_bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	if ResourceLoader.exists(path):
		var art := TextureRect.new()
		art.texture = load(path)
		art.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		art.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
		art.mouse_filter = Control.MOUSE_FILTER_IGNORE
		_room_bg.add_child(UiKit.fill_parent(art))
	add_child(UiKit.fill_parent(_room_bg))
	var root := UiKit.vbox(Tokens.SPACE_SM)
	add_child(UiKit.fill_parent(UiKit.margin(root, Tokens.SPACE_MD)))
	root.add_child(_on_room_panel(_top_bar()))
	_page_host = PanelContainer.new()
	_page_host.add_theme_stylebox_override("panel", StyleBoxEmpty.new())
	_page_host.size_flags_vertical = Control.SIZE_EXPAND_FILL
	root.add_child(_page_host)
	_bottom = UiKit.hbox(Tokens.SPACE_LG)
	root.add_child(_on_room_panel(_bottom))
	_rebuild()
	_next_story()


## 주 시작 이야기가 남았으면 대화 화면을 위에 띄운다 (끝나면 다음 것, 다 끝나면 허브를 다시 그린다)
func _next_story() -> void:
	var events: Array = _app.career.events("after" if _app.career.game_done else "start")
	if events.is_empty():
		_notify_messages()
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


# ---------- 폰 ----------

## 폰을 연다 (room 이 있으면 그 대화방부터). 닫으면 허브를 다시 그린다
func open_phone(room: String = "") -> void:
	if _phone != null or _overlay != null:
		return
	_hide_toast()
	_phone = PhoneView.new(_app.career, room)
	add_child(UiKit.fill_parent(_phone))
	_phone.changed.connect(_app.save_career)
	_phone.closed.connect(func() -> void:
		_phone.queue_free()
		_phone = null
		_rebuild())


## 지금 떠 있는 폰 화면 (스크린샷 스크립트용, 없으면 null)
func phone_view() -> PhoneView:
	return _phone


## 새 문자가 왔다: 오른쪽 위에 알림 띠가 오른쪽에서 미끄러져 들어왔다가 다시 미끄러져 나간다 (누르면 그 대화방이 열린다).
## 이야기가 다 끝난 뒤에 뜬다. 시간은 balance.json career.phone 의 toastSec · toastSlideSec
func _notify_messages() -> void:
	var phone: Phone = _app.career.phone
	var fresh: Array = phone.take_fresh(_app.career)
	if fresh.is_empty():
		return
	_app.save_career()
	var first: Dictionary = fresh[0]
	var card := PanelContainer.new()
	var style := StyleBoxFlat.new()
	style.bg_color = Tokens.PHONE_BODY
	style.set_corner_radius_all(Tokens.RADIUS_BUBBLE)
	style.set_content_margin_all(Tokens.SPACE_SM)
	style.content_margin_left = Tokens.SPACE_SM + Tokens.SPACE_XS
	style.shadow_color = Tokens.SHADOW
	style.shadow_size = Tokens.SPACE_SM
	style.anti_aliasing = true
	card.add_theme_stylebox_override("panel", style)
	var row := UiKit.hbox(Tokens.SPACE_SM)
	row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var badge := Shapes.Badge.new(phone.total_unread())
	badge.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	row.add_child(badge)
	var col := UiKit.vbox(0)
	col.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	col.add_child(UiKit.label("알림이 %d개 쌓여 있습니다." % phone.total_unread(), Tokens.FONT_CAPTION + 1, Tokens.HUD_TEXT, Tokens.FONT_BOLD))
	var preview := UiKit.label("%s: %s" % [first["name"], first["text"]], Tokens.FONT_CAPTION, Tokens.HUD_TEXT_SOFT)
	preview.clip_text = true
	col.add_child(preview)
	row.add_child(col)
	card.add_child(row)
	var hit := Button.new()
	hit.flat = true
	hit.focus_mode = Control.FOCUS_NONE
	hit.pressed.connect(func() -> void: open_phone(first["room"]))
	card.add_child(hit)
	card.custom_minimum_size.x = Tokens.TOAST_WIDTH
	_toast = card
	add_child(card)
	# 화면 오른쪽 밖에서 시작해 안으로 들어오고, 잠깐 머문 뒤 다시 밖으로
	var shown := size.x - Tokens.TOAST_WIDTH - Tokens.SPACE_SM
	var hidden := size.x + Tokens.SPACE_MD
	var slide := float(_app.career.cfg.phone["toastSlideSec"])
	card.position = Vector2(hidden, Tokens.SPACE_SM)
	var tw := card.create_tween()
	tw.tween_property(card, "position:x", shown, slide).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
	tw.tween_interval(float(_app.career.cfg.phone["toastSec"]))
	tw.tween_property(card, "position:x", hidden, slide).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_IN)
	tw.tween_callback(_hide_toast)


func _hide_toast() -> void:
	if is_instance_valid(_toast):
		_toast.queue_free()
	_toast = null


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
		if t == PHONE_TAB:
			b.pressed.connect(func() -> void: open_phone())
			# 알림 배지: 글자 오른쪽 아래에 걸친다
			_phone_badge = Shapes.Badge.new()
			_phone_badge.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_RIGHT)
			_phone_badge.position += Vector2(Tokens.BADGE_SIZE * 0.4, -Tokens.SPACE_SM - Tokens.SPACE_XS)
			b.add_child(_phone_badge)
		else:
			b.pressed.connect(func() -> void: _show_tab(t))
		tabs.add_child(b)
		_tab_buttons[t] = b
	bar.add_child(tabs)
	var when := "%d학년 겨울 · 캠프 %d/%d주" % [career.player.grade, career.camp_week, int(career.cfg.camp["weeks"])] if career.in_camp() \
		else "%d학년 · %d주차" % [career.player.grade, career.week]
	bar.add_child(UiKit.label(when, Tokens.FONT_CAPTION, Tokens.INK_SOFT))
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
	_phone_badge.count = _app.career.phone.total_unread()


# ---------- 페이지 ----------

func _rebuild() -> void:
	for c in _page_host.get_children():
		c.queue_free()
	_close_picker()
	_room_bg.visible = _tab == "홈"
	match _tab:
		"선수":
			_page_host.add_child(_player_page())
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
	_app.save_career()


## 내 방: 왼쪽 능력치·소식 | 가운데 내 선수 일러스트 | 오른쪽 주간 달력 + 진행 버튼
func _home_page() -> Control:
	var row := UiKit.hbox(Tokens.SPACE_SM)
	var left := UiKit.vbox(Tokens.SPACE_SM)
	left.custom_minimum_size.x = 196
	left.add_child(_status_card())
	var news := _inbox_card()
	news.size_flags_vertical = Control.SIZE_EXPAND_FILL
	news.visible = news.get_meta("count", 0) > 0  # 소식이 없으면 방이 더 보이게
	left.add_child(news)
	row.add_child(left)
	var middle := Control.new()
	middle.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	middle.mouse_filter = Control.MOUSE_FILTER_IGNORE
	if ResourceLoader.exists(PLAYER_ART):
		var me := TextureRect.new()
		me.texture = load(PLAYER_ART)
		me.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		me.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		me.mouse_filter = Control.MOUSE_FILTER_IGNORE
		middle.add_child(UiKit.fill_parent(me))
	_float_host = UiKit.fill_parent(Control.new())
	_float_host.mouse_filter = Control.MOUSE_FILTER_IGNORE
	middle.add_child(_float_host)
	row.add_child(middle)
	var right := UiKit.vbox(Tokens.SPACE_SM)
	right.custom_minimum_size.x = 352
	var cal := _calendar_card()
	cal.size_flags_vertical = Control.SIZE_EXPAND_FILL
	right.add_child(cal)
	right.add_child(_go_button())
	row.add_child(right)
	return row


## 이름·종합 능력치·능력치 막대·컨디션 (반투명 흰 판)
func _status_card() -> Control:
	var career: CareerState = _app.career
	var p: PlayerData = career.player
	var card := _room_card()
	var col := UiKit.vbox(2)
	card.add_child(col)
	var head := UiKit.hbox(Tokens.SPACE_SM)
	var names := UiKit.vbox(0)
	names.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	names.add_child(UiKit.label(p.name, Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD))
	names.add_child(UiKit.label("%s %d학년" % [career.my_school.short, p.grade], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	head.add_child(names)
	var ovr := UiKit.vbox(0)
	var k := UiKit.kicker("OVR")
	k.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	ovr.add_child(k)
	var v := UiKit.title(str(p.overall()), Tokens.FONT_TITLE)
	v.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	ovr.add_child(v)
	head.add_child(ovr)
	col.add_child(head)
	for st: String in PlayerData.STATS:
		col.add_child(_mini_bar(PlayerData.STAT_LABELS[st], p.stat(st), Tokens.STAT_COLORS[st]))
	col.add_child(_mini_bar("컨디션", p.condition, Tokens.WARN, 100))
	return card


# ---------- 주간 달력 ----------

## 이번 주 달력: 평일 칸(cfg.schedule.slots) + 주말(경기). 실행 전엔 칸을 눌러 바꾸고, 실행 뒤엔 결과 도장
func _calendar_card() -> Control:
	var career: CareerState = _app.career
	var card := _room_card()
	var col := UiKit.vbox(Tokens.SPACE_SM)
	card.add_child(col)
	var head := UiKit.hbox(Tokens.SPACE_SM)
	var when := "WINTER CAMP · %d/%d주" % [career.camp_week, int(career.cfg.camp["weeks"])] if career.in_camp() else "SCHEDULE · %d주차" % career.week
	var kick := UiKit.kicker(when, Tokens.PRIMARY if career.in_camp() else Tokens.INK_SOFT)
	kick.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	head.add_child(kick)
	var hint := "결과" if _ran() else ("부상 — 이번 주는 모두 휴식" if career.player.is_injured() else "칸을 눌러 바꾼다")
	head.add_child(UiKit.label(hint, Tokens.FONT_CAPTION, Tokens.BAD if career.player.is_injured() and not _ran() else Tokens.INK_SOFT))
	col.add_child(head)
	var cells := UiKit.hbox(Tokens.SPACE_XS)
	cells.size_flags_vertical = Control.SIZE_EXPAND_FILL
	var slots := career.schedule_slots()
	career.plan_ready()  # 칸 수를 맞춘다
	for i in slots.size():
		cells.add_child(_slot_cell(i))
	cells.add_child(_weekend_cell())
	col.add_child(cells)
	if career.in_camp() and career.camp_focus != "":
		col.add_child(UiKit.label("캠프 집중 과제 · %s" % career.camp_focus_label(), Tokens.FONT_CAPTION, Tokens.ACCENT, Tokens.FONT_BOLD))
	elif _ran() and _revealed < 0:
		var sum := UiKit.label(career.week_training, Tokens.FONT_CAPTION, Tokens.INK, Tokens.FONT_BOLD)
		sum.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		col.add_child(sum)
	return card


## 평일 칸 하나: 요일 · 훈련 이름 · 오르는 능력치/컨디션 · (실행 전) 실패 확률 / (실행 뒤) 결과 도장
func _slot_cell(i: int) -> Control:
	var career: CareerState = _app.career
	var ran := _ran()
	var shown := ran and (_revealed < 0 or i < _revealed)
	var id: String = career.week_schedule[i]["id"] if ran else career.week_plan[i]
	var color: Color = Tokens.ROOM_EMPTY
	var col := UiKit.vbox(2)
	col.add_child(UiKit.label(career.schedule_slots()[i], Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD))
	if id == "":
		col.add_child(UiKit.label("비어 있음", Tokens.FONT_CAPTION + 1, Tokens.INK_SOFT, Tokens.FONT_BOLD))
		col.add_child(UiKit.label("눌러서 고르기", Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	else:
		var t: Dictionary = career.cfg.training[id]
		var stat: String = t["stat"]
		color = Tokens.STAT_COLORS.get(stat, Tokens.REST_COLOR)
		var name := UiKit.label(t["label"], Tokens.FONT_CAPTION + 1, Tokens.INK, Tokens.FONT_BOLD)
		name.clip_text = true
		col.add_child(name)
		if shown:
			var r: Dictionary = career.week_schedule[i]
			col.add_child(UiKit.label(_slot_gain_text(r), Tokens.FONT_CAPTION, Tokens.INK))
			col.add_child(UiKit.label("컨디션 %+d" % r["condition"], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
		else:
			col.add_child(UiKit.label("%s ▲" % PlayerData.STAT_LABELS[stat] if stat != "" else "회복", Tokens.FONT_CAPTION, Tokens.INK))
			col.add_child(UiKit.label("컨디션 %+d" % career.slot_condition(id), Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	col.add_child(UiKit.spacer())
	if shown:
		var r: Dictionary = career.week_schedule[i]
		var stamp := _stamp(r)
		col.add_child(stamp)
		if i == _revealed - 1:
			_pop(stamp)
	elif id != "" and not ran:
		var fail := float(career.slot_chances(id, _expected_condition(i))["fail"])
		if fail > 0.0:
			col.add_child(UiKit.label("실패 %d%%" % roundi(fail * 100.0), Tokens.FONT_CAPTION, Tokens.BAD if fail >= 0.2 else Tokens.INK_SOFT))
	var cell := _cell(col, color, id == "")
	if not ran and not career.game_done:
		var hit := Button.new()
		hit.flat = true
		hit.focus_mode = Control.FOCUS_NONE
		hit.pressed.connect(func() -> void: _open_picker(i))
		cell.add_child(hit)
	return cell


## 칸 i 를 시작할 때 예상 컨디션 (앞 칸들의 컨디션 변화를 더한다 — 실제 실행과 같은 순서)
func _expected_condition(i: int) -> int:
	var career: CareerState = _app.career
	var cond := career.player.condition
	for j in i:
		var id: String = career.week_plan[j]
		if id != "":
			cond = clampi(cond + career.slot_condition(id), 0, 100)
	return cond


## 주말 칸: 경기 상대 (캠프 훈련 주면 경기 없음)
func _weekend_cell() -> Control:
	var career: CareerState = _app.career
	var col := UiKit.vbox(2)
	col.add_child(UiKit.label("토·일", Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD))
	var has_game := not career.in_camp() or career.camp_has_game()
	if has_game:
		var opp := career.opponent()
		col.add_child(UiKit.label("연습경기" if career.in_camp() else "경기", Tokens.FONT_CAPTION + 1, Tokens.INK, Tokens.FONT_BOLD))
		col.add_child(UiKit.label("vs %s" % opp.short, Tokens.FONT_CAPTION, Tokens.UNIFORM_THEM, Tokens.FONT_BOLD))
		col.add_child(UiKit.label(career.round_text() if not career.in_camp() else "기록 안 남음", Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	else:
		col.add_child(UiKit.label("쉬는 날", Tokens.FONT_CAPTION + 1, Tokens.INK, Tokens.FONT_BOLD))
		col.add_child(UiKit.label("캠프 훈련 주", Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	col.add_child(UiKit.spacer())
	col.add_child(UiKit.label("끝" if career.game_done else "눌러서 자세히", Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD))
	var cell := _cell(col, Tokens.UNIFORM_THEM if has_game else Tokens.REST_COLOR, false)
	# 누르면 다음 경기 카드 (상대 선발·타순·내 출전)
	var hit := Button.new()
	hit.flat = true
	hit.focus_mode = Control.FOCUS_NONE
	hit.pressed.connect(func() -> void:
		var card := _match_card()
		card.custom_minimum_size = Vector2(440, 250)
		_open_popup(card))
	cell.add_child(hit)
	return cell


## 칸 바탕: 흰 판 + 위쪽 색 띠
func _cell(content: Control, color: Color, empty: bool) -> PanelContainer:
	var cell := PanelContainer.new()
	var s := StyleBoxFlat.new()
	s.bg_color = Tokens.ROOM_EMPTY if empty else Tokens.SURFACE
	s.border_color = color
	s.border_width_top = 4
	if empty:
		s.set_border_width_all(1)
		s.border_color = Tokens.DIVIDER
	s.set_corner_radius_all(Tokens.SPACE_SM)
	s.set_content_margin_all(Tokens.SPACE_SM)
	s.anti_aliasing = true
	cell.add_theme_stylebox_override("panel", s)
	cell.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	cell.size_flags_stretch_ratio = 1.0
	content.mouse_filter = Control.MOUSE_FILTER_IGNORE
	cell.add_child(content)
	return cell


## 결과 도장 ("대성공!" · "성공" · "실패" · 휴식은 "회복")
func _stamp(r: Dictionary) -> Control:
	var outcome: String = r["outcome"]
	var text: String = "회복" if outcome == "rest" else (r["outcome_label"] + ("!" if outcome == "great" else ""))
	var color: Color = Tokens.OUTCOME_COLORS.get(outcome, Tokens.REST_COLOR)
	var box := PanelContainer.new()
	var s := StyleBoxFlat.new()
	s.bg_color = color
	s.set_corner_radius_all(Tokens.RADIUS_BUBBLE)
	s.content_margin_left = Tokens.SPACE_SM
	s.content_margin_right = Tokens.SPACE_SM
	box.add_theme_stylebox_override("panel", s)
	var l := UiKit.label(text, Tokens.FONT_CAPTION + 1, Tokens.SURFACE, Tokens.FONT_BOLD)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	box.add_child(l)
	return box


## 칸 결과의 성장 글자 ("컨택 +0.4", 칸이 오르면 "컨택 +1칸")
func _slot_gain_text(r: Dictionary) -> String:
	if r["stat"] == "":
		return "푹 쉬었다"
	var label: String = PlayerData.STAT_LABELS[r["stat"]]
	if int(r["raised"]) > 0:
		return "%s +%d칸" % [label, r["raised"]]
	return "%s +%.1f" % [label, r["amount"]]


## 방금 찍힌 도장: 크게 떴다가 제자리로 + 일러스트 위로 성장 글자가 떠오른다
func _pop(stamp: Control) -> void:
	var r: Dictionary = _app.career.week_schedule[_revealed - 1]
	stamp.pivot_offset = Vector2(30, 10)
	stamp.scale = Vector2.ONE * 1.8
	stamp.modulate.a = 0.0
	var tw := stamp.create_tween().set_parallel()
	tw.tween_property(stamp, "scale", Vector2.ONE, 0.25).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	tw.tween_property(stamp, "modulate:a", 1.0, 0.12)
	if _float_host == null:
		return
	var text: String = ("%s!  " % r["outcome_label"] if r["outcome"] == "great" else "") + _slot_gain_text(r)
	var color: Color = Tokens.OUTCOME_COLORS.get(r["outcome"], Tokens.REST_COLOR)
	var l := UiKit.label(text, Tokens.FONT_TITLE, color, Tokens.FONT_BOLD)
	l.add_theme_color_override("font_outline_color", Tokens.SURFACE)
	l.add_theme_constant_override("outline_size", 6)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	# 얼굴을 가리지 않게 일러스트 위쪽 가장자리에서 떠오른다
	l.set_anchors_and_offsets_preset(Control.PRESET_CENTER_TOP)
	l.grow_horizontal = Control.GROW_DIRECTION_BOTH
	l.position.y = Tokens.SPACE_SM
	_float_host.add_child(l)
	var y := l.position.y
	var ft := l.create_tween()
	ft.tween_property(l, "position:y", y - 40.0, 0.7).set_ease(Tween.EASE_OUT)
	ft.parallel().tween_property(l, "modulate:a", 0.0, 0.7).set_delay(0.3)
	ft.tween_callback(l.queue_free)


## 화면 가운데에 카드를 띄운다 (바깥을 누르면 닫힌다)
func _open_popup(card: Control) -> void:
	_close_picker()
	var cover := ColorRect.new()
	cover.color = Tokens.SCRIM
	_picker = UiKit.fill_parent(cover)
	add_child(_picker)
	var back := Button.new()
	back.flat = true
	back.focus_mode = Control.FOCUS_NONE
	back.pressed.connect(_close_picker)
	cover.add_child(UiKit.fill_parent(back))
	card.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
	card.grow_horizontal = Control.GROW_DIRECTION_BOTH
	card.grow_vertical = Control.GROW_DIRECTION_BOTH
	cover.add_child(card)


## 칸에 넣을 훈련을 고르는 판
func _open_picker(slot: int) -> void:
	var career: CareerState = _app.career
	var card := UiKit.card()
	card.custom_minimum_size.x = 420
	_open_popup(card)
	var col := UiKit.vbox(Tokens.SPACE_XS)
	card.add_child(col)
	col.add_child(UiKit.kicker("%s · 무엇을 할까" % career.schedule_slots()[slot]))
	for id: String in career.cfg.training:
		var t: Dictionary = career.cfg.training[id]
		var stat: String = t["stat"]
		var parts := ["%s ▲" % PlayerData.STAT_LABELS[stat] if stat != "" else "회복", "컨디션 %+d" % career.slot_condition(id)]
		var mult := career.training_multiplier(id)
		if mult != 1.0:
			parts.append("캠프 ×%.1f" % mult)
		var ch := career.slot_chances(id, _expected_condition(slot))
		if stat != "":
			parts.append("대성공 %d%% · 실패 %d%%" % [roundi(float(ch["great"]) * 100.0), roundi(float(ch["fail"]) * 100.0)])
		var b := UiKit.button("%s   %s" % [t["label"], " · ".join(parts)], func() -> void:
			career.set_plan(slot, id)
			_rebuild(), Tokens.STAT_COLORS.get(stat, Tokens.REST_COLOR), Tokens.FONT_CAPTION + 1)
		b.alignment = HORIZONTAL_ALIGNMENT_LEFT
		b.disabled = career.player.is_injured() and stat != ""
		col.add_child(b)
	col.add_child(UiKit.label("앞 칸에서 컨디션이 깎이면 뒤 칸의 실패 확률이 오른다", Tokens.FONT_CAPTION, Tokens.INK_SOFT))


func _close_picker() -> void:
	if _picker != null:
		_picker.queue_free()
		_picker = null


## 지금 떠 있는 칸 고르기 판 (스크린샷 스크립트용, 없으면 null)
func picker() -> Control:
	return _picker


## [스케줄 시작]: 결과는 한 번에 정해 저장하고, 화면은 revealSec 마다 칸 하나씩 도장을 찍는다
func _start_schedule() -> void:
	_app.career.run_schedule()
	_app.save_career()
	_revealed = 0
	_rebuild()
	_reveal_next()


func _reveal_next() -> void:
	if _revealed < 0:
		return
	if _revealed >= _app.career.week_schedule.size():
		_revealed = -1
		_rebuild()
		return
	_revealed += 1
	_rebuild()
	_reveal_tween = create_tween()
	_reveal_tween.tween_interval(float(_app.career.cfg.schedule["revealSec"]))
	_reveal_tween.tween_callback(_reveal_next)


## 결과 연출 건너뛰기
func _skip_reveal() -> void:
	if _reveal_tween != null:
		_reveal_tween.kill()
	_revealed = -1
	_rebuild()


## 이번 주 스케줄을 실행했나
func _ran() -> bool:
	return _app.career.week_training != ""


## 다음 경기: 대회·라운드, 우리 학교 vs 상대 (방패 + 순위), 상대 선발
func _match_card() -> Control:
	var career: CareerState = _app.career
	if career.in_camp():
		return _camp_card()
	var card := _card()
	card.size_flags_vertical = Control.SIZE_EXPAND_FILL
	var col := UiKit.vbox(Tokens.SPACE_XS)
	card.add_child(col)
	var kind := "주말리그" if career.is_league() else "전국대회"
	var opp_head := career.opponent()
	var rival_tag := " · 라이벌 학교" if opp_head.id == career.rival.school_id and career.people.is_met("rival") else ""
	# 이번 주 경기를 마쳤으면 (불러온 커리어) 다음 주 경기를 보여 준다. 홈·원정은 주마다 번갈아
	var week := career.week + (1 if career.game_done else 0)
	col.add_child(UiKit.kicker("NEXT MATCH · %d주차 · %s%s" % [week, "홈" if week % 2 == 0 else "원정", rival_tag]))
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
	col.add_child(_lineup_row())
	return card


## 우리 팀 선발 타순 (작은 글씨, 내 선수는 강조). 내가 선발이 아니면 끝에 이유를 붙인다
func _lineup_row() -> Control:
	var career: CareerState = _app.career
	var flow := HFlowContainer.new()
	flow.add_theme_constant_override("h_separation", Tokens.SPACE_XS)
	flow.add_theme_constant_override("v_separation", 0)
	flow.add_child(UiKit.label("라인업", Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD))
	for r: Dictionary in career.next_lineup():
		var me: bool = r["me"]
		flow.add_child(UiKit.label("%d %s" % [r["slot"], r["name"]], Tokens.FONT_CAPTION, Tokens.PRIMARY if me else Tokens.INK_SOFT, Tokens.FONT_BOLD if me else Tokens.FONT_REGULAR))
	if career.player.is_injured():
		flow.add_child(UiKit.label("· %s 결장" % career.player.injury_text(), Tokens.FONT_CAPTION, Tokens.BAD, Tokens.FONT_BOLD))
	elif career.player.bench_next:
		flow.add_child(UiKit.label("· 벤치 대기 (대타)", Tokens.FONT_CAPTION, Tokens.PRIMARY, Tokens.FONT_BOLD))
	elif career.next_role() != "start":
		flow.add_child(UiKit.label("· 벤치", Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD))
	return flow


## 겨울 캠프 주: 연습경기가 있으면 상대, 없으면 훈련 주 안내. 집중 과제와 지금까지 연습경기
func _camp_card() -> Control:
	var career: CareerState = _app.career
	var camp := career.cfg.camp
	var card := _card(true)
	card.size_flags_vertical = Control.SIZE_EXPAND_FILL
	var col := UiKit.vbox(Tokens.SPACE_XS)
	card.add_child(col)
	var has_game := career.camp_has_game()
	col.add_child(UiKit.kicker("WINTER CAMP · %d/%d주" % [career.camp_week, int(camp["weeks"])], Tokens.PRIMARY))
	col.add_child(UiKit.title("연습경기" if has_game else "훈련 주", Tokens.FONT_TITLE))
	var focus := "집중 과제 · 아직 (첫 주에 감독과 정한다)"
	if career.camp_focus == CareerState.CAMP_FOCUS_BODY:
		focus = "집중 과제 · %s (컨디션 하락 ×%.1f)" % [career.camp_focus_label(), career.camp_condition_multiplier()]
	elif career.camp_focus != "":
		focus = "집중 과제 · %s (훈련 ×%.1f)" % [career.camp_focus_label(), float(camp["focusMultiplier"])]
	col.add_child(UiKit.label("%s · 다른 훈련 ×%.1f" % [focus, float(camp["trainingMultiplier"])], Tokens.FONT_CAPTION, Tokens.ACCENT, Tokens.FONT_BOLD))
	col.add_child(UiKit.spacer())
	if career.game_done:
		col.add_child(UiKit.label("이번 주 일정을 마쳤다.", Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD))
	elif has_game:
		var opp := career.opponent()
		var vs := UiKit.hbox(Tokens.SPACE_MD)
		vs.alignment = BoxContainer.ALIGNMENT_CENTER
		vs.add_child(_team(career.my_school.short, career.my_school.name, "연습경기", Tokens.UNIFORM_US))
		var v := UiKit.label("VS", Tokens.FONT_HERO, Color(Tokens.INK_SOFT, 0.6), Tokens.FONT_BOLD)
		v.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		vs.add_child(v)
		vs.add_child(_team(opp.short, opp.name, "전력 %s" % _stars(opp.strength), Tokens.UNIFORM_THEM))
		col.add_child(vs)
	else:
		var t := UiKit.label("이번 주는 경기 없이 훈련만 한다. 훈련을 고르고 주를 마치면 캠프 이야기가 이어진다.", Tokens.FONT_BODY, Tokens.INK)
		t.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		col.add_child(t)
	col.add_child(UiKit.spacer())
	for g: Dictionary in career.camp_games:
		var r: int = g["result"]
		col.add_child(UiKit.label("연습경기 vs %s  %d : %d %s · %s" % [g["opponent"], g["my"], g["opp"], ["패", "무", "승"][r + 1], g["line"]], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	if has_game and not career.game_done:
		col.add_child(UiKit.label("연습경기는 순위·시즌 기록에 들어가지 않는다 (성장·폼·용돈은 그대로)", Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	return card


## 훈련만 하는 캠프 주를 마친다 → 남은 이야기 → [다음 주]
func _finish_camp_week() -> void:
	_app.career.finish_camp_week()
	_rebuild()
	_next_story()


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


## 진행 버튼: 스케줄 시작 → (결과 도장) → 경기 준비 / 캠프 훈련 주면 주 마치기 → 다음 주
func _go_button() -> Button:
	var career: CareerState = _app.career
	var p := career.player
	if career.game_done:
		return _wide_button("다음 주  ›", _app.next_week)
	if not _ran():
		var b := _wide_button("스케줄 시작  ›", _start_schedule)
		b.disabled = not career.plan_ready()
		return b
	if _revealed >= 0:
		return _wide_button("건너뛰기  ›", _skip_reveal, Tokens.INK_SOFT)
	if career.in_camp() and not career.camp_has_game():
		return _wide_button("이번 주 마치기  ›", _finish_camp_week)
	return _wide_button("경기 보기 (결장)  ›" if p.is_injured() else ("경기 결과 보기 (벤치)  ›" if career.next_role() == "bench" else "경기 준비  ›"), _app.show_game)


func _wide_button(text: String, on_press: Callable, color: Color = Tokens.ACCENT) -> Button:
	var b := UiKit.button(text, on_press, color, Tokens.FONT_TITLE)
	b.alignment = HORIZONTAL_ALIGNMENT_LEFT
	b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	return b


func _mini_bar(name: String, value: int, color: Color = Tokens.PRIMARY, max_value: int = 99) -> Control:
	var row := UiKit.hbox(Tokens.SPACE_SM)
	var n := UiKit.label(name, Tokens.FONT_CAPTION, Tokens.INK_SOFT)
	n.custom_minimum_size.x = 40
	row.add_child(n)
	var bar := ProgressBar.new()
	bar.max_value = max_value
	bar.value = value
	bar.show_percentage = false
	bar.custom_minimum_size.y = 5
	bar.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	bar.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	var bg := StyleBoxFlat.new()
	bg.bg_color = Tokens.DIVIDER
	var fill := StyleBoxFlat.new()
	fill.bg_color = color
	bar.add_theme_stylebox_override("background", bg)
	bar.add_theme_stylebox_override("fill", fill)
	row.add_child(bar)
	var v := UiKit.label(str(value), Tokens.FONT_CAPTION, Tokens.INK, Tokens.FONT_BOLD)
	v.custom_minimum_size.x = 20
	v.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	row.add_child(v)
	return row


## 받은 소식: 이번 주 훈련 + 지난 경기 뒤 소식 (라이벌 기록·유망주 랭킹·구단 관심)
func _inbox_card() -> Control:
	var career: CareerState = _app.career
	var items: Array = [] if _ran() else ["%s · 이번 주 스케줄을 짜 보자" % career.people.person("coach").call]
	var unread := career.phone.total_unread()
	if unread > 0:
		items.append("폰 · 안 읽은 문자가 있다 (%d)" % unread)
	items.append_array(career.news)
	var card := _room_card()
	var col := UiKit.vbox(Tokens.SPACE_XS)
	card.add_child(col)
	col.add_child(UiKit.kicker("INBOX · %d" % items.size()))
	for i in items.size():
		col.add_child(_inbox_line(items[i], i == 0))
	card.clip_contents = true
	card.set_meta("count", items.size())
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
	for item: Array in [["컨디션", str(p.condition)], ["폼", _form_text(p.form)], ["평판", str(p.reputation)], ["지갑", PlayerData.money_text(p.money)], ["스케줄", "%d/1" % (1 if _ran() else 0)]]:
		var c := UiKit.vbox(0)
		c.add_child(UiKit.label(item[0], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
		c.add_child(UiKit.label(item[1], Tokens.FONT_BODY, Tokens.INK, Tokens.FONT_BOLD))
		_bottom.add_child(c)
	var gap := Control.new()
	gap.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_bottom.add_child(gap)
	var next := UiKit.button("다음 주", _app.next_week, Tokens.ACCENT)
	next.disabled = not _app.career.game_done
	next.tooltip_text = "경기를 마치면 다음 주로 넘어간다"
	next.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	_bottom.add_child(next)


# ---------- 자동 진행 ----------

## 모든 칸을 한 훈련으로 채우고 결과 연출 없이 바로 실행 (스크린샷 스크립트용)
func _train(id: String) -> void:
	_app.career.train(id)
	_revealed = -1
	_rebuild()


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
	var role := "이번 경기 벤치 대기 (대타)" if p.bench_next else p.role_text()
	col.add_child(UiKit.label("%s %d학년 · 외야수 · %s" % [career.my_school.name, p.grade, role], Tokens.FONT_CAPTION, Tokens.INK_SOFT))
	for s in PlayerData.STATS:
		col.add_child(UiKit.stat_bar(PlayerData.STAT_LABELS[s], p.stat(s), 99, Tokens.STAT_COLORS[s]))
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
	var teams := career.scouting.interested_teams(career.cfg, p.scout_interest, p.grade)
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

## 방 그림 위 반투명 흰 판
func _room_card() -> PanelContainer:
	var card := _card()
	var s := card.get_theme_stylebox("panel").duplicate() as StyleBoxFlat
	s.bg_color = Tokens.ROOM_PANEL
	card.add_theme_stylebox_override("panel", s)
	return card


## 위·아래 막대를 반투명 흰 띠 위에 (홈 탭에서 방 그림 위에 글자가 읽히게)
func _on_room_panel(c: Control) -> Control:
	var panel := PanelContainer.new()
	var s := StyleBoxFlat.new()
	s.bg_color = Tokens.ROOM_PANEL
	s.set_corner_radius_all(Tokens.SPACE_SM)
	s.content_margin_left = Tokens.SPACE_SM
	s.content_margin_right = Tokens.SPACE_SM
	s.content_margin_top = 2
	s.content_margin_bottom = 2
	panel.add_theme_stylebox_override("panel", s)
	panel.add_child(c)
	return panel


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
