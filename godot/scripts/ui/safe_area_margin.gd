class_name SafeAreaMargin
extends MarginContainer
## 노치·펀치홀·홈 바를 피하는 바깥 여백. 폰에서는 DisplayServer.get_display_safe_area() 로 가려지는 만큼 더 띄우고,
## 어느 쪽이든 최소 base 만큼은 띄운다. 가로 화면이라 노치는 보통 왼쪽이나 오른쪽에 온다.
## 맥·웹 데스크톱 창은 화면의 안전 영역(메뉴 막대·독 제외)이 창과 상관없어서 최소 여백만 쓴다.

var base := Tokens.SPACE_SM


func _ready() -> void:
	get_viewport().size_changed.connect(_update)
	_update()


func _update() -> void:
	var inset := [0.0, 0.0, 0.0, 0.0]  # 왼쪽 · 위 · 오른쪽 · 아래 (화면 좌표)
	if OS.has_feature("mobile"):
		var win := Vector2(DisplayServer.window_get_size())
		var safe := Rect2(DisplayServer.get_display_safe_area())
		if win.x > 0.0 and win.y > 0.0:
			var k := get_viewport_rect().size / win  # 기기 픽셀 → 화면 좌표
			inset = [safe.position.x * k.x, safe.position.y * k.y, (win.x - safe.end.x) * k.x, (win.y - safe.end.y) * k.y]
	for i in 4:
		add_theme_constant_override(["margin_left", "margin_top", "margin_right", "margin_bottom"][i], base + maxi(0, roundi(inset[i])))
