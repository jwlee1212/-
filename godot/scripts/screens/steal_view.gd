class_name StealView
extends Control
## 도루 화면: 내야를 가까이 비추고, 투수 동작을 보다가 던지는 순간 [뛴다!] 를 누른다.
## 투수의 시각표와 판정은 Steal(로직)이 정하고, 여기는 그 시각표대로 그리고 탭 시각을 넘긴다.
## 시간은 Time.get_ticks_usec() 하나로 잰다 (원칙 4). 끝나면 finished(결과) — 안 뛰었으면 kind = NONE.

signal finished(result: Steal.Result)
## 효과음을 내야 할 때 (경기 화면이 재생기로 넘긴다)
signal sound_requested(sfx: SoundSynth.Sfx, volume: float, pitch_scale: float)

## 내야가 화면에 차게 맞추는 가짜 담장 거리 (m): 가운데(2루 뒤)·좌우(1·3루 밖)
const VIEW_CENTER_M := 50.0
const VIEW_LINE_M := 42.0
## 견제·투구 동작 때 투수가 몸을 옮기는 거리 (m), 견제 송구가 날아가는 시간·안 뛰고 닫힐 때까지 (초)
const PITCHER_LEAN_M := 1.6
const PICKOFF_THROW_S := 0.45
const NO_RUN_HOLD_S := 0.7
## 버튼 크기 (px)
const GO_SIZE := Vector2(170, 72)

var _phys: BattedBallSim.Config
var _cfg: Dictionary
var _from: int
var _speed: int
## 플레이 전 1·2·3루 주자 (타순 번호, -1 = 빈 베이스) 와 내 타순 번호
var _bases: Array
var _my_id: int
var _latency_ms: float
var _rng: RandomNumberGenerator
var _plan: Steal.Plan
var _start_ms := 0.0
## 판정 (탭했거나 시간이 다 됐을 때 정해진다)
var _result: Steal.Result = null
## 판정 뒤 화면을 닫는 시각 (ms)
var _close_ms := INF
## 판정이 정해진 시각 (ms)
var _decided_ms := 0.0
var _closed := false
var _safe_cheered := false
var _mitt_played := false
var _top_speed := 0.0
var _label: Label
var _go: Button
var _skip: Button


## bases: 지금 주자들, my_id: 내 타순 번호, from: 내 베이스(1·2), speed: 주력, latency_ms: 입력 지연 보정
func _init(phys: BattedBallSim.Config, cfg: Dictionary, bases: Array, my_id: int, from: int, speed: int, seed_value: int, latency_ms: float) -> void:
	_phys = phys
	_cfg = cfg
	_bases = bases
	_my_id = my_id
	_from = from
	_speed = speed
	_latency_ms = latency_ms
	_rng = RandomNumberGenerator.new()
	_rng.seed = seed_value
	_plan = Steal.plan(cfg, _rng)
	_top_speed = RunnerAI.new(my_id, from, speed, phys).top_speed


func _ready() -> void:
	# 도트 경기장·선수는 칸이 번지지 않게
	if PixelArt.on():
		texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	mouse_filter = Control.MOUSE_FILTER_STOP
	clip_contents = true
	_start_ms = _now()
	var chip := UiKit.glass_panel()
	add_child(chip)
	chip.position = Vector2(Tokens.SPACE_SM, Tokens.SPACE_SM)
	_label = UiKit.label("", Tokens.FONT_LABEL, Tokens.HUD_TEXT, Tokens.FONT_BOLD)
	chip.add_child(_label)
	_go = UiKit.button("뛴다!", _on_go, Tokens.GOOD, Tokens.FONT_HERO)
	_go.custom_minimum_size = GO_SIZE
	# 손가락이 닿는 순간 스타트 (떼는 순간이 아니라) — 반응 시간이 승부다
	_go.action_mode = BaseButton.ACTION_MODE_BUTTON_PRESS
	add_child(_go)
	_go.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_RIGHT, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_MD)
	_go.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	_go.grow_vertical = Control.GROW_DIRECTION_BEGIN
	_skip = UiKit.button("안 뛴다", _on_skip, Tokens.INK_SOFT, Tokens.FONT_LABEL)
	add_child(_skip)
	_skip.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_LEFT, Control.PRESET_MODE_MINSIZE, Tokens.SPACE_MD)
	_skip.grow_vertical = Control.GROW_DIRECTION_BEGIN
	_skip.position.y -= BattingHud.GEAR_CLEARANCE  # 개발 빌드의 ⚙ 손맛 버튼 자리를 비운다


