class_name FitnessView
extends Control
## 입부 첫날 체력 테스트 화면 (2026-11-01): 50m 달리기 → 스쿼트 → 반응 속도를 차례로 하고 결과·보상을 보여 준다.
##   종목마다: 설명 카드 [시작] → 도트 무대에서 직접 조작 → 결과 카드 (기록 · 등급 · 감독 한마디 · 오를 능력치) → 다음 종목
##   다 끝나면 종합 카드 (종합 등급 · 감독 한마디 · 실제로 오른 능력치·신뢰) [확인] → finished
## 판정은 RunTest · LiftTest · ReactTest(로직), 보상은 CareerState.finish_minigame. 이 화면은 그리기·입력·소리만 한다 (원칙 1).
## 시간은 Time.get_ticks_usec() 하나로 잰다 (원칙 4). 무대는 도트 배경·칸(art/pixel/minigame*.png)을 정수배로 그린다.
## career 가 null 이면 연습(개발 빌드 로비): 보상 없이 결과만 보여 준다.
##
## 조작 (폰은 터치, 맥은 마우스·키보드):
##   50m: 화면 왼쪽 절반 = 왼발, 오른쪽 절반 = 오른발 (← → 또는 A D)
##   스쿼트: 누르고 있기 = 숨 들이마시며 앉기, 떼기 = 내쉬며 일어서기 (스페이스)
##   반응: 아무 데나 탭 = 스윙 (스페이스)

## 보상까지 반영했다 (홈이 저장한다)
signal completed
signal finished

enum Step { INTRO, PLAY, RESULT, SUMMARY }

## 무대 위 인물 자리 (도트 배경 좌표, 발 자리)
const RUN_START_PX := 20.0
const RUN_ME_FOOT_Y := 86
const RUN_BUDDY_FOOT_Y := 72
const RUN_LINE_FOOT_Y := 89
const RUN_CAMERA_LEAD := 70.0
const RUN_FAR_PARALLAX := 0.35
const LIFT_FOOT := Vector2(100, 80)
const LIFT_COACH_FOOT := Vector2(166, 80)
const REACT_FOOT := Vector2(74, 82)
const REACT_LAMP_FOOT := Vector2(160, 31)
const REACT_COACH_FOOT := Vector2(126, 82)
## 출발 구령 사이 시간 (제자리에 → 차렷), 부정 출발 뒤 다시 준비까지, 반응 신호 사이 — 화면 흐름이라 손맛 수치가 아니다
const CALL_SEC := 0.7
const RETRY_SEC := 0.9
const BETWEEN_SEC := 0.7
## 결승 뒤 멈추는 감속 (1초에 줄어드는 비율), 만세로 바뀌는 속도
const COAST_DRAG := 3.0
const CHEER_SPEED := 1.5

var results := {}

var _cfg: CareerConfig
var _fc: Dictionary
var _career: CareerState
var _sound: SoundPlayer
var _kinds: Array = []
var _k := 0
var _step := Step.INTRO
var _stage: Stage
var _card: Control
var _scrim: ColorRect
var _hud: Control
var _kicker: Label
var _record: Label
var _big: Label
var _hint: Label
var _pads: Array[PanelContainer] = []
var _gauge: Gauge
var _dots: Dots
var _call_tween: Tween
var _shake_until_us := 0
var _shake_px := 0.0
var _hitstop_until_us := 0
var _last_us := 0
## 지금 종목 상태
var _run: RunTest
var _lift: LiftTest
var _react: ReactTest
## 종목 안 진행: 다음 일이 일어날 시각(µs)과 그 이름 ("call2" · "go" · "result" · "retry" · "wait" · "next_rep" …)
var _next_at_us := 0
var _next_what := ""
var _anim := 0.0
var _buddy_anim := 0.0
var _coast_x := 0.0
var _coast_v := 0.0
var _rng_run: RandomNumberGenerator


## career 가 null 이면 연습 (보상 없음). sound 는 없어도 된다 (테스트)
## kinds: 할 종목들 (비우면 세 종목 모두). 종목 하나만 하면(시나리오가 종목 사이에 대사를 넣을 때) 감독 한마디 대신
## 대본의 대사가 이어지고, 세 종목이 다 모인 마지막 종목 뒤에만 종합 카드가 뜬다
func _init(cfg: CareerConfig, career: CareerState, sound: SoundPlayer, kinds: Array = []) -> void:
	_cfg = cfg
	_fc = cfg.fitness
	_career = career
	_sound = sound
	_kinds = kinds if not kinds.is_empty() else _fc["order"]
	mouse_filter = Control.MOUSE_FILTER_STOP
	var frame := ColorRect.new()
	frame.color = Tokens.STAGE_FRAME
	frame.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(UiKit.fill_parent(frame))
	_stage = Stage.new()
	add_child(_stage)
	_hud = UiKit.fill_parent(Control.new())
	_hud.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_hud)
	_build_hud()
	_scrim = ColorRect.new()
	_scrim.color = Tokens.DIALOGUE_BG_SCRIM
	_scrim.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(UiKit.fill_parent(_scrim))
	_rng_run = _rng("fitness-run")
	resized.connect(_layout)


