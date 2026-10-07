class_name BattedBallSim
extends RefCounted
## 타구 물리 + 수비 판정. 결과(안타·아웃·장타·홈런)는 확률표가 아니라 이 계산에서 나온다.
## 타구 중계 화면도 같은 계산 결과(경로·수비수 이동)를 그대로 그린다 — 화면과 결과가 어긋날 수 없다.
##
## 좌표: 홈플레이트가 원점, x 는 1루 쪽 +, y 는 가운데 방향 +, z 는 위 (단위 m).
## 방향 각도: 0 = 가운데, 음수 = 3루 쪽(왼쪽), 양수 = 1루 쪽(오른쪽).
##
## 순서
## 1. 공중 궤적: 타구 속도·발사각·방향으로 출발, 중력 + 공기 저항(속도 제곱) + 역회전 양력으로 적분
##    펜스를 펜스 높이보다 높게 넘으면 홈런, 낮게 닿으면 펜스 맞고 떨어짐
## 2. 공중 포구: 공이 잡을 수 있는 높이 아래로 내려온 순간, 반응 시간 뒤 달려온 수비수가 거기 있으면 아웃
## 3. 땅볼·빠진 공: 튀어서 굴러가는 공을 가장 먼저 막는 수비수가 줍는다
## 4. 내야수가 주우면 1루 송구 vs 타자 주루, 외야수가 주우면 2·3루 송구 vs 타자 주루로 단타·2루타·3루타

const DT := 0.02
const MAX_TIME := 14.0


