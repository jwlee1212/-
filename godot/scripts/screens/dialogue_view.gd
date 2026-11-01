class_name DialogueView
extends Control
## 대화 화면 (게임빌 프로야구식): 화면 전체에 배경 · 인물 일러스트 · 이름표 · 대사창 · 선택지.
## 이벤트 하나를 대본대로 진행한다 (DialogueRun): 여러 인물이 번갈아 말하고(말하는 사람에 따라 일러스트·이름표가 바뀐다),
## 중간중간 선택지가 나오고, 고른 것에 따라 다음 대사가 달라진다. 대화가 끝나면 변화를 보여 주고 finished.
## 대사는 글자가 차례로 나오고, 화면을 탭하면 다 나오거나 다음 줄로 넘어간다.
## 오른쪽 위 [건너뛰기]는 "정말로 건너뛰시겠습니까?" 를 물은 뒤 이 장면(이벤트 하나 = 배경 하나)을 끝까지 넘긴다.
## 남은 선택지는 첫 번째(못 고르면 고를 수 있는 것)로 고르고, 변화가 있으면 보여 준 뒤 닫는다 (2026-10-30 유저 요청).
## 선택지에 돈이 들면 값이 보이고, 모자라면 못 누른다. 내가 고른 말은 내 대사로 한 번 나온다.
## 이야기 속 문자(ui phone 줄·폰 선택지)는 대사창 대신 오른쪽에 폰 화면이 떠서 말풍선으로 주고받는다 — 받은 문자는 왼쪽,
## 내 문자는 오른쪽, 선택지는 내 말풍선 모양 버튼. 지문이 나오면 폰을 내리고 대사창으로 돌아온다 (2026-11-02 유저 요청).
##
## 그림은 아직 없어서 자리만 보인다. 파일을 넣으면 자동으로 쓴다:
##   일러스트 godot/art/portraits/<인물 id>.png  (주인공은 얼굴을 보이지 않는다 — 유저가 자기를 넣어 몰입하도록, 2026-11-01)
##   배경     godot/art/backgrounds/<장소 id>.png (EventBook.place_of, 장소 이름은 content.json places)
## 글자 속도는 balance.json career.story.dialogueCharsPerSec.

signal finished

const PORTRAIT_DIR := "res://art/portraits/%s.png"
const BACKGROUND_DIR := "res://art/backgrounds/%s.png"

var _career: CareerState
var _event: Dictionary
var _run: DialogueRun
## 단계: 대사 → 선택 → (대사 …) → 변화 보기 → 끝
enum Stage { LINES, CHOICE, RESULT, DONE }
var _stage := Stage.LINES
var _shown := 0.0
## 지금 일러스트 자리에 있는 인물
var _portrait_id := ""

var _portrait: Control
var _name_plate: PanelContainer
var _name_label: Label
var _text: Label
var _parts: Label
var _more: Label
var _choices: VBoxContainer
var _choice_buttons: Array[Button] = []
var _skip_button: Button
## 건너뛰기 확인 창 (없으면 null)
var _confirm: Control
## 대사창 묶음 (폰 화면이 뜨면 숨긴다)
var _box_area: Control
## 이야기 속 문자 화면 (없으면 null): 폰 몸체 · 대화방 이름 · 말풍선 목록 · 선택지 자리
var _phone: Control
var _phone_title: Label
var _phone_scroll: ScrollContainer
var _phone_list: VBoxContainer
var _phone_choices: VBoxContainer