func _ready() -> void:
	_layout()
	_open_intro()


# ---------- 흐름 ----------

func kind() -> String:
	return _kinds[_k]


func step() -> Step:
	return _step


func _open_intro() -> void:
	_step = Step.INTRO
	_setup_stage()
	var how := _t("events.%s.how" % kind())
	var lines: Array[Control] = [
		_label("종목 %d / %d · %s" % [_k + 1, _kinds.size(), _t("events.%s.stat" % kind())], "Kicker"),
		_label(_t("events.%s.title" % kind()), "Title"),
		_wrapped(how, "Body"),
	]
	if _k == 0:
		lines.push_front(_label(_t("title"), "Caption"))
		lines.append(_wrapped(_t("intro"), "Caption"))
	if kind() == "run":
		lines.append(_label("옆 레인 동기 %s · 기록 %.2f초" % [_name("buddy"), float(_fc["run"]["buddySec"])], "Caption"))
	_show_card(lines, "시작", _start_play)


func _start_play() -> void:
	_close_card()
	_step = Step.PLAY
	var now := Time.get_ticks_usec()
	_last_us = now
	_big.text = ""
	match kind():
		"run":
			_call("제자리에")
			_schedule(now, CALL_SEC, "call2")
			_hint.text = "왼쪽 · 오른쪽을 번갈아 탭  (← →)"
		"lift":
			_new_rep(now)
			_hint.text = "누르고 있기 = 숨 들이마시며 앉기 · 떼기 = 내쉬며 일어서기  (스페이스)"
		"react":
			_hint.text = "초록불 = 바로 탭 · 빨간불 = 참기  (스페이스)"
			_schedule(now, BETWEEN_SEC, "wait")
			_call("준비")
	_update_hud(now)


func _finish_kind(value: float, grade: String) -> void:
	results[kind()] = {"grade": grade, "value": value}
	_schedule(Time.get_ticks_usec(), float(_fc["resultSec"]), "result")


func _open_result() -> void:
	_step = Step.RESULT
	_big.text = ""
	var r: Dictionary = results[kind()]
	var g: String = r["grade"]
	var grade := _label(g, "Title")
	grade.add_theme_font_size_override("font_size", Tokens.FONT_DISPLAY)  # 도장 같은 큰 글자 (등급색은 변형 하나로 못 정한다)
	grade.add_theme_color_override("font_color", Tokens.GRADE_COLORS[g])
	grade.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", Tokens.SPACE_LG)
	row.alignment = BoxContainer.ALIGNMENT_CENTER
	row.add_child(_label(FitnessTest.value_text(kind(), float(r["value"])), "Number"))
	row.add_child(grade)
	var lines: Array[Control] = [_label(_t("events.%s.title" % kind()), "Kicker"), row]
	if not _story_mode():
		lines.append(_wrapped("%s  \"%s\"" % [_name("coach"), _t("events.%s.coach.%s" % [kind(), g])], "Body"))
	var gain := int(_fc["statGain"][g])
	lines.append(_label("%s +%d" % [_t("events.%s.stat" % kind()), gain] if gain > 0 else "%s 그대로" % _t("events.%s.stat" % kind()), "BodyBold"))
	if kind() == "run" and not _story_mode():
		var won := float(r["value"]) >= 0.0 and float(r["value"]) < float(_fc["run"]["buddySec"])
		lines.append(_wrapped("%s  \"%s\"" % [_name("buddy"), _t("buddy.%s" % ("win" if won else "lose"))], "Caption"))
	var last := _k == _kinds.size() - 1
	_show_card(lines, "결과 보기 ›" if last else "다음 종목 ›", _next_kind)
	_play(SoundSynth.Sfx.CHEER_SMALL if g in ["S", "A"] else SoundSynth.Sfx.MITT, 0.6)


func _next_kind() -> void:
	_close_card()
	if _k < _kinds.size() - 1:
		_k += 1
		_open_intro()
	elif _story_mode():
		# 종목 하나씩 (시나리오): 결과를 반영하고, 세 종목이 다 모였으면 종합 카드
		_career.finish_minigame(results)
		completed.emit()
		if _career.all_fitness_done():
			results = _career.fitness.duplicate(true)
			_kinds = _fc["order"]
			_open_summary(true)
		else:
			finished.emit()
	else:
		_open_summary()


## 시나리오 모드인가: 커리어가 있고 세 종목 중 일부만 한다
func _story_mode() -> bool:
	return _career != null and _kinds.size() < (_fc["order"] as Array).size()


