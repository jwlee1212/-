extends Node
## 내 수비 확인용: 유격수 시점(타구 직전 → 굴러옴 → 원이 점 안 → 1루로 돌기 → 송구 게이지)과
## 1루 클로즈업(공이 날아옴 → 접전 → 아웃 · 세이프 · 악송구), 포구 실책·못 던짐을 찍는다 → godot/build/shots/field-*.png
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
	get_tree().quit()


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
	_shift(p, int(g * float(p.cfg["gaugeRiseSec"]) * 1e6))


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
