class_name Rundown
extends RefCounted
## 런다운(협살): 송구가 주자보다 한참 먼저 베이스에 닿아 주자가 두 베이스 사이에 갇혔을 때.
##
## 두 베이스를 잇는 선 위의 추격으로 푼다 (PlaySimulator 가 고정 간격으로 step() 을 부른다):
## - 주자는 공을 가진 수비수 반대쪽 베이스로 도망친다. 방향을 바꾸려면 반응(reactS) 뒤 감속(reverseDecelMps2)·가속이 든다
## - 공을 가진 수비수는 주자에게 달려든다. 주자가 반대편 수비수 가까이(throwAtM) 몰리면 그에게 던진다 —
##   주자는 공이 손을 떠나는 걸 보고 다시 돌아서야 해서, 받은 수비수가 금방 따라잡는다 (실제 런다운이 대부분 아웃인 이유)
## - 주자(AI)는 공이 움직일 때마다 두 베이스의 여유를 잰다: "공을 쥔 수비수가 그 베이스에서 태그할 수 있는 시각 − 내가 닿는 시각"
##   (방향을 바꾸면 반응·감속이 더 든다). 여유가 큰 쪽으로 뛴다
## - 주자는 한 바퀴 거리를 넘게 달리면 지쳐 느려진다 (RunnerAI.speed_cap) — 왔다 갔다 해도 결국 잡힌다
## - 송구마다 errorChance 로 공을 놓친다 (errorRecoverS 동안 태그 못 함) — 주자가 사는 길
## - 공을 가진 수비수가 tagReachM 안에 들면 태그 아웃, 그 전에 베이스에 닿으면 세이프
## 조작 주자는 갇힌 순간·공이 손을 떠날 때·놓쳤을 때, 그리고 그 사이에도 pollS 마다 어느 쪽으로 뛸지 판단한다
## (decide 콜백 = PlaySimulator._decide, RunControl 이 고른다) — 런다운 내내 베이스 버튼으로 방향을 바꿀 수 있다.
## 사이사이 판단의 AI 값은 "지금 방향 그대로"라서, 아무것도 누르지 않으면 AI 주자와 똑같이 움직인다.
## 노드가 아닌 로직이고 난수는 PlaySimulator 의 시드 난수만 쓴다 (원칙 1·2).

## 주자
var runner: RunnerAI
## 뒤 베이스(돌아갈 곳)·앞 베이스(가려던 곳), 4 = 홈
var back: int
var fwd: int
## 시작·끝 시각, 끝났나, 결과 "out" | "back" | "fwd"
var start_t: float
var end_t := -1.0
var done := false
var outcome := ""
## 송구 횟수, 놓친 횟수
var throws := 0
var errors := 0
## 기록 (화면이 그린다): 첫 기록 시각, 수비수 이름 → 위치 (DT 간격), 공 위치 (DT 간격)
var t0 := -1.0
var tracks := {}
var ball_track := PackedVector3Array()
## 반대편 베이스로 들어간 수비수 {name, from, to, react, ready} (화면이 런다운 전 움직임을 같은 식으로 그린다)
var partner_cover := {}

var _cfg: Dictionary
var _rng: RandomNumberGenerator
## func(runner, ai_go: bool, show: float, since: float) -> bool — 앞 베이스 쪽으로 뛰는가
var _decide: Callable
var _a: Vector2
var _b: Vector2
var _len: float
## 주자: 뒤 베이스에서의 거리 (m), 선을 따라 부호 있는 속도 (+ = 앞 베이스 쪽), 뛰려는 방향
var _s := 0.0
var _v := 0.0
var _dir := -1
## 확정 전인 방향 판단 {t: 확정 시각, ai: AI 라면 앞으로 가나, show: 판단이 생긴 시각}
var _pending := {}
## 수비수 둘 [{name, pos, speed, home: 지키는 베이스 자리, side: 0 = 뒤 베이스 쪽 / 1 = 앞 베이스 쪽}]
var _f: Array = []
## 공을 가진 수비수 번호 (송구 중이면 -1), 날아가는 송구 {from, to, release, arrive, to_i, error}
var _holder := 1
var _throw := {}
var _recover_until := 0.0
## 유저가 조작하는 주자인가 (그러면 사이사이 판단을 연다)
var _controlled := false


