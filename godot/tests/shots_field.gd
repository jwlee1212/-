extends Node
## 내 수비 확인용: 유격수 시점(타구 직전 → 굴러옴 → 원이 점 안 → 1루로 돌기 → 송구 게이지)과
## 1루 클로즈업(공이 날아옴 → 접전 → 아웃 · 세이프 · 악송구), 포구 실책·못 던짐을 찍는다 → godot/build/shots/field-*.png
## 병살 기회(무사 1루): 2루로 돌기 → 토스 게이지 → 토스 → 2루 아웃 → 피벗 → 1루 클로즈업, 올세이프 → field-dp-*.png
##   ~/Applications/Godot.app/Contents/MacOS/Godot --always-on-top --path godot res://tests/shots_field.tscn


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(844, 390)
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	var balance := BalanceLoader.load_balance()
	var phys := BattingConfig.from_balance(balance).ball_physics
	var fc: Dictionary = balance["game"]["fielding"]
	var tr := func(p: FieldPlay) -> float: return float(p.arrive_us - p.hit_us)
	# [이름, 장면 만들기(FieldView, FieldPlay) → 그 장면이 되도록 시계를 옮긴다]
	var shots := [
		["01-intro", func(_v: FieldView, p: FieldPlay) -> void: _shift(p, -200000)],
		["02-rolling", func(_v: FieldView, p: FieldPlay) -> void: _shift(p, p.hit_us - p.start_us + int(tr.call(p) * 0.45))],
		["03-inside", func(_v: FieldView, p: FieldPlay) -> void: _shift(p, p.hit_us - p.start_us + int(tr.call(p) - p.catch_window_sec() * 0.5e6))],
		["04-turn", func(v: FieldView, p: FieldPlay) -> void:
			_catch(v, p)
			_shift(p, int(float(p.aim_us - p.caught_us) * 0.4))],
		["05-gauge", func(v: FieldView, p: FieldPlay) -> void:
			_catch(v, p)
			_charge(v, p, 0.87)],
		# 클로즈업: [게이지 값, 보여 줄 플레이 시각 — 공 도착 기준 초 (특별한 값은 _closeup 참고)]
		["06-cu-flight", func(v: FieldView, p: FieldPlay) -> void: _closeup(v, p, 0.75, -0.5)],
		["07-cu-reach", func(v: FieldView, p: FieldPlay) -> void: _closeup(v, p, 0.75, -0.1)],
		["08-cu-out", func(v: FieldView, p: FieldPlay) -> void: _closeup(v, p, 0.75, 99.0)],
		["09-cu-bonus", func(v: FieldView, p: FieldPlay) -> void: _closeup(v, p, 0.88, 99.0)],
		["10-cu-bangbang", func(v: FieldView, p: FieldPlay) -> void: _closeup(v, p, 0.75, -0.03)],
		["11a-cu-touch", func(v: FieldView, p: FieldPlay) -> void: _closeup(v, p, 0.4, -99.0)],
		["11b-cu-pass", func(v: FieldView, p: FieldPlay) -> void: _closeup(v, p, 0.4, -98.0)],
		["11-cu-safe", func(v: FieldView, p: FieldPlay) -> void: _closeup(v, p, 0.4, 99.0)],
		["12-cu-wild", func(v: FieldView, p: FieldPlay) -> void: _closeup(v, p, 0.97, 0.12)],
		["13-miss", func(v: FieldView, p: FieldPlay) -> void:
			_shift(p, p.hit_us - p.start_us + int(tr.call(p) * 0.5))
			v._tap()
			_shift(p, int(tr.call(p) * 0.6))],
		["14-late", func(v: FieldView, p: FieldPlay) -> void:
			_catch(v, p)
			_shift(p, int((p.runner_s - p.since_hit(Time.get_ticks_usec())) * 1e6) + 50000)],
	]
	for shot: Array in shots:
		var v := FieldView.new(fc, phys, 11, "최강타", 50, 50, 0.0)
		add_child(UiKit.fill_parent(v))
		await get_tree().process_frame
		await get_tree().process_frame
		(shot[1] as Callable).call(v, v.play())
		await _shot("field-%s" % shot[0])
		v.queue_free()
		await get_tree().process_frame
	# 병살: [이름, 원하는 결과, 게이지, 장면]
	var dp_shots := [
		["dp-04-turn", FieldPlay.Kind.DOUBLE_PLAY, 0.875, "turn"],
		["dp-05-gauge", FieldPlay.Kind.DOUBLE_PLAY, 0.875, "gauge"],
		["dp-06-toss", FieldPlay.Kind.DOUBLE_PLAY, 0.875, "toss"],
		["dp-07-out2", FieldPlay.Kind.DOUBLE_PLAY, 0.875, "call2"],
		["dp-08-pivot", FieldPlay.Kind.DOUBLE_PLAY, 0.875, "pivot"],
		["dp-09-cu-out", FieldPlay.Kind.DOUBLE_PLAY, 0.875, "cu"],
		["dp-10-force", FieldPlay.Kind.FORCE_OUT, 0.75, "cu"],
		["dp-11-allsafe", FieldPlay.Kind.ALL_SAFE, 0.4, "call2"],
	]
	for shot: Array in dp_shots:
		var v := await _dp_view(fc, phys, shot[1], shot[2])
		var p := v.play()
		match shot[3]:
			"turn":
				_catch(v, p)
				_shift(p, int(float(p.aim_us - p.caught_us) * 0.5))
			"gauge":
				_catch(v, p)
				_charge(v, p, shot[2])
			_:
				_catch(v, p)
				_charge(v, p, shot[2])
				v._release()
				var t0 := p.since_hit(p.thrown_us)
				match shot[3]:
					"toss": v._sec_s = t0 + (p.arrive2_s - t0) * 0.6
					"call2": v._sec_s = maxf(p.arrive2_s, p.lead_s) + 0.25
					"pivot": v._sec_s = p.release1_s + 0.07
					"cu":
						v._sec = false
						v._cu = true
						v._cu_s = maxf(p.arrive_s, p.runner_s) + 0.5
				v._close_us = 0
				print("%s g=%.2f 토스 %.2f/주자 %.2f · 1루 %.2f/타자 %.2f" % [FieldPlay.kind_label(p.kind), shot[2], p.arrive2_s, p.lead_s, p.arrive_s, p.runner_s])
		await _shot("field-%s" % shot[0])
		v.queue_free()
		await get_tree().process_frame
	get_tree().quit()


