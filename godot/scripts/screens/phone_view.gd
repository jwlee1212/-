class_name PhoneView
extends Control
## 폰 화면 (허브 위에 겹쳐 뜬다). 가로 화면 가운데 오른쪽에 세로 폰 하나, 그 옆에 지금 보고 있는 인물의 일러스트.
## 폰 안: 메시지(대화방 목록 → 대화방, 답장) · 연락처(관계 상태 문구 → 인물 카드, 선물·만나기).
## 폰 밖 어두운 곳을 누르면 닫힌다. 로직은 Phone(문자) · CareerState.socialize(교류)가 하고 여기는 그리기만 한다.
## 관계는 숫자 없이 문구로만 보인다 — 숨은 수치는 그대로다.

signal closed
## 답장·교류·읽음으로 커리어가 바뀌었다 (허브가 저장한다)
signal changed

const TAB_MESSAGES := "메시지"
const TAB_CONTACTS := "연락처"

var _career: CareerState
var _tab := TAB_MESSAGES
## 열려 있는 대화방 / 인물 카드 ("" 이면 목록)
var _room := ""
var _person := ""
## 대화방에서 마지막 몇 줄을 차례로 띄울지 (답장 직후)
var _reveal := 0

var _side: Control
var _screen: VBoxContainer
var _scroll: ScrollContainer


func _init(career: CareerState, room: String = "") -> void:
	_career = career
	_room = room


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_STOP
	var scrim := ColorRect.new()
	scrim.color = Tokens.SCRIM
	scrim.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(UiKit.fill_parent(scrim))
	var row := UiKit.hbox(Tokens.SPACE_MD)
	row.alignment = BoxContainer.ALIGNMENT_CENTER
	row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var wrap := UiKit.margin(row, Tokens.SPACE_SM)
	wrap.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(UiKit.fill_parent(wrap))
	_side = Control.new()
	_side.custom_minimum_size.x = Tokens.PHONE_SIDE_WIDTH
	_side.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_side.clip_contents = true
	row.add_child(_side)
	var body := PanelContainer.new()
	var s := StyleBoxFlat.new()
	s.bg_color = Tokens.PHONE_SCREEN
	s.border_color = Tokens.PHONE_BODY
	s.set_border_width_all(Tokens.PHONE_BEZEL)
	s.set_corner_radius_all(Tokens.RADIUS_PHONE)
	s.set_content_margin_all(Tokens.PHONE_BEZEL + Tokens.SPACE_SM)
	s.anti_aliasing = true
	body.add_theme_stylebox_override("panel", s)
	body.custom_minimum_size.x = Tokens.PHONE_WIDTH
	row.add_child(body)
	_screen = UiKit.vbox(Tokens.SPACE_XS)
	body.add_child(_screen)
	_rebuild()


## 폰 밖을 누르면 닫는다
func _gui_input(e: InputEvent) -> void:
	if (e is InputEventMouseButton and e.pressed and e.button_index == MOUSE_BUTTON_LEFT) or (e is InputEventScreenTouch and e.pressed):
		accept_event()
		closed.emit()


# ---------- 바깥에서 부르는 것 (버튼 · 스크린샷 스크립트) ----------

func show_tab(tab: String) -> void:
	_tab = tab
	_room = ""
	_person = ""
	_rebuild()


func open_room(room: String) -> void:
	_tab = TAB_MESSAGES
	_room = room
	_rebuild()


func open_person(id: String) -> void:
	_tab = TAB_CONTACTS
	_person = id
	_rebuild()


func reply(index: int) -> void:
	_reveal = _career.phone.reply(_career, _room, index)
	changed.emit()
	_rebuild()


# ---------- 조립 ----------

func _rebuild() -> void:
	for c in _screen.get_children():
		c.queue_free()
	for c in _side.get_children():
		c.queue_free()
	_scroll = null
	_screen.add_child(_status_bar())
	if _tab == TAB_MESSAGES and _room != "":
		_build_room()
	elif _tab == TAB_MESSAGES:
		_build_rooms()
	elif _person != "":
		_build_person()
	else:
		_build_contacts()
	_screen.add_child(UiKit.divider())
	_screen.add_child(_nav())


