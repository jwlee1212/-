class_name FieldView
extends Control
## 내 수비 화면 (유격수 시점): 유격수 자리에서 홈플레이트 쪽을 보고 서 있다. 공과 그라운드 위주로 그린다.
##   ① 포구: '딱!' 소리와 함께 공이 홈에서 굴러온다. 화면에 점과 줄어드는 원이 뜨고, 원이 점 안에 들어올 때 탭 → 포구
##   ② 송구: 화면이 1루로 휙 돌아가고(멀리 타자 주자가 1루로 달린다) 오른쪽에 송구 게이지가 뜬다. 꾹 눌러 힘을 모으고 금색에서 뗀다
##   ③ 1루 클로즈업: 내야(유격수 쪽)에서 1루를 바라본 장면으로 바뀐다. 공이 카메라 뒤에서 화면 안쪽으로 날아가고, 1루수가
##      카메라를 보고 뻗어 잡는다. 타자 주자는 오른쪽(홈 쪽)에서 왼쪽으로 달려와 앞다리를 쭉 뻗어 발끝으로 베이스를 찍고,
##      루심이 아웃(주먹)·세이프(두 팔)를 외친다. 접전이면 도착 직전부터 슬로모션
##   끝나면 결과를 잠깐 보여 주고 finished(FieldPlay.Kind)
## 판정은 FieldPlay(로직). 여기는 그리기·입력·소리만 한다 (원칙 1). 시간은 Time.get_ticks_usec() 하나 (원칙 4).
## 그라운드는 화면 한 칸(그림 1픽셀)마다 땅의 어디인지 카메라를 거꾸로 계산해 도트로 찍는다 (중계 화면과 같은 땅 색).
## 클로즈업은 make_pixel_art.py 가 찍은 배경·칸(closeup_bg.png · closeup.png)을 정수배로 그린다.
## 공·원·게이지는 매끈하게 그린다 (원칙 6).

signal finished(kind: FieldPlay.Kind)
## 효과음을 내야 할 때 (경기 화면이 재생기로 넘긴다)
signal sound_requested(sfx: SoundSynth.Sfx, volume: float, pitch_scale: float)

## 카메라 (m): 유격수 자리에서 뒤로 CAM_BACK, 눈높이 CAM_HEIGHT, 내려다보는 각 CAM_PITCH_DEG, 화면 높이 대비 초점 거리
const CAM_BACK := 1.5
const CAM_HEIGHT := 1.6
const CAM_PITCH_DEG := 12.0
const CAM_FOCAL := 1.2
## 홈 뒤 그물 담장 (홈에서 m, 높이 m)
const BACKSTOP_M := 18.0
const BACKSTOP_H := 3.5
## 파울 지역 폭 (파울 라인에서 관중석 담장까지 m), 담장 위 관중석 높이 (그림 칸)
const FOUL_DEPTH_M := 16.0
const STANDS_ROWS := 18.0
## 공 반지름 (m — 캐주얼하게 실제보다 크게), 굴러오며 튀는 횟수와 첫 바운드 높이 (m)
const BALL_R := 0.08
const HOPS := 3
const HOP_M := 0.9
## 공이 빠진 뒤 계속 굴러가는 거리 (도착을 1 로 볼 때)
const THROUGH_MAX := 1.8
## 땅볼 감속 (1 = 같은 속도, 클수록 처음에 빠르고 끝에 느리다)
const ROLL_EASE := 1.7
## 클로즈업: 1m 가 배경 그림 몇 칸인가 (사람 키보다 거리를 줄여 그려 주자가 일찍 보이게), 주자가 1루를 지나 달려 나가는 거리(m)
const CU_PX_PER_M := 8.0
const CU_OVERRUN_M := 4.5
## 클로즈업 주자 한 걸음(칸 하나)이 몇 m 인가, 1루수가 뻗기 시작하는 때(공 도착 몇 초 전), 루심 판정이 늦는 시간(초)
## 베이스 터치: 1루까지 이만큼(m) 남으면 앞다리를 뻗고(발끝이 베이스에 닿는 순간이 1루 도착), 지난 뒤 이만큼까지 그 발을 딛고 넘어간다
const CU_TOUCH_FROM_M := 0.55
const CU_PASS_TO_M := -1.2
const CU_STEP_M := 0.6
const CU_REACH_S := 0.35
const CU_CALL_DELAY_S := 0.12
## 송구 게이지 막대 (화면 비율·px)
const GAUGE_W := 26.0
const GAUGE_TOP := 0.2
const GAUGE_BOTTOM := 0.82