func _init(career: CareerState, event: Dictionary) -> void:
	_career = career
	_event = event
	_run = DialogueRun.new(career, event)


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_STOP
	_build_background()
	_portrait = Control.new()
	_portrait.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_portrait.anchor_left = 0.06
	_portrait.anchor_right = 0.36
	_portrait.anchor_top = 0.08
	_portrait.anchor_bottom = 1.0
	_portrait.offset_bottom = -Tokens.SPACE_LG  # 대사창 아래로 삐져나오지 않게
	_portrait.clip_contents = true
	add_child(_portrait)
	_build_header()
	_build_box()
	_choices = UiKit.vbox(Tokens.SPACE_SM)
	_choices.anchor_left = 0.52
	_choices.anchor_right = 1.0
	_choices.anchor_top = 0.12
	_choices.anchor_bottom = 0.62
	_choices.offset_right = -Tokens.SPACE_MD
	_choices.alignment = BoxContainer.ALIGNMENT_END
	add_child(_choices)
	_build_skip()
	_step()


func _process(delta: float) -> void:
	if _text.visible_ratio < 1.0:
		_shown += delta * float(_career.cfg.story["dialogueCharsPerSec"])
		_text.visible_characters = int(_shown)
		if _text.visible_characters >= _text.get_total_character_count():
			_text.visible_ratio = 1.0
	_more.visible = _text.visible_ratio >= 1.0 and _stage != Stage.CHOICE
	_skip_button.visible = (_stage == Stage.LINES or _stage == Stage.CHOICE) and _confirm == null


func _gui_input(e: InputEvent) -> void:
	var tapped: bool = (e is InputEventMouseButton and e.pressed and e.button_index == MOUSE_BUTTON_LEFT) or (e is InputEventScreenTouch and e.pressed)
	if tapped:
		accept_event()
		advance()


## 탭: 글자가 아직 나오는 중이면 다 보여 주고, 아니면 다음 줄 / 선택지 / 끝
func advance() -> void:
	if _text.visible_ratio < 1.0:
		_text.visible_ratio = 1.0
		return
	match _stage:
		Stage.LINES:
			_step()
		Stage.RESULT:
			_stage = Stage.DONE
			finished.emit()


## 선택 (버튼 · 스크린샷 스크립트). 아직 대사가 남아 있으면 다음 선택 지점까지 넘긴 뒤 고른다
func choose(index: int) -> void:
	if _stage == Stage.RESULT or _stage == Stage.DONE:
		return
	while not _run.at_choice():
		if _run.next()["type"] == "end":
			_end()
			return
	if _run.block(index) != "":
		return
	var label: String = _run.next()["options"][index]["label"]
	_run.choose(index)
	_clear_choices()
	_stage = Stage.LINES
	# 폰 선택지: 보낸 말을 내 말풍선으로 (괄호만 있는 행동이면 말풍선 없이) 바로 다음 줄로
	if _run.last_was_phone:
		if _run.last_phone_text != "" and _run.last_echo:
			_phone_bubble(_run.last_phone_text, true)
		_step()
		return
	# 대본 이벤트는 내가 고른 말을 내 대사로 한 번 보여 준다 (괄호로 쓴 행동은 지문으로).
	# 옛 형식의 짧은 이벤트는 선택지가 대개 행동이라 바로 결과로 넘어간다
	if not _event.has("script") or not _run.last_echo:
		_step()
	elif label.begins_with("("):
		_show_line({"who": "", "name": "", "role": "", "portrait": "", "face": "", "text": label.trim_prefix("(").trim_suffix(")")})
	else:
		_show_line({"who": "player", "name": _career.player.name, "role": "", "portrait": "", "face": "", "text": label})


## 남은 대화를 건너뛴다: 선택 지점마다 want 번째(막혀 있으면 고를 수 있는 것)를 고르고 끝낸다 (스크린샷 스크립트·건너뛰기)
func skip(want: int = 0) -> void:
	if _stage == Stage.RESULT or _stage == Stage.DONE:
		if _stage == Stage.RESULT:
			_stage = Stage.DONE
			finished.emit()
		return
	_run.auto(want)
	_clear_choices()
	_run.commit()
	_stage = Stage.DONE
	finished.emit()