## 맨 위: 학년·주차 | 닫기
func _status_bar() -> Control:
	var bar := UiKit.hbox()
	var when := "%d학년 겨울 캠프 %d주" % [_career.player.grade, _career.camp_week] if _career.in_camp() else "%d학년 · %d주차" % [_career.player.grade, _career.week]
	var l := UiKit.label(when, Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD)
	l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	bar.add_child(l)
	bar.add_child(_text_button("닫기", func() -> void: closed.emit(), Tokens.INK_SOFT))
	return bar


## 맨 아래: 메시지 · 연락처 (안 읽은 문자 수 / 관계 문구가 바뀐 사람 수)
func _nav() -> Control:
	var bar := UiKit.hbox(0)
	for item: Array in [[TAB_MESSAGES, _career.phone.total_unread()], [TAB_CONTACTS, _career.phone.relation_changes(_career)]]:
		var tab: String = item[0]
		var text := tab if int(item[1]) == 0 else "%s  %d" % [tab, item[1]]
		var b := _text_button(text, func() -> void: show_tab(tab), Tokens.PRIMARY if tab == _tab else (Tokens.BADGE if int(item[1]) > 0 else Tokens.INK_SOFT), Tokens.FONT_BODY)
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		bar.add_child(b)
	return bar


## 목록 화면 머리: 제목 (+ 뒤로)
func _header(title: String, back: Callable = Callable()) -> Control:
	var bar := UiKit.hbox(Tokens.SPACE_SM)
	if back.is_valid():
		bar.add_child(_text_button("‹ 뒤로", back, Tokens.PRIMARY))
	var t := UiKit.title(title, Tokens.FONT_BODY)
	t.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	t.clip_text = true
	bar.add_child(t)
	return bar


func _list() -> VBoxContainer:
	var scroll := ScrollContainer.new()
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	var col := UiKit.vbox(Tokens.SPACE_XS)
	col.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(col)
	_screen.add_child(scroll)
	_scroll = scroll
	return col


# ---------- 메시지 ----------

func _build_rooms() -> void:
	var phone := _career.phone
	_screen.add_child(_header("메시지"))
	var col := _list()
	if phone.order.is_empty():
		col.add_child(_note("아직 받은 문자가 없다."))
	for room: String in phone.order:
		var all: Array = phone.lines(room)
		var last: Dictionary = all[-1]
		var n := int(phone.unread.get(room, 0))
		var waiting := phone.open_message(room) != ""
		var top := UiKit.hbox(Tokens.SPACE_XS)
		var name := UiKit.title(Phone.room_name(_career, room), Tokens.FONT_BODY)
		name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		name.clip_text = true
		top.add_child(name)
		if n > 0:
			top.add_child(_badge(str(n)))
		elif waiting:
			top.add_child(UiKit.label("답장 대기", Tokens.FONT_CAPTION, Tokens.ACCENT, Tokens.FONT_BOLD))
		var preview := UiKit.label(("나: " if last["from"] == Phone.ME else "") + last["text"], Tokens.FONT_CAPTION, Tokens.INK if n > 0 else Tokens.INK_SOFT)
		preview.clip_text = true
		col.add_child(_row([top, preview], func() -> void: open_room(room)))