## 물리·수비 수치 (balance.json batting.battedBall + fielding)
class Config:
	var contact_height: float
	var gravity: float
	var drag_k: float
	var lift_k: float
	var bounce_retention: float
	var ground_decel: float
	var positions := {}  # 수비 위치 이름 -> Vector2
	var infield := PackedStringArray()
	var reaction_infield: float
	var reaction_outfield: float
	var reaction_by_position := {}  # 위치별 반응 시간 (투수·포수 등)
	var accel_infield: float
	var accel_outfield: float
	var max_speed_infield: float
	var max_speed_outfield: float
	var reach_height: float
	var max_launch: float
	var catch_radius: float
	var transfer_s: float
	var range_penalty_s: float
	var range_for_penalty: float
	var throw_mps: float
	var outfield_throw_mps: float
	var outfield_strong_m: float
	var long_throw_slow_s_per_m: float
	var relay_min_m: float
	var relay_leg_share: float
	var relay_transfer_s: float
	var relay_prefer_s: float
	var pickup_s: float
	var batter_start_s: float
	var batter_run_at0: float
	var batter_run_at100: float
	var round_base_s: float
	var extra_base_margin_s: float
	var base_distance: float
	var line_fence: float
	var center_fence: float
	var fence_height: float
	## 주루 수치 (balance.json game.running) — 주자 FSM 이 쓴다
	var running: Dictionary

	static func from(balance: Dictionary) -> Config:
		var b: Dictionary = balance["batting"]["battedBall"]
		var f: Dictionary = balance["fielding"]
		var c := Config.new()
		c.contact_height = BattingConfig.num(b, "contactHeightM")
		c.gravity = BattingConfig.num(b, "gravity")
		c.drag_k = BattingConfig.num(b, "dragK")
		c.lift_k = BattingConfig.num(b, "liftK")
		c.bounce_retention = BattingConfig.num(b, "bounceSpeedRetention")
		c.ground_decel = BattingConfig.num(b, "groundDecelMps2")
		c.max_launch = BattingConfig.num(b, "maxLaunchDeg")
		for name: String in f["positions"]:
			var ad: Array = f["positions"][name]
			c.positions[name] = BattedBallSim.polar(float(ad[0]), float(ad[1]))
		c.infield = PackedStringArray(f["infield"])
		c.reaction_infield = BattingConfig.num(f, "reactionS.infield")
		c.reaction_outfield = BattingConfig.num(f, "reactionS.outfield")
		for key: String in f["reactionS"]:
			if not key.begins_with("_") and key != "infield" and key != "outfield":
				c.reaction_by_position[key] = BattingConfig.num(f, "reactionS." + key)
		c.accel_infield = BattingConfig.num(f, "accelMps2.infield")
		c.accel_outfield = BattingConfig.num(f, "accelMps2.outfield")
		c.max_speed_infield = BattingConfig.num(f, "maxSpeedMps.infield")
		c.max_speed_outfield = BattingConfig.num(f, "maxSpeedMps.outfield")
		c.reach_height = BattingConfig.num(f, "reachHeightM")
		c.catch_radius = BattingConfig.num(f, "catchRadiusM")
		c.transfer_s = BattingConfig.num(f, "transferS")
		c.range_penalty_s = BattingConfig.num(f, "rangeTransferPenaltyS")
		c.range_for_penalty = BattingConfig.num(f, "rangeForPenaltyM")
		c.throw_mps = BattingConfig.num(f, "throwMps")
		c.outfield_throw_mps = BattingConfig.num(f, "outfieldThrowMps")
		c.outfield_strong_m = BattingConfig.num(f, "outfieldStrongRangeM")
		c.long_throw_slow_s_per_m = BattingConfig.num(f, "longThrowSlowSPerM")
		c.relay_min_m = BattingConfig.num(f, "relay.minDistanceM")
		c.relay_leg_share = BattingConfig.num(f, "relay.legShare")
		c.relay_transfer_s = BattingConfig.num(f, "relay.transferS")
		c.relay_prefer_s = BattingConfig.num(f, "relay.preferS")
		c.pickup_s = BattingConfig.num(f, "pickupS")
		c.batter_start_s = BattingConfig.num(f, "batterStartS")
		c.batter_run_at0 = BattingConfig.num(f, "batterRunMpsAt0")
		c.batter_run_at100 = BattingConfig.num(f, "batterRunMpsAt100")
		c.round_base_s = BattingConfig.num(f, "roundBaseS")
		c.extra_base_margin_s = BattingConfig.num(f, "extraBaseMarginS")
		c.base_distance = BattingConfig.num(f, "baseDistanceM")
		c.line_fence = BattingConfig.num(f, "park.lineFenceM")
		c.center_fence = BattingConfig.num(f, "park.centerFenceM")
		c.fence_height = BattingConfig.num(f, "park.fenceHeightM")
		c.running = balance["game"]["running"]
		return c

	## 방향별 펜스 거리 (좌우 끝 → 가운데로 갈수록 멀어진다)
	func fence_at(spray_deg: float) -> float:
		var t := clampf(absf(spray_deg) / 45.0, 0.0, 1.0)
		return center_fence - (center_fence - line_fence) * pow(t, 1.5)

	func base_pos(base: int) -> Vector2:
		var d := base_distance / sqrt(2.0)
		match base:
			1: return Vector2(d, d)
			2: return Vector2(0, d * 2.0)
			3: return Vector2(-d, d)
		return Vector2.ZERO

	func reaction(name: String) -> float:
		if reaction_by_position.has(name):
			return reaction_by_position[name]
		return reaction_infield if name in infield else reaction_outfield