var _fc: Dictionary
var _phys: BattedBallSim.Config
var _seed: int
var _batter_name: String
var _runner_speed: int
var _fielding: int
var _latency_us: int
var _play: FieldPlay
## 카메라 둘: 홈을 보는 것(포구), 1루를 보는 것(송구)
var _cam_home: _Cam
var _cam_first: _Cam
var _tex_home: Texture2D
var _tex_first: Texture2D
var _u := 3.0
## 공이 굴러오는 길 (땅 위 점, 야구장 좌표): 홈 → 점 아래
var _ball_to := Vector2.ZERO
var _close_us := 0
var _closed := false
var _shake_until_us := 0
## 클로즈업: 지금 보여 주는 플레이 시각(맞은 뒤 초 — 슬로모션이면 느리게 흐른다)과 마지막으로 잰 실제 시각
var _cu := false
var _cu_s := 0.0
var _cu_last_us := 0
var _cu_ball_done := false
var _cu_runner_done := false
var _cu_called := false
## 이번 프레임의 화면 흔들림 (그리기 변환)
var _shake := Vector2.ZERO
var _label: Label
var _hint: Label
var _result: Label


## 카메라: 땅 위 점 ↔ 화면. 야구장 좌표 x 1루 쪽, y 가운데 방향, z 위
class _Cam:
	var pos: Vector3
	var fwd: Vector3
	var right: Vector3
	var up: Vector3
	var focal: float
	var center: Vector2

	func _init(p_pos: Vector3, look_ground: Vector2, view: Vector2, pitch_deg: float, focal_ratio: float) -> void:
		pos = p_pos
		var flat := Vector3(look_ground.x - pos.x, look_ground.y - pos.y, 0.0).normalized()
		var pitch := deg_to_rad(pitch_deg)
		fwd = (flat * cos(pitch) + Vector3(0, 0, -sin(pitch))).normalized()
		right = fwd.cross(Vector3(0, 0, 1)).normalized()
		up = right.cross(fwd)
		focal = view.y * focal_ratio
		center = view * 0.5

	func to_screen(p: Vector3) -> Vector2:
		var d := p - pos
		var depth := maxf(d.dot(fwd), 0.05)
		return center + Vector2(d.dot(right), -d.dot(up)) * focal / depth

	func depth_of(p: Vector3) -> float:
		return maxf((p - pos).dot(fwd), 0.05)

	## 화면 점 → 땅 위 점 (지평선 위면 INF)
	func ground_at(s: Vector2) -> Vector2:
		var q := (s - center) / focal
		var dir := fwd + right * q.x - up * q.y
		if dir.z >= -0.0001:
			return Vector2.INF
		var t := -pos.z / dir.z
		return Vector2(pos.x + dir.x * t, pos.y + dir.y * t)


## fc: balance.json game.fielding, phys: 타구 물리 설정(수비 위치·베이스·타자 주자 속도), runner_speed: 타자 주력(0~100),
## fielding: 내 수비 능력치(경기용), latency_ms: 입력 지연 보정
func _init(fc: Dictionary, phys: BattedBallSim.Config, seed_value: int, batter_name: String, runner_speed: int, fielding: int, latency_ms: float) -> void:
	_fielding = fielding
	_fc = fc
	_phys = phys
	_seed = seed_value
	_batter_name = batter_name
	_runner_speed = runner_speed
	_latency_us = int(latency_ms * 1000.0)


func _ready() -> void:
	texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	mouse_filter = Control.MOUSE_FILTER_STOP
	clip_contents = true
	focus_mode = Control.FOCUS_ALL
	var chip := UiKit.glass_panel()
	add_child(chip)
	chip.position = Vector2(Tokens.SPACE_SM, Tokens.SPACE_SM)
	var col := UiKit.vbox(2)
	chip.add_child(col)
	_label = UiKit.label("★ 유격수 (수비 %d)  |  타자 %s (주력 %d)" % [_fielding, _batter_name, _runner_speed], Tokens.FONT_LABEL, Tokens.HUD_TEXT, Tokens.FONT_BOLD)
	col.add_child(_label)
	_hint = UiKit.label("", Tokens.FONT_CAPTION, Tokens.HUD_TEXT_SOFT)
	col.add_child(_hint)
	_result = UiKit.title("", Tokens.FONT_HERO, Tokens.HUD_TEXT)
	_result.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_result.add_theme_color_override("font_outline_color", Tokens.NAVY)
	_result.add_theme_constant_override("outline_size", 8)
	add_child(_result)
	_result.set_anchors_and_offsets_preset(Control.PRESET_CENTER_TOP)
	_result.grow_horizontal = Control.GROW_DIRECTION_BOTH
	_result.position.y += Tokens.SPACE_XL * 2
	grab_focus()


