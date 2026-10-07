class_name RunnerAI
extends RefCounted
## 주자 한 명의 주루 AI (유한 상태 머신).
##
## 노드가 아니라 RefCounted 로직이다 (CLAUDE.md 원칙 1·2·4): PlaySimulator 가 고정 간격(DT)으로 step() 을 부르고,
## 타구·포구 같은 사건은 PlaySimulator 의 시그널을 구독해 받는다. 화면은 track(위치 기록)을 그리기만 한다.
##
## 좌표는 BattedBallSim 과 같다: 홈 (0,0), x 는 1루 쪽 +, y 는 가운데 쪽 + (m). 베이스 번호 0 = 홈, 1·2·3 = 1·2·3루, 4 = 홈(득점).

enum State {
	IDLE,       ## 베이스에서 대기 (리드한 자리 포함)
	RUN,        ## 진루 중
	RETURNING,  ## 원래 베이스로 귀루 중
	HALFWAY,    ## 뜬공에 다음 베이스 쪽 일부 지점까지 가서 대기
	TAG_UP,     ## 3루 주자: 뜬공이 잡히길 베이스에서 기다렸다가 홈으로
	SLIDING,    ## 접전이라 베이스 앞에서 슬라이딩
	SAFE,       ## 베이스에 살아서 멈춤 (플레이 끝까지 그 자리)
	OUT,        ## 아웃 (필드에서 빠진다)
	SCORED,     ## 득점
}

signal state_changed(runner: RunnerAI, from: State, to: State)
signal base_reached(runner: RunnerAI, base: int)

## 타순 번호 (박스스코어용)
var id: int
## 플레이 시작 때 있던 베이스 (0 = 타자)
var origin: int
## 마지막으로 밟은 베이스
var base: int
## 지금 향하는 베이스 (멈출 곳)
var target: int
var state := State.IDLE
## 밀려나는 주자인가 (뒤가 다 차 있었다)
var forced := false
## 위치·속도
var pos := Vector2.ZERO
var speed := 0.0
var top_speed: float
## 위치 기록 (DT 간격) — 중계 화면이 그대로 그린다
var track := PackedVector2Array()
## 상태 변화 기록 [[시각, 상태], ...] (테스트·디버그)
var history: Array = []
## 이번 경로 (Curve2D) 와 경로 위 진행 거리
var curve: Curve2D = null
var offset := 0.0
## 경로 끝에서 멈추는가 (아니면 지나쳐 달린다: 1루 오버런)
var stop_at_end := true
## 뜬공 대기 지점 (HALFWAY)
var halfway_point := Vector2.ZERO
## 이 시각부터 움직인다 (반응·태그업 출발 시각)
var move_after := 0.0
## 타자: 1루를 지나쳐 달리는가 (내야 땅볼). 외야로 간 공이면 1루를 둥글게 돌며 2루를 노린다
var overrun_first := true

var _phys: BattedBallSim.Config
var _run: Dictionary


func _init(p_id: int, p_origin: int, speed_rating: int, phys: BattedBallSim.Config) -> void:
	id = p_id
	origin = p_origin
	base = p_origin
	target = p_origin
	_phys = phys
	_run = phys.running
	top_speed = lerpf(float(_run["topSpeedAt0"]), float(_run["topSpeedAt100"]), clampi(speed_rating, 0, 100) / 100.0)
	pos = base_pos(p_origin)
	if p_origin > 0:
		# 루상 주자는 리드한 자리에서 시작한다
		pos = pos.move_toward(base_pos(p_origin + 1), float(_run["leadOffM"]))


func is_batter() -> bool:
	return origin == 0


func is_done() -> bool:
	return state == State.SAFE or state == State.OUT or state == State.SCORED


func is_on_field() -> bool:
	return state != State.OUT and state != State.SCORED


# ---------- 베이스·경로 ----------

func base_pos(b: int) -> Vector2:
	if b <= 0 or b >= 4:
		return Vector2.ZERO
	return _phys.base_pos(b)


