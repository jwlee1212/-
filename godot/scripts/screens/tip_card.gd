class_name TipCard
extends Control
## 튜토리얼 팁 카드 (2026-11-02): 주위를 어둡게 덮고 가운데 카드에 제목과 번호 붙은 단계 몇 줄을 보여 준다.
## 단계 글의 [버튼 이름] 은 굵은 강조색으로 보인다. [알겠어요] → finished(false), [팁 그만 보기] → finished(true)
## (남은 팁을 모두 본 것으로 친다 — 정하는 건 앱이 한다). 내용은 content.json tutorial, 언제 띄울지는 Tutorial.

signal finished(skip_all: bool)

## 카드 너비 (px) — 844 가로 화면에서 양옆에 여백이 남고, 한 줄에 서른 자 남짓
const CARD_WIDTH := 560
## 단계 번호 동그라미 지름 (px)
const BADGE := 24

## 어느 팁인가 (Tutorial.IDS, 스크린샷 스크립트용)
var id := ""
var _done := false


func _init(tip: Dictionary, p_id: String = "") -> void:
	id = p_id
	mouse_filter = Control.MOUSE_FILTER_STOP
	var cover := ColorRect.new()
	cover.color = Tokens.SCRIM
	cover.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(UiKit.fill_parent(cover))
	var center := CenterContainer.new()
	center.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(UiKit.fill_parent(center))
	var card := PanelContainer.new()
	# 어두운 막 위라 반투명 흰 판(Card)은 회색으로 보인다 → 같은 판을 불투명하게
	var box := ThemeBuilder.card_box()
	box.bg_color = Tokens.SURFACE
	card.add_theme_stylebox_override("panel", box)
	card.custom_minimum_size.x = CARD_WIDTH
	center.add_child(card)
	var col := VBoxContainer.new()
	col.add_theme_constant_override("separation", Tokens.SPACE_SM)
	card.add_child(col)

	var kicker := Label.new()
	kicker.theme_type_variation = "Kicker"
	kicker.text = "TIP"
	kicker.add_theme_color_override("font_color", Tokens.ACCENT)
	col.add_child(kicker)
	var title := Label.new()
	title.theme_type_variation = "Title"
	title.text = str(tip.get("title", ""))
	col.add_child(title)

	var steps := VBoxContainer.new()
	steps.add_theme_constant_override("separation", Tokens.SPACE_SM)
	col.add_child(steps)
	var n := 0
	for s: Variant in tip.get("steps", []):
		n += 1
		steps.add_child(_step(n, str(s)))

	var foot := HBoxContainer.new()
	foot.add_theme_constant_override("separation", Tokens.SPACE_SM)
	foot.alignment = BoxContainer.ALIGNMENT_END
	col.add_child(foot)
	var skip := Button.new()
	skip.theme_type_variation = "SecondaryButton"
	skip.text = "팁 그만 보기"
	skip.focus_mode = Control.FOCUS_NONE
	skip.custom_minimum_size.y = Tokens.TOUCH_MIN
	skip.pressed.connect(_close.bind(true))
	foot.add_child(skip)
	var ok := Button.new()
	ok.theme_type_variation = "CtaButton"
	ok.text = "알겠어요"
	ok.focus_mode = Control.FOCUS_NONE
	ok.custom_minimum_size.y = Tokens.TOUCH_MIN
	ok.pressed.connect(_close.bind(false))
	foot.add_child(ok)


## 단계 한 줄: 주황 번호 동그라미 + 글 (줄바꿈)
func _step(n: int, text: String) -> Control:
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", Tokens.SPACE_SM)
	var badge := PanelContainer.new()
	var style := StyleBoxFlat.new()
	style.bg_color = Tokens.ACCENT
	style.set_corner_radius_all(BADGE / 2)
	style.anti_aliasing = true
	badge.add_theme_stylebox_override("panel", style)
	badge.custom_minimum_size = Vector2(BADGE, BADGE)
	badge.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	var num := Label.new()
	num.text = str(n)
	num.add_theme_font_override("font", Tokens.FONT_BOLD)
	num.add_theme_font_size_override("font_size", Tokens.FONT_CAPTION)
	num.add_theme_color_override("font_color", Tokens.SURFACE)
	num.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	num.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	badge.add_child(num)
	row.add_child(badge)
	var body := RichTextLabel.new()
	body.bbcode_enabled = true
	body.fit_content = true
	body.scroll_active = false
	body.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	body.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	body.mouse_filter = Control.MOUSE_FILTER_IGNORE
	body.add_theme_font_override("normal_font", Tokens.FONT_REGULAR)
	body.add_theme_font_override("bold_font", Tokens.FONT_BOLD)
	body.add_theme_font_size_override("normal_font_size", Tokens.FONT_BODY)
	body.add_theme_font_size_override("bold_font_size", Tokens.FONT_BODY)
	body.add_theme_color_override("default_color", Tokens.INK)
	body.text = bbcode(text)
	row.add_child(body)
	return row


## "[스윙]을 눌러요" → 대괄호 부분을 굵은 강조색으로 (대괄호는 남긴다 — 화면의 버튼 이름과 같아 보이게)
static func bbcode(text: String) -> String:
	var plain := text.replace("[", "[lb]")
	var re := RegEx.create_from_string("\\[lb\\]([^\\[\\]]*)\\]")
	return re.sub(plain, "[b][color=#%s][lb]$1][/color][/b]" % Tokens.ACCENT.to_html(false), true)


func _close(skip_all: bool) -> void:
	if _done:
		return
	_done = true
	finished.emit(skip_all)


## 스크린샷·테스트 스크립트용: [알겠어요] 를 누른 것처럼
func confirm() -> void:
	_close(false)
