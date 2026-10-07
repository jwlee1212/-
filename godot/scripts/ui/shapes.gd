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


## 로비의 타자 실루엣: 조명 기둥 + 그림자 고리 + 단순 도형 타자(헬멧·몸통·다리·배트·등번호)
class BatterSilhouette:
	extends Control
	var number := 7

	func _draw() -> void:
		var w := size.x
		var h := size.y
		var cx := w * 0.55
		var ground := h * 0.88
		# 조명 기둥 (사다리꼴)
		draw_colored_polygon(PackedVector2Array([Vector2(cx - w * 0.16, 0), Vector2(cx + w * 0.22, 0), Vector2(cx + w * 0.42, h), Vector2(cx - w * 0.36, h)]), Color(Tokens.WARN, 0.12))
		# 그림자 고리
		for i in 3:
			var r := Vector2(w * (0.18 + i * 0.1), h * (0.04 + i * 0.022))
			_ellipse(Vector2(cx, ground), r, Color(Tokens.INK_SOFT, 0.25 - i * 0.06))
		var u := h / 10.0
		var ink := Tokens.INK
		# 다리
		draw_rect(Rect2(cx - u * 0.9, ground - u * 3.0, u * 0.7, u * 3.0), ink)
		draw_rect(Rect2(cx + u * 0.2, ground - u * 3.0, u * 0.7, u * 3.0), ink)
		# 몸통 + 등번호 띠
		draw_rect(Rect2(cx - u * 1.2, ground - u * 6.2, u * 2.4, u * 3.4), Tokens.UNIFORM_US)
		draw_rect(Rect2(cx - u * 1.2, ground - u * 4.6, u * 2.4, u * 0.35), Tokens.ACCENT)
		var t := str(number)
		var fs := int(u * 0.9)
		var tw := Tokens.FONT_BOLD.get_string_size(t, HORIZONTAL_ALIGNMENT_LEFT, -1, fs).x
		draw_string(Tokens.FONT_BOLD, Vector2(cx - tw / 2.0, ground - u * 3.3), t, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, Tokens.SURFACE)
		# 헬멧 (머리 큰 SD 비율)
		draw_circle(Vector2(cx, ground - u * 7.2), u * 1.15, Tokens.HELMET, true, -1.0, true)
		draw_rect(Rect2(cx - u * 1.3, ground - u * 7.0, u * 2.6, u * 0.3), Tokens.HELMET)
		# 배트 (어깨 위로 비스듬히)
		draw_line(Vector2(cx + u * 0.9, ground - u * 5.8), Vector2(cx + u * 2.6, ground - u * 9.6), Tokens.BAT, maxf(u * 0.35, 2.0), true)

	func _ellipse(c: Vector2, r: Vector2, color: Color) -> void:
		var pts := PackedVector2Array()
		for i in 33:
			var a := TAU * i / 32.0
			pts.append(c + Vector2(cos(a) * r.x, sin(a) * r.y))
		draw_polyline(pts, color, 1.5, true)