func _build_room() -> void:
	var phone := _career.phone
	var had_unread := phone.unread.has(_room)
	phone.mark_read(_room)
	if had_unread:
		changed.emit()
	_screen.add_child(_header(Phone.room_name(_career, _room), func() -> void: show_tab(TAB_MESSAGES)))
	var group := Phone.is_group(_career, _room)
	if not group:
		_show_side(_room)
	var col := _list()
	var all: Array = phone.lines(_room)
	var prev := ""
	for i in all.size():
		var line: Dictionary = all[i]
		var from: String = line["from"]
		var node: Control
		if from == Phone.SYSTEM:
			node = _note(line["text"])
		else:
			node = _bubble(line["text"], from == Phone.ME, _career.people.person(from).name if group and from != Phone.ME and from != prev else "")
		prev = from
		col.add_child(node)
		# 답장 직후: 새로 붙은 줄을 차례로 띄운다 (내 답장은 바로, 돌아온 답은 하나씩)
		var k := i - (all.size() - _reveal)
		if k > 0:
			node.modulate.a = 0.0
			var tw := create_tween()
			tw.tween_interval(k * float(_career.cfg.phone["bubbleMs"]) / 1000.0)
			tw.tween_property(node, "modulate:a", 1.0, 0.15)
			tw.tween_callback(_scroll_to_end)
	_reveal = 0
	var options := phone.reply_options(_career, _room)
	if not options.is_empty():
		_screen.add_child(UiKit.divider())
		var left := phone.weeks_left(_career, _room)
		_screen.add_child(UiKit.label("답장 · %s (안 해도 된다)" % ("이번 주까지" if left <= 1 else "%d주 남음" % left), Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD))
		for i in options.size():
			var b := UiKit.button(options[i], func() -> void: reply(i), Tokens.PRIMARY, Tokens.FONT_CAPTION)
			b.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
			b.alignment = HORIZONTAL_ALIGNMENT_LEFT
			_screen.add_child(b)
	_scroll_to_end.call_deferred()


func _scroll_to_end() -> void:
	if not is_instance_valid(_scroll):
		return
	await get_tree().process_frame
	if is_instance_valid(_scroll):
		_scroll.scroll_vertical = int(_scroll.get_v_scroll_bar().max_value)


## 말풍선: 받은 건 왼쪽, 보낸 건 오른쪽. name 이 있으면 위에 보낸 사람 이름 (단체방)
func _bubble(text: String, mine: bool, name: String) -> Control:
	var col := UiKit.vbox(2)
	if name != "":
		col.add_child(UiKit.label(name, Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD))
	var row := UiKit.hbox(0)
	row.alignment = BoxContainer.ALIGNMENT_END if mine else BoxContainer.ALIGNMENT_BEGIN
	var panel := PanelContainer.new()
	var s := StyleBoxFlat.new()
	s.bg_color = Tokens.BUBBLE_ME if mine else Tokens.BUBBLE_THEM
	s.set_corner_radius_all(Tokens.RADIUS_BUBBLE)
	s.content_margin_left = Tokens.SPACE_SM + 2
	s.content_margin_right = Tokens.SPACE_SM + 2
	s.content_margin_top = Tokens.SPACE_XS + 1
	s.content_margin_bottom = Tokens.SPACE_XS + 1
	s.anti_aliasing = true
	panel.add_theme_stylebox_override("panel", s)
	var l := UiKit.label(text, Tokens.FONT_CAPTION + 1, Tokens.BUBBLE_ME_TEXT if mine else Tokens.INK)
	# 짧은 글은 글자 너비만큼, 긴 글은 최대 너비에서 줄을 바꾼다
	var w := Tokens.FONT_REGULAR.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, Tokens.FONT_CAPTION + 1).x
	if w > Tokens.BUBBLE_MAX_WIDTH:
		l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		l.custom_minimum_size.x = Tokens.BUBBLE_MAX_WIDTH
	panel.add_child(l)
	row.add_child(panel)
	col.add_child(row)
	return col


# ---------- 연락처 ----------