## applied: 이미 커리어에 반영했다 (종목 하나씩 한 시나리오 모드의 마지막 종목 뒤)
func _open_summary(applied: bool = false) -> void:
	_step = Step.SUMMARY
	var parts: Array[String] = []
	if _career != null and not applied:
		parts = _career.finish_minigame(results)
		completed.emit()
	var row := FitnessTest.overall(_fc, results)
	var g: String = row["grade"]
	var lines: Array[Control] = [_label(_t("title"), "Kicker")]
	for k: String in _kinds:
		var r: Dictionary = results[k]
		var line := HBoxContainer.new()
		line.add_theme_constant_override("separation", Tokens.SPACE_MD)
		var title_l := _label(_t("events.%s.title" % k), "Body")
		title_l.custom_minimum_size.x = 120
		line.add_child(title_l)
		var value := _label(FitnessTest.value_text(k, float(r["value"])), "BodyBold")
		value.custom_minimum_size.x = 96
		line.add_child(value)
		var gl := _label(r["grade"], "BodyBold")
		gl.add_theme_color_override("font_color", Tokens.GRADE_COLORS[r["grade"]])
		line.add_child(gl)
		lines.append(line)
	var big := _label("종합 %s" % g, "Title")
	big.add_theme_color_override("font_color", Tokens.GRADE_COLORS[g])
	lines.append(big)
	lines.append(_wrapped("%s  \"%s\"" % [_name("coach"), _t("overall.%s" % g)], "Body"))
	if _career != null and applied:
		# 초기 능력치 표시 (docs/scenario EP01 "UI stat_screen")
		var stats: Array[String] = []
		for k: String in _kinds:
			var st: String = _fc[k]["stat"]
			stats.append("%s %d" % [PlayerData.STAT_LABELS[st], _career.player.stat(st)])
		lines.append(_wrapped("초기 능력치 — " + " · ".join(stats), "BodyBold"))
	elif _career != null:
		lines.append(_wrapped(", ".join(parts) if not parts.is_empty() else "변화 없음", "BodyBold"))
	else:
		lines.append(_label("연습이라 능력치는 오르지 않는다", "Caption"))
	_show_card(lines, "확인", func() -> void: finished.emit())
	_play(SoundSynth.Sfx.CHEER_BIG if g == "S" else SoundSynth.Sfx.CHEER_SMALL, 0.5)


# ---------- 매 프레임 ----------

func _process(delta: float) -> void:
	var now := Time.get_ticks_usec()
	if _next_what != "" and now >= _next_at_us:
		var what := _next_what
		_next_what = ""
		_on_timer(what, now)
	if _step == Step.PLAY:
		var frozen := now < _hitstop_until_us
		match kind():
			"run":
				_tick_run(now, delta, frozen)
			"lift":
				_tick_lift(now)
			"react":
				_tick_react(now)
		_update_hud(now)
	# 흔들림: 시각으로 정해지는 무늬 (난수 아님), 남은 시간에 비례해 잦아든다
	var off := Vector2.ZERO
	if now < _shake_until_us:
		var left := float(_shake_until_us - now) / (float(_fc["fx"]["shakeMs"]) * 1000.0)
		var t := float(now) / 1e6
		off = Vector2(sin(t * 91.0), cos(t * 73.0)) * _shake_px * left
	_stage.position = _stage_origin + off.round()
	_stage.queue_redraw()
	_last_us = now


func _on_timer(what: String, now: int) -> void:
	match what:
		"result":
			_open_result()
		"call2":
			_call("차렷")
			_schedule(now, CALL_SEC + _run.ready_delay(_rng_run), "go")
		"go":
			_run.go(now)
			_call("땅!")
			_play(SoundSynth.Sfx.WHISTLE)
		"retry":
			_call("제자리에")
			_schedule(now, CALL_SEC, "call2")
		"next_rep":
			_new_rep(now)
		"wait":
			_big.text = ""
			_stage.batter_frame = 0
			_react.start_wait(now)
		"pose":
			_stage.batter_frame = 2
			_schedule(now, BETWEEN_SEC, "wait")
		"rest":
			_stage.batter_frame = 0
		"rise":
			_stage.lifter_frame = 6
			if _lift.phase == LiftTest.Phase.DONE:
				_finish_kind(float(_lift.best_kg), _lift.grade())
			else:
				_schedule(now, float(_fc["lift"]["riseSec"]) * 0.5 + 0.3, "next_rep")


# ---------- 50m ----------

