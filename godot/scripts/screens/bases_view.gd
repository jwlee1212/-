class_name BasesView
extends Control
## 주자 다이아몬드 + 아웃 표시

var bases := [false, false, false]
var outs := 0


func _init() -> void:
	custom_minimum_size = Vector2(110, 86)


func _draw() -> void:
	var c := Vector2(size.x / 2.0, 34)
	var r := 13.0
	var gap := 22.0
	var spots := [c + Vector2(gap, 0), c + Vector2(0, -gap), c + Vector2(-gap, 0)]  # 1루, 2루, 3루
	for i in 3:
		var p: Vector2 = spots[i]
		var pts := PackedVector2Array([p + Vector2(0, -r), p + Vector2(r, 0), p + Vector2(0, r), p + Vector2(-r, 0)])
		draw_colored_polygon(pts, Tokens.WARN if bases[i] else Color(Tokens.INK_SOFT, 0.25))
	var home := c + Vector2(0, gap)
	draw_colored_polygon(PackedVector2Array([home + Vector2(-6, -4), home + Vector2(6, -4), home + Vector2(6, 2), home + Vector2(0, 7), home + Vector2(-6, 2)]), Tokens.INK_SOFT)
	for i in 2:
		draw_circle(Vector2(size.x / 2.0 - 10 + i * 20, 76), 6.0, Tokens.BAD if i < outs else Color(Tokens.INK_SOFT, 0.25), true, -1.0, true)