## 화면 크기가 정해진 뒤 처음 한 번: 카메라·그라운드 그림·로직을 만든다
func _setup() -> void:
	var view := size
	_u = maxf(1.0, round(PixelArt.unit(view)))
	var ss: Vector2 = _phys.positions.get("SS", BattedBallSim.polar(-14, 33))
	var back := ss.normalized() * CAM_BACK
	var eye := Vector3(ss.x + back.x, ss.y + back.y, CAM_HEIGHT)
	_cam_home = _Cam.new(eye, Vector2.ZERO, view, CAM_PITCH_DEG, CAM_FOCAL)
	_cam_first = _Cam.new(eye, _phys.base_pos(1), view, CAM_PITCH_DEG, CAM_FOCAL)
	_tex_home = ImageTexture.create_from_image(_ground_image(_cam_home, view))
	_tex_first = ImageTexture.create_from_image(_ground_image(_cam_first, view))
	var rng := RandomNumberGenerator.new()
	rng.seed = _seed
	_play = FieldPlay.new(_fc, rng, view.x / view.y, _now_us(), FieldPlay.runner_first_s(_phys, _runner_speed), _fielding)
	_ball_to = _cam_home.ground_at(_play.dot * view.y)
	if _ball_to == Vector2.INF:
		_ball_to = ss
	# 송구 거리: 잡은 자리 → 1루
	_play.throw_m = _ball_to.distance_to(_phys.base_pos(1))
	_play.runner_speed = _runner_speed


func _now_us() -> int:
	return Time.get_ticks_usec()


func _gui_input(event: InputEvent) -> void:
	# 터치가 흉내 낸 마우스 입력은 버린다 (누름·뗌이 두 번 들어오지 않게)
	var mouse: bool = event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.device != InputEvent.DEVICE_ID_EMULATION
	if mouse or event is InputEventScreenTouch:
		if event.pressed:
			_tap()
		else:
			_release()
		accept_event()


func _unhandled_key_input(event: InputEvent) -> void:
	if event is InputEventKey and not event.echo and event.keycode == KEY_SPACE:
		if event.pressed:
			_tap()
		else:
			_release()


## 누른 순간 (입력 지연만큼 앞당겨 판정한다): 포구, 또는 송구 게이지 모으기 시작. 스크린샷·테스트 스크립트도 부른다
func _tap() -> void:
	if _play == null:
		return
	var t := _now_us() - _latency_us
	match _play.press(t):
		"catch":
			sound_requested.emit(SoundSynth.Sfx.MITT, 1.0, 1.15)
			_shake_until_us = _now_us() + 220000
		"early":
			sound_requested.emit(SoundSynth.Sfx.MITT, 0.6, 0.7)
			_finish_later(float(_fc["resultHoldSec"]))


## 뗀 순간: 모으던 게이지로 던진다 (누를 때와 같은 입력 지연 보정 — 모은 시간은 그대로)
func _release() -> void:
	if _play == null:
		return
	if _play.release(_now_us() - _latency_us) == "throw":
		_on_throw()


func _on_throw() -> void:
	sound_requested.emit(SoundSynth.Sfx.SWING, 0.9, 1.2)
	_start_closeup()


func _finish_later(sec: float) -> void:
	_close_us = _now_us() + int(sec * 1e6)


func _process(_delta: float) -> void:
	if size.x <= 0.0 or size.y <= 0.0:
		return
	if _play == null:
		_setup()
	var t := _now_us()
	match _play.update(t):
		"hit":
			sound_requested.emit(SoundSynth.Sfx.CRACK_SOLID, 0.8, 1.0)
		"through":
			_finish_later(float(_fc["resultHoldSec"]))
		"throw":
			_on_throw()  # 끝까지 눌러 저절로 던졌다
		"late":
			# 못 던진 사이 주자가 1루를 밟았다 — 유격수 화면 그대로 세이프
			sound_requested.emit(SoundSynth.Sfx.STEP, 0.8, 1.0)
			_finish_later(float(_fc["resultHoldSec"]))
	if _cu:
		_step_closeup(t)
	_update_labels(t)
	if _close_us > 0 and t >= _close_us and not _closed:
		_closed = true
		finished.emit(_play.kind)
	queue_redraw()


