class_name NavButton
extends Button
## 아래 탭 버튼 하나: 글자 + 할 일이 있을 때만 보이는 알림 배지(강조색). 눌리는 자리는 TOUCH_MIN 이상.
## 색·글자는 테마 변형 NavButton / NavButtonOn (ThemeBuilder)

var badge: Shapes.Badge
## 지금 고른 탭인가
var on := false:
	set(v):
		on = v
		theme_type_variation = "NavButtonOn" if v else "NavButton"


func _init(label: String) -> void:
	text = label
	focus_mode = Control.FOCUS_NONE
	custom_minimum_size = Vector2(Tokens.TOUCH_MIN, Tokens.TOUCH_MIN)
	size_flags_horizontal = Control.SIZE_EXPAND_FILL
	size_flags_vertical = Control.SIZE_SHRINK_CENTER
	theme_type_variation = "NavButton"
	badge = Shapes.Badge.new()
	add_child(badge)
	# 오른쪽 위 모서리 안쪽
	badge.set_anchors_and_offsets_preset(Control.PRESET_TOP_RIGHT, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_XS)


func set_badge(count: int) -> void:
	badge.count = count