func _tick_run(now: int, delta: float, frozen: bool) -> void:
	if _run.phase == RunTest.Phase.RUNNING:
		_run.advance(now)
		if _run.phase == RunTest.Phase.DONE:
			_coast_x = _run.x
			_coast_v = _run.v
			_call("%.2f초!" % _run.finish_sec)
			_play(SoundSynth.Sfx.WHISTLE, 0.7)
			_finish_kind(_run.finish_sec, _run.grade())
	var x := _run.x
	var v := _run.v
	if _run.phase == RunTest.Phase.DONE:
		_coast_x += _coast_v * delta
		_coast_v *= exp(-COAST_DRAG * delta)
		x = _coast_x
		v = _coast_v
	var px_per_m := float(_fc["run"]["pxPerM"])
	var fps := float(_fc["run"]["runFpsPerMps"])
	if not frozen:
		_anim += v * fps * delta
	var bt := _run.elapsed(now) if _run.phase != RunTest.Phase.READY else 0.0
	var bx := _run.buddy_x(bt) if _run.phase != RunTest.Phase.READY else 0.0
	var bv := (_run.buddy_x(bt + 0.05) - bx) / 0.05
	_buddy_anim += bv * fps * delta
	var s := _stage
	s.cam = maxf(0.0, RUN_START_PX + x * px_per_m - RUN_CAMERA_LEAD)
	s.me_x = RUN_START_PX + x * px_per_m
	s.buddy_x = RUN_START_PX + bx * px_per_m
	s.finish_x = RUN_START_PX + _run.distance * px_per_m
	if _run.phase == RunTest.Phase.READY:
		s.runner_frame = 0
	elif _run.stumbling(now):
		s.runner_frame = 5
	elif _run.phase == RunTest.Phase.DONE and v < CHEER_SPEED:
		s.runner_frame = 6
	else:
		s.runner_frame = 1 + int(_anim) % 4
	if _run.phase == RunTest.Phase.READY:
		s.buddy_frame = 0
	elif bx >= _run.distance:
		s.buddy_frame = 5
	else:
		s.buddy_frame = 1 + int(_buddy_anim) % 4


func _run_tap(foot: int, now: int) -> void:
	_flash_pad(foot)
	match _run.tap(foot, now):
		"step":
			_play(SoundSynth.Sfx.STEP, 0.8, 0.9 + 0.2 * foot)
		"stumble":
			_call("헛발!")
			_shake(0.6)
			_play(SoundSynth.Sfx.BUZZ, 0.5)
		"early":
			_call("부정 출발!")
			_play(SoundSynth.Sfx.BUZZ)
			_schedule(now, RETRY_SEC, "retry")


# ---------- 스쿼트 ----------

func _new_rep(now: int) -> void:
	_stage.lifter_frame = 0
	_stage.coach_frame = 0
	_call("도전 %dkg" % _lift.kg)
	_last_us = now


func _tick_lift(now: int) -> void:
	if _lift.phase == LiftTest.Phase.HOLD:
		if _lift.update(now) == "late":
			_lift_result("late", now)
			return
		var g := _lift.gauge(now)
		_stage.lifter_frame = 4 if g > _lift.zone().y else mini(3, int(g / 0.25))
		_stage.jitter = 1 if g > _lift.zone().y and (now / 50000) % 2 == 0 else 0
	else:
		_stage.jitter = 0
	_gauge.value = _lift.gauge(now)
	_gauge.zone = _lift.zone()
	_gauge.center = float(_fc["lift"]["zoneCenter"])
	_gauge.perfect = float(_fc["lift"]["perfectWidth"])
	_gauge.queue_redraw()


func _lift_press(now: int) -> void:
	if _next_what == "":
		_lift.press(now)
		_big.text = ""


func _lift_release(now: int) -> void:
	var r := _lift.release(now)
	if r != "":
		_lift_result(r, now)


func _lift_result(r: String, now: int) -> void:
	var rise := float(_fc["lift"]["riseSec"])
	_stage.coach_frame = 1
	_stage.jitter = 0
	match r:
		"perfect", "good":
			_call("완벽!" if r == "perfect" else "성공!")
			_play(SoundSynth.Sfx.CLANK, 1.0, 1.15 if r == "perfect" else 1.0)
			_shake(1.0 if r == "perfect" else 0.5)
			if r == "perfect":
				_hitstop()
			_stage.lifter_frame = 1  # 일어서는 중 → 잠시 뒤 입김
			_schedule(now, rise * 0.5, "rise")
		"early":
			_call("숨이 덜 찼다!")
			_stage.lifter_frame = 5
			_play(SoundSynth.Sfx.BUZZ)
		"late":
			_call("숨이 턱!")
			_stage.lifter_frame = 5
			_play(SoundSynth.Sfx.BUZZ)
			_shake(0.6)
	if r in ["perfect", "good"]:
		return  # 일어서는 그림(rise) 뒤에 다음 무게 또는 끝
	if _lift.phase == LiftTest.Phase.DONE:
		_finish_kind(float(_lift.best_kg), _lift.grade())
	else:
		_schedule(now, rise + 0.3, "next_rep")


# ---------- 반응 ----------

func _tick_react(now: int) -> void:
	match _react.update(now):
		"lit":
			_stage.lamp_frame = 2 if _react.is_red() else 1
			_play(SoundSynth.Sfx.BEEP, 0.8, 0.8 if _react.is_red() else 1.0)
		"miss":
			_react_feedback("놓쳤다!", "miss", now)
			_play(SoundSynth.Sfx.BUZZ, 0.6)
		"held":
			_react_feedback("참았다!", "held", now)
			_play(SoundSynth.Sfx.MITT, 0.6)