## 타구 하나의 결과와 화면용 기록
class Result:
	var outcome: SwingJudge.Outcome
	var batted_ball: SwingJudge.BattedBall
	var ev_kmh: float
	var launch_deg: float
	var spray_deg: float
	## 공 위치 기록 (DT 간격). 마지막 칸이 플레이가 끝난 곳 (잡힘·주움·펜스 넘음)
	var path := PackedVector3Array()
	var caught := false
	var home_run := false
	var off_wall := false
	## 첫 낙하 지점까지 수평 거리 (홈런은 비행 거리)
	var distance_m := 0.0
	## 공을 잡거나 주운 수비수와 그 움직임
	var fielder := ""
	var fielder_from := Vector2.ZERO
	var fielder_to := Vector2.ZERO
	var fielder_reaction := 0.0
	var fielder_arrive := 0.0
	## 송구 (없으면 throw_base = 0)
	var throw_base := 0
	## 송구를 놓은 시각, 공이 받는 사람 미트에 들어간 시각 (초). 송구가 없으면 -1
	var throw_release := -1.0
	var throw_arrive := -1.0
	## 1루에서 공을 받는 사람 ("1B" 또는 커버 들어온 "P") 과 그 움직임
	var receiver := ""
	var receiver_from := Vector2.ZERO
	var receiver_reaction := 0.0
	var receiver_arrive := 0.0
	## 1루수가 직접 잡아 그대로 베이스를 밟았는가
	var self_putout := false
	## 번트 수비 위치였나 (커버 계산이 같은 위치에서 출발하도록)
	var bunt := false
	## 타자 주자: 달리기 속도(m/s), 1루를 밟는 시각
	var runner_mps := 7.5
	var runner_start := 0.8
	var runner_first := -1.0
	var round_base_s := 0.4
	var base_distance := 27.43
	## 1루 판정 여유: 타자 도착 시각 − 공 도착 시각 (+면 아웃, 0 이하면 세이프)
	var play_margin := 0.0

	func duration() -> float:
		return maxf(path.size() - 1, 0) * DT

	## 타자 주자가 n 루를 밟는 시각 (베이스를 돌 때마다 roundBase 만큼 더 걸린다)
	func runner_time(bases: int) -> float:
		return runner_start + bases * base_distance / runner_mps + maxi(bases - 1, 0) * round_base_s

	## 1루 송구 판정이 있는 내야 땅볼인가
	func is_first_base_play() -> bool:
		return throw_base == 1 and not caught

	## 중계 화면에서 플레이가 끝나는 시각: 공이 멈춘 뒤에도 송구·주루가 끝날 때까지
	func play_end() -> float:
		var end := duration()
		if throw_arrive > 0.0:
			end = maxf(end, throw_arrive)
		var bases := 0
		match outcome:
			SwingJudge.Outcome.SINGLE: bases = 1
			SwingJudge.Outcome.DOUBLE: bases = 2
			SwingJudge.Outcome.TRIPLE: bases = 3
		if bases > 0:
			end = maxf(end, runner_time(bases))
		return end + 0.15


static func polar(angle_deg: float, dist: float) -> Vector2:
	var a := deg_to_rad(angle_deg)
	return Vector2(sin(a), cos(a)) * dist


## 수비 위치 (번트면 1·3루수와 투수가 앞으로 나온다)
static func fielder_positions(cfg: Config, bunt: bool) -> Dictionary:
	var pos := cfg.positions.duplicate()
	if bunt:
		pos["1B"] = polar(38, 18)
		pos["3B"] = polar(-38, 18)
		pos["P"] = polar(0, 14)
	return pos