## [건너뛰기]: 확인 창을 띄운다
func ask_skip() -> void:
	if _confirm != null or is_chosen():
		return
	var cover := ColorRect.new()
	cover.color = Tokens.SCRIM
	_confirm = UiKit.fill_parent(cover)
	add_child(_confirm)
	var card := UiKit.card()
	var col := UiKit.vbox(Tokens.SPACE_SM)
	card.add_child(col)
	var q := UiKit.title("정말로 건너뛰시겠습니까?", Tokens.FONT_TITLE)
	q.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	col.add_child(q)
	var note := UiKit.label("이 장면을 끝까지 넘깁니다. 남은 선택지는 자동으로 고릅니다.", Tokens.FONT_CAPTION, Tokens.INK_SOFT)
	note.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	col.add_child(note)
	var row := UiKit.hbox(Tokens.SPACE_SM)
	row.alignment = BoxContainer.ALIGNMENT_CENTER
	row.add_child(UiKit.button("계속 보기", _close_confirm, Tokens.INK_SOFT))
	row.add_child(UiKit.button("건너뛰기", skip_scene, Tokens.PRIMARY))
	col.add_child(row)
	card.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
	card.grow_horizontal = Control.GROW_DIRECTION_BOTH
	card.grow_vertical = Control.GROW_DIRECTION_BOTH
	cover.add_child(card)


func _close_confirm() -> void:
	if _confirm != null:
		_confirm.queue_free()
		_confirm = null


## 이 장면을 끝까지 넘긴다: 남은 선택은 첫 번째로 고르고, 마지막 대사와 변화를 보여 준다 (변화가 없으면 바로 닫는다)
func skip_scene() -> void:
	_close_confirm()
	if is_chosen():
		return
	_run.auto(0)
	_clear_choices()
	_stage = Stage.LINES
	_show_line({"who": "", "name": "", "role": "", "portrait": "", "face": "", "text": _run.last_text, "ui": ""})
	_text.visible_ratio = 1.0
	_end()


## 대화가 끝났나 (변화를 보여 주는 중이거나 닫히는 중)
func is_chosen() -> bool:
	return _stage == Stage.RESULT or _stage == Stage.DONE


# ---------- 줄 · 선택지 ----------

## 대본의 다음 걸음
func _step() -> void:
	var step := _run.next()
	match step["type"]:
		"line":
			_show_line(step)
		"choice":
			if step.get("ui", "") == "phone":
				_phone_on(step.get("room", ""))
			_show_choices(step["options"])
		"end":
			_end()


func _show_line(line: Dictionary) -> void:
	if line.get("ui", "") == "phone" and line["who"] != "":
		_phone_on(line.get("room", ""))
		if line["portrait"] != "" and line["who"] != "player" and _portrait_key(line) != _portrait_id:
			_set_portrait(line["portrait"], line["name"], line["face"])
		_phone_bubble(line["text"], line["who"] == "player")
		_text.text = ""
		_text.visible_ratio = 1.0
		return
	_phone_off()
	var narration: bool = line["who"] == ""
	var mine: bool = line["who"] == "player"
	if line["portrait"] != "" and _portrait_key(line) != _portrait_id:
		_set_portrait(line["portrait"], line["name"], line["face"])
	_set_speaker(line["name"], line["role"])
	_portrait.modulate = Tokens.PORTRAIT_IDLE if narration or mine else Color.WHITE
	_type(line["text"], Tokens.NARRATION if narration else (Tokens.PRIMARY if mine else Tokens.INK))