func _update_labels(t: int) -> void:
	var show_result := false
	match _play.phase:
		FieldPlay.Phase.INTRO:
			_hint.text = "타자가 친다… 준비!"
		FieldPlay.Phase.CATCH:
			_hint.text = "원이 점 안에 들어올 때 탭!"
		FieldPlay.Phase.THROW:
			_hint.text = ("금색에서 떼!" if _play.charging() else "1루로! 꾹 눌러 힘을 모으고 금색에서 떼기") if _play.aiming(t) else "공을 빼서…"
		FieldPlay.Phase.DONE:
			_hint.text = ""
			show_result = _cu_called if _cu else true
	_result.text = FieldPlay.kind_label(_play.kind) if show_result else ""
	var colors := {FieldPlay.Kind.BONUS: Tokens.WARN, FieldPlay.Kind.NORMAL: Tokens.GOOD}
	_result.add_theme_color_override("font_color", colors.get(_play.kind, Tokens.BAD))


# ---------- 1루 클로즈업 ----------

func _start_closeup() -> void:
	_cu = true
	_cu_s = _play.since_hit(_play.thrown_us)
	_cu_last_us = _now_us()


## 판정이 나는 플레이 시각 (맞은 뒤 초): 공·주자 가운데 늦은 쪽 + 루심이 보고 외치는 시간. 악송구는 공이 빠진 뒤
func _call_s() -> float:
	if _play.kind == FieldPlay.Kind.WILD:
		return _play.arrive_s + 0.3
	return maxf(_play.arrive_s, _play.runner_s) + CU_CALL_DELAY_S


## 클로즈업 시계를 실제 시간만큼(접전이면 도착 직전부터 느리게) 흘리고, 공·주자·판정 순간에 소리를 낸다
func _step_closeup(t: int) -> void:
	var dt := float(t - _cu_last_us) / 1e6
	_cu_last_us = t
	var rate := 1.0
	var close: bool = _play.kind != FieldPlay.Kind.WILD and absf(_play.margin()) < float(_fc["closeupSlowWithinSec"])
	if close and _cu_s >= minf(_play.arrive_s, _play.runner_s) - 0.3 and _cu_s <= _call_s():
		rate = float(_fc["closeupSlowMo"])
	_cu_s += dt * rate
	if not _cu_ball_done and _cu_s >= _play.arrive_s:
		_cu_ball_done = true
		if _play.kind != FieldPlay.Kind.WILD:
			sound_requested.emit(SoundSynth.Sfx.MITT, 1.0, 0.85)
	if not _cu_runner_done and _cu_s >= _play.runner_s:
		_cu_runner_done = true
		sound_requested.emit(SoundSynth.Sfx.STEP, 0.9, 0.9)
	if not _cu_called and _cu_s >= _call_s():
		_cu_called = true
		if _play.is_out():
			sound_requested.emit(SoundSynth.Sfx.CHEER_SMALL, 0.9 if _play.kind == FieldPlay.Kind.BONUS else 0.6, 1.0)
			_shake_until_us = t + 160000
		_finish_later(float(_fc["resultHoldSec"]))


## 클로즈업 한 칸 크기 (배경 높이를 화면 높이에 맞춘 정수배)와 배경 왼쪽 위
func _cu_unit() -> float:
	return maxf(1.0, round(size.y / PixelMeta.CLOSEUP_BG.y))


func _cu_origin() -> Vector2:
	var u := _cu_unit()
	return Vector2(round((size.x - PixelMeta.CLOSEUP_BG.x * u) * 0.5), size.y - PixelMeta.CLOSEUP_BG.y * u)


## 타자 주자가 1루에서 얼마나 떨어져 있나 (m, 1루를 지나면 음수 — 달려 나가다 멈춘다. 악송구면 계속 달린다)
func _runner_to_bag(s: float) -> float:
	var start := _phys.batter_start_s
	var total := _phys.base_distance
	if s <= start:
		return total
	var d := total * (1.0 - (s - start) / (_play.runner_s - start))
	if d >= 0.0 or _play.kind == FieldPlay.Kind.WILD:
		return d
	# 1루를 지나면 속도를 줄이며 CU_OVERRUN_M 까지 달려 나가 선다 (그 두 배 거리를 달릴 시간 동안)
	var k := minf(1.0, -d / (CU_OVERRUN_M * 2.0))
	return -CU_OVERRUN_M * (1.0 - pow(1.0 - k, 2.0))


