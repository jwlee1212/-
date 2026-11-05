class_name BottomNav
extends PanelContainer
## 홈 아래 바: 왼쪽 탭(균등 분할) + 오른쪽 진행 버튼 자리(CTA, 화면에 하나뿐인 강조색 버튼).
## 엄지가 닿는 아래쪽에 자주 누르는 것을 모은다. 탭을 누르면 tab_pressed(이름)

signal tab_pressed(name: String)

var _buttons := {}
var _cta_slot: HBoxContainer


func _init(tabs: Array) -> void:
	theme_type_variation = "Bar"
	custom_minimum_size.y = Tokens.BOTTOM_NAV_HEIGHT
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", Tokens.SPACE_MD)
	add_child(row)
	var tab_row := HBoxContainer.new()
	tab_row.add_theme_constant_override("separation", Tokens.SPACE_SM)
	tab_row.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	for t: String in tabs:
		var b := NavButton.new(t)
		b.pressed.connect(func() -> void: tab_pressed.emit(t))
		tab_row.add_child(b)
		_buttons[t] = b
	row.add_child(tab_row)
	_cta_slot = HBoxContainer.new()
	_cta_slot.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	row.add_child(_cta_slot)


func select(name: String) -> void:
	for t: String in _buttons:
		(_buttons[t] as NavButton).on = t == name


func set_badge(name: String, count: int) -> void:
	(_buttons[name] as NavButton).set_badge(count)


## 진행 버튼을 갈아 끼운다 (상태마다 글자·동작이 달라서 매번 새로 만든다)
func set_cta(button: Button) -> void:
	for c in _cta_slot.get_children():
		c.queue_free()
	button.custom_minimum_size = Vector2(maxf(button.custom_minimum_size.x, Tokens.TOUCH_MIN * 5), Tokens.TOUCH_MIN)
	_cta_slot.add_child(button)


func cta() -> Button:
	for c in _cta_slot.get_children():
		if not c.is_queued_for_deletion():
			return c as Button
	return null