static func simulate(cfg: Config, ev_kmh: float, launch_deg: float, spray_deg: float, runner_speed: int, bunt: bool = false) -> Result:
	var r := Result.new()
	# 수직 위를 넘는 발사각은 공이 뒤로 날아간다 (정규분포 끝자락) — 높은 뜬공으로 자른다
	launch_deg = minf(launch_deg, cfg.max_launch)
	r.ev_kmh = ev_kmh
	r.bunt = bunt
	r.launch_deg = launch_deg
	r.spray_deg = spray_deg
	r.batted_ball = _batted_type(launch_deg)
	var positions := fielder_positions(cfg, bunt)
	var dir := Vector2(sin(deg_to_rad(spray_deg)), cos(deg_to_rad(spray_deg)))
	var v := ev_kmh / 3.6
	var h := v * cos(deg_to_rad(launch_deg))
	var vz := v * sin(deg_to_rad(launch_deg))
	var p := Vector3(0, 0, cfg.contact_height)
	var fence := cfg.fence_at(spray_deg)
	r.path.append(p)

	# 1. 공중 궤적
	var t := 0.0
	var crossed_fence := false
	while t < MAX_TIME:
		var sp := sqrt(h * h + vz * vz)
		var ah := -cfg.drag_k * sp * h - cfg.lift_k * sp * vz
		var az := -cfg.gravity - cfg.drag_k * sp * vz + cfg.lift_k * sp * h
		h += ah * DT
		vz += az * DT
		var xy := Vector2(p.x, p.y) + dir * h * DT
		p = Vector3(xy.x, xy.y, p.z + vz * DT)
		t += DT
		var dist := xy.length()
		if not crossed_fence and dist >= fence:
			crossed_fence = true
			if p.z > cfg.fence_height:
				r.home_run = true
			else:
				# 펜스에 맞고 떨어진다
				r.off_wall = true
				p = Vector3(dir.x * (fence - 0.5), dir.y * (fence - 0.5), maxf(p.z, 0.0))
				r.path.append(p)
				r.distance_m = fence
				break
		if p.z <= 0.0:
			p.z = 0.0
			r.path.append(p)
			r.distance_m = dist
			break
		r.path.append(p)
	if r.home_run:
		r.outcome = SwingJudge.Outcome.HOME_RUN
		r.distance_m = maxf(r.distance_m, Vector2(p.x, p.y).length())
		return r

	# 2. 공중 포구 (잡을 수 있는 높이로 내려온 순간마다, 가장 먼저 닿는 수비수)
	if not r.off_wall:
		for i in r.path.size():
			var q := r.path[i]
			if q.z > cfg.reach_height:
				continue
			var at := i * DT
			var best := _first_fielder(cfg, positions, Vector2(q.x, q.y), at)
			if best != "":
				r.caught = true
				r.path = r.path.slice(0, i + 1)
				_set_fielder(cfg, r, positions, best, Vector2(q.x, q.y), at)
				r.outcome = SwingJudge.Outcome.LINE_OUT if launch_deg < 20.0 and launch_deg > 0.0 else SwingJudge.Outcome.FLY_OUT
				return r

	# 3. 땅 위: 튀어서 굴러간다 (펜스에 맞았으면 그 자리에 떨어진다)
	var ground_speed := 0.0 if r.off_wall else h * cfg.bounce_retention
	var gp := Vector2(p.x, p.y)
	var fielded := ""
	while t < MAX_TIME:
		var best := _first_fielder(cfg, positions, gp, t)
		if best != "":
			fielded = best
			_set_fielder(cfg, r, positions, best, gp, t)
			break
		ground_speed = maxf(0.0, ground_speed - cfg.ground_decel * DT)
		var next := gp + dir * ground_speed * DT
		if next.length() < fence - 0.5:
			gp = next
		t += DT
		r.path.append(Vector3(gp.x, gp.y, 0.0))
	if fielded == "":
		# 이론상 오지 않는다 (멈춘 공에는 결국 누군가 닿는다)
		r.outcome = SwingJudge.Outcome.SINGLE
		return r

	# 4. 송구 vs 타자 주루
	r.runner_mps = lerpf(cfg.batter_run_at0, cfg.batter_run_at100, clampi(runner_speed, 0, 100) / 100.0)
	r.runner_start = cfg.batter_start_s
	r.round_base_s = cfg.round_base_s
	r.base_distance = cfg.base_distance
	r.runner_first = r.runner_time(1)
	if fielded in cfg.infield:
		_first_base_play(cfg, r, positions, gp, t)
		return r
	# 외야로 빠진 공: 줍고 던지는 시간 vs 타자가 2·3루를 밟는 시간
	var ready := t + cfg.pickup_s
	var throw2 := ready + outfield_to_base_s(cfg, gp.distance_to(cfg.base_pos(2)))
	var throw3 := ready + outfield_to_base_s(cfg, gp.distance_to(cfg.base_pos(3)))
	r.throw_release = ready
	if throw3 > r.runner_time(3) + cfg.extra_base_margin_s:
		r.outcome = SwingJudge.Outcome.TRIPLE
		r.throw_base = 3
		r.throw_arrive = throw3
	elif throw2 > r.runner_time(2) + cfg.extra_base_margin_s:
		r.outcome = SwingJudge.Outcome.DOUBLE
		r.throw_base = 2
		r.throw_arrive = throw2
	else:
		r.outcome = SwingJudge.Outcome.SINGLE
		r.throw_base = 2
		r.throw_arrive = throw2
	return r