## a·b: 뒤·앞 베이스 자리, holder: 앞 베이스에서 공을 받은 수비수 {name, pos}, partner: 뒤 베이스를 맡을 수비수 {name, pos} + 커버 기록
func _init(p_runner: RunnerAI, p_back: int, p_fwd: int, a: Vector2, b: Vector2, holder: Dictionary, partner: Dictionary,
		t: float, cfg: Dictionary, rng: RandomNumberGenerator, decide: Callable, controlled: bool = false) -> void:
	runner = p_runner
	back = p_back
	fwd = p_fwd
	start_t = t
	_cfg = cfg
	_rng = rng
	_decide = decide
	_controlled = controlled
	_a = a
	_b = b
	_len = a.distance_to(b)
	partner_cover = partner.get("cover", {})
	_f = [
		{"name": partner.name, "pos": partner.pos, "speed": 0.0, "home": a, "side": 0},
		{"name": holder.name, "pos": holder.pos, "speed": 0.0, "home": b, "side": 1},
	]
	for f: Dictionary in _f:
		tracks[f.name] = PackedVector2Array()
	_s = clampf(_len - runner.pos.distance_to(b), 0.0, _len)
	_v = runner.speed
	# 공이 앞 베이스로 날아오는 걸 보고 있었다 — 갇힌 순간 바로 돌아선다.
	# 그래도 판단은 하나 연다: 조작 주자는 갇히자마자 베이스 버튼이 뜬다
	_dir = -1
	_new_decision(t, t, b)
	runner.enter_rundown(t)
	runner.target = back
	runner.pos = _line(_s)


## 한 간격 진행
func step(dt: float, t: float) -> void:
	if done:
		return
	if t0 < 0.0:
		t0 = t
	_lock_decision(t)
	_move_runner(dt)
	_move_fielders(dt, t)
	_update_throw(t)
	if _throw.is_empty():
		_maybe_throw(t)
	_record(t)
	# 태그가 먼저, 그다음 베이스 도착
	if _can_tag(t) and (_f[_holder].pos as Vector2).distance_to(runner.pos) <= float(_cfg["tagReachM"]):
		_end("out", t)
	elif _s <= 0.0 and _v <= 0.0:
		_end("back", t)
	elif _s >= _len and _v >= 0.0:
		_end("fwd", t)
	elif t - start_t >= float(_cfg["maxS"]):
		# 너무 길어지면 결국 몰려서 잡힌다 (실제로 끝없이 이어지지 않는다)
		_end("out", t)


## 공이 지금 있는 자리 (끝난 뒤 수비가 다음 송구를 정할 때)
func ball_pos() -> Vector2:
	return _f[maxi(_holder, 0)].pos


# ---------- 주자 ----------

func _move_runner(dt: float) -> void:
	var vmax := runner.speed_cap()
	var want := _dir * vmax
	if _v * _dir < 0.0:
		# 반대로 달리던 중: 멈추고 돌아선다
		_v += _dir * float(_cfg["reverseDecelMps2"]) * dt
	else:
		_v = move_toward(_v, want, float(runner._run["accelMps2"]) * dt)
	var before := _s
	_s = clampf(_s + _v * dt, 0.0, _len)
	runner.run_dist += absf(_s - before)
	runner.speed = absf(_v)
	runner.target = fwd if _dir > 0 else back
	runner.pos = _line(_s)


## 판단 시각이 되면 방향을 확정한다 (조작 주자면 유저 선택)
func _lock_decision(t: float) -> void:
	if not _pending.is_empty() and t >= float(_pending.t):
		var go: bool = _decide.call(runner, bool(_pending.ai), float(_pending.show), start_t)
		_dir = 1 if go else -1
		_pending = {}
	if _controlled and _pending.is_empty():
		# 사이사이 판단: AI 라면 지금 방향 그대로
		_pending = {"t": t + float(_cfg["pollS"]), "ai": _dir > 0, "show": t}


## 공의 움직임을 보고 판단이 생긴다: 반응 시간 뒤 확정.
## ball_t: 공을 쥔 수비수가 태그할 수 있게 되는 시각, ball_at: 그 수비수의 자리
func _new_decision(t: float, ball_t: float, ball_at: Vector2) -> void:
	var ai := _margin(1, t, ball_t, ball_at) > _margin(-1, t, ball_t, ball_at)
	_pending = {"t": t + float(_cfg["reactS"]), "ai": ai, "show": t}


## 그 쪽 베이스(toward +1 = 앞, -1 = 뒤)로 뛰면 얼마나 여유 있게 닿나 (초, 음수면 태그당한다)
func _margin(toward: int, t: float, ball_t: float, ball_at: Vector2) -> float:
	var base := _b if toward > 0 else _a
	var d := absf((_len if toward > 0 else 0.0) - _s)
	var tag_t := ball_t + maxf(0.0, ball_at.distance_to(base) - float(_cfg["tagReachM"])) / float(_cfg["chaseMps"])
	return tag_t - (t + _eta(d, toward))


## 지금 상태에서 그 방향으로 d 미터를 가는 시간: 같은 방향이면 계속 달리고, 반대면 반응 → 멈춤 → 다시 가속
func _eta(d: float, toward: int) -> float:
	var a := float(runner._run["accelMps2"])
	var vmax := runner.speed_cap()
	var v0 := absf(_v)
	var pre := 0.0
	if _v * toward < 0.0:
		var react := float(_cfg["reactS"])
		var decel := float(_cfg["reverseDecelMps2"])
		pre = react + v0 / decel
		d += v0 * react + v0 * v0 / (2.0 * decel)
		v0 = 0.0
	var d_acc := (vmax * vmax - v0 * v0) / (2.0 * a)
	if d <= d_acc:
		return pre + (-v0 + sqrt(v0 * v0 + 2.0 * a * d)) / a
	return pre + (vmax - v0) / a + (d - d_acc) / vmax