func _now() -> float:
	return Time.get_ticks_usec() / 1000.0


## 화면에 뜬 뒤 흐른 시간 (ms)
func elapsed_ms() -> float:
	return _now() - _start_ms


## 투구 동작이 시작된 뒤 흐른 시간 (초, 그 전이면 음수)
func _since_go() -> float:
	return (elapsed_ms() - _plan.go_ms) / 1000.0


func _on_go() -> void:
	if _result != null:
		return
	# 입력 지연 보정은 반응 시간만 줄인다 (투구 뒤에 누른 탭이 투구 전으로 넘어가지 않게)
	var tap := elapsed_ms()
	_decide(maxf(_plan.go_ms, tap - _latency_ms) if tap >= _plan.go_ms else tap)


func _on_skip() -> void:
	if _result == null:
		_decide(-1.0)
		_close_ms = _now()


## tap_ms 로 판정을 정한다 (음수 = 안 뛴다). 스크린샷·테스트 스크립트도 부른다
func _decide(tap_ms: float) -> void:
	_result = Steal.judge(_cfg, _phys, _plan, tap_ms, _from, _speed, _rng)
	_decided_ms = _now()
	_go.visible = false
	_skip.visible = false
	var hold: float = _cfg["resultHoldMs"]
	match _result.kind:
		Steal.Kind.NONE:
			_close_ms = _now() + NO_RUN_HOLD_S * 1000.0
		Steal.Kind.PICKED_OFF:
			_close_ms = _now() + PICKOFF_THROW_S * 1000.0 + hold
		_:
			sound_requested.emit(SoundSynth.Sfx.SWING, 0.7, 1.0)
			# 판정(수비 시간)까지 본 뒤 hold 만큼 붙잡는다
			_close_ms = _start_ms + _plan.go_ms + maxf(_result.defense_s, _result.runner_s()) * 1000.0 + hold


func _process(_delta: float) -> void:
	queue_redraw()
	if _result == null and elapsed_ms() > _plan.end_ms():
		_decide(-1.0)
	_label.text = _caption()
	if _result != null and _result.attempted() and _result.kind != Steal.Kind.PICKED_OFF:
		# 포수 미트 소리, 살았으면 함성
		if not _mitt_played and _since_go() >= _result.delivery_s:
			_mitt_played = true
			sound_requested.emit(SoundSynth.Sfx.MITT, 0.9, 1.0)
		if not _safe_cheered and _result.kind == Steal.Kind.SAFE and _since_go() >= _result.defense_s:
			_safe_cheered = true
			sound_requested.emit(SoundSynth.Sfx.CHEER_SMALL, 1.0, 1.0)
	if not _closed and _now() >= _close_ms:
		_closed = true
		set_process(false)
		finished.emit(_result)


func _caption() -> String:
	var to_name := "%d루" % (_from + 1)
	if _result == null:
		if _plan.in_feint(elapsed_ms()):
			return "견제! — 아직 뛰면 안 된다"
		return "★ %s 도루 기회 — 투수가 던지는 순간 [뛴다!]" % to_name
	match _result.kind:
		Steal.Kind.NONE:
			return "뛰지 않았다"
		Steal.Kind.PICKED_OFF:
			return "투구 전에 뛰었다 — 견제사…"
	var done := _since_go() >= _result.defense_s
	var react := "스타트 %.2f초" % (_result.reaction_ms / 1000.0)
	if not done:
		return "★ %s로! (%s)" % [to_name, react]
	var verdict := "도루 성공!" if _result.kind == Steal.Kind.SAFE else "도루 실패…"
	return "%s %s · %.2f초 차" % [verdict, react, absf(_result.margin_s())]