## from 에서 to 베이스까지의 주로. 중간 베이스는 바깥으로 둥글게 돌며 밟는다 (라운딩).
## overrun: 끝 베이스를 지나쳐 달리는 거리 (1루 오버런)
func build_route(from_pos: Vector2, from_base: int, to_base: int, overrun: float = 0.0) -> Curve2D:
	var c := Curve2D.new()
	c.bake_interval = 0.5
	c.add_point(from_pos)
	var bulge: float = _run["roundingBulgeM"]
	var round_start: float = _run["roundingStartM"]
	var center := Vector2(0, _phys.base_distance / sqrt(2.0))  # 다이아몬드 가운데
	for b in range(from_base + 1, to_base):
		var bp := base_pos(b)
		var dir_in := (bp - base_pos(b - 1)).normalized()
		var dir_out := (base_pos(b + 1) - bp).normalized()
		var outward := (bp - center).normalized()
		# 베이스 roundingStartM 앞에서 바깥으로 벌렸다가 베이스를 밟고 다음 베이스로 꺾는다.
		# 이미 그 지점을 지나 베이스 가까이 왔으면(판단이 늦었으면) 벌리지 않고 바로 베이스로
		if from_pos.distance_to(bp) > round_start + 1.0:
			var wide := bp - dir_in * round_start + outward * bulge
			c.add_point(wide, -dir_in * 2.0, dir_in * 2.0)
		var tangent := (dir_in + dir_out).normalized()
		c.add_point(bp, -tangent * 3.0, tangent * 3.0)
	var end := base_pos(to_base)
	if overrun > 0.0 and to_base < 4:
		var dir := (end - base_pos(to_base - 1)).normalized()
		c.add_point(end)
		c.add_point(end + dir * overrun)
	else:
		c.add_point(end)
	return c


## 경로를 정하고 달리기 시작 (지금 자리에서, 마지막으로 밟은 베이스 다음부터 to 베이스까지)
func run_to(to_base: int, t: float, overrun: float = 0.0) -> void:
	if move_after > t + 5.0:
		move_after = t  # 태그업 대기 중이었다면 지금 출발
	curve = build_route(pos, base, to_base, overrun)
	offset = 0.0
	target = to_base
	stop_at_end = overrun <= 0.0
	_set_state(State.RUN, t)


## 원래 베이스로 귀루
func return_to_base(t: float) -> void:
	curve = Curve2D.new()
	curve.bake_interval = 0.5
	curve.add_point(pos)
	curve.add_point(base_pos(base))
	offset = 0.0
	target = base
	stop_at_end = true
	move_after = t + float(_run["returnReactionS"])
	_set_state(State.RETURNING, t)


## 남은 경로 거리
func remaining() -> float:
	if curve == null:
		return 0.0
	return maxf(0.0, curve.get_baked_length() - offset)


## 지금 속도로 target 베이스에 닿는 데 걸리는 시간 (가속·감속 근사)
func eta_to_target() -> float:
	return _eta(remaining(), speed)


## 멈춰 있다가 출발해 d 미터를 달리는 데 걸리는 시간 (태그업 판단용)
func eta_from_rest(d: float) -> float:
	return _eta(d, 0.0)


## 지금 위치에서 b 베이스까지 (계속 달린다고 할 때) 걸리는 시간 — 송구 예측과 비교하는 값
func eta_to_base(b: int) -> float:
	var d := remaining()
	for k in range(target + 1, b + 1):
		d += base_pos(k - 1).distance_to(base_pos(k))
	return _eta(d, speed) + maxi(b - target, 0) * float(_phys.round_base_s) * 0.5


func _eta(d: float, v0: float) -> float:
	var a: float = _run["accelMps2"]
	var vmax := top_speed
	if v0 >= vmax:
		return d / vmax
	var d_acc := (vmax * vmax - v0 * v0) / (2.0 * a)
	if d <= d_acc:
		return (-v0 + sqrt(v0 * v0 + 2.0 * a * d)) / a
	return (vmax - v0) / a + (d - d_acc) / vmax


# ---------- 사건 (PlaySimulator 시그널) ----------