func _build_contacts() -> void:
	var phone := _career.phone
	_screen.add_child(_header("연락처"))
	var col := _list()
	for id: String in _career.people.by_id:
		var person: People.Person = _career.people.by_id[id]
		if not person.met:
			continue
		var was := phone.relation_was(_career, id)
		var top := UiKit.hbox(Tokens.SPACE_XS)
		var name := UiKit.title(person.name, Tokens.FONT_BODY)
		name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		top.add_child(name)
		top.add_child(UiKit.label(person.role, Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD))
		var now := _status_text(person)
		var status := UiKit.label(now if was == "" else "%s → %s" % [was, now], Tokens.FONT_CAPTION, Tokens.PRIMARY if was == "" else Tokens.ACCENT, Tokens.FONT_BOLD)
		col.add_child(_row([top, status], func() -> void: open_person(id)))
		if was != "":
			# 관계 문구가 바뀐 순간: 한 번 깜빡이고, 본 것으로 적어 둔다 (다음에 열면 평소 색)
			var tw := create_tween().set_loops(3)
			tw.tween_property(status, "modulate:a", 0.25, 0.25)
			tw.tween_property(status, "modulate:a", 1.0, 0.25)
			phone.see_relation(_career, id)
			changed.emit()
		elif not phone.seen_relation.has(id):
			phone.see_relation(_career, id)


## 관계 상태 문구 (숫자 없이). 연애 중이면 그걸 먼저
func _status_text(person: People.Person) -> String:
	var t := People.relation_text(_career.people.relation(person.id))
	if _career.people.partner == person.id:
		return "♥ 연애 중 · " + t
	return t


func _build_person() -> void:
	var person: People.Person = _career.people.by_id[_person]
	var e := _career.cfg.economy
	_screen.add_child(_header(person.call, func() -> void: show_tab(TAB_CONTACTS)))
	_show_side(_person)
	var col := _list()
	col.add_child(UiKit.label(person.role, Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD))
	var desc := UiKit.label(person.desc, Tokens.FONT_CAPTION + 1, Tokens.INK)
	desc.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	col.add_child(desc)
	col.add_child(_field(person.rel_label, _status_text(person)))
	if person.id == "rival":
		col.add_child(_field("요즘", "유망주 %d위 · 타율 %s" % [_career.rival_rank(), _career.rival.player.season.average()]))
	elif person.team_name != "":
		col.add_child(_field("소속", person.team_name))
	elif person.id == "senior" and _career.player.grade > 1:
		col.add_child(_field("요즘", "졸업 · 대학 야구부"))
	var last := _career.phone.last_contact(person.id)
	var ago := _career.total_week - last
	col.add_child(_field("마지막 문자", "아직 없음" if last < 0 else ("이번 주" if ago == 0 else "%d주 전" % ago)))
	if _career.phone.rooms.has(person.id):
		col.add_child(_text_button("문자 보기 ›", func() -> void: open_room(person.id), Tokens.PRIMARY))
	# 교류 (한 주에 한 번, 돈이 든다): 선물 / 만나기(연애 후보만)
	var buttons := UiKit.hbox(Tokens.SPACE_XS)
	if CareerState.can_gift(person):
		buttons.add_child(_social_button("선물 %s" % PlayerData.money_text(int(e["giftCost"])), person.id, "gift"))
	if person.romance:
		buttons.add_child(_social_button("%s %s" % ["데이트" if _career.people.partner == person.id else "만나기", PlayerData.money_text(int(e["hangoutCost"]))], person.id, "hangout"))
	if buttons.get_child_count() > 0:
		col.add_child(UiKit.divider())
		col.add_child(buttons)
		var why := _career.social_block(person.id, "gift" if CareerState.can_gift(person) else "hangout")
		col.add_child(_note(_career.week_social if _career.week_social != "" else (why if why != "" else "교류는 한 주에 한 번 · 지갑 %s" % PlayerData.money_text(_career.player.money))))


func _social_button(text: String, person_id: String, kind: String) -> Button:
	var b := UiKit.button(text, func() -> void:
		_career.socialize(person_id, kind)
		changed.emit()
		_rebuild(), Tokens.BAD if kind == "hangout" else Tokens.PRIMARY, Tokens.FONT_CAPTION)
	b.disabled = _career.social_block(person_id, kind) != ""
	b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	return b