func _react_tap(now: int) -> void:
	match _react.tap(now):
		"hit":
			_stage.batter_frame = 1
			_hitstop()
			_shake(0.5)
			_play(SoundSynth.Sfx.CRACK_SOLID, 0.8)
			_react_feedback("%dms" % _react.last_ms(), "hit", now)
		"fooled":
			_stage.batter_frame = 2
			_play(SoundSynth.Sfx.SWING)
			_react_feedback("헛스윙!", "fooled", now)
			_shake(0.4)
		"early":
			_stage.batter_frame = 3
			_call("성급했다!")
			_play(SoundSynth.Sfx.BUZZ, 0.6)
			_dots.marks.append("early")
			_dots.queue_redraw()
			_schedule(now, 0.4, "rest")


func _react_feedback(text: String, mark: String, now: int) -> void:
	_call(text)
	_dots.marks.append(mark)
	_dots.queue_redraw()
	_stage.coach_frame = 1
	var lamp_off := func() -> void: _stage.lamp_frame = 0
	var tw := create_tween()
	tw.tween_interval(0.35)
	tw.tween_callback(lamp_off)
	if _react.phase == ReactTest.Phase.DONE:
		_finish_kind(float(_react.score_ms()), _react.grade())
	elif mark == "hit":
		_schedule(now, float(_fc["fx"]["hitstopMs"]) / 1000.0, "pose")  # 맞는 순간에 잠깐 멈췄다가 팔로스루 → 다음 신호
	else:
		_schedule(now, BETWEEN_SEC, "wait")


# ---------- 입력 ----------

func _input(e: InputEvent) -> void:
	if _step != Step.PLAY:
		return
	var now := Time.get_ticks_usec()
	var pressed := false
	var released := false
	var pos := Vector2(-1, -1)
	var foot := -1
	if e is InputEventScreenTouch:
		pressed = e.pressed
		released = not e.pressed
		pos = e.position
	elif e is InputEventMouseButton and e.button_index == MOUSE_BUTTON_LEFT and e.device != InputEvent.DEVICE_ID_EMULATION:
		pressed = e.pressed
		released = not e.pressed
		pos = e.position
	elif e is InputEventKey and not e.echo:
		var key: Key = e.keycode
		if key in [KEY_LEFT, KEY_A]:
			foot = 0
		elif key in [KEY_RIGHT, KEY_D]:
			foot = 1
		elif key != KEY_SPACE:
			return
		pressed = e.pressed
		released = not e.pressed
	else:
		return
	get_viewport().set_input_as_handled()
	match kind():
		"run":
			if pressed:
				if foot < 0:
					foot = 0 if pos.x < get_global_rect().get_center().x else 1
				_run_tap(foot, now)
		"lift":
			if pressed:
				_lift_press(now)
			elif released:
				_lift_release(now)
		"react":
			if pressed and _react.phase in [ReactTest.Phase.WAIT, ReactTest.Phase.LIT]:
				_react_tap(now)


# ---------- 무대·HUD ----------

var _stage_origin := Vector2.ZERO


func _layout() -> void:
	var bg := MinigameMeta.BG_SIZE
	var s := maxf(1.0, floorf(minf(size.x / bg.x, size.y / bg.y)))
	_stage.unit = int(s)
	_stage.size = bg * s
	_stage_origin = ((size - bg * s) / 2.0).floor()
	_stage.position = _stage_origin


func _setup_stage() -> void:
	var s := _stage
	s.mode = kind()
	s.runner_frame = 0
	s.buddy_frame = 0
	s.lifter_frame = 0
	s.batter_frame = 0
	s.lamp_frame = 0
	s.coach_frame = 0
	s.jitter = 0
	s.cam = 0.0
	s.me_x = RUN_START_PX
	s.buddy_x = RUN_START_PX
	_anim = 0.0
	_buddy_anim = 0.0
	match kind():
		"run":
			_run = RunTest.new(_fc["run"], _stat(_fc["run"]["stat"]))
			s.finish_x = RUN_START_PX + _run.distance * float(_fc["run"]["pxPerM"])
		"lift":
			_lift = LiftTest.new(_fc["lift"], _stat(_fc["lift"]["stat"]))
		"react":
			_react = ReactTest.new(_fc["react"], _rng("fitness-react"))
			_dots.total = _react.reds.size()
			_dots.marks.clear()
	for p in _pads:
		p.visible = kind() == "run"
	_gauge.visible = kind() == "lift"
	_dots.visible = kind() == "react"
	_kicker.text = "%s  %d / %d" % [_t("events.%s.title" % kind()), _k + 1, _kinds.size()]
	_record.text = ""
	_record.get_parent().visible = false
	_hint.text = ""
	_stage.queue_redraw()


