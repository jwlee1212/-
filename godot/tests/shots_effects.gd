extends Control
## 확인용 스크린샷: 타격 이펙트를 급별(파울·빗맞음·정타·홈런) × 시간별로 한 장에 늘어놓는다 → godot/build/shots/effects.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --path godot res://tests/shots_effects.tscn

const TIMES := [0.08, 0.3, 0.55, 0.8]
var _view: BattingView


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(1688, 780)
	var app: Control = load("res://scenes/batting.tscn").instantiate()
	app.save_path = "user://shots.save"
	add_child(app)
	app.visible = false
	await get_tree().process_frame
	_view = BattingView.new()
	_view.session = app.new_batting_session()
	_view.size = Vector2(844, 390)
	queue_redraw()
	await get_tree().create_timer(0.4).timeout
	await RenderingServer.frame_post_draw
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	get_viewport().get_texture().get_image().save_png("res://build/shots/effects.png")
	get_tree().quit()


func _draw() -> void:
	if _view == null:
		return
	draw_rect(Rect2(0, 0, 844, 400), Tokens.GRASS)
	var pres := _view.session.presentation
	var rows := [["파울", SwingJudge.Quality.FOUL, false], ["빗맞음", SwingJudge.Quality.WEAK, false],
		["정타", SwingJudge.Quality.SOLID, false], ["홈런", SwingJudge.Quality.SOLID, true]]
	var y := 30.0
	for row: Array in rows:
		var span: float = pres.burst_home_run_ms if row[2] else pres.burst_ms
		var step := 150.0 if row[2] else 110.0
		draw_string(Tokens.FONT_BOLD, Vector2(8, y + 5), row[0], HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Tokens.CHALK)
		for i in TIMES.size():
			_view._draw_burst(self, Vector2(110 + i * step + (40 if row[2] else 0), y), span * TIMES[i], row[1], row[2])
		y += 150.0 if row[0] == "정타" else (95.0 if row[0] == "빗맞음" else 60.0)