func _show_choices(options: Array) -> void:
	_stage = Stage.CHOICE
	for i in options.size():
		var o: Dictionary = options[i]
		var text: String = o["label"]
		if _phone != null:
			# 폰 선택지: 내 말풍선 모양 버튼 (오른쪽)
			var send := DialogueRun.phone_text(text)
			var pb := UiKit.button(send if send != "" else text, func() -> void: choose(i), Tokens.BUBBLE_ME if send != "" else Tokens.INK_SOFT, Tokens.FONT_CAPTION + 1)
			pb.size_flags_horizontal = Control.SIZE_SHRINK_END
			# 긴 말만 줄을 바꾼다 (짧은 말에 줄바꿈을 켜면 글자 폭이 0 으로 잡힌다)
			if Tokens.FONT_REGULAR.get_string_size(pb.text, HORIZONTAL_ALIGNMENT_LEFT, -1, Tokens.FONT_CAPTION + 1).x > Tokens.BUBBLE_MAX_WIDTH:
				pb.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
				pb.custom_minimum_size.x = Tokens.BUBBLE_MAX_WIDTH
			_phone_choices.add_child(pb)
			_choice_buttons.append(pb)
			continue
		if int(o["cost"]) > 0:
			text += "  · %s" % PlayerData.money_text(int(o["cost"]))
		var b := UiKit.button(text, func() -> void: choose(i), Tokens.PRIMARY, Tokens.FONT_LABEL)
		b.alignment = HORIZONTAL_ALIGNMENT_LEFT
		if o["block"] != "":
			b.disabled = true
			b.text += "  (돈 부족)"
		_choices.add_child(b)
		_choice_buttons.append(b)


func _clear_choices() -> void:
	for b in _choice_buttons:
		b.queue_free()
	_choice_buttons.clear()


## 대화 끝: 효과를 반영하고, 변화가 있으면 마지막 줄 아래에 보여 준 뒤 한 번 더 탭하면 닫는다
func _end() -> void:
	_run.commit()
	var parts := _career.book.last_parts
	if parts.is_empty():
		_stage = Stage.DONE
		finished.emit()
		return
	_stage = Stage.RESULT
	_box_area.visible = true  # 폰 화면이 떠 있어도 변화는 대사창에
	_parts.text = " · ".join(parts)
	_parts.visible = true


func _set_speaker(name: String, role: String) -> void:
	_name_plate.visible = name != ""
	_name_label.text = name if role == "" or name.contains(role) else "%s  ·  %s" % [name, role]


func _type(text: String, color: Color) -> void:
	_text.text = text
	_text.add_theme_color_override("font_color", color)
	_text.visible_characters = 0
	_shown = 0.0


# ---------- 그리기 ----------

## 배경: 그림이 있으면 그림, 없으면 단색 + "배경 · 장소" 자리 표시
func _build_background() -> void:
	var place := EventBook.place_of(_career, _event)
	var path := BACKGROUND_DIR % place
	if ResourceLoader.exists(path):
		var tex := TextureRect.new()
		tex.texture = load(path)
		tex.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		tex.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
		tex.mouse_filter = Control.MOUSE_FILTER_IGNORE
		add_child(UiKit.fill_parent(tex))
		var scrim := ColorRect.new()
		scrim.color = Tokens.DIALOGUE_BG_SCRIM
		scrim.mouse_filter = Control.MOUSE_FILTER_IGNORE
		add_child(UiKit.fill_parent(scrim))
		return
	var bg := ColorRect.new()
	bg.color = Tokens.DIALOGUE_BG
	bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(UiKit.fill_parent(bg))
	var name: String = _career.cfg.places.get(place, place)
	var hint := UiKit.label("배경 · %s\nart/backgrounds/%s.png" % [name, place], Tokens.FONT_CAPTION, Tokens.DIALOGUE_BG_TEXT, Tokens.FONT_BOLD)
	hint.position = Vector2(Tokens.SPACE_MD, Tokens.SPACE_MD)
	hint.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(hint)


## 지금 일러스트 열쇠: 인물 id (+ _표정)
static func _portrait_key(line: Dictionary) -> String:
	return line["portrait"] if line["face"] == "" else "%s_%s" % [line["portrait"], line["face"]]


