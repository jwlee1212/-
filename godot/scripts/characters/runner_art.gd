class_name RunnerArt
extends RefCounted
## 주자 전용 그림. 위치·동작·슬라이딩 진행도는 호출 화면이 공개 상태에서 전달한다.


static func frame_for(velocity: Vector2, phase: int, slide_progress: float = -1.0) -> int:
	if slide_progress >= 0.0:
		return 20 + clampi(int(slide_progress * 4.0), 0, 3)
	var row := 0
	if velocity.y < 0.0:
		if absf(velocity.x) > absf(velocity.y) * 0.5:
			row = 1 if velocity.x >= 0.0 else 2
	else:
		row = 3 if velocity.x >= 0.0 else 4
	return row * 4 + posmod(phase, 4)


static func draw_frame(canvas: CanvasItem, feet: Vector2, height: float, frame: int, us: bool = true, flip: bool = false) -> void:
	# 새 그림이 있는 대각선 두 방향과 헤드퍼스트 슬라이딩을 먼저 쓴다.
	if frame >= 4 and frame < 12:
		var art_frame := frame if frame < 8 else frame + 4
		PixelArt.draw_illustrated(canvas, "runner", art_frame, feet.round(), height, us, flip)
		return
	if frame >= 20 and frame < 24:
		PixelArt.draw_illustrated(canvas, "runner", frame - 20, feet.round(), height, us, flip)
		return
	# 원본의 서 있는 키 약 184px를 기준으로 기존 중계 선수 크기를 따른다.
	var variant := 8 if height <= 23.0 else (4 if height <= 46.0 else 1)
	var tex := PixelArt.texture("runners_ext" + ("" if us else "_them") + ("" if variant == 1 else "_s%d" % variant))
	if tex == null:
		return
	var cell := RunnersExtMeta.CELL / variant
	var anchor := RunnersExtMeta.FEET / variant
	var scale := height / (184.0 / variant)
	var rect := Rect2(feet.round() - anchor * scale, cell * scale)
	if flip:
		rect.position.x = feet.round().x + anchor.x * scale
		rect.size.x = -rect.size.x
	canvas.draw_texture_rect_region(tex, rect, Rect2(Vector2(clampi(frame, 0, 23) * cell.x, 0), cell))


static func draw_runner(canvas: CanvasItem, cam: BroadcastView.Cam, at: Vector2, us: bool, velocity: Vector2 = Vector2.ZERO, time: float = 0.0, pose: String = "", face: float = 0.0, slide_progress: float = -1.0) -> void:
	# 대기·리드 그림은 기존 헬멧 주자 칸을 사용한다.
	if slide_progress < 0.0 and pose != "slide" and (pose in ["lead", "onbase"] or velocity.length() < 0.05):
		PixelArt.draw_fielder(canvas, cam, at, us, Vector2.ZERO, time, pose if pose != "" else "onbase", face)
		return
	var feet := cam.ground(at)
	var head := cam.to_screen(Vector3(at.x, at.y, 1.8 * PixelArt.FIELDER_SD))
	var height := maxf(feet.y - head.y, 4.0)
	var pixels_per_meter := maxf(cam.scale_at(Vector3(at.x, at.y, 0.0)), 0.01)
	var speed := velocity.length() / PixelArt.FIELDER_VEL_DT / pixels_per_meter
	var phase := int(floor(time * speed / PixelArt.FIELDER_STRIDE_M))
	var progress := slide_progress
	if pose == "slide" and progress < 0.0:
		progress = 1.0
	draw_frame(canvas, feet, height, frame_for(velocity, phase, progress), us, progress >= 0.0 and face < 0.0)