# ---------- 그리기 ----------

func _draw() -> void:
	var rect := Rect2(Vector2.ZERO, size)
	var cam := BroadcastView.Cam.new(rect, VIEW_CENTER_M, VIEW_LINE_M, 0.0)
	draw_rect(rect, Tokens.STANDS)
	BroadcastView._draw_field(self, cam, _phys)
	var positions := BattedBallSim.fielder_positions(_phys, false)
	var from_pos := _phys.base_pos(_from)
	var to_pos := _phys.base_pos(_from + 1)
	var home := Vector2.ZERO
	var mound: Vector2 = positions["P"]
	var e := elapsed_ms()
	var tau := _since_go()
	var picked := _result != null and _result.kind == Steal.Kind.PICKED_OFF
	var running := _result != null and _result.attempted() and not picked

	# 수비수: 투수는 견제 때 내 베이스 쪽으로, 투구 때 홈 쪽으로 몸을 옮긴다. 베이스 커버는 송구에 맞춰 들어간다
	var cover_name := "SS" if _from == 1 else "3B"
	for name: String in positions:
		var at: Vector2 = positions[name]
		if name == "P":
			at = _pitcher_pos(mound, from_pos, e, picked)
		elif name == cover_name and running:
			at = at.lerp(to_pos, clampf(tau / maxf(_result.defense_s - 0.3, 0.1), 0.0, 1.0))
		elif name == ("1B" if _from == 1 else "2B") and not running:
			at = at.lerp(from_pos, 0.8)  # 주자를 묶어 두는 수비수
		BroadcastView._draw_person(self, cam, at, Tokens.UNIFORM_THEM)

	# 다른 주자는 베이스에, 내 주자는 리드한 자리에서 (뛰면 가속해서 달린다)
	for i in 3:
		if _bases[i] != -1 and _bases[i] != _my_id:
			BroadcastView._draw_person(self, cam, _phys.base_pos(i + 1), Tokens.UNIFORM_US)
	var me := _runner_pos(from_pos, to_pos, tau, picked)
	var caught_gone := running and _result.kind == Steal.Kind.CAUGHT and tau > _result.defense_s + BroadcastView.RUNNER_FADE_S
	var picked_gone := picked and _since_decide() > PICKOFF_THROW_S + BroadcastView.RUNNER_FADE_S
	if not caught_gone and not picked_gone:
		BroadcastView._draw_person(self, cam, me, Tokens.UNIFORM_US)
		BroadcastView.draw_marker(self, cam, me)

	# 공: 투수 손 → (투구) 포수 → (송구) 베이스. 견제사는 투수 → 내 베이스
	var ball := Vector3(mound.x, mound.y, 1.4)
	if picked:
		var k := clampf(_since_decide() / PICKOFF_THROW_S, 0.0, 1.0)
		ball = Vector3(mound.x, mound.y, 1.5).lerp(Vector3(from_pos.x, from_pos.y, 1.0), k)
	elif tau >= 0.0 and (running or _result == null or _result.kind == Steal.Kind.NONE):
		var delivery := float(_cfg["deliveryS"])
		if tau < delivery:
			# 동작의 뒤쪽 절반 동안 공이 날아간다
			var k := clampf((tau - delivery * 0.55) / (delivery * 0.45), 0.0, 1.0)
			ball = Vector3(mound.x, mound.y, 1.6).lerp(Vector3(home.x, home.y - 1.0, 0.8), k)
		elif running:
			var arrive := _result.defense_s - float(_phys.running["tagS"])
			var k := clampf((tau - delivery) / maxf(arrive - delivery, 0.1), 0.0, 1.0)
			ball = Vector3(home.x, home.y, 1.5).lerp(Vector3(to_pos.x, to_pos.y, 0.6), k)
			ball.z += sin(k * PI) * 2.0
		else:
			ball = Vector3(home.x, home.y - 1.0, 0.8)
	BroadcastView._draw_ball(self, cam, ball)

	# 투수 머리 위 신호: 세트 / 견제! / 투구!
	if _result == null or _result.kind == Steal.Kind.NONE:
		_draw_cue(cam, mound, e)
	# 판정 말풍선
	var call := PlaySimulator.PlayResult.new()
	if picked and _since_decide() >= PICKOFF_THROW_S:
		call.calls.append({"base": _from, "t": 0.0, "out": true, "text": "견제사!"})
		BroadcastView._draw_calls(self, cam, _phys, call, minf(_since_decide() - PICKOFF_THROW_S, BroadcastView.CALL_SHOW_S))
	elif running and tau >= _result.defense_s:
		var safe := _result.kind == Steal.Kind.SAFE
		call.calls.append({"base": _from + 1, "t": 0.0, "out": not safe, "text": "세이프!" if safe else "아웃!"})
		BroadcastView._draw_calls(self, cam, _phys, call, minf(tau - _result.defense_s, BroadcastView.CALL_SHOW_S))