## 타구 발생 (PlaySimulator.ball_hit 시그널에 is_force_play 를 묶어 구독한다).
## ball_type: SwingJudge.BattedBall, hit_pos: 공을 잡을(주울) 예상 지점, out_count: 플레이 전 아웃, is_force_play: 밀려나는가
func on_ball_hit(ball_type: SwingJudge.BattedBall, hit_pos: Vector2, out_count: int, t: float, is_force_play: bool) -> void:
	forced = is_force_play
	if is_batter():
		# 타자는 무조건 1루로 (오버런은 안타 판단 뒤 정한다)
		move_after = t + float(_phys.batter_start_s) * 0.5
		run_to(1, t, float(_run["overrunM"]) if overrun_first else 0.0)
		return
	# 루상 주자는 2차 리드로 이미 움직이는 중 — 출발 속도가 0 이 아니다
	speed = float(_run["secondaryLeadSpeedMps"])
	if out_count >= 2:
		# 2사: 타구 종류와 상관없이 맞는 순간 질주
		run_to(base + 1, t)
		return
	match ball_type:
		SwingJudge.BattedBall.GROUND:
			if is_force_play:
				run_to(base + 1, t)
			else:
				# 태그 플레이: 타구가 내 진행 방향 정면이면 멈추고, 반대쪽이면 뛴다
				var to_ball := (hit_pos - pos).normalized()
				var to_next := (base_pos(base + 1) - pos).normalized() if base < 3 else (Vector2.ZERO - pos).normalized()
				if to_ball.dot(to_next) > float(_run["groundHoldDot"]):
					# 정면 타구: 베이스로 돌아가 멈춘다 (IDLE 과 같다 — 리드한 자리에서 베이스로)
					return_to_base(t)
					move_after = t
				else:
					run_to(base + 1, t)
		_:
			# 3루 주자는 태그업 준비. 내야 뜬공·라이너는 베이스로 돌아가 붙는다 (잡히면 송구가 금방 온다).
			# 외야 뜬공은 하프웨이, 외야 라이너는 리드한 자리에서 멈춘다(freeze) — 빠지는 게 보이면 그때 뛴다
			if base == 3:
				_back_to_bag_for_tag(t)
			elif hit_pos.length() < float(_run["infieldDepthM"]):
				return_to_base(t)
			else:
				var nb := base_pos(base + 1) if base < 3 else Vector2.ZERO
				var share := 0.0 if ball_type == SwingJudge.BattedBall.LINE else halfway_share(hit_pos)
				halfway_point = pos if share * _phys.base_distance <= pos.distance_to(base_pos(base)) else base_pos(base).lerp(nb, share)
				curve = Curve2D.new()
				curve.bake_interval = 0.5
				curve.add_point(pos)
				curve.add_point(halfway_point)
				offset = 0.0
				target = base
				stop_at_end = true
				_set_state(State.HALFWAY, t)


## 하프웨이로 갈 거리 (베이스 간 거리에 대한 비율).
## "잡히면 송구가 내 베이스에 오기 전에 돌아올 수 있는 만큼"만 나간다:
## 송구 시간 = 포구 후 준비 + 포구 지점→내 베이스 송구(바로 또는 중계), 귀루 시간 = 반응 + √(2d/가속)
func halfway_share(catch_pos: Vector2) -> float:
	var throw_s := _phys.transfer_s * 0.5 + BattedBallSim.outfield_to_base_s(_phys, catch_pos.distance_to(base_pos(base)))
	var spare := maxf(0.0, throw_s - float(_run["returnReactionS"]) - float(_run["halfwaySafetyS"]))
	var d := 0.5 * float(_run["accelMps2"]) * spare * spare
	return clampf(d / _phys.base_distance, 0.0, float(_run["halfwayMaxShare"]))


## 뜬공이 잡혔다: 하프웨이 주자는 귀루, 진루하던 주자(2사)는 의미 없음, 태그업 주자는 PlaySimulator 가 출발을 정한다
func on_ball_caught(t: float) -> void:
	match state:
		State.HALFWAY, State.RUN, State.SLIDING:
			if not is_batter():
				return_to_base(t)
		State.TAG_UP:
			move_after = t  # 잡힌 순간부터 뛸 수 있다 (리터치)


