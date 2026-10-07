extends Node
## 주자 플레이 중계 확인용: 1·2루에서 외야 안타·병살 플레이를 만들어 시간대별로 찍는다 → godot/build/shots/run-*.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --path godot res://tests/shots_runners.tscn


## 정해진 시각의 중계 화면을 그리는 캔버스
class Frame:
	extends Control
	var phys: BattedBallSim.Config
	var play: PlaySimulator.PlayResult
	var t := 0.0

	func _draw() -> void:
		BroadcastView.draw(self, Rect2(Vector2.ZERO, size), phys, play.ball, t, false, false, play)


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(844, 390)
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	var bc := BattingConfig.from_balance(BalanceLoader.load_balance())
	var phys := bc.ball_physics
	var frame := Frame.new()
	frame.phys = phys
	add_child(frame)
	frame.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	# 1) 1·2루, 좌중간 안타  2) 1루, 유격수 땅볼 (병살 기회)  3) 2루, 먼 외야 안타 (중계 플레이)
	var cases := [["single", 140, 12, -15, [1, 2, -1]], ["dp", 135, -8, -12, [1, -1, -1]], ["relay", 155, 14, 20, [-1, 2, -1]]]
	for c: Array in cases:
		var play := _make(phys, c[1], c[2], c[3], c[4])
		if c[0] == "dp":
			# 병살이 나는 타구를 찾는다
			for spray in range(-30, 10, 2):
				var p2 := _make(phys, c[1], c[2], spray, c[4])
				if p2.double_play:
					play = p2
					break
		if c[0] == "relay":
			# 중계맨을 거치는 송구가 나오는 타구를 찾는다
			for spray in range(-40, 41, 4):
				var p3 := _make(phys, c[1], c[2], spray, c[4])
				if p3.throws.any(func(x: Dictionary) -> bool: return x.get("cut", false)):
					play = p3
					break
		frame.play = play
		print("%s: %s %s, 득점 %s, 주자 %s, 송구 %d, 판정 %s, %.2f초" % [c[0], SwingJudge.Outcome.keys()[play.batter_outcome], play.note, play.scorers, play.bases,
			play.throws.size(), play.calls.map(func(x: Dictionary) -> String: return x.text), play.duration])
		for k in [0.0, 0.25, 0.5, 0.65, 0.75, 1.0]:
			frame.t = play.duration * k
			frame.queue_redraw()
			await _shot("run-%s-%d" % [c[0], int(k * 100)])
	get_tree().quit()


func _make(phys: BattedBallSim.Config, ev: float, la: float, spray: float, bases: Array) -> PlaySimulator.PlayResult:
	var ball := BattedBallSim.simulate(phys, ev, la, spray, 50)
	var rng := RandomNumberGenerator.new()
	rng.seed = 7
	return PlaySimulator.new(phys, ball, 4, bases, func(_i: int) -> int: return 50, 0, rng).run()


func _shot(name: String) -> void:
	await get_tree().process_frame
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % name)