## 내야 땅볼의 1루 판정: 공이 1루에서 받는 사람 미트에 들어가는 시각 vs 타자 주자가 1루를 밟는 시각.
## 공이 먼저면 아웃, 타자가 같거나 먼저면 세이프 (동시는 주자 우선 — 야구 규칙).
## - 공이 미트에 들어가는 시각 = max(포구 + 송구 준비 + 송구 비행, 받는 사람이 베이스에 도착한 시각)
## - 받는 사람은 보통 1루수. 1루수가 직접 잡았으면 스스로 베이스를 밟는 것과 커버 들어온 투수에게 던지는 것 중 빠른 쪽
## - 멀리 뛰어가서 잡았으면 몸이 흐트러져 송구 준비가 늦다 (내야안타가 나오는 곳)
static func _first_base_play(cfg: Config, r: Result, positions: Dictionary, gp: Vector2, t: float) -> void:
	r.throw_base = 1
	var bag := cfg.base_pos(1)
	var ran := r.fielder_from.distance_to(gp)
	var transfer := cfg.transfer_s + cfg.range_penalty_s * minf(1.0, ran / cfg.range_for_penalty)
	var release := t + transfer
	var flight := gp.distance_to(bag) / cfg.throw_mps
	var catch_t: float
	if r.fielder == "1B":
		var self_t := t + cfg.transfer_s * 0.5 + _run_time(cfg, "1B", gp.distance_to(bag))
		var p_from: Vector2 = positions["P"]
		var p_arrive := cfg.reaction("P") + _run_time(cfg, "P", p_from.distance_to(bag))
		var thrown := maxf(release + flight, p_arrive)
		if self_t <= thrown:
			r.self_putout = true
			r.receiver = "1B"
			r.receiver_from = gp
			r.receiver_reaction = t + cfg.transfer_s * 0.5
			r.receiver_arrive = self_t
			catch_t = self_t
		else:
			r.receiver = "P"
			r.receiver_from = p_from
			r.receiver_reaction = cfg.reaction("P")
			r.receiver_arrive = p_arrive
			r.throw_release = release
			catch_t = thrown
	else:
		var b_from: Vector2 = positions["1B"]
		r.receiver = "1B"
		r.receiver_from = b_from
		r.receiver_reaction = cfg.reaction("1B")
		r.receiver_arrive = cfg.reaction("1B") + _run_time(cfg, "1B", b_from.distance_to(bag))
		r.throw_release = release
		catch_t = maxf(release + flight, r.receiver_arrive)
	r.throw_arrive = catch_t
	r.play_margin = r.runner_first - catch_t
	r.outcome = SwingJudge.Outcome.GROUND_OUT if catch_t < r.runner_first else SwingJudge.Outcome.SINGLE


## 시각 at 에 지점 spot 에 가장 먼저 닿을 수 있는 수비수 (아무도 못 닿으면 "")
static func _first_fielder(cfg: Config, positions: Dictionary, spot: Vector2, at: float) -> String:
	var best := ""
	var best_need := INF
	for name: String in positions:
		var need := _need_time(cfg, name, positions[name], spot)
		if need <= at and need < best_need:
			best = name
			best_need = need
	return best


## 수비수가 spot 까지 가는 데 걸리는 시간: 반응 + 가속해서 달리기 (잡는 반경만큼은 덜 뛴다).
## 가속이 있어서 짧은 시간엔 몇 미터밖에 못 간다 — 강한 땅볼·라이너가 빠져나가는 이유
static func _need_time(cfg: Config, name: String, from: Vector2, spot: Vector2) -> float:
	return cfg.reaction(name) + _run_time(cfg, name, maxf(0.0, from.distance_to(spot) - cfg.catch_radius))