## 공이 떨어졌거나(뜬공 안타) 내야를 빠져나갔다(땅볼 안타): 멈춰 있던 주자도 다음 베이스로
func on_ball_landed(t: float) -> void:
	if is_batter() or not is_on_field():
		return
	match state:
		State.HALFWAY, State.TAG_UP, State.IDLE, State.RETURNING, State.SAFE:
			run_to(base + 1, t)


func _back_to_bag_for_tag(t: float) -> void:
	curve = Curve2D.new()
	curve.bake_interval = 0.5
	curve.add_point(pos)
	curve.add_point(base_pos(base))
	offset = 0.0
	target = base
	stop_at_end = true
	move_after = 1e9  # 잡히기 전까지는 베이스에 붙어 있는다
	_set_state(State.TAG_UP, t)


# ---------- 매 간격 ----------

## 한 간격 진행: 경로를 따라 가속·감속하며 움직인다. 슬라이딩은 PlaySimulator 가 접전이면 켠다
func step(dt: float, t: float) -> void:
	if is_done() or state == State.OUT:
		track.append(pos)
		return
	var moving := (state == State.RUN or state == State.RETURNING or state == State.SLIDING or state == State.HALFWAY
		or (state == State.TAG_UP and t >= move_after))
	# 길이 0 경로(라이너에 제자리 멈춤 등)는 움직일 곳이 없다
	if not moving or curve == null or t < move_after or curve.get_baked_length() < 0.01:
		speed = 0.0
		track.append(pos)
		return
	var a: float = _run["accelMps2"]
	var vmax := top_speed
	# 라운딩 구간(경로 중간 베이스 근처)에서는 조금 느려진다
	if curve.point_count > 3 and remaining() > float(_run["roundingStartM"]):
		vmax *= float(_run["roundingSpeedFactor"]) if _near_corner() else 1.0
	var rem := remaining()
	if state == State.SLIDING:
		speed = maxf(2.0, speed - float(_run["slideDecelMps2"]) * dt)
	elif stop_at_end and state != State.RETURNING:
		# 멈출 곳 앞에서 부드럽게 줄인다: v ≤ √(2·감속·남은 거리). 귀루는 줄이지 않고 베이스로 몸을 던진다
		var v_stop := sqrt(2.0 * float(_run["decelMps2"]) * rem)
		speed = minf(minf(speed + a * dt, vmax), maxf(v_stop, 1.0))
	else:
		speed = minf(speed + a * dt, vmax)
	offset = minf(offset + speed * dt, curve.get_baked_length())
	pos = curve.sample_baked(offset)
	track.append(pos)


func _near_corner() -> bool:
	for i in range(1, curve.point_count - 1):
		if pos.distance_to(curve.get_point_position(i)) < float(_run["roundingStartM"]):
			return true
	return false


func arrived() -> bool:
	return curve != null and remaining() <= 0.05


func slide(t: float) -> void:
	if state == State.RUN:
		_set_state(State.SLIDING, t)


func set_safe(t: float) -> void:
	base = target
	speed = 0.0
	_set_state(State.SAFE, t)
	base_reached.emit(self, base)


func set_scored(t: float) -> void:
	base = 4
	_set_state(State.SCORED, t)
	base_reached.emit(self, 4)


func set_out(t: float) -> void:
	_set_state(State.OUT, t)


## 지나간 베이스 갱신 (경로 위에서 베이스를 밟았으면)
func touch_passed_bases() -> void:
	if curve == null or state == State.RETURNING:
		return
	# 목적지 베이스는 실제로 닿아야(arrived) 밟은 것이다. 지나쳐 달리는 1루(오버런)만 지나가며 밟는다
	var last := target if not stop_at_end else target - 1
	for b in range(base + 1, last + 1):
		if b >= 4:
			continue
		if pos.distance_to(base_pos(b)) < 1.0:
			base = b


func _set_state(s: State, t: float) -> void:
	if s == state:
		return
	var old := state
	state = s
	history.append([t, s])
	state_changed.emit(self, old, s)