## 일러스트: 왼쪽 가운데, 대사창 뒤로 아래가 가려지게. 말하는 사람이 바뀌면 갈아 끼운다.
## 표정(face)이 있으면 <id>_<face>.png, 그 그림이 없으면 기본 <id>.png.
## 그림이 없으면 테두리 상자 + 이름 + 파일 경로. id 가 none 이면 비운다
func _set_portrait(id: String, shown_name: String, face := "") -> void:
	_portrait_id = id if face == "" else "%s_%s" % [id, face]
	for c in _portrait.get_children():
		c.queue_free()
	if id == "none" or id == "":
		return
	var path := PORTRAIT_DIR % _portrait_id
	if not ResourceLoader.exists(path):
		path = PORTRAIT_DIR % id
	if ResourceLoader.exists(path):
		_portrait.add_child(UiKit.fill_parent(_picture(path)))
		return
	var slot := PanelContainer.new()
	var style := StyleBoxFlat.new()
	style.bg_color = Tokens.PORTRAIT_SLOT
	style.border_color = Tokens.PORTRAIT_BORDER
	style.set_border_width_all(2)
	style.corner_radius_top_left = Tokens.RADIUS_CARD
	style.corner_radius_top_right = Tokens.RADIUS_CARD
	slot.add_theme_stylebox_override("panel", style)
	slot.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_portrait.add_child(UiKit.fill_parent(slot))
	var col := UiKit.vbox(Tokens.SPACE_XS)
	col.alignment = BoxContainer.ALIGNMENT_CENTER
	col.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	for l: Label in [UiKit.label("일러스트 자리", Tokens.FONT_CAPTION, Tokens.PORTRAIT_BORDER, Tokens.FONT_BOLD),
			UiKit.title(shown_name, Tokens.FONT_TITLE, Tokens.PORTRAIT_BORDER),
			UiKit.label("art/portraits/%s.png" % id, Tokens.FONT_CAPTION, Tokens.PORTRAIT_BORDER)]:
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		col.add_child(l)
	var top := MarginContainer.new()
	top.add_theme_constant_override("margin_top", Tokens.SPACE_LG * 2)
	top.add_child(col)
	slot.add_child(top)


## 일러스트 그림 한 장: 자리를 꽉 채우고 넘치는 쪽은 자른다 (그림 비율이 달라도 인물 크기가 비슷하게 보인다)
func _picture(path: String) -> TextureRect:
	var tex := TextureRect.new()
	tex.texture = load(path)
	tex.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	tex.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
	tex.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return tex


## 오른쪽 위 머리말 아래: [건너뛰기 ▶▶] (변화를 보여 주는 중엔 숨는다)
func _build_skip() -> void:
	_skip_button = UiKit.button("건너뛰기  ▶▶", ask_skip, Tokens.DIALOGUE_HEADER_BG, Tokens.FONT_CAPTION + 1)
	_skip_button.add_theme_color_override("font_color", Tokens.INK)
	_skip_button.add_theme_color_override("font_hover_color", Tokens.INK)
	_skip_button.add_theme_color_override("font_pressed_color", Tokens.INK)
	add_child(_skip_button)
	_skip_button.set_anchors_and_offsets_preset(Control.PRESET_TOP_RIGHT, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_SM)
	_skip_button.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	_skip_button.position.y += Tokens.SPACE_LG * 2  # 머리말 판 아래


## 오른쪽 위: 이야기 머리말 + 지갑
func _build_header() -> void:
	var story: bool = _event.get("story", false)
	var panel := PanelContainer.new()
	var style := StyleBoxFlat.new()
	style.bg_color = Tokens.DIALOGUE_HEADER_BG
	style.set_corner_radius_all(Tokens.SPACE_SM)
	style.content_margin_left = Tokens.SPACE_SM + Tokens.SPACE_XS
	style.content_margin_right = Tokens.SPACE_SM + Tokens.SPACE_XS
	style.content_margin_top = Tokens.SPACE_XS
	style.content_margin_bottom = Tokens.SPACE_XS
	panel.add_theme_stylebox_override("panel", style)
	panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var col := UiKit.vbox(2)
	col.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var head := UiKit.kicker("STORY · %s" % EventBook.chapter(_career) if story else "EVENT · %s" % _event.get("title", ""), Tokens.ACCENT if story else Tokens.PRIMARY)
	head.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	col.add_child(head)
	var wallet := UiKit.label("지갑 %s" % PlayerData.money_text(_career.player.money), Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD)
	wallet.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	col.add_child(wallet)
	panel.add_child(col)
	add_child(panel)
	panel.set_anchors_and_offsets_preset(Control.PRESET_TOP_RIGHT, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_SM)
	panel.grow_horizontal = Control.GROW_DIRECTION_BEGIN


