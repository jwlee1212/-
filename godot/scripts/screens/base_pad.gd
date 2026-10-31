class_name BasePad
extends Control
## 베이스 버튼: 내 주자가 뛰는 동안 내내 화면 오른쪽 아래에 다이아몬드 모양으로 1루·2루·3루·홈 버튼이 뜬다.
## 누른 베이스까지 내 주자가 뛴다 (그 베이스에서 멈춘다). 달리는 중에 바꿔 누르면 바로 그쪽으로 — 지나온 베이스를 누르면 돌아선다. 판단은 RunControl(로직)이 하고, 여기는 버튼만 보여 준다.
## 타석 화면(내가 친 타구)과 경기 화면의 주루 장면(내가 루상에 있을 때) 양쪽에서 쓴다.
## 앞 주자가 막은 베이스 너머는 꺼진다 (추월 금지). 런다운에 갇히면 갇힌 두 베이스만 켜진다.
## 키보드: 1·2·3 = 1·2·3루, 4·H = 홈 (맥에서 해 볼 때)

## 버튼 크기, 다이아몬드 한 변의 절반 (가운데에서 버튼 중심까지), 버튼 모서리 (px)
const BUTTON := 58.0
const SPREAD := 50.0
const BUTTON_RADIUS := 22
## 다이아몬드 위 자리 (가운데 기준 방향): 2루 위, 3루 왼쪽, 1루 오른쪽, 홈 아래
const SPOTS := {1: Vector2(1, 0), 2: Vector2(0, -1), 3: Vector2(-1, 0), 4: Vector2(0, 1)}

## 지금의 RunControl (없으면 null) 을 돌려주는 함수
var control_of: Callable
## 베이스를 눌렀을 때 (소리용)
signal picked(base: int)

var _pad: Control
var _chip: PanelContainer
var _title: Label
var _buttons := {}
var _shown := ""


func _init(p_control_of: Callable) -> void:
	control_of = p_control_of


func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	# 다이아몬드 바로 위: 안내 (타석 HUD 의 위쪽 칩들과 겹치지 않는 자리)
	_chip = UiKit.glass_panel()
	add_child(_chip)
	_title = UiKit.label("", Tokens.FONT_CAPTION, Tokens.HUD_TEXT, Tokens.FONT_BOLD)
	_chip.add_child(_title)
	_chip.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_RIGHT, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_SM)
	_chip.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	_chip.grow_vertical = Control.GROW_DIRECTION_BEGIN
	_chip.position.y -= SPREAD * 2 + BUTTON + Tokens.SPACE_XS
	# 오른쪽 아래: 다이아몬드 (타석의 스윙 버튼·도루의 [뛴다!] 와 같은 엄지 자리)
	_pad = Control.new()
	_pad.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_pad.custom_minimum_size = Vector2.ONE * (SPREAD * 2 + BUTTON)
	add_child(_pad)
	_pad.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_RIGHT, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_SM)
	_pad.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	_pad.grow_vertical = Control.GROW_DIRECTION_BEGIN
	for base: int in SPOTS:
		var b := Button.new()
		b.text = "홈" if base == 4 else "%d루" % base
		b.focus_mode = Control.FOCUS_NONE
		b.custom_minimum_size = Vector2.ONE * BUTTON
		b.size = Vector2.ONE * BUTTON
		b.position = Vector2.ONE * SPREAD + (SPOTS[base] as Vector2) * SPREAD
		b.add_theme_font_override("font", Tokens.FONT_BOLD)
		b.add_theme_font_size_override("font_size", Tokens.FONT_LABEL)
		# 손가락이 닿는 순간 고른다 (떼는 순간이 아니라)
		b.action_mode = BaseButton.ACTION_MODE_BUTTON_PRESS
		b.pressed.connect(_pick.bind(base))
		_pad.add_child(b)
		_buttons[base] = b
	_set_shown(false)


func _set_shown(on: bool) -> void:
	_chip.visible = on
	_pad.visible = on


