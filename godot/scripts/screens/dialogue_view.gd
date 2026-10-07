class_name DialogueView
extends Control
## 대화 화면 (게임빌 프로야구식): 화면 전체에 배경 · 인물 일러스트 · 이름표 · 대사창 · 선택지.
## 이벤트 하나를 보여 준다. 대사는 글자가 차례로 나오고, 화면을 탭하면 다 나오거나 다음 줄로 넘어간다.
## 마지막 줄 뒤에 선택지(돈이 드는 건 값 표시, 모자라면 못 누름) → 내 대답 → 결과(지문) + 변화 → 탭하면 finished.
##
## 그림은 아직 없어서 자리만 보인다. 파일을 넣으면 자동으로 쓴다:
##   일러스트 godot/art/portraits/<인물 id>.png  (EventBook.portrait_of)
##   배경     godot/art/backgrounds/<장소 id>.png (EventBook.place_of, 장소 이름은 content.json places)
## 글자 속도는 balance.json career.story.dialogueCharsPerSec.

signal finished

const PORTRAIT_DIR := "res://art/portraits/%s.png"
const BACKGROUND_DIR := "res://art/backgrounds/%s.png"

var _career: CareerState
var _event: Dictionary
## 보여 줄 줄들 [{"who", "name", "role", "text"}], 지금 줄 번호
var _lines: Array = []
var _index := 0
## 단계: 대사 → 선택 → 결과
enum Stage { LINES, CHOICE, RESULT, DONE }
var _stage := Stage.LINES
var _shown := 0.0

var _portrait: Control
var _name_plate: PanelContainer
var _name_label: Label
var _text: Label
var _parts: Label
var _more: Label
var _choices: VBoxContainer
var _choice_buttons: Array[Button] = []


func _init(career: CareerState, event: Dictionary) -> void:
	_career = career
	_event = event
	_lines = EventBook.lines(career, event)


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_STOP
	_build_background()
	_build_portrait()
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
	_show_line()


func _process(delta: float) -> void:
	if _text.visible_ratio < 1.0:
		_shown += delta * float(_career.cfg.story["dialogueCharsPerSec"])
		_text.visible_characters = int(_shown)
		if _text.visible_characters >= _text.get_total_character_count():
			_text.visible_ratio = 1.0
	_more.visible = _text.visible_ratio >= 1.0 and _stage != Stage.CHOICE


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
			_index += 1
			if _index < _lines.size():
				_show_line()
			else:
				_show_choices()
		Stage.RESULT:
			_stage = Stage.DONE
			finished.emit()


## 선택 (버튼 · 스크린샷 스크립트). 대사가 남아 있어도 바로 고른다
func choose(index: int) -> void:
	if _stage == Stage.RESULT or _stage == Stage.DONE:
		return
	var choice: Dictionary = _event["choices"][index]
	if EventBook.choice_block(_career, choice) != "":
		return
	for b in _choice_buttons:
		b.queue_free()
	_choice_buttons.clear()
	var label := EventBook.fill(_career, choice["label"])
	_career.resolve_event(_event, index)
	var entry: Dictionary = _career.book.log[-1]
	_stage = Stage.RESULT
	# 내 대답은 이름표 아래 작은 줄로, 결과는 지문으로
	_set_speaker("", "")
	_portrait.modulate = Color(1, 1, 1, 0.55)
	_type("“%s”\n%s" % [label, entry["result"]], Tokens.INK)
	_parts.text = " · ".join(_career.book.last_parts)
	_parts.visible = not _career.book.last_parts.is_empty()


func is_chosen() -> bool:
	return _stage == Stage.RESULT or _stage == Stage.DONE


# ---------- 줄 · 선택지 ----------

func _show_line() -> void:
	if _lines.is_empty():
		_show_choices()
		return
	var line: Dictionary = _lines[_index]
	var narration: bool = line["who"] == ""
	_set_speaker(line["name"], line["role"])
	_portrait.modulate = Color(1, 1, 1, 0.55) if narration else Color.WHITE
	_type(line["text"], Tokens.NARRATION if narration else Tokens.INK)


func _show_choices() -> void:
	_stage = Stage.CHOICE
	var choices: Array = _event["choices"]
	for i in choices.size():
		var c: Dictionary = choices[i]
		var text := EventBook.fill(_career, c["label"])
		if c.has("cost"):
			text += "  · %s" % PlayerData.money_text(int(c["cost"]))
		var b := UiKit.button(text, func() -> void: choose(i), Tokens.PRIMARY, Tokens.FONT_LABEL)
		b.alignment = HORIZONTAL_ALIGNMENT_LEFT
		var block := EventBook.choice_block(_career, c)
		if block != "":
			b.disabled = true
			b.text += "  (돈 부족)"
		_choices.add_child(b)
		_choice_buttons.append(b)


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


## 일러스트: 왼쪽 가운데, 대사창 뒤로 아래가 가려지게. 그림이 없으면 테두리 상자 + 이름 + 파일 경로
func _build_portrait() -> void:
	var id := EventBook.portrait_of(_career, _event)
	var holder := Control.new()
	holder.mouse_filter = Control.MOUSE_FILTER_IGNORE
	holder.anchor_left = 0.06
	holder.anchor_right = 0.36
	holder.anchor_top = 0.08
	holder.anchor_bottom = 1.0
	add_child(holder)
	_portrait = holder
	if id == "none":
		return
	var path := PORTRAIT_DIR % id
	if ResourceLoader.exists(path):
		var tex := TextureRect.new()
		tex.texture = load(path)
		tex.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		tex.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		tex.mouse_filter = Control.MOUSE_FILTER_IGNORE
		holder.add_child(UiKit.fill_parent(tex))
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
	holder.add_child(UiKit.fill_parent(slot))
	var col := UiKit.vbox(Tokens.SPACE_XS)
	col.alignment = BoxContainer.ALIGNMENT_CENTER
	col.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	var name := EventBook.speaker_name(_career, _event)
	for l: Label in [UiKit.label("일러스트 자리", Tokens.FONT_CAPTION, Tokens.PORTRAIT_BORDER, Tokens.FONT_BOLD),
			UiKit.title(name, Tokens.FONT_TITLE, Tokens.PORTRAIT_BORDER),
			UiKit.label("art/portraits/%s.png" % id, Tokens.FONT_CAPTION, Tokens.PORTRAIT_BORDER)]:
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		col.add_child(l)
	var top := MarginContainer.new()
	top.add_theme_constant_override("margin_top", Tokens.SPACE_LG * 2)
	top.add_child(col)
	slot.add_child(top)


## 오른쪽 위: 이야기 머리말 + 지갑
func _build_header() -> void:
	var story: bool = _event.get("story", false)
	var col := UiKit.vbox(2)
	col.anchor_left = 1.0
	col.anchor_right = 1.0
	col.offset_left = -320
	col.offset_right = -Tokens.SPACE_MD
	col.offset_top = Tokens.SPACE_MD
	col.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var head := UiKit.kicker("STORY · %s" % EventBook.chapter(_career) if story else "EVENT · %s" % _event.get("title", ""), Tokens.ACCENT if story else Tokens.PRIMARY)
	head.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	col.add_child(head)
	var wallet := UiKit.label("지갑 %s" % PlayerData.money_text(_career.player.money), Tokens.FONT_CAPTION, Tokens.INK_SOFT, Tokens.FONT_BOLD)
	wallet.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	col.add_child(wallet)
	add_child(col)


## 아래: 이름표 + 대사창 (글자 · 변화 · ▼)
func _build_box() -> void:
	var area := Control.new()
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
