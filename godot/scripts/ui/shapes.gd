class_name Shapes
extends RefCounted
## 메뉴 화면용 그림 부품 (코드로 그린다, 색은 Tokens 에서)


## 오른쪽 끝이 비스듬히 잘린 강조 막대 (선택된 메뉴 뒤 바탕)
class SlantBar:
	extends Control
	var color := Tokens.ACCENT

	func _draw() -> void:
		var cut := size.y * Tokens.SLANT * 2.0
		draw_colored_polygon(PackedVector2Array([Vector2.ZERO, Vector2(size.x, 0), Vector2(size.x - cut, size.y), Vector2(0, size.y)]), color)


## 오른쪽으로 기울인 굵은 글자 (로고). 글꼴 변형 대신 그릴 때 기울인다
class SlantText:
	extends Control
	var text := ""
	var font_size := Tokens.FONT_TITLE
	var color := Tokens.INK

	func _init(p_text: String, p_size: int, p_color: Color = Tokens.INK) -> void:
		text = p_text
		font_size = p_size
		color = p_color
		mouse_filter = Control.MOUSE_FILTER_IGNORE
		var w := Tokens.FONT_BOLD.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size).x
		custom_minimum_size = Vector2(w + font_size * Tokens.SLANT, font_size * 1.15)

	func _draw() -> void:
		var h := size.y
		# 위쪽일수록 오른쪽으로 민다 (y 가 아래로 커지므로 x' = x − SLANT·y + SLANT·h)
		draw_set_transform_matrix(Transform2D(Vector2(1, 0), Vector2(-Tokens.SLANT, 1), Vector2(Tokens.SLANT * h, 0)))
		var ascent := Tokens.FONT_BOLD.get_ascent(font_size)
		draw_string(Tokens.FONT_BOLD, Vector2(0, (h - Tokens.FONT_BOLD.get_height(font_size)) / 2.0 + ascent), text, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, color)
		draw_set_transform_matrix(Transform2D.IDENTITY)


## 알림 배지: 빨간 원 안에 흰 숫자 (0 이면 안 보인다)
class Badge:
	extends Control
	var count := 0:
		set(v):
			count = v
			visible = v > 0
			queue_redraw()

	func _init(p_count: int = 0) -> void:
		mouse_filter = Control.MOUSE_FILTER_IGNORE
		custom_minimum_size = Vector2(Tokens.BADGE_SIZE, Tokens.BADGE_SIZE)
		size = custom_minimum_size
		count = p_count

	func _draw() -> void:
		var r := size.y / 2.0
		draw_circle(size / 2.0, r, Tokens.BADGE, true, -1.0, true)
		var t := str(count) if count < 100 else "99+"
		var fs := Tokens.FONT_CAPTION - (0 if t.length() < 2 else 2)
		var font := Tokens.FONT_BOLD
		var tw := font.get_string_size(t, HORIZONTAL_ALIGNMENT_LEFT, -1, fs).x
		draw_string(font, Vector2((size.x - tw) / 2.0, (size.y - font.get_height(fs)) / 2.0 + font.get_ascent(fs)), t, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, Tokens.SURFACE)


## 학교 방패 (위는 평평, 아래는 뾰족) + 약칭
class Shield:
	extends Control
	var color := Tokens.UNIFORM_US
	var text := ""

	func _init(p_color: Color, p_text: String) -> void:
		color = p_color
		text = p_text
		custom_minimum_size = Vector2(64, 72)

	func _draw() -> void:
		var w := size.x
		var h := size.y
		var tip := h * 0.22
		draw_colored_polygon(PackedVector2Array([Vector2(0, 0), Vector2(w, 0), Vector2(w, h - tip), Vector2(w / 2.0, h), Vector2(0, h - tip)]), color)
		var font := Tokens.FONT_BOLD
		var fs := Tokens.FONT_TITLE if text.length() <= 2 else Tokens.FONT_BODY
		var tw := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, fs).x
		draw_string(font, Vector2((w - tw) / 2.0, (h - tip) / 2.0 + fs * 0.4), text, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, Tokens.SURFACE)