# ---------- 수비 ----------

func _move_fielders(dt: float, t: float) -> void:
	for i in _f.size():
		var f: Dictionary = _f[i]
		var goal: Vector2 = f.home
		if i == _holder and _throw.is_empty():
			# 공을 쥐면 주자에게 달려든다 (놓친 공을 줍는 중이면 그 자리)
			goal = f.pos if t < _recover_until else runner.pos
		else:
			# 공이 없으면 내 베이스를 지키다가, 주자가 오면 조금 앞으로 나와 송구 거리를 줄인다
			var come: float = (runner.pos.distance_to(f.home) - float(_cfg["tagReachM"])) * 0.5
			goal = (f.home as Vector2).move_toward(runner.pos, clampf(come, 0.0, float(_cfg["closeInM"])))
		var d := (f.pos as Vector2).distance_to(goal)
		var v_stop := sqrt(2.0 * float(_cfg["fielderAccelMps2"]) * d)
		f.speed = minf(minf(float(f.speed) + float(_cfg["fielderAccelMps2"]) * dt, float(_cfg["chaseMps"])), maxf(v_stop, 0.5))
		f.pos = (f.pos as Vector2).move_toward(goal, float(f.speed) * dt)


## 공을 가진 수비수가 던질 때인가: 주자가 반대편 수비수 쪽으로 달려 가까이 왔고, 그가 받을 자리에 있고,
## 내가 직접 태그하기엔 멀다
func _maybe_throw(t: float) -> void:
	if _holder < 0 or not _can_tag(t):
		return
	var other := 1 - _holder
	var o: Dictionary = _f[other]
	var toward := 1 if int(o.side) == 1 else -1
	if _v * toward <= 0.5:
		return
	if (o.pos as Vector2).distance_to(o.home) > float(_cfg["readyM"]):
		return
	var gap := (o.pos as Vector2).distance_to(runner.pos)
	var mine := (_f[_holder].pos as Vector2).distance_to(runner.pos)
	if gap > float(_cfg["throwAtM"]) or mine <= float(_cfg["tagReachM"]) + 1.0:
		return
	var from: Vector2 = _f[_holder].pos
	var flight := from.distance_to(o.pos) / float(_cfg["throwMps"]) + float(_cfg["catchS"])
	_throw = {"from": from, "release": t, "arrive": t + flight, "to_i": other, "error": _rng.randf() < float(_cfg["errorChance"])}
	_holder = -1
	throws += 1
	# 주자는 공이 손을 떠나는 걸 보고 다시 판단한다
	_new_decision(t, float(_throw.arrive), o.pos)


func _update_throw(t: float) -> void:
	if _throw.is_empty() or t < float(_throw.arrive):
		return
	_holder = int(_throw.to_i)
	if bool(_throw.error):
		# 공을 놓쳤다: 줍는 동안 태그 못 한다. 주자는 그걸 보고 다시 판단한다
		errors += 1
		_recover_until = t + float(_cfg["errorRecoverS"])
		_new_decision(t, _recover_until, _f[_holder].pos)
	else:
		_recover_until = 0.0
	_throw = {}


## 공을 쥐고 태그할 수 있나 (송구 중이거나 놓친 공을 줍는 중이 아니면)
func _can_tag(t: float) -> bool:
	return _holder >= 0 and t >= _recover_until


# ---------- 기록·끝 ----------

func _record(t: float) -> void:
	for f: Dictionary in _f:
		# Packed 배열은 값으로 복사되므로 꺼내서 붙이고 다시 넣는다
		var tr: PackedVector2Array = tracks[f.name]
		tr.append(f.pos)
		tracks[f.name] = tr
	var ball: Vector3
	if not _throw.is_empty():
		var to: Vector2 = _f[int(_throw.to_i)].pos
		var k := clampf((t - float(_throw.release)) / maxf(float(_throw.arrive) - float(_throw.release), 0.01), 0.0, 1.0)
		var p := (_throw.from as Vector2).lerp(to, k)
		ball = Vector3(p.x, p.y, 1.4 + sin(k * PI) * 0.8)
	else:
		var p: Vector2 = _f[_holder].pos
		ball = Vector3(p.x, p.y, 0.15 if t < _recover_until else 1.1)
	ball_track.append(ball)


func _end(result: String, t: float) -> void:
	done = true
	outcome = result
	end_t = t
	_throw = {}
	if _holder < 0:
		_holder = 0
	if result == "back":
		runner.pos = _a
	elif result == "fwd":
		runner.pos = _b


func _line(s: float) -> Vector2:
	return _a.lerp(_b, s / maxf(_len, 0.01))


## 화면·테스트용 기록 (PlayResult.rundowns 에 들어간다)
func to_record(slow: bool) -> Dictionary:
	return {"runner": runner.id, "back": back, "fwd": fwd, "start": start_t, "t0": t0, "end": end_t,
		"outcome": outcome, "throws": throws, "errors": errors, "tracks": tracks, "ball": ball_track,
		"partner_cover": partner_cover, "slow": slow}
