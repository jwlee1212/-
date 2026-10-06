extends Control
## 1루 판정 장면 확인용: 아웃 한 개·세이프 한 개를 골라 판정 순간 앞뒤를 찍는다 → godot/build/shots/fb-*.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --path godot res://tests/shots_first_base.tscn

var _cfg: BattingConfig
var _r: BattedBallSim.Result
var _t := 0.0


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(844, 390)
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	_cfg = BattingConfig.from_balance(BalanceLoader.load_balance())
	var out_play: BattedBallSim.Result = null
	var safe_play: BattedBallSim.Result = null
	# 간발의 차(0.3초 이내) 아웃·세이프를 하나씩 찾는다
	for ev in range(40, 150, 5):
		for spray in range(-40, 41, 4):
			var r := BattedBallSim.simulate(_cfg.ball_physics, ev, -8, spray, 70)
			if not r.is_first_base_play() or absf(r.play_margin) > 0.3:
				continue
			if r.outcome == SwingJudge.Outcome.GROUND_OUT and out_play == null:
				out_play = r
			elif r.outcome == SwingJudge.Outcome.SINGLE and safe_play == null:
				safe_play = r
	for pair in [["out", out_play], ["safe", safe_play]]:
		_r = pair[1]
		if _r == null:
			print("%s 장면 없음" % pair[0])
			continue
		print("%s: 수비 %s, 받는 사람 %s, 공 %.2f초, 타자 %.2f초" % [pair[0], _r.fielder, _r.receiver, _r.throw_arrive, _r.runner_first])
		for k in [["a", _r.duration() + 0.1], ["b", minf(_r.throw_arrive, _r.runner_first) - 0.15], ["c", maxf(_r.throw_arrive, _r.runner_first) + 0.05]]:
			_t = k[1]
			queue_redraw()
			await RenderingServer.frame_post_draw
			await RenderingServer.frame_post_draw
			get_viewport().get_texture().get_image().save_png("res://build/shots/fb-%s-%s.png" % [pair[0], k[0]])
	get_tree().quit()


func _draw() -> void:
	draw_rect(Rect2(Vector2.ZERO, size), Tokens.BACKGROUND)
	if _r != null:
		BroadcastView.draw(self, Rect2(Vector2.ZERO, size), _cfg.ball_physics, _r, _t, false)