func _draw_closeup() -> void:
	var u := _cu_unit()
	var o := _cu_origin()
	var bg := PixelArt.texture("closeup_bg")
	var tex := PixelArt.texture("closeup")
	if bg != null:
		draw_texture_rect(bg, Rect2(o, PixelMeta.CLOSEUP_BG * u), false)
	if tex == null:
		return
	var s := _cu_s
	var arrive := _play.arrive_s
	var wild := _play.kind == FieldPlay.Kind.WILD
	# 루심 (가장 뒤)
	var ump := "ump_set"
	if _cu_called and not wild:
		ump = "ump_out" if _play.is_out() else "ump_safe"
	_cu_cell(tex, ump, PixelMeta.CLOSEUP_UMP_FOOT, false)
	# 타자 주자 (1루수보다 뒤 — 1루 선 위를 오른쪽에서 왼쪽으로). 달리기 4칸 → 베이스 1m 앞에서 앞다리를 뻗어 터치 → 딛고 넘어감 → 멈춤.
	# 그림의 발 자리를 앞발 끝만큼 오른쪽으로 옮겨 그려서, 1루에 닿는 시각(판정)에 뻗은 발끝이 베이스 위에 온다
	var to_bag := _runner_to_bag(s)
	var rx := PixelMeta.CLOSEUP_BAG.x - PixelMeta.CLOSEUP_TOUCH_FOOT.x + to_bag * CU_PX_PER_M
	var run: String
	if to_bag <= -CU_OVERRUN_M + 0.01:
		run = "run_b"
	elif to_bag <= CU_TOUCH_FROM_M and to_bag > -0.35 and not wild:
		run = "run_touch"
	elif to_bag <= -0.35 and to_bag > CU_PASS_TO_M and not wild:
		run = "run_pass"
	else:
		run = ["run_a", "run_b", "run_c", "run_d"][int(floor((_phys.base_distance - to_bag) / CU_STEP_M)) % 4]
	_cu_cell(tex, run, Vector2(rx, PixelMeta.CLOSEUP_RUNNER_Y), false)
	# 1루수 (정면, 가장 앞): 기다림 → 뻗기 시작 → 쭉 뻗기 → 잡음 (악송구면 뛰어오른다)
	var fb := "fb_ready"
	if wild:
		if s >= arrive - 0.3 and s < arrive + 0.25:
			fb = "fb_jump"
	elif s >= arrive:
		fb = "fb_catch"
	elif s >= arrive - CU_REACH_S * 0.45:
		fb = "fb_stretch"
	elif s >= arrive - CU_REACH_S:
		fb = "fb_reach"
	_cu_cell(tex, fb, PixelMeta.CLOSEUP_FB_FOOT, false)
	# 공: 카메라 뒤(유격수 손)에서 화면 아래로 들어와 작아지며 글러브로. 악송구는 뛰어오른 글러브 위로 빠져 관중석 쪽으로 멀어진다
	var from := PixelMeta.CLOSEUP_BAG + Vector2(26, 46)
	var glove := PixelMeta.CLOSEUP_FB_FOOT + (PixelMeta.CLOSEUP_GLOVE_JUMP + Vector2(0, -6) if wild else PixelMeta.CLOSEUP_GLOVE)
	var t0 := _play.since_hit(_play.thrown_us)
	var k := (s - t0) / maxf(arrive - t0, 0.01)
	if k < 1.0 or wild:
		var p: Vector2
		if k <= 1.0:
			p = from.lerp(glove, k) + Vector2(0, -sin(PI * k) * 8.0)
		else:
			p = glove + (glove - from).normalized() * (k - 1.0) * (arrive - t0) * 40.0
		# 멀어질수록 작게 (카메라 바로 앞 4.5칸 → 글러브 1.6칸 → 빠지면 더 작게)
		var r := (lerpf(4.5, 1.6, sqrt(k)) if k <= 1.0 else maxf(0.8, 1.6 - (k - 1.0) * 2.0)) * u
		var sp := o + p * u
		draw_circle(sp + Vector2(0, 2), r, Tokens.SHADOW)
		draw_circle(sp, r, Tokens.CHALK)
		draw_arc(sp, r, 0.0, TAU, 16, Tokens.NAVY, maxf(1.0, u * 0.4), true)