## 판정이 정해진 뒤 흐른 시간 (초) — 견제사 연출용
func _since_decide() -> float:
	return (_now() - _decided_ms) / 1000.0


func _pitcher_pos(mound: Vector2, from_pos: Vector2, e: float, picked: bool) -> Vector2:
	if picked or _plan.in_feint(e):
		return mound.move_toward(from_pos, PITCHER_LEAN_M)
	if e >= _plan.go_ms:
		var k := clampf((e - _plan.go_ms) / (float(_cfg["deliveryS"]) * 550.0), 0.0, 1.0)
		return mound.move_toward(Vector2.ZERO, PITCHER_LEAN_M * k)
	return mound


## 내 주자의 자리: 리드한 곳에서 기다리다, 스타트하면 가속해서 달린다 (판정에 쓴 속도 그대로)
func _runner_pos(from_pos: Vector2, to_pos: Vector2, tau: float, picked: bool) -> Vector2:
	var run: Dictionary = _phys.running
	var lead := float(run["leadOffM"]) + (float(_cfg["extraLeadSecondM"]) if _from == 2 else 0.0)
	var start := from_pos.move_toward(to_pos, lead)
	if picked:
		return start.move_toward(to_pos, 1.5)  # 역동작에 걸렸다
	if _result == null or not _result.attempted():
		return start
	var t := tau - _result.reaction_ms / 1000.0
	if _result.kind == Steal.Kind.CAUGHT:
		t = minf(t, _result.defense_s - _result.reaction_ms / 1000.0)  # 태그된 자리에서 멈춘다
	if t <= 0.0:
		return start
	var a := float(run["accelMps2"])
	var t_acc := _top_speed / a
	var d := 0.5 * a * t * t if t < t_acc else 0.5 * a * t_acc * t_acc + _top_speed * (t - t_acc)
	return start.move_toward(to_pos, minf(d, _result.distance_m))


func _draw_cue(cam: BroadcastView.Cam, mound: Vector2, e: float) -> void:
	var text := "세트"
	var color := Tokens.INK_SOFT
	if _plan.in_feint(e):
		text = "견제!"
		color = Tokens.BAD
	elif e >= _plan.go_ms:
		text = "투구!"
		color = Tokens.GOOD
	var font := Tokens.FONT_BOLD
	var at := cam.to_screen(Vector3(mound.x, mound.y, 4.5))
	var w := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, Tokens.FONT_TITLE).x
	var pill := StyleBoxFlat.new()
	pill.bg_color = color
	pill.set_corner_radius_all(Tokens.RADIUS_CARD)
	pill.anti_aliasing = true
	var box := Rect2(at - Vector2(w / 2.0 + Tokens.SPACE_MD, Tokens.FONT_TITLE + Tokens.SPACE_XS), Vector2(w + Tokens.SPACE_MD * 2, Tokens.FONT_TITLE + Tokens.SPACE_SM * 2))
	draw_style_box(pill, box)
	draw_string(font, Vector2(box.position.x, at.y), text, HORIZONTAL_ALIGNMENT_CENTER, box.size.x, Tokens.FONT_TITLE, Tokens.SURFACE)