## 병살 기회 수비 화면: 게이지 g 로 토스하면 결과가 want 가 되는 시드를 찾는다 (로직만 미리 굴려 본다)
func _dp_view(fc: Dictionary, phys: BattedBallSim.Config, want: FieldPlay.Kind, g: float) -> FieldView:
	for seed_value in range(1, 200):
		var v := FieldView.new(fc, phys, seed_value, "최강타", 50, 50, 0.0, true, 50)
		add_child(UiKit.fill_parent(v))
		await get_tree().process_frame
		await get_tree().process_frame
		var p := v.play()
		# 같은 시드로 로직만 따로 굴려 결과를 본다
		var rng := RandomNumberGenerator.new()
		rng.seed = seed_value
		var q := FieldPlay.new(fc, rng, p.aspect, 0, p.runner_s, 50)
		q.throw_m = p.throw_m
		q.setup_double_play(p.lead_s, 50, p.feed_m, p.pivot_m)
		q.update(q.hit_us)
		q.press(q.arrive_us)
		q.press(q.aim_us)
		q.release(q.aim_us + int(g * q.rise_sec() * 1e6))
		if q.kind == want:
			return v
		v.queue_free()
	return null


## 로직의 시계를 dt_us 만큼 앞으로 감는다 (모든 시각을 그만큼 과거로)
func _shift(p: FieldPlay, dt_us: int) -> void:
	p.start_us -= dt_us
	p.hit_us -= dt_us
	p.arrive_us -= dt_us
	if p.caught_us > 0:
		p.caught_us -= dt_us
		p.aim_us -= dt_us
	if p.thrown_us > 0:
		p.thrown_us -= dt_us
	if p.charge_us > 0:
		p.charge_us -= dt_us
	p.update(Time.get_ticks_usec())


func _catch(v: FieldView, p: FieldPlay) -> void:
	_shift(p, p.arrive_us - p.start_us)
	v._tap()


## 게이지가 뜨는 순간 눌러 g 까지 모은 장면으로 (아직 안 뗐다)
func _charge(v: FieldView, p: FieldPlay, g: float) -> void:
	_shift(p, p.aim_us - Time.get_ticks_usec())
	v._tap()
	_shift(p, int(g * p.rise_sec() * 1e6))


## 게이지 g 로 던지고, 클로즈업을 공 도착 + at 초 (99 = 판정 뒤) 장면으로
func _closeup(v: FieldView, p: FieldPlay, g: float, at: float) -> void:
	_catch(v, p)
	_charge(v, p, g)
	v._release()
	# 99 = 판정 뒤, -99 = 주자가 1루를 밟는 순간(발끝 터치), -98 = 그 0.1초 뒤(딛고 넘어감)
	if at > 90.0:
		v._cu_s = maxf(p.arrive_s, p.runner_s) + 0.5
	elif at < -90.0:
		v._cu_s = p.runner_s + (0.1 if at > -99.0 else 0.0)
	else:
		v._cu_s = p.arrive_s + at
	v._close_us = 0
	print("%s g=%.2f(%s, 아웃 확률 %.2f) 공 %.2f 주자 %.2f 여유 %.2f" % [FieldPlay.kind_label(p.kind), g, p.zone, p.out_chance, p.arrive_s, p.runner_s, p.margin()])


func _shot(name: String) -> void:
	await get_tree().process_frame
	await get_tree().process_frame
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % name)