## 클로즈업 칸 하나 (발 자리 feet, 배경 그림 픽셀). flip: 좌우 뒤집기 (오른쪽을 본다)
func _cu_cell(tex: Texture2D, name: String, feet: Vector2, flip: bool) -> void:
	var u := _cu_unit()
	var cell := PixelMeta.CLOSEUP_CELL
	var region := Rect2(Vector2(int(PixelMeta.CLOSEUP[name]) * cell.x, 0), cell)
	var foot := _cu_origin() + feet.round() * u
	if flip:
		# 발 자리를 축으로 좌우를 뒤집는다 (음수 크기 사각형은 Godot 가 왼쪽 끝을 옮겨 버려서 변환으로 뒤집는다)
		draw_set_transform(_shake + foot, 0.0, Vector2(-1, 1))
		draw_texture_rect_region(tex, Rect2(-PixelMeta.CLOSEUP_FEET * u, cell * u), region)
		draw_set_transform(_shake)
	else:
		draw_texture_rect_region(tex, Rect2(foot - PixelMeta.CLOSEUP_FEET * u, cell * u), region)


# ---------- 그리기 ----------

func _draw() -> void:
	if _play == null:
		return
	var t := _now_us()
	var view := size
	_shake = Vector2.ZERO
	if t < _shake_until_us:
		var k := float(_shake_until_us - t) / 220000.0
		_shake = Vector2(sin(t / 9000.0), cos(t / 11000.0)) * float(_fc["catchShakePx"]) * k
	draw_set_transform(_shake)
	if _cu:
		_draw_closeup()
		draw_set_transform(Vector2.ZERO)
		return
	# 송구 단계: 홈 쪽 그림이 오른쪽으로 빠지고 1루 쪽 그림이 왼쪽에서 들어온다 (몸을 왼쪽으로 돌리는 느낌)
	var turn := 0.0
	if _play.caught_us > 0 and _play.kind != FieldPlay.Kind.MISS:
		turn = clampf(float(t - _play.caught_us) / float(_play.aim_us - _play.caught_us), 0.0, 1.0)
		turn = 1.0 - pow(1.0 - turn, 3.0)
	var off: float = round(view.x * turn / _u) * _u
	if turn < 1.0:
		draw_texture_rect(_tex_home, Rect2(Vector2(off, 0), _tex_home.get_size() * _u), false)
		_draw_batter_runner(_cam_home, Vector2(off, 0), t)
	if turn > 0.0:
		var o := Vector2(off - view.x, 0)
		draw_texture_rect(_tex_first, Rect2(o, _tex_first.get_size() * _u), false)
		var bag := _phys.base_pos(1)
		PixelArt.draw_fielder_screen(self, o + _cam_first.to_screen(Vector3(bag.x + 0.6, bag.y + 0.4, 0.0)), true, _u)
		_draw_batter_runner(_cam_first, o, t)
	match _play.phase:
		FieldPlay.Phase.INTRO, FieldPlay.Phase.CATCH:
			_draw_rolling_ball(t, view)
			_draw_catch_marks(t, view)
		FieldPlay.Phase.THROW:
			if turn < 1.0:
				_draw_catch_flash(t, view, Vector2(off, 0))
			if _play.aiming(t):
				_draw_gauge(_play.gauge_at(t), view)
		FieldPlay.Phase.DONE:
			if _play.kind == FieldPlay.Kind.MISS:
				_draw_rolling_ball(t, view)
	draw_set_transform(Vector2.ZERO)


## 타자 주자: 치고 나면 1루로 달린다 (자동 경기와 같은 출발·속도 — 1루를 밟는 시각이 판정과 똑같다)
func _draw_batter_runner(cam: _Cam, o: Vector2, t: int) -> void:
	var s := _play.since_hit(t)
	var k := clampf((s - _phys.batter_start_s) / (_play.runner_s - _phys.batter_start_s), 0.0, 1.0) if s > 0.0 else 0.0
	var p := _phys.base_pos(1) * k if s > 0.0 else Vector2(-0.8, 0.0)
	PixelArt.draw_fielder_screen(self, o + cam.to_screen(Vector3(p.x, p.y, 0.0)), false, maxf(1.0, _u - 1.0))


