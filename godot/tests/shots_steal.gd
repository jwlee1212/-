extends Node
## 내 주루 확인용: 도루 화면(세트 → 견제 → 투구 → 달리는 중 → 판정)과 주루 판단 버튼을 찍는다 → godot/build/shots/steal-*.png, runctl-*.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --path godot res://tests/shots_steal.tscn


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(844, 390)
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	var balance := BalanceLoader.load_balance()
	var bc := BattingConfig.from_balance(balance)
	var phys := bc.ball_physics
	var steal_cfg: Dictionary = balance["game"]["steal"]

	# 견제가 섞인 시각표를 찾는다
	var seed_value := 1
	for s in range(1, 200):
		var rng := RandomNumberGenerator.new()
		rng.seed = s
		if not Steal.plan(steal_cfg, rng).feints.is_empty():
			seed_value = s
			break
	# 1루 → 2루: 세트, 견제, 투구 직후, 달리는 중, 판정
	for shot: Array in [["1-set", 300.0, -1.0], ["2-feint", -2.0, -1.0], ["3-go", -3.0, -1.0], ["4-run", -3.0, 1.6], ["5-call", -3.0, 4.2]]:
		var v := StealView.new(phys, steal_cfg, [4, -1, 6], 4, 1, 60, seed_value, 0.0)
		add_child(UiKit.fill_parent(v))
		await get_tree().process_frame
		var plan: Steal.Plan = v._plan
		var at: float = shot[1]
		if at == -2.0:
			at = plan.feints[0] + plan.feint_ms * 0.5
		elif at == -3.0:
			at = plan.go_ms + 120.0
		if float(shot[2]) > 0.0:
			# 0.2초 만에 스타트한 뒤 shot[2] 초가 지난 순간
			v._start_ms = Time.get_ticks_usec() / 1000.0 - plan.go_ms - 200.0
			v._decide(plan.go_ms + 200.0)
			v._start_ms = Time.get_ticks_usec() / 1000.0 - plan.go_ms - float(shot[2]) * 1000.0
			v._close_ms = INF
		else:
			v._start_ms = Time.get_ticks_usec() / 1000.0 - at
		await _shot("steal-%s" % shot[0])
		v.queue_free()
		await get_tree().process_frame
	# 2루 → 3루 견제사
	var picked := StealView.new(phys, steal_cfg, [-1, 4, -1], 4, 2, 60, seed_value, 0.0)
	add_child(UiKit.fill_parent(picked))
	await get_tree().process_frame
	picked._decide(100.0)
	picked._decided_ms -= 700.0
	picked._close_ms = INF
	await _shot("steal-6-picked")
	picked.queue_free()

	# 주루 판단: 2루 주자(나)가 외야 안타에 3루를 돌 때
	for spray in range(-40, 41, 4):
		var ball := BattedBallSim.simulate(phys, 140, 10, spray, 50)
		var resim := func(choices: Array) -> PlaySimulator.PlayResult:
			var rng := RandomNumberGenerator.new()
			rng.seed = 7
			return PlaySimulator.new(phys, ball, 5, [-1, 4, -1], func(_i: int) -> int: return 50, 0, rng, 4, choices).run()
		var control := RunControl.new(resim.call([]), resim)
		if not control.has_decisions():
			continue
		var d: Dictionary = control.play.decisions[0]
		for shot: Array in [["1-prompt", float(d.t) - 0.5, -1], ["2-picked", float(d.t) - 0.2, 1]]:
			var h := HighlightView.new(phys, control.play, "★ 내 주루 · 타자 박서준", 1.5, false)
			h.control = RunControl.new(resim.call([]), resim)
			h.mark_id = 4
			add_child(UiKit.fill_parent(h))
			await get_tree().process_frame
			if int(shot[2]) == 1:
				h.control.set_target(int(d.from) if d.ai else int(d.to))
			h._start_ms = Time.get_ticks_usec() / 1000.0 - float(shot[1]) / 1.5 * 1000.0
			await _shot("runctl-%s" % shot[0])
			h.queue_free()
			await get_tree().process_frame
		break
	get_tree().quit()


func _shot(name: String) -> void:
	await get_tree().process_frame
	await get_tree().process_frame
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % name)