# ---------- 이야기 속 문자 (폰 화면) ----------

## 폰 화면을 띄운다 (이미 떠 있으면 대화방 이름만). 대사창은 숨긴다
func _phone_on(room: String) -> void:
	if _phone == null:
		_build_phone()
	if room != "":
		_phone_title.text = Phone.room_name(_career, room)
	_box_area.visible = false


## 폰 화면을 내린다 (지문·보통 대사가 나오면)
func _phone_off() -> void:
	if _phone != null:
		_phone.queue_free()
		_phone = null
	_box_area.visible = true


## 말풍선 하나를 붙이고 맨 아래로 내린다
func _phone_bubble(text: String, mine: bool) -> void:
	var b := PhoneView.bubble(text, mine, "")
	b.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_phone_list.add_child(b)
	b.modulate.a = 0.0
	create_tween().tween_property(b, "modulate:a", 1.0, 0.15)
	_phone_scroll_end.call_deferred()


func _phone_scroll_end() -> void:
	await get_tree().process_frame
	if is_instance_valid(_phone_scroll):
		_phone_scroll.scroll_vertical = int(_phone_scroll.get_v_scroll_bar().max_value)


## 폰 몸체: 오른쪽 가운데, 화면 높이 거의 전부. 위에 대화방 이름, 가운데 말풍선, 아래 선택지
func _build_phone() -> void:
	var body := PanelContainer.new()
	var s := StyleBoxFlat.new()
	s.bg_color = Tokens.PHONE_SCREEN
	s.border_color = Tokens.PHONE_BODY
	s.set_border_width_all(Tokens.PHONE_BEZEL)
	s.set_corner_radius_all(Tokens.RADIUS_PHONE)
	s.set_content_margin_all(Tokens.PHONE_BEZEL + Tokens.SPACE_SM)
	s.anti_aliasing = true
	body.add_theme_stylebox_override("panel", s)
	body.mouse_filter = Control.MOUSE_FILTER_IGNORE
	body.anchor_left = 0.5
	body.anchor_right = 0.5
	body.anchor_top = 0.0
	body.anchor_bottom = 1.0
	# 일러스트(왼쪽)와 머리말(오른쪽 위) 사이
	body.offset_left = -Tokens.PHONE_WIDTH / 2.0 + Tokens.PHONE_WIDTH * 0.2
	body.offset_right = Tokens.PHONE_WIDTH / 2.0 + Tokens.PHONE_WIDTH * 0.2
	body.offset_top = Tokens.SPACE_SM
	body.offset_bottom = -Tokens.SPACE_SM
	add_child(body)
	_phone = body
	var col := UiKit.vbox(Tokens.SPACE_XS)
	col.mouse_filter = Control.MOUSE_FILTER_IGNORE
	body.add_child(col)
	_phone_title = UiKit.title("", Tokens.FONT_BODY)
	_phone_title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	col.add_child(_phone_title)
	col.add_child(UiKit.divider())
	_phone_scroll = ScrollContainer.new()
	_phone_scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	_phone_scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_phone_scroll.mouse_filter = Control.MOUSE_FILTER_PASS
	col.add_child(_phone_scroll)
	_phone_list = UiKit.vbox(Tokens.SPACE_XS)
	_phone_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_phone_list.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_phone_scroll.add_child(_phone_list)
	_phone_choices = UiKit.vbox(Tokens.SPACE_XS)
	_phone_choices.mouse_filter = Control.MOUSE_FILTER_IGNORE
	col.add_child(_phone_choices)
	# 오른쪽 위 머리말·건너뛰기를 가리지 않게 맨 뒤에서 앞으로 옮기지 않는다 (먼저 그려진 것 위에 폰)
	move_child(body, get_child_count() - 1)
	if _skip_button != null:
		move_child(_skip_button, get_child_count() - 1)


