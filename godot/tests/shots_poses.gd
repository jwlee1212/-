extends Node2D
## 중계 화면 상황 동작 확인용 (2026-11-07): 무사 1루 유격수 땅볼 병살을 순간별로 찍는다 → godot/build/shots/pose-*.png
## 땅볼 잡기 · 2루 송구 · 2루 슬라이딩 · 2루수 피벗 송구 · 1루 판정 · 뜬공 잡기
##   ~/Applications/Godot.app/Contents/MacOS/Godot --always-on-top --path godot res://tests/shots_poses.tscn

var _p: BattedBallSim.Config
var _ball: BattedBallSim.Result
var _play: PlaySimulator.PlayResult
var _t := 0.0


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(844, 390)
	texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	_p = BattingConfig.from_balance(BalanceLoader.load_balance()).ball_physics
	for ev in [120, 135, 150]:
		for spray in range(-24, -3, 2):
			var ball := BattedBallSim.simulate(_p, ev, -8, spray, 50, false, true)
			if ball.fielder != "SS" or ball.caught or _play != null:
				continue
			var rng := RandomNumberGenerator.new()
			rng.seed = 3
			var play := PlaySimulator.new(_p, ball, 4, [1, -1, -1], func(_x: int) -> int: return 50, 0, rng).run()
			if play.double_play:
				_play = play
				_ball = ball
	var times := [
		["pose-1-grounder", _ball.fielder_arrive],
		["pose-1b-held", (_ball.duration() + float(_play.throws[0].release)) / 2.0],
		["pose-2-throw-2b", float(_play.throws[0].release)],
		["pose-3-slide", float(_play.calls[0].t)],
		["pose-4-pivot", float(_play.throws[1].release)],
		["pose-5-first", float(_play.calls[1].t)],
	]
	for item: Array in times:
		_t = item[1]
		queue_redraw()
		await RenderingServer.frame_post_draw
		await RenderingServer.frame_post_draw
		get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % item[0])
	# 뜬공 잡기
	_play = null
	_ball = BattedBallSim.simulate(_p, 140, 35, 0, 50)
	_t = _ball.fielder_arrive
	queue_redraw()
	await RenderingServer.frame_post_draw
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/pose-6-fly.png")
	# 다이빙 캐치: 범위 안에서 다이빙으로 고른 뜬공 하나
	for ev in range(100, 171, 6):
		for la in [8, 14, 20, 26]:
			for spray in range(-40, 41, 6):
				var b := BattedBallSim.simulate(_p, ev, la, spray, 50)
				if b.caught and not b.home_run and BroadcastView._dives(b) and _ball.fielder_arrive != b.fielder_arrive:
					_ball = b
	for item: Array in [["pose-7-dive-a", _ball.fielder_arrive - 0.15], ["pose-7-dive-b", _ball.fielder_arrive + 0.2]]:
		_t = item[1]
		queue_redraw()
		await RenderingServer.frame_post_draw
		await RenderingServer.frame_post_draw
		get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % item[0])
	get_tree().quit()


func _draw() -> void:
	if _ball != null:
		BroadcastView.draw(self, Rect2(Vector2.ZERO, Vector2(844, 390)), _p, _ball, _t, false, false, _play)