## "신뢰  그럭저럭" 한 줄
func _field(name: String, value: String) -> Control:
	var row := UiKit.hbox(Tokens.SPACE_SM)
	var n := UiKit.label(name, Tokens.FONT_CAPTION, Tokens.INK_SOFT)
	n.custom_minimum_size.x = 72
	row.add_child(n)
	var v := UiKit.label(value, Tokens.FONT_CAPTION + 1, Tokens.PRIMARY, Tokens.FONT_BOLD)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	row.add_child(v)
	return row


# ---------- 폰 옆 인물 ----------

## 폰 왼쪽에 인물 일러스트(있으면)와 이름표
func _show_side(person_id: String) -> void:
	var person := _career.people.person(person_id)
	if person == null:
		return
	var path := DialogueView.PORTRAIT_DIR % person.id
	if ResourceLoader.exists(path):
		var tex := TextureRect.new()
		tex.texture = load(path)
		tex.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		tex.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
		tex.mouse_filter = Control.MOUSE_FILTER_IGNORE
		_side.add_child(UiKit.fill_parent(tex))
	var plate := UiKit.glass_panel()
	var col := UiKit.vbox(0)
	plate.add_child(col)
	col.add_child(UiKit.label(person.role, Tokens.FONT_CAPTION, Tokens.HUD_TEXT_SOFT, Tokens.FONT_BOLD))
	col.add_child(UiKit.label(person.name, Tokens.FONT_BODY, Tokens.HUD_TEXT, Tokens.FONT_BOLD))
	plate.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_LEFT)
	plate.grow_vertical = Control.GROW_DIRECTION_BEGIN
	_side.add_child(plate)


# ---------- 부품 ----------

## 눌리는 목록 한 칸 (흰 카드 위에 투명 버튼을 겹친다)
func _row(children: Array, on_press: Callable) -> Control:
	var card := UiKit.card(Tokens.SURFACE)
	var s := card.get_theme_stylebox("panel").duplicate() as StyleBoxFlat
	s.set_corner_radius_all(Tokens.RADIUS_BUBBLE)
	s.set_content_margin_all(Tokens.SPACE_SM)
	card.add_theme_stylebox_override("panel", s)
	var col := UiKit.vbox(2)
	col.mouse_filter = Control.MOUSE_FILTER_IGNORE
	for c: Control in children:
		col.add_child(c)
	card.add_child(col)
	var hit := Button.new()
	hit.flat = true
	hit.focus_mode = Control.FOCUS_NONE
	hit.pressed.connect(on_press)
	card.add_child(hit)
	return card


## 가운데 작은 안내 글 (답장할 때를 놓쳤다 · 변화 설명 · 빈 목록)
func _note(text: String) -> Label:
	var l := UiKit.label(EventBook.josa(text), Tokens.FONT_CAPTION, Tokens.INK_SOFT)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	return l


func _badge(text: String) -> Control:
	var p := PanelContainer.new()
	var s := StyleBoxFlat.new()
	s.bg_color = Tokens.BADGE
	s.set_corner_radius_all(Tokens.RADIUS_BUBBLE)
	s.content_margin_left = Tokens.SPACE_SM - 2
	s.content_margin_right = Tokens.SPACE_SM - 2
	p.add_theme_stylebox_override("panel", s)
	p.add_child(UiKit.label(text, Tokens.FONT_CAPTION, Tokens.SURFACE, Tokens.FONT_BOLD))
	return p


## 바탕 없는 글자 버튼
func _text_button(text: String, on_press: Callable, color: Color, size: int = Tokens.FONT_CAPTION) -> Button:
	var b := Button.new()
	b.text = text
	b.flat = true
	b.focus_mode = Control.FOCUS_NONE
	b.add_theme_font_override("font", Tokens.FONT_BOLD)
	b.add_theme_font_size_override("font_size", size)
	for state in ["font_color", "font_hover_color", "font_pressed_color", "font_focus_color"]:
		b.add_theme_color_override(state, color)
	b.pressed.connect(on_press)
	return b
