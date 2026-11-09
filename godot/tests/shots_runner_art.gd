extends Node2D
## 새 주자 24칸과 상대 팀 색을 실제 렌더러에서 확인한다.


func _ready() -> void:
	get_window().size = Vector2i(1000, 700)
	get_window().content_scale_size = Vector2i(1000, 700)
	await get_tree().process_frame
	await get_tree().process_frame
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/runner-art.png")
	get_tree().quit()


func _draw() -> void:
	draw_rect(Rect2(0, 0, 1000, 700), Tokens.SHADOW)
	for row in 6:
		for col in 4:
			RunnerArt.draw_frame(self, Vector2(80 + col * 110, 100 + row * 100), 72, row * 4 + col)
			RunnerArt.draw_frame(self, Vector2(560 + col * 110, 100 + row * 100), 32, row * 4 + col, false)