## 베이스 b 를 커버하러 들어가는 수비수: 공을 잡은 사람을 빼고 가장 먼저 닿는 사람.
## 돌려주는 값 {"name", "from", "react", "ready"} — react 에 출발해 ready 에 베이스에 선다 (타구 시각 기준).
## 주루 판정(PlaySimulator)과 중계 화면이 같은 값을 쓴다
static func base_cover(cfg: Config, r: Result, b: int, busy: Array = []) -> Dictionary:
	return first_to(cfg, r, Vector2.ZERO if b >= 4 else cfg.base_pos(b), busy, false)


## goal 에 가장 먼저 설 수 있는 수비수 (공을 잡은 사람과 busy 는 빼고, infield_only 면 내야수만).
## 돌려주는 값 {"name", "from", "to", "react", "ready"}
static func first_to(cfg: Config, r: Result, goal: Vector2, busy: Array, infield_only: bool) -> Dictionary:
	var positions := fielder_positions(cfg, r.bunt)
	var best := {}
	for name: String in positions:
		if name == r.fielder or name in busy or (infield_only and not (name in cfg.infield)):
			continue
		var from: Vector2 = positions[name]
		var ready := cfg.reaction(name) + _run_time(cfg, name, from.distance_to(goal))
		if best.is_empty() or ready < float(best.ready):
			best = {"name": name, "from": from, "to": goal, "react": cfg.reaction(name), "ready": ready}
	return best


## 외야수가 d 미터를 바로 던지는 시간: 힘이 닿는 거리까지는 곧게, 넘으면 m마다 느려진다 (높이 띄우거나 원바운드)
static func outfield_throw_s(cfg: Config, d: float) -> float:
	return d / cfg.outfield_throw_mps + maxf(0.0, d - cfg.outfield_strong_m) * cfg.long_throw_slow_s_per_m


## 중계 플레이에서 외야수가 던지는 거리 (그 자리에 중계맨이 선다)
static func relay_leg_m(cfg: Config, d: float) -> float:
	return minf(d * cfg.relay_leg_share, cfg.outfield_strong_m)


## 외야수가 d 미터 떨어진 베이스로 공을 보내는 가장 빠른 시간 (바로 vs 중계, 중계맨은 제때 선다고 본다).
## 주자 판단·타구 기록용 어림값 — 실제 플레이는 PlaySimulator 가 중계맨의 도착까지 따진다
static func outfield_to_base_s(cfg: Config, d: float) -> float:
	var direct := outfield_throw_s(cfg, d)
	if d < cfg.relay_min_m:
		return direct
	var leg := relay_leg_m(cfg, d)
	return minf(direct, outfield_throw_s(cfg, leg) + cfg.relay_transfer_s + (d - leg) / cfg.throw_mps)


## 수비수가 멈춘 상태에서 d 미터를 달리는 시간 (주루 플레이에서 직접 베이스를 밟을지 정할 때)
static func run_time(cfg: Config, name: String, d: float) -> float:
	return _run_time(cfg, name, d)


## 멈춘 상태에서 d 미터를 가속해서 달리는 시간 (반응 시간 제외)
static func _run_time(cfg: Config, name: String, d: float) -> float:
	var infielder := name in cfg.infield
	var a := cfg.accel_infield if infielder else cfg.accel_outfield
	var vmax := cfg.max_speed_infield if infielder else cfg.max_speed_outfield
	var d_acc := vmax * vmax / (2.0 * a)
	return sqrt(2.0 * d / a) if d <= d_acc else vmax / a + (d - d_acc) / vmax


static func _set_fielder(cfg: Config, r: Result, positions: Dictionary, name: String, spot: Vector2, at: float) -> void:
	r.fielder = name
	r.fielder_from = positions[name]
	r.fielder_to = spot
	r.fielder_reaction = cfg.reaction(name)
	r.fielder_arrive = minf(at, _need_time(cfg, name, positions[name], spot))


static func _batted_type(launch_deg: float) -> SwingJudge.BattedBall:
	if launch_deg < 10.0:
		return SwingJudge.BattedBall.GROUND
	if launch_deg < 25.0:
		return SwingJudge.BattedBall.LINE
	return SwingJudge.BattedBall.FLY