func _pick(base: int) -> void:
	var c: RunControl = control_of.call()
	if c != null:
		c.set_target(base)
		picked.emit(base)


func _process(_delta: float) -> void:
	var c: RunControl = control_of.call()
	# 내 주자가 뛰는 동안 내내 떠 있다 (지금 고를 게 없으면 버튼만 꺼진다)
	var on := c != null and c.active()
	_set_shown(on)
	if not on:
		_shown = ""
		return
	var d := c.pending()
	if d.is_empty():
		var idle := "idle:%s" % c.target
		if _shown != idle:
			_shown = idle
			_title.text = "★ 내 주루"
			for base: int in _buttons:
				_style(_buttons[base], false, base == c.target, false)
		return
	var key := "%s:%s:%s:%s:%s:%s" % [d.kind, c.min_base(), c.max_base(), c.target, c.target_counts(), c.coach_base()]
	if key == _shown:
		return
	_shown = key
	var coach := c.coach_base()
	var rundown := str(d.kind) == "rundown"
	var sign := ""
	if coach >= 0:
		if rundown:
			sign = " · 코치: %s로" % base_name(coach)
		else:
			sign = " · 코치: %s" % (base_name(coach) + ("까지" if coach > c.min_base() else "에서 멈춰"))
	var lead := ""
	if not rundown and c.max_base() < 4:
		lead = " · 앞 주자 때문에 %s까지" % base_name(c.max_base())
	if rundown:
		_title.text = "★ 런다운! 도망칠 베이스를 누르세요" + sign
	elif d.kind == "tag":
		_title.text = "★ 태그업 — 베이스를 누르세요" + sign
	else:
		_title.text = "★ 달릴 베이스를 누르세요" + sign + lead
	# 지금대로면 어디까지 가나: 누른 베이스, 안 눌렀으면 보조는 코치 사인·수동은 가장 가까운 베이스
	var going := c.going()
	for base: int in _buttons:
		_style(_buttons[base], base >= c.min_base() and base <= c.max_base(), base == going, not c.target_counts())


## 키보드로도 누른다 (맥·웹에서 해 볼 때)
func _unhandled_key_input(event: InputEvent) -> void:
	if not _pad.visible or not (event is InputEventKey) or not event.pressed or event.echo:
		return
	var keys := {KEY_1: 1, KEY_2: 2, KEY_3: 3, KEY_4: 4, KEY_H: 4}
	var k: Key = (event as InputEventKey).keycode
	if keys.has(k):
		_pick(keys[k])
		get_viewport().set_input_as_handled()


## 버튼 모양: 못 누르는 베이스는 흐리게, 지금 목표는 채워서 (아직 안 눌렀으면 = 코치 사인이면 테두리만 강조)
func _style(b: Button, enabled: bool, is_target: bool, tentative: bool) -> void:
	b.disabled = not enabled
	var s := StyleBoxFlat.new()
	s.set_corner_radius_all(BUTTON_RADIUS)
	s.anti_aliasing = true
	s.bg_color = Tokens.HUD_GLASS
	s.border_color = Tokens.HUD_BORDER
	s.set_border_width_all(1)
	if is_target and not tentative:
		s.bg_color = Tokens.GOOD
		s.border_color = Tokens.HUD_TEXT
		s.set_border_width_all(2)
	elif is_target:
		s.border_color = Tokens.WARN
		s.set_border_width_all(3)
	for state in ["normal", "hover", "pressed", "focus", "disabled"]:
		b.add_theme_stylebox_override(state, s)
	for state in ["font_color", "font_hover_color", "font_pressed_color", "font_focus_color", "font_disabled_color"]:
		b.add_theme_color_override(state, Tokens.HUD_TEXT)
	b.modulate.a = 1.0 if enabled else 0.3


static func base_name(b: int) -> String:
	return "홈" if b >= 4 or b <= 0 else "%d루" % b