func _build_hud() -> void:
	var top := HBoxContainer.new()
	top.set_anchors_and_offsets_preset(Control.PRESET_TOP_WIDE, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_MD)
	top.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_hud.add_child(top)
	var left := UiKit.glass_panel()
	_kicker = UiKit.label("", Tokens.FONT_LABEL, Tokens.HUD_TEXT, Tokens.FONT_BOLD)
	left.add_child(_kicker)
	top.add_child(left)
	top.add_child(UiKit.spacer())
	var right := UiKit.glass_panel()
	_record = UiKit.label("", Tokens.FONT_TITLE, Tokens.HUD_TEXT, Tokens.FONT_BOLD)
	right.add_child(_record)
	top.add_child(right)
	_big = UiKit.label("", Tokens.FONT_HERO, Tokens.HUD_TEXT, Tokens.FONT_BOLD)
	_big.add_theme_constant_override("outline_size", Tokens.SPACE_SM)
	_big.add_theme_color_override("font_outline_color", Tokens.INK)
	_big.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_big.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	_big.set_anchors_and_offsets_preset(Control.PRESET_CENTER_TOP)
	_big.anchor_top = 0.22
	_big.anchor_bottom = 0.22
	_big.grow_horizontal = Control.GROW_DIRECTION_BOTH
	_hud.add_child(_big)
	_hint = UiKit.label("", Tokens.FONT_CAPTION, Tokens.HUD_TEXT_SOFT)
	_hint.add_theme_constant_override("outline_size", Tokens.SPACE_XS)
	_hint.add_theme_color_override("font_outline_color", Tokens.INK)
	_hint.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_hint.set_anchors_and_offsets_preset(Control.PRESET_CENTER_BOTTOM, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_SM)
	_hint.grow_horizontal = Control.GROW_DIRECTION_BOTH
	_hint.grow_vertical = Control.GROW_DIRECTION_BEGIN
	_hud.add_child(_hint)
	# 50m 발 버튼 자리 (누르는 곳은 화면 절반씩 — 이건 보이는 표시)
	for i in 2:
		var pad := PanelContainer.new()
		var box := StyleBoxFlat.new()
		box.bg_color = Tokens.FOOT_PAD
		box.set_corner_radius_all(Tokens.RADIUS_CARD)
		pad.add_theme_stylebox_override("panel", box)
		pad.custom_minimum_size = Vector2(Tokens.TOUCH_MIN * 2.5, Tokens.TOUCH_MIN * 1.6)
		pad.mouse_filter = Control.MOUSE_FILTER_IGNORE
		var l := UiKit.label("왼발" if i == 0 else "오른발", Tokens.FONT_TITLE, Tokens.HUD_TEXT, Tokens.FONT_BOLD)
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		l.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
		pad.add_child(l)
		pad.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_LEFT if i == 0 else Control.PRESET_BOTTOM_RIGHT, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_LG)
		pad.grow_horizontal = Control.GROW_DIRECTION_END if i == 0 else Control.GROW_DIRECTION_BEGIN
		pad.grow_vertical = Control.GROW_DIRECTION_BEGIN
		_hud.add_child(pad)
		_pads.append(pad)
	_gauge = Gauge.new()
	_gauge.custom_minimum_size = Vector2(Tokens.SPACE_LG + Tokens.SPACE_XS, 220)
	_gauge.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
	_gauge.anchor_left = 0.66
	_gauge.anchor_right = 0.66
	_gauge.grow_horizontal = Control.GROW_DIRECTION_BOTH
	_gauge.grow_vertical = Control.GROW_DIRECTION_BOTH
	_hud.add_child(_gauge)
	_dots = Dots.new()
	_dots.custom_minimum_size = Vector2(220, Tokens.SPACE_LG)
	_dots.set_anchors_and_offsets_preset(Control.PRESET_CENTER_TOP)
	_dots.anchor_top = 0.14
	_dots.anchor_bottom = 0.14
	_dots.grow_horizontal = Control.GROW_DIRECTION_BOTH
	_hud.add_child(_dots)


func _update_hud(now: int) -> void:
	_record.get_parent().visible = _step != Step.INTRO
	match kind():
		"run":
			_record.text = "%.2f초" % _run.elapsed(now)
		"lift":
			_record.text = "최고 %s · 실패 %d/%d" % [FitnessTest.value_text("lift", float(_lift.best_kg)), _lift.fails, int(_fc["lift"]["maxFails"])]
		"react":
			_record.text = "%d / %d" % [mini(_react.index + 1, _react.reds.size()), _react.reds.size()]


func _flash_pad(foot: int) -> void:
	var pad := _pads[foot]
	var box := pad.get_theme_stylebox("panel") as StyleBoxFlat
	box.bg_color = Tokens.FOOT_PAD_ON
	var tw := create_tween()
	tw.tween_property(box, "bg_color", Tokens.FOOT_PAD, 0.12)