## 지금 떠 있는 폰 화면 (스크린샷 스크립트용, 없으면 null)
func phone_screen() -> Control:
	return _phone


## 아래: 이름표 + 대사창 (글자 · 변화 · ▼)
func _build_box() -> void:
	var area := Control.new()
	_box_area = area
	area.mouse_filter = Control.MOUSE_FILTER_IGNORE
	area.anchor_left = 0.0
	area.anchor_right = 1.0
	area.anchor_top = 1.0
	area.anchor_bottom = 1.0
	area.offset_left = Tokens.SPACE_MD
	area.offset_right = -Tokens.SPACE_MD
	area.offset_top = -132
	area.offset_bottom = -Tokens.SPACE_MD
	add_child(area)
	var box := PanelContainer.new()
	var style := StyleBoxFlat.new()
	style.bg_color = Tokens.DIALOGUE_BOX
	style.set_corner_radius_all(Tokens.SPACE_SM + Tokens.SPACE_XS)
	style.border_color = Tokens.PRIMARY
	style.border_width_top = 3
	style.content_margin_left = Tokens.SPACE_LG
	style.content_margin_right = Tokens.SPACE_LG
	style.content_margin_top = Tokens.SPACE_MD + Tokens.SPACE_XS
	style.content_margin_bottom = Tokens.SPACE_SM
	box.add_theme_stylebox_override("panel", style)
	box.mouse_filter = Control.MOUSE_FILTER_IGNORE
	area.add_child(UiKit.fill_parent(box))
	var col := UiKit.vbox(Tokens.SPACE_XS)
	col.mouse_filter = Control.MOUSE_FILTER_IGNORE
	box.add_child(col)
	_text = UiKit.label("", Tokens.FONT_BODY + 1, Tokens.INK, Tokens.FONT_BOLD)
	_text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_text.size_flags_vertical = Control.SIZE_EXPAND_FILL
	col.add_child(_text)
	var bottom := UiKit.hbox()
	_parts = UiKit.label("", Tokens.FONT_CAPTION, Tokens.GOOD, Tokens.FONT_BOLD)
	_parts.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_parts.visible = false
	bottom.add_child(_parts)
	_more = UiKit.label("▼", Tokens.FONT_CAPTION, Tokens.PRIMARY, Tokens.FONT_BOLD)
	_more.size_flags_horizontal = Control.SIZE_SHRINK_END | Control.SIZE_EXPAND
	bottom.add_child(_more)
	col.add_child(bottom)
	# 이름표: 대사창 왼쪽 위에 걸친다
	_name_plate = PanelContainer.new()
	var ps := StyleBoxFlat.new()
	ps.bg_color = Tokens.ACCENT if _event.get("story", false) else Tokens.PRIMARY
	ps.set_corner_radius_all(Tokens.SPACE_SM)
	ps.content_margin_left = Tokens.SPACE_MD
	ps.content_margin_right = Tokens.SPACE_MD
	ps.content_margin_top = 2
	ps.content_margin_bottom = 2
	_name_plate.add_theme_stylebox_override("panel", ps)
	_name_plate.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_name_plate.position = Vector2(Tokens.SPACE_MD, -Tokens.SPACE_MD)
	_name_label = UiKit.label("", Tokens.FONT_LABEL, Tokens.SURFACE, Tokens.FONT_BOLD)
	_name_plate.add_child(_name_label)
	area.add_child(_name_plate)