## 송구 게이지 (화면 오른쪽 세로 막대): 아래부터 약함(회색) · 좋음(초록) · 완벽(금색) · 좋음 · 악송구(빨강).
## 누르고 있으면 아래부터 차오르고(찬 부분은 밝게, 안 찬 칸은 어둡게) 바늘이 따라 오른다
func _draw_gauge(g: float, view: Vector2) -> void:
	var x := view.x - GAUGE_W - Tokens.SPACE_XL
	var y0 := view.y * GAUGE_BOTTOM
	var y1 := view.y * GAUGE_TOP
	var h := y0 - y1
	var at := func(v: float) -> float: return y0 - h * v
	var frame := Rect2(x - 4, y1 - 4, GAUGE_W + 8, h + 8)
	draw_rect(frame, Tokens.GAUGE_BG)
	var lo := _play.perfect_min()
	var hi := _play.perfect_max()
	var zones := [[0.0, float(_fc["gaugeWeakMax"]), Tokens.INK_SOFT], [float(_fc["gaugeWeakMax"]), lo, Tokens.GAUGE_ZONE],
		[lo, hi, Tokens.GAUGE_PERFECT], [hi, float(_fc["gaugeWildMin"]), Tokens.GAUGE_ZONE],
		[float(_fc["gaugeWildMin"]), 1.0, Tokens.BAD]]
	for z: Array in zones:
		var top: float = at.call(z[1])
		var col: Color = z[2]
		draw_rect(Rect2(x, top, GAUGE_W, float(at.call(z[0])) - top), col if g >= float(z[0]) and g > 0.0 else col.darkened(0.45))
	# 바늘: 막대를 가로지르는 선 + 왼쪽 세모
	var ny: float = at.call(g)
	draw_line(Vector2(x - 6, ny), Vector2(x + GAUGE_W + 6, ny), Tokens.CHALK, 4.0)
	draw_colored_polygon(PackedVector2Array([Vector2(x - 8, ny - 8), Vector2(x - 8, ny + 8), Vector2(x - 1, ny)]), Tokens.CHALK)
	var font := Tokens.FONT_BOLD
	draw_string(font, Vector2(x - 14, y1 - 12), "송구", HORIZONTAL_ALIGNMENT_LEFT, -1, Tokens.FONT_CAPTION, Tokens.HUD_TEXT)


## 굴러오는 공 (튀면서 점으로 다가온다. 빠지면 그대로 지나간다)
func _draw_rolling_ball(t: int, _view: Vector2) -> void:
	if t < _play.hit_us:
		return
	var s := _roll(_play.ball_progress(t))
	var g := Vector2.ZERO.lerp(_ball_to, s)
	var z := 0.0
	if s < 1.0:
		z = absf(sin(PI * HOPS * s)) * HOP_M * (1.0 - s * 0.7)
	var shadow := _cam_home.to_screen(Vector3(g.x, g.y, 0.0))
	var at := _cam_home.to_screen(Vector3(g.x, g.y, z + BALL_R))
	var r := maxf(2.0, BALL_R * _cam_home.focal / _cam_home.depth_of(Vector3(g.x, g.y, 0.0)))
	draw_circle(shadow, r * 1.1, Tokens.SHADOW)
	# 꼬리: 조금 전 자리들을 옅게
	for i in range(3, 0, -1):
		var gs := Vector2.ZERO.lerp(_ball_to, _roll(_play.ball_progress(t - 25000 * i)))
		draw_circle(_cam_home.to_screen(Vector3(gs.x, gs.y, BALL_R)), r * (1.0 - 0.18 * i), Color(Tokens.CHALK, 0.22))
	draw_circle(at, r, Tokens.CHALK)
	draw_arc(at, r, 0.0, TAU, 16, Tokens.NAVY, maxf(1.0, r * 0.18), true)


## 시간 진행(0~1) → 굴러온 거리(0~1): 땅볼은 끝으로 갈수록 느려진다. 그래서 원이 점 안에 들어올 때 공도 거의 점에 와 있다.
## 1 을 넘으면(빠진 공) 그 속도 그대로 계속 굴러간다
static func _roll(k: float) -> float:
	if k <= 0.0:
		return 0.0
	if k < 1.0:
		return 1.0 - pow(1.0 - k, ROLL_EASE)
	return minf(1.0 + (k - 1.0) * 0.6, THROUGH_MAX)