## 가운데 큰 글자가 튀어나온다
func _call(text: String) -> void:
	_big.text = text
	_big.reset_size()
	_big.pivot_offset = _big.size / 2.0
	_big.scale = Vector2.ONE * 1.5
	_big.modulate.a = 0.0
	var pop := float(_fc["fx"]["popSec"])
	if _call_tween != null:
		_call_tween.kill()
	_call_tween = create_tween()
	_call_tween.tween_property(_big, "scale", Vector2.ONE, pop).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	_call_tween.parallel().tween_property(_big, "modulate:a", 1.0, pop * 0.4)
	_call_tween.tween_interval(float(_fc["fx"]["callHoldSec"]))
	_call_tween.tween_property(_big, "modulate:a", 0.0, pop)


## strength 0~1 × shakePx
func _shake(strength: float) -> void:
	_shake_px = float(_fc["fx"]["shakePx"]) * strength * float(_stage.unit) / 2.0
	_shake_until_us = Time.get_ticks_usec() + int(float(_fc["fx"]["shakeMs"]) * 1000.0)


func _hitstop() -> void:
	_hitstop_until_us = Time.get_ticks_usec() + int(float(_fc["fx"]["hitstopMs"]) * 1000.0)


func _schedule(now: int, sec: float, what: String) -> void:
	_next_at_us = now + int(sec * 1e6)
	_next_what = what


func _play(sfx: SoundSynth.Sfx, volume: float = 1.0, pitch: float = 1.0) -> void:
	if _sound != null:
		_sound.play(sfx, volume, pitch)


# ---------- 카드 ----------

func _show_card(lines: Array[Control], cta: String, on_press: Callable) -> void:
	_close_card()
	_scrim.color = Tokens.SCRIM
	var card := PanelContainer.new()
	card.theme_type_variation = "Card"
	var col := VBoxContainer.new()
	col.add_theme_constant_override("separation", Tokens.SPACE_SM)
	card.add_child(col)
	for l in lines:
		col.add_child(l)
	var b := Button.new()
	b.text = cta
	b.theme_type_variation = "CtaButton"
	b.focus_mode = Control.FOCUS_NONE
	b.custom_minimum_size.y = Tokens.TOUCH_MIN
	b.pressed.connect(on_press)
	col.add_child(b)
	card.custom_minimum_size.x = 440
	card.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
	card.grow_horizontal = Control.GROW_DIRECTION_BOTH
	card.grow_vertical = Control.GROW_DIRECTION_BOTH
	add_child(card)
	_card = card
	card.pivot_offset = card.size / 2.0
	card.scale = Vector2.ONE * 0.9
	create_tween().tween_property(card, "scale", Vector2.ONE, 0.18).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)


func _close_card() -> void:
	if _card != null:
		_card.queue_free()
		_card = null
	_scrim.color = Tokens.DIALOGUE_BG_SCRIM


## 지금 카드의 버튼 (스크린샷·테스트용, 없으면 null)
func card_button() -> Button:
	if _card == null:
		return null
	var found := _card.find_children("*", "Button", true, false)
	return found[-1] if not found.is_empty() else null


static func _label(text: String, variation: String) -> Label:
	var l := Label.new()
	l.text = text
	l.theme_type_variation = variation
	return l


static func _wrapped(text: String, variation: String) -> Label:
	var l := _label(text, variation)
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.custom_minimum_size.x = 400
	return l


# ---------- 글·수치 ----------

func _t(path: String) -> String:
	if _career != null:
		return _career.minigame_text(path)
	var v: Variant = _cfg.minigames.get(FitnessTest.ID, {})
	for key in path.split("."):
		if not (v is Dictionary and (v as Dictionary).has(key)):
			return ""
		v = v[key]
	return str(v).replace("{player}", "너")


func _name(person: String) -> String:
	if _career != null and _career.people.person(person) != null:
		return _career.people.person(person).name
	return {"coach": "감독", "buddy": "동기"}.get(person, person)


func _stat(id: String) -> int:
	return _career.player.stat(id) if _career != null else int(_cfg.start[id])


func _rng(salt: String) -> RandomNumberGenerator:
	if _career != null:
		return _career.rng_for(salt)
	var r := RandomNumberGenerator.new()
	r.seed = hash([_cfg.seed, salt])
	return r


# ---------- 도트 무대 ----------

## 배경·인물 칸을 unit 배로 그린다 (정수배 · 같은 격자, 돌리지 않는다)
class Stage:
	extends Control
	var unit := 4
	var mode := "run"
	var cam := 0.0
	var me_x := 0.0
	var buddy_x := 0.0
	var finish_x := 0.0
	var runner_frame := 0
	var buddy_frame := 0
	var lifter_frame := 0
	var batter_frame := 0
	var lamp_frame := 0
	var coach_frame := 0
	var jitter := 0

	func _init() -> void:
		clip_contents = true
		texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
		mouse_filter = Control.MOUSE_FILTER_IGNORE

	func _draw() -> void:
		var bg := PixelArt.texture("minigame_bg")
		var sheet := PixelArt.texture("minigame")
		if bg == null or sheet == null:
			return
		match mode:
			"run":
				_tile(bg, "run_far", cam * FitnessView.RUN_FAR_PARALLAX)
				_tile(bg, "run_near", cam)
				_sprite(sheet, "lines", 0, Vector2(FitnessView.RUN_START_PX - cam, FitnessView.RUN_LINE_FOOT_Y))
				_sprite(sheet, "lines", 1, Vector2(finish_x - cam, FitnessView.RUN_LINE_FOOT_Y))
				_sprite(sheet, "buddy", buddy_frame, Vector2(buddy_x - cam, FitnessView.RUN_BUDDY_FOOT_Y))
				_sprite(sheet, "runner", runner_frame, Vector2(me_x - cam, FitnessView.RUN_ME_FOOT_Y))
			"lift":
				_tile(bg, "weight", 0.0)
				_sprite(sheet, "coach", coach_frame, FitnessView.LIFT_COACH_FOOT)
				_sprite(sheet, "lifter", lifter_frame, FitnessView.LIFT_FOOT + Vector2(jitter, 0))
			"react":
				_tile(bg, "react", 0.0)
				_sprite(sheet, "lamp", lamp_frame, FitnessView.REACT_LAMP_FOOT)
				_sprite(sheet, "coach", coach_frame, FitnessView.REACT_COACH_FOOT)
				_sprite(sheet, "batter", batter_frame, FitnessView.REACT_FOOT)

	## 배경 한 줄을 가로로 이어 붙여 offset 칸만큼 흘린다
	func _tile(tex: Texture2D, row_name: String, offset: float) -> void:
		var w := MinigameMeta.BG_SIZE.x
		var h := MinigameMeta.BG_SIZE.y
		var row: int = MinigameMeta.BG_ROWS[row_name]
		var o := fposmod(roundf(offset), w)
		draw_texture_rect_region(tex, Rect2(0, 0, (w - o) * unit, h * unit), Rect2(o, row * h, w - o, h))
		if o > 0.0:
			draw_texture_rect_region(tex, Rect2((w - o) * unit, 0, o * unit, h * unit), Rect2(0, row * h, o, h))

	## 인물 칸 하나: foot (배경 좌표) 에 칸의 발 자리를 맞춘다
	func _sprite(tex: Texture2D, row_name: String, frame: int, foot: Vector2) -> void:
		var info: Array = MinigameMeta.ROWS[row_name]
		var c := MinigameMeta.CELL
		var at := (foot - MinigameMeta.FOOT).round()
		draw_texture_rect_region(tex, Rect2(at * unit, c * unit), Rect2(Vector2(clampi(frame, 0, int(info[1]) - 1) * c.x, int(info[0]) * c.y), c))


## 스쿼트 호흡 게이지: 아래에서 위로 찬다. 초록 = 목표 구간, 노란 줄 = 완벽
class Gauge:
	extends Control
	var value := 0.0
	var zone := Vector2(0.65, 0.9)
	var center := 0.78
	var perfect := 0.05

	func _init() -> void:
		mouse_filter = Control.MOUSE_FILTER_IGNORE

	func _draw() -> void:
		var r := Rect2(Vector2.ZERO, size)
		draw_rect(r, Tokens.GAUGE_BG)
		var h := size.y
		draw_rect(Rect2(0, h * (1.0 - zone.y), size.x, h * (zone.y - zone.x)), Color(Tokens.GAUGE_ZONE, 0.55))
		draw_rect(Rect2(0, h * (1.0 - center - perfect / 2.0), size.x, h * perfect), Tokens.GAUGE_PERFECT)
		var fill := h * value
		var color := Tokens.GAUGE_ZONE if value >= zone.x and value <= zone.y else Tokens.HUD_TEXT
		draw_rect(Rect2(Tokens.SPACE_XS, h - fill, size.x - Tokens.SPACE_SM, fill), Color(color, 0.85))
		draw_rect(Rect2(-Tokens.SPACE_XS, h - fill - 2, size.x + Tokens.SPACE_SM, 4), Tokens.HUD_TEXT)
		draw_rect(r, Tokens.HUD_BORDER, false, 2.0)


## 반응 테스트 진행 점: 신호마다 하나 (맞힘 초록 · 참음 파랑 · 실수 빨강 · 남은 것 흐림)
class Dots:
	extends Control
	var total := 6
	var marks: Array[String] = []

	func _init() -> void:
		mouse_filter = Control.MOUSE_FILTER_IGNORE

	func _draw() -> void:
		var done := marks.filter(func(m: String) -> bool: return m != "early")
		var gap := size.x / float(maxi(total, 1))
		for i in total:
			var c := Tokens.BROADCAST_EMPTY
			if i < done.size():
				c = {"hit": Tokens.COUNT_BALL, "held": Tokens.PRIMARY, "miss": Tokens.COUNT_OUT, "fooled": Tokens.COUNT_OUT}.get(done[i], Tokens.COUNT_OUT)
			draw_circle(Vector2(gap * (i + 0.5), size.y / 2.0), size.y / 3.0, c)