## 점과 줄어드는 원 (원이 점 안에 들어오면 초록)
func _draw_catch_marks(t: int, view: Vector2) -> void:
	var c := _play.dot * view.y
	var dot_r := float(_fc["dotRadius"]) * view.y
	var ring := _play.ring_radius(t) * view.y
	var inside := ring <= dot_r and _play.phase == FieldPlay.Phase.CATCH and t >= _play.hit_us
	draw_circle(c, dot_r, Color(Tokens.WARN, 0.35))
	draw_arc(c, dot_r, 0.0, TAU, 40, Tokens.WARN, 2.0, true)
	draw_circle(c, maxf(3.0, dot_r * 0.22), Tokens.WARN)
	if t >= _play.hit_us and ring > 0.0:
		var col := Tokens.GOOD if inside else Tokens.CHALK
		draw_arc(c, ring, 0.0, TAU, 56, Color(Tokens.NAVY, 0.5), 6.0, true)
		draw_arc(c, ring, 0.0, TAU, 56, col, 3.5, true)


## 잡는 순간 번쩍임 (점 자리에 퍼지는 고리)
func _draw_catch_flash(t: int, view: Vector2, o: Vector2) -> void:
	var k := float(t - _play.caught_us) / (float(_fc["catchFlashMs"]) * 1000.0)
	if k >= 1.0:
		return
	var c := _play.dot * view.y + o
	var r := float(_fc["dotRadius"]) * view.y * (1.0 + k * 1.6)
	draw_circle(c, r, Color(Tokens.CHALK, 0.6 * (1.0 - k)))
	draw_arc(c, r, 0.0, TAU, 40, Color(Tokens.WARN, 1.0 - k), 4.0, true)


# ---------- 그라운드 도트 ----------

## 그라운드 그림: 칸마다 카메라를 거꾸로 계산해 땅의 어디인지 찾고, 중계 화면과 같은 땅 색을 칠한다.
## 칸 열마다 아래에서 위로 올라가다 땅이 경기장 밖(홈 뒤 그물·파울 지역 끝·담장 너머)에 닿으면 거기가 담장 밑동 →
## 담장(BACKSTOP_H) → 관중석(STANDS_ROWS 칸) → 하늘
func _ground_image(cam: _Cam, view: Vector2) -> Image:
	var w := int(ceil(view.x / _u))
	var h := int(ceil(view.y / _u))
	var img := Image.create(w, h, false, Image.FORMAT_RGBA8)
	var d := _phys.base_distance / sqrt(2.0)
	var dirt2 := Tokens.DIRT.darkened(0.1)
	for x in w:
		var wall_base := -1.0
		var wall_top := -1.0
		for y in range(h - 1, -1, -1):
			var sy := float(y) + 0.5
			var col: Color
			if wall_base < 0.0:
				var p := cam.ground_at(Vector2(x + 0.5, sy) * _u)
				if p == Vector2.INF or _outside(p):
					wall_base = float(y + 1)
					var foot := p if p != Vector2.INF else Vector2.ZERO
					var top := cam.to_screen(Vector3(foot.x, foot.y, BACKSTOP_H)).y / _u if p != Vector2.INF else wall_base - 2.0
					wall_top = minf(top, wall_base - 2.0)
				else:
					var m_per_px := _u * cam.depth_of(Vector3(p.x, p.y, 0.0)) / cam.focal
					col = PixelArt._ground_color(p, _phys, d, Tokens.GRASS, Tokens.GRASS_LIGHT, Tokens.FOUL_GRASS, Tokens.DIRT, dirt2, m_per_px)
			if wall_base >= 0.0:
				if sy >= wall_top:
					col = Tokens.WALL if sy > wall_top + 1.0 else Tokens.WARN  # 담장 꼭대기 노란 선
				elif sy >= wall_top - STANDS_ROWS:
					col = PixelArt._stands_color(x, y, wall_top)
				elif sy >= wall_top - STANDS_ROWS - 2.0:
					col = Tokens.STANDS_DARK  # 지붕 끝
				else:
					col = Tokens.SKY
			img.set_pixel(x, y, col)
	return img


## 경기장 밖인가: 홈 뒤 그물 너머, 파울 지역 끝(파울 라인에서 FOUL_DEPTH_M 넘게), 외야 담장 너머
func _outside(p: Vector2) -> bool:
	if p.y < -BACKSTOP_M:
		return true
	var deg := rad_to_deg(atan2(p.x, p.y))
	var r := p.length()
	if absf(deg) > 45.0:
		return r * sin(deg_to_rad(absf(deg) - 45.0)) > FOUL_DEPTH_M
	return r > _phys.fence_at(deg)


## 지금 로직 (스크린샷·테스트 스크립트용)
func play() -> FieldPlay:
	return _play
