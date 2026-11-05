class_name PlaySimulator
extends RefCounted
## 한 플레이 진행: 공이 맞은 순간부터 모든 주자가 멈출 때까지.
##
## - 타구(BattedBallSim.Result)의 시각표대로 사건 시그널을 쏜다: ball_hit → (ball_landed | ball_caught) → ball_fielded
## - 주자(RunnerAI)들은 시그널을 구독해 상태를 바꾸고, 매 간격(DT) step() 으로 움직인다
## - 수비는 공을 잡는 순간 "어디로 던지면 잡을 수 있나"를 예측해 송구한다 (병살은 2루 → 1루 중계)
## - 송구를 받은 야수는 다시 판단해, 더 가려는 주자를 잡을 수 있으면 또 던진다 (중계 플레이)
## - 송구가 베이스에 먼저 닿으면 그 베이스로 오던 주자는 아웃 (포스·태그, 뜬공 귀루 실패는 더블아웃)
## - 주자는 베이스에 다가가면 "내 도착 + 판단 오차 < 예상 송구 도착"이면 한 베이스 더 간다
## 결과(PlayResult)가 아웃·득점·주자 위치를 정하고, 중계 화면은 그 기록(주자 궤적·송구·판정)을 그대로 그린다.
## 노드가 아니라 RefCounted 이고 고정 간격으로 진행하므로, 같은 입력·같은 시드면 언제나 같은 결과다.

signal ball_hit(ball_type: SwingJudge.BattedBall, hit_pos: Vector2, out_count: int, t: float)
signal ball_landed(t: float)
signal ball_caught(t: float)
signal ball_fielded(t: float, at: Vector2)
signal throw_released(from: Vector2, base: int, t: float)
signal throw_arrived(base: int, t: float)

const DT := BattedBallSim.DT
const MAX_TIME := 20.0


## 한 플레이의 결과
class PlayResult:
	var ball: BattedBallSim.Result
	var runners: Array[RunnerAI] = []
	## 송구 [{from, base, release, arrive, carry, cover?, cut?, to?, target?}] — carry 면 던지지 않고 잡은 수비수가 직접 뛰어가 밟는다.
## cut 이면 중계맨에게 가는 공 (base -1, to = 중계맨 자리, target = 처음 노린 베이스). cover = 받으러 들어간 수비수 {name, from, to, react, ready}
	var throws: Array = []
	## 판정 [{base, t, out, text}]
	var calls: Array = []
	var outs := 0
	## 득점한 주자 타순 번호
	var scorers: Array[int] = []
	## 플레이가 끝난 뒤 1·2·3루 주자 (타순 번호, -1 = 빈 베이스)
	var bases := [-1, -1, -1]
	var batter_outcome: SwingJudge.Outcome
	var note := ""
	var sac_fly := false
	var double_play := false
	var rbi := 0
	var duration := 0.0
	## 유저가 조작하는 주자의 판단 순간들 [{t, id, kind: "extra"|"tag"|"rundown"|"dir", from, to, ai: AI 라면 뛰었나, go: 실제로 뛰었나,
	## (dir = 베이스 사이를 달리는 중 앞/뒤, AI 값은 지금 방향 그대로)
	## limit?: 앞 주자에 막혀 갈 수 있는 가장 먼 베이스, show?·since?: 런다운 판단이 생긴 시각·런다운 시작 시각}]
	var decisions: Array = []
	## 런다운 기록 (Rundown.to_record) — 화면이 수비수·공을 그대로 그린다
	var rundowns: Array = []
	## 느리게 보여 줄 구간 [[시작, 끝, 배속]] (플레이 시각, 시작 순) — 내 주자가 런다운에 갇혔을 때 판단할 틈
	var slow: Array = []

	## 화면 시각(느린 구간을 모르는 흐른 시간) → 플레이 시각
	func play_time(w: float) -> float:
		var t := 0.0
		var wall := 0.0
		for s: Array in slow:
			if w <= wall + (float(s[0]) - t):
				return t + (w - wall)
			wall += float(s[0]) - t
			t = float(s[0])
			var span := (float(s[1]) - t) / float(s[2])
			if w <= wall + span:
				return t + (w - wall) * float(s[2])
			wall += span
			t = float(s[1])
		return t + (w - wall)

	## 플레이 시각 → 화면 시각 (play_time 의 반대)
	func wall_time(p: float) -> float:
		var t := 0.0
		var wall := 0.0
		for s: Array in slow:
			if p <= float(s[0]):
				break
			wall += float(s[0]) - t
			var e := minf(p, float(s[1]))
			wall += (e - float(s[0])) / float(s[2])
			t = e
			if p <= float(s[1]):
				return wall
		return wall + (p - t)


var result := PlayResult.new()
## 유저가 조작하는 주자의 타순 번호 (-1 = 없음, 모두 AI). run() 전에 정한다
var controlled_id := -1
## 조작 주자의 판단을 순서대로 미리 정한 값 (true = 뛴다). 모자라는 판단은 AI 대로 간다.
## 판단 전까지의 진행은 choices 와 상관없이 같으므로(난수도 같은 만큼 쓴다), 화면은 AI 판단으로 먼저 돌려 보고
## 유저가 다르게 고르면 그 판단까지의 choices 를 넣어 다시 돌린다 (RunControl)
var choices: Array = []

var _phys: BattedBallSim.Config
var _run: Dictionary
var _ball: BattedBallSim.Result
var _rng: RandomNumberGenerator
var _outs_before: int
var _batter: RunnerAI
var _t := 0.0
var _land_t := -1.0
var _secured := false
var _ball_at := Vector2.ZERO
var _hit_base := -1  # 외야수가 처음 송구할 때 타자가 노리던 베이스 (안타 종류 기록용)
var _ball_free_t := INF  # 공을 가진 야수가 다시 던질 수 있는 시각 (송구가 날아가는 중이면 INF)
var _ball_thrower_infield := false  # 지금 공을 가진 사람이 내야(중계·베이스 커버)인가
var _pending: Array = []  # 날아가는 송구 [{from, base, release, arrive, relay_to, ...}] (중계맨에게 가는 공은 base -1, cut true)
var _assigned := {}  # 베이스 → 그 베이스에 들어간 사람 {name, ready, ...} (같은 베이스에 커버를 두 번 뽑지 않게)
var _relay_men: Array = []  # 중계맨으로 나간 수비수 이름 (커버로 다시 뽑지 않게)
var _decided := {}  # 주자별로 이미 판단한 베이스 (같은 베이스를 두 번 판단하지 않게)
var _out_log: Array = []  # [[시각, 포스 아웃인가 또는 타자 1루 전 아웃인가]]
var _score_log: Array = []  # [[시각, 주자]]
var _rd: Rundown = null  # 진행 중인 런다운 (공은 그 안에 있다)
var _poll := {}  # 조작 주자의 열려 있는 방향 판단 {t: 확정 시각, show, from, to}


## bases: 플레이 전 1·2·3루 주자 타순 번호(-1 빈 베이스), speed_of: 타순 번호 → 주력(0~100)
## controlled: 유저가 조작하는 주자의 타순 번호 (-1 = 없음), p_choices: 그 주자의 판단을 미리 정한 값
func _init(phys: BattedBallSim.Config, ball: BattedBallSim.Result, batter_id: int, bases: Array, speed_of: Callable,
		outs_before: int, rng: RandomNumberGenerator, controlled: int = -1, p_choices: Array = []) -> void:
	controlled_id = controlled
	choices = p_choices
	_phys = phys
	_run = phys.running
	_ball = ball
	_rng = rng
	_outs_before = outs_before
	result.ball = ball
	_batter = RunnerAI.new(batter_id, 0, int(speed_of.call(batter_id)), phys)
	# 내야 땅볼이면 1루를 지나쳐 달리고, 외야로 간 공이면 1루를 돌며 2루를 노린다
	_batter.overrun_first = not ball.caught and ball.fielder in phys.infield
	result.runners.append(_batter)
	for i in 3:
		if bases[i] != -1:
			result.runners.append(RunnerAI.new(bases[i], i + 1, int(speed_of.call(bases[i])), phys))
	# 시그널 구독: 타구 발생은 주자마다 "밀려나는가"를 묶어서 넘긴다
	for r in result.runners:
		ball_hit.connect(r.on_ball_hit.bind(_is_forced(r.origin, bases)))
		ball_caught.connect(r.on_ball_caught)
		ball_landed.connect(r.on_ball_landed)


func run() -> PlayResult:
	if _ball.home_run:
		return _home_run()
	var hit_pos := _ball.fielder_to if _ball.fielder != "" else Vector2(_ball.path[_ball.path.size() - 1].x, _ball.path[_ball.path.size() - 1].y)
	ball_hit.emit(_ball.batted_ball, hit_pos, _outs_before, 0.0)
	_land_t = _landing_time()
	var secure_t := _ball.duration()
	while _t < MAX_TIME:
		_t += DT
		# 타구 사건
		if _land_t > 0.0 and absf(_t - _land_t) < DT * 0.5 and not _ball.caught:
			ball_landed.emit(_t)
		if not _secured and _t >= secure_t:
			_secured = true
			_ball_at = _ball.fielder_to
			if _ball.caught:
				ball_caught.emit(_t)
				_on_caught()
			ball_fielded.emit(_t, _ball_at)
			_defense_decide(_ball_at, _ready_time())
		# 송구 도착
		for th: Dictionary in _pending.duplicate():
			if _t >= th.arrive:
				_pending.erase(th)
				if th.get("cut", false):
					_on_cut(th)
				else:
					_on_throw_arrived(th)
		# 런다운: 공은 그 안에 있다
		if _rd != null:
			_rd.step(DT, _t)
			if _rd.done:
				_end_rundown()
		# 공을 받은 야수가 다시 판단: 더 가려는 주자를 잡을 수 있으면 던진다
		if _secured and _pending.is_empty() and _t >= _ball_free_t and _rd == null:
			_defense_followup()
		# 주자
		for r in result.runners:
			r.step(DT, _t)
			_check_runner(r)
		_poll_controlled()
		if _total_outs() >= 3 or (_all_settled() and _pending.is_empty() and _secured):
			break
	_finish()
	return result


# ---------- 사건 ----------

## 뜬공이 잡히면: 타자 아웃, 하프웨이 주자는 귀루(FSM), 3루 주자는 태그업 판단
func _on_caught() -> void:
	_batter.set_out(_t)
	_add_out(false)
	for r in result.runners:
		if r.state == RunnerAI.State.TAG_UP and _total_outs() < 3:
			# 리터치 후 홈: 내가 홈에 닿는 시각 vs 홈 송구 도착 (판단 오차 포함)
			var tag: float = _run["tagUpS"]
			var runner_eta := _t + tag + r.eta_from_rest(r.base_pos(3).length())
			var throw_eta := _t + _phys.transfer_s * 0.5 + BattedBallSim.outfield_to_base_s(_phys, _ball_at.length()) + float(_run["tagS"])
			if _ball.fielder in _phys.infield:
				throw_eta = _t  # 내야 뜬공에는 태그업하지 않는다
			var go := runner_eta + _judgment() < throw_eta
			if not (_ball.fielder in _phys.infield):
				go = _decide(r, "tag", 4, go)
			if go:
				r.move_after = _t + tag
				r.run_to(4, _t)


## 송구 도착: 그 베이스로 오던 주자(아직 못 닿은)는 아웃
func _on_throw_arrived(th: Dictionary) -> void:
	var b: int = th.base
	throw_arrived.emit(b, _t)
	var victim: RunnerAI = null
	for r in result.runners:
		if not r.is_on_field() or r.state == RunnerAI.State.SAFE:
			continue
		# 이미 그 베이스를 밟고 지나간 주자(1루 오버런 등)는 아웃이 아니다
		# 그 베이스로 오는 주자, 또는 아직 밟지 않은 그 베이스를 지나 더 가려는 주자 (밟아야 하니 결국 거기서 태그된다)
		var heading := (r.state == RunnerAI.State.RUN or r.state == RunnerAI.State.SLIDING) and r.target >= b and r.base < b
		var returning := r.state == RunnerAI.State.RETURNING and r.base == b
		# 여럿이면 베이스에 가장 가까운 주자가 태그된다
		if (heading or returning) and not r.arrived():
			if victim == null or r.pos.distance_to(_base_xy(b)) < victim.pos.distance_to(_base_xy(b)):
				victim = r
	var name := "홈" if b == 4 or b == 0 else "%d루" % b
	if victim != null and _total_outs() < 3 and _rundown_ok(victim, b, th):
		# 송구가 한참 먼저 왔다: 베이스에서 태그되는 대신 두 베이스 사이에 갇힌다
		_start_rundown(victim, b)
		return
	if victim != null and _total_outs() < 3:
		victim.set_out(_t)
		var force: bool = th.get("force", false) or (victim.is_batter() and b == 1)
		_add_out(force)
		result.calls.append({"base": b, "t": _t, "out": true, "text": "%s 아웃!" % name})
		if th.get("relay_to", -1) != -1 and _total_outs() < 3:
			var relay_base: int = th.relay_to
			var from := _base_xy(b)
			var release: float = _t + float(_run["pivotS"])
			# 1루는 포스, 그 밖(1루를 밟은 뒤 2루)은 포스가 풀려 태그해야 한다
			var forced_relay := relay_base == 1
			var arrive := maxf(release + from.distance_to(_base_xy(relay_base)) / _phys.throw_mps, _cover_ready(relay_base)) + (0.0 if forced_relay else float(_run["tagS"]))
			if _worth_throwing(relay_base, arrive):
				_throw(from, relay_base, release, arrive, -1, forced_relay, not forced_relay)
	elif th.get("contest", false):
		result.calls.append({"base": b, "t": _t, "out": false, "text": "%s 세이프!" % name})
	if _pending.is_empty():
		# 공은 이제 그 베이스(커버한 야수)에 있다
		_ball_at = _base_xy(b)
		_ball_thrower_infield = true
		_ball_free_t = _t + float(_run["pivotS"])


## 중계맨이 공을 받았다: 다시 판단한다. 처음 노린 주자를 아직 잡을 수 있으면 그대로, 송구 틈에 더 가려는
## 주자가 있으면 그쪽으로 끊어 던지고, 아무도 못 잡으면 공을 쥔다 — 중계 플레이의 이점
func _on_cut(th: Dictionary) -> void:
	_ball_at = th.to
	_ball_thrower_infield = true
	if not _defense_followup(_phys.relay_transfer_s):
		_ball_free_t = _t + _phys.relay_transfer_s


## 공을 가진 야수(베이스 커버·중계맨)의 다음 송구: 아직 베이스에 못 닿은 주자 중 잡을 수 있는 가장 앞 주자에게.
## delay: 공을 받고 다시 던지기까지 (중계맨). 던졌으면 true
func _defense_followup(delay: float = 0.0) -> bool:
	if _total_outs() >= 3:
		return false
	var release := _t + delay
	var best_base := -1
	var best := {}
	for r in result.runners:
		if not r.is_on_field() or r.arrived():
			continue
		var heading := (r.state == RunnerAI.State.RUN or r.state == RunnerAI.State.SLIDING) and r.target > r.base
		if not heading and r.state != RunnerAI.State.RETURNING:
			continue
		var b := r.target
		var d := _deliver(_ball_at, b, release, _phys.throw_mps, -1.0, false)
		var tag: float = 0.0 if (r.forced and r.target == r.origin + 1) or r.state == RunnerAI.State.RETURNING else float(_run["tagS"])
		if d.arrive + tag < _t + r.eta_to_target() and b > best_base:
			best_base = b
			best = d
			best.arrive = d.arrive + tag
	if best_base < 0:
		return false
	_send(_ball_at, best_base, best, -1, false, true)
	return true


## 매 간격: 주자가 목적지에 닿았는지, 한 베이스 더 갈지, 접전 슬라이딩
func _check_runner(r: RunnerAI) -> void:
	if not r.is_on_field() or r.state == RunnerAI.State.SAFE or r.state == RunnerAI.State.RUNDOWN:
		return
	r.touch_passed_bases()
	if r.state == RunnerAI.State.RUN or r.state == RunnerAI.State.SLIDING:
		# 한 베이스 더? (멈출 베이스 decisionDistanceM 앞에서 한 번 판단)
		var key := "%d:%d" % [r.id, r.target]
		# 내가 친 내야 타구도 1루 앞에서 판단한다 (1루를 지나쳐 달릴지 2루로 갈지) — AI 타자는 내야 타구엔 무조건 1루 오버런
		var my_overrun := r.id == controlled_id and r.is_batter() and r.target == 1 and not r.stop_at_end
		var to_stop := r.remaining() if r.stop_at_end else r.pos.distance_to(r.base_pos(r.target))
		if (r.stop_at_end or my_overrun) and r.target < 4 and to_stop < float(_run["decisionDistanceM"]) and not _decided.has(key):
			# 앞 주자가 아직 다음 베이스를 정하지 않았으면 (베이스 decisionLatestM 앞까지) 기다렸다가 판단한다
			if not _blocked_by_undecided(r) or to_stop < float(_run["decisionLatestM"]):
				_decided[key] = true
				var go := false
				if _extra_allowed(r):
					go = _decide(r, "extra", r.target + 1, _extra_in_time(r), {"limit": _reach_limit(r, r.target + 1)})
				elif r.id == controlled_id and r.is_batter() and _may_try_extra(r):
					# 내가 친 내야 타구: AI 라면 멈추지만(병살 위험·송구가 가깝다) 유저는 더 갈 수 있다
					go = _decide(r, "extra", r.target + 1, false, {"limit": _reach_limit(r, r.target + 1)})
				if go:
					r.run_to(r.target + 1, _t)
		# 접전이면 슬라이딩
		if r.stop_at_end and r.remaining() < float(_run["slideStartM"]) and _incoming_throw(r.target) >= 0.0:
			var gap := _incoming_throw(r.target) - (_t + r.eta_to_target())
			if absf(gap) < float(_run["closePlayS"]):
				r.slide(_t)
	if r.arrived() and r.state != RunnerAI.State.HALFWAY and r.state != RunnerAI.State.TAG_UP:
		if r.target >= 4:
			r.set_scored(_t)
			_score_log.append([_t, r.id])
		elif r.state == RunnerAI.State.RETURNING or r.stop_at_end:
			r.set_safe(_t)
		elif r.is_batter() and not r.stop_at_end:
			# 1루 오버런 뒤 1루로 돌아와 선다
			r.target = 1
			r.set_safe(_t)
			r.pos = r.base_pos(1)


## 내가 가려는 다음 베이스로 앞 주자가 가고 있는데, 그 주자가 한 베이스 더 갈지 아직 안 정했나
func _blocked_by_undecided(r: RunnerAI) -> bool:
	var next := r.target + 1
	for o in result.runners:
		if o != r and o.is_on_field() and o.state == RunnerAI.State.RUN and o.target == next and o.stop_at_end and next < 4:
			return not _decided.has("%d:%d" % [o.id, o.target])
	return false


## 조작 주자의 판단이면 정해 둔 값(없으면 AI 판단)을 쓰고 기록한다. 다른 주자는 AI 판단 그대로. extra: 기록에 더할 값
func _decide(r: RunnerAI, kind: String, to_base: int, ai: bool, extra: Dictionary = {}) -> bool:
	if r.id != controlled_id:
		return ai
	var k := result.decisions.size()
	var go: bool = choices[k] if k < choices.size() else ai
	var d := {"t": _t, "id": r.id, "kind": kind, "from": to_base - 1, "to": to_base, "ai": ai, "go": go}
	d.merge(extra)
	result.decisions.append(d)
	return go


## 앞 주자를 추월하지 않고 갈 수 있는 가장 먼 베이스 (next 부터 앞으로, 다른 주자가 서 있거나 가는 베이스 바로 뒤까지). 홈은 막히지 않는다
func _reach_limit(r: RunnerAI, next: int) -> int:
	var limit := next
	while limit < 3:
		if _taken(limit + 1, r):
			return limit
		limit += 1
	return 4


# ---------- 런다운 ----------

## 베이스에 먼저 온 송구에 걸린 주자가 런다운이 되나: 포스 플레이가 아니고(포스면 베이스만 밟으면 아웃),
## 한 베이스를 밟고 다음 베이스로 가던 중이며, 아직 triggerM 넘게 남았을 때 (가까우면 그냥 태그 아웃)
func _rundown_ok(r: RunnerAI, b: int, th: Dictionary) -> bool:
	if _rd != null or b < 2 or th.get("force", false):
		return false
	if r.state != RunnerAI.State.RUN and r.state != RunnerAI.State.SLIDING:
		return false
	if r.forced and r.target == r.origin + 1:
		return false
	if r.base != b - 1:
		return false
	return r.remaining() >= float(_run["rundown"]["triggerM"])


## 런다운 시작: 앞 베이스에서 공을 받은 수비수와 뒤 베이스를 맡은 수비수가 주자를 사이에 둔다
func _start_rundown(r: RunnerAI, b: int) -> void:
	var a := b - 1
	var holder_name: String = _assigned[b].name if _assigned.has(b) else _ball.fielder
	var pc := _cover_info(a)
	if pc.is_empty() or pc.name == holder_name:
		var busy := _busy_names()
		busy.append(holder_name)
		pc = BattedBallSim.base_cover(_phys, _ball, a, busy)
	var partner := {"name": pc.get("name", "P"), "pos": _base_xy(a)}
	if pc.has("from"):
		# 아직 들어오는 중이면 화면과 같은 식으로 지금 자리를 낸다 (런다운 전에는 커버 기록대로 그려진다)
		var k := clampf((_t - float(pc.react)) / maxf(float(pc.ready) - float(pc.react), 0.01), 0.0, 1.0)
		partner.pos = (pc.from as Vector2).lerp(pc.to, k)
		partner.cover = pc
	_assigned[a] = pc
	var to_base := b
	var decide := func(rr: RunnerAI, ai: bool, show: float, since: float) -> bool:
		return _decide(rr, "rundown", to_base, ai, {"show": show, "since": since})
	_rd = Rundown.new(r, a, b, _base_xy(a), _base_xy(b), {"name": holder_name, "pos": _base_xy(b)}, partner,
		_t, _run["rundown"], _rng, decide, r.id == controlled_id)
	_ball_free_t = INF
	result.calls.append({"base": b, "t": _t, "out": false, "warn": true, "text": "런다운!", "at": r.pos})
	# 수비가 런다운에 묶인 틈에 뒤 주자는 한 베이스 더 간다 (갇힌 주자가 돌아갈 베이스는 비워 둔다)
	for o in result.runners:
		if o != r and o.state == RunnerAI.State.SAFE and o.base >= 1 and o.base + 1 < a and not _taken(o.base + 1, o):
			o.run_to(o.base + 1, _t)


## 런다운이 끝났다: 아웃·세이프·득점을 반영하고, 공을 쥔 수비수가 다른 주자를 볼 수 있게 한다
func _end_rundown() -> void:
	var rd := _rd
	_rd = null
	var r := rd.runner
	var mine := r.id == controlled_id
	result.rundowns.append(rd.to_record(mine))
	if mine:
		result.slow.append([rd.start_t, rd.end_t, float(_run["rundown"]["slowMo"])])
	match rd.outcome:
		"out":
			r.set_out(_t)
			_add_out(false)
			result.calls.append({"base": rd.back, "t": _t, "out": true, "text": "태그 아웃!", "at": r.pos})
		"back":
			r.target = rd.back
			r.set_safe(_t)
			result.calls.append({"base": rd.back, "t": _t, "out": false, "text": "%s 세이프!" % _base_name(rd.back)})
			_reclaim_base(r, rd.back)
		"fwd":
			if rd.fwd >= 4:
				r.set_scored(_t)
				_score_log.append([_t, r.id])
			else:
				r.target = rd.fwd
				r.set_safe(_t)
			result.calls.append({"base": rd.fwd, "t": _t, "out": false, "text": "%s 세이프!" % _base_name(rd.fwd)})
	_ball_at = rd.ball_pos()
	_ball_thrower_infield = true
	_ball_free_t = _t + float(_run["pivotS"])


## 런다운에서 뒤 베이스로 살아 돌아왔는데 뒤 주자가 거기 와 있으면: 베이스는 앞 주자 것이다 (야구 규칙).
## 서 있던 뒤 주자는 태그 아웃, 오는 중이던 뒤 주자는 자기 베이스로 돌아간다
func _reclaim_base(r: RunnerAI, b: int) -> void:
	for o in result.runners:
		if o == r or not o.is_on_field():
			continue
		if o.state == RunnerAI.State.SAFE and o.base == b and _total_outs() < 3:
			o.set_out(_t)
			_add_out(false)
			result.calls.append({"base": b, "t": _t, "out": true, "text": "태그 아웃!"})
		elif (o.state == RunnerAI.State.RUN or o.state == RunnerAI.State.SLIDING) and o.target == b and o.base < b:
			o.return_to_base(_t)


static func _base_name(b: int) -> String:
	return "홈" if b <= 0 or b >= 4 else "%d루" % b


## 내가 친 내야 타구에서 한 베이스 더 시도할 수 있나: 잡힌 공이 아니고 앞이 비었으면 (추월 금지)
func _may_try_extra(r: RunnerAI) -> bool:
	var next := r.target + 1
	return not _ball.caught and (next >= 4 or not _taken(next, r))


# ---------- 내 주자의 방향 바꾸기 ----------

## 조작 주자가 베이스 사이를 달리는 동안 pollS 마다 "앞으로 / 뒤로" 판단을 연다 (베이스 버튼을 주루 내내 바꿀 수 있게).
## AI 값은 "지금 방향 그대로"라서 아무것도 누르지 않으면 AI 주루와 똑같다. 다르게 고르면 돌아서거나(귀루) 다시 앞으로 간다
func _poll_controlled() -> void:
	if controlled_id < 0:
		return
	var r: RunnerAI = null
	for o in result.runners:
		if o.id == controlled_id:
			r = o
	if r == null:
		return
	var ok := _dir_eligible(r)
	if not _poll.is_empty():
		if not ok or int(_poll.from) != r.base:
			_poll = {}
		elif _t >= float(_poll.t):
			var ai := _heading_forward(r)
			var to: int = _poll.to
			var go := _decide(r, "dir", to, ai, {"show": _poll.show, "limit": _reach_limit(r, to)})
			_poll = {}
			if go and not ai:
				# 돌아서서 다시 앞으로 (반응 시간 뒤 멈춘 자리에서 출발)
				r.run_to(to, _t)
				r.speed = 0.0
				r.move_after = _t + float(_run["returnReactionS"])
			elif ai and not go:
				r.return_to_base(_t)
			ok = _dir_eligible(r)
	if _poll.is_empty() and ok:
		_poll = {"t": _t + float(_run["pollS"]), "show": _t, "from": r.base, "to": r.base + 1}


## 앞 베이스로 가는 중인가 (마지막으로 밟은 베이스보다 먼 곳을 향해 달린다)
func _heading_forward(r: RunnerAI) -> bool:
	return (r.state == RunnerAI.State.RUN or r.state == RunnerAI.State.SLIDING) and r.target > r.base


## 방향을 바꿀 수 있는 때인가: 베이스 사이를 달리는 중이고(1루를 밟은 뒤), 앞·뒤 베이스가 다 비어 있고(추월 금지·한 베이스 한 명),
## 밀려나는 주자가 아니고, 잡힌 뜬공이 아닐 때 (그땐 귀루·태그업 규칙을 따른다)
func _dir_eligible(r: RunnerAI) -> bool:
	if _ball.caught or not r.is_on_field() or r.base < 1 or r.base >= 4:
		return false
	if r.state != RunnerAI.State.RUN and r.state != RunnerAI.State.SLIDING and r.state != RunnerAI.State.RETURNING:
		return false
	if r.forced and r.base == r.origin and r.target == r.origin + 1:
		return false
	var to := r.base + 1
	if (to < 4 and _taken(to, r)) or _taken(r.base, r):
		return false
	return true


## 한 베이스 더 가는 게 허용되나 (판단할 거리가 되는가): 앞이 막혔거나 내야에서 잡힌 공이면 안 된다
func _extra_allowed(r: RunnerAI) -> bool:
	var next := r.target + 1
	# 앞 주자가 거기 서 있거나 거기서 멈추려고 가는 중이면 못 간다 (추월 금지). 홈은 여럿이 들어와도 된다
	for o in result.runners:
		if o != r and o.is_on_field() and next < 4 and (o.target == next or (o.state == RunnerAI.State.SAFE and o.base == next)):
			return false
	if _ball.caught or _ball.fielder in _phys.infield:
		return false  # 내야에서 잡힌 공은 한 베이스씩만 (병살 위험)
	return true


## 한 베이스 더 갈 만한가 (AI 판단): 내가 다음 베이스에 닿는 시각 + 판단 오차 < 그 베이스로 오는 송구 도착(+태그)
func _extra_in_time(r: RunnerAI) -> bool:
	var next := r.target + 1
	var runner_eta := _t + r.eta_to_base(next)
	var throw_eta := _predict_throw(next)
	# 아웃 카운트에 따라 과감함이 다르다: 2사면 아웃돼도 잃을 게 적어 더 과감하게 돌린다
	var margin: float = _run["sendMarginByOutsS"][clampi(_total_outs(), 0, 2)]
	# 타자 주자는 1루에 서는 게 기본이라 더 신중하고, 3루에서 아웃되는 건 가장 나쁜 주루라 3루 도전은 더 신중하다
	if r.is_batter():
		margin += float(_run["batterExtraMarginS"])
	if next == 3:
		margin += float(_run["thirdBaseExtraMarginS"])
	return runner_eta + _judgment() + margin < throw_eta + float(_run["tagS"])


## 그 베이스로 오는 송구의 예상 도착 시각 (아직 공을 못 잡았으면 잡는 시각부터 계산)
func _predict_throw(b: int) -> float:
	var outfielder := not (_ball.fielder in _phys.infield)
	if _secured:
		for th: Dictionary in _pending:
			if th.base == b:
				return th.arrive
		for th: Dictionary in _pending:
			# 아직 공이 손에 있으면 어디로 던질지 주자는 모른다 → 내 베이스로 던진다고 보고 판단한다
			if _t < float(th.release):
				return float(th.release) + _throw_time(th.from, b, th.from == _ball.fielder_to and outfielder)
			# 송구가 날아가는 중이면: 받은 사람(베이스 커버·중계맨)이 다시 던진다. 중계맨에게 가는 공은 어디로든 돌릴 수 있다
			var cut: bool = th.get("cut", false)
			var at: Vector2 = th.to if cut else _base_xy(th.base)
			var delay: float = _phys.relay_transfer_s if cut else float(_run["pivotS"])
			return th.arrive + delay + at.distance_to(_base_xy(b)) / _phys.throw_mps
		if _ball_free_t < INF:
			# 공을 쥔 수비수가 지켜보는 중
			return maxf(_t, _ball_free_t) + _throw_time(_ball_at, b, _of_holding())
		return maxf(_t, _ball.duration() + _ready_time()) + _throw_time(_ball.fielder_to, b, outfielder)
	return maxf(_t, _ball.duration()) + _ready_time() + _throw_time(_ball.fielder_to, b, outfielder)


## from 에서 베이스 b 까지 공이 가는 시간 (외야수면 바로 또는 중계, 내야수면 바로)
func _throw_time(from: Vector2, b: int, outfielder: bool) -> float:
	var d := from.distance_to(_base_xy(b))
	return BattedBallSim.outfield_to_base_s(_phys, d) if outfielder else d / _phys.throw_mps


func _incoming_throw(b: int) -> float:
	for th: Dictionary in _pending:
		if th.base == b:
			return th.arrive
	return -1.0


# ---------- 수비 판단 ----------

## 공을 잡은 수비수가 어디로 던질지: 잡을 수 있는 주자 중 가장 앞선 주자, 땅볼 병살 기회면 2루 → 1루
func _defense_decide(from: Vector2, ready: float) -> void:
	var release := _t + ready
	var ground := not _ball.caught and _ball.fielder in _phys.infield
	# 땅볼: 1루 주자 포스 + 타자 1루 병살
	if ground:
		var outs := _total_outs()
		var lead := _forced_runner_to(2)
		# 2루 토스는 짧고 빠르다. 베이스 바로 옆에서 잡았으면 직접 밟는다 (_deliver)
		var feed := _t + float(_run["feedTransferS"]) + _range_penalty()
		# 1) 병살: 무사·1사에 1루 주자 포스 → 2루 → 1루
		if lead != null and outs < 2:
			var d2 := _deliver(from, 2, feed, _phys.throw_mps)
			if d2.arrive < _t + lead.eta_to_target():
				_throw(from, 2, d2.release, d2.arrive, 1, true, true, d2.carry)
				return
		# 2) 잡을 수 있는 가장 앞 포스 아웃 (홈 → 3루 → 2루). 2사여도 1루보다 쉬운 포스 아웃이면 그쪽으로
		for b: int in [4, 3, 2]:
			var forced := _forced_runner_to(b)
			if forced == null:
				continue
			var d := _deliver(from, b, feed if b == 2 else release, _phys.throw_mps)
			if d.arrive < _t + forced.eta_to_target():
				_throw(from, b, d.release, d.arrive, 1 if outs < 2 else -1, true, true, d.carry)
				return
		# 3) 1루: 받는 사람이 베이스에 도착한 뒤에야 잡을 수 있다. 1루수가 직접 밟는 경우는 타구 계산(self_putout) 그대로
		var d1: Dictionary
		if _ball.self_putout:
			d1 = {"release": _ball.receiver_reaction, "arrive": _ball.throw_arrive, "carry": true}
		else:
			d1 = _deliver(from, 1, release, _phys.throw_mps, _ball.receiver_arrive if _ball.receiver != "" else 0.0)
		# 1루를 직접 밟았는데 1루 주자가 2루로 가는 중이면 2루로 던져 태그 (역병살)
		var relay := 2 if d1.carry and lead != null and outs < 2 else -1
		if _worth_throwing(1, d1.arrive):
			_throw(from, 1, d1.release, d1.arrive, relay, true, true, d1.carry)
			return
		# 4) 1루도 늦으면 접전이라도 되는 가장 앞 포스 베이스로
		for b: int in [4, 3, 2]:
			if _forced_runner_to(b) == null:
				continue
			var d := _deliver(from, b, feed if b == 2 else release, _phys.throw_mps)
			if _worth_throwing(b, d.arrive):
				_throw(from, b, d.release, d.arrive, -1, true, true, d.carry)
				return
		# 내야안타가 확실하면 던지지 않고 공을 쥔다 (더 가려는 주자가 있으면 그때 던진다)
		_ball_free_t = release
		_ball_thrower_infield = true
		return
	# 뜬공 포구 뒤: 귀루 못 한 주자(더블아웃) 또는 태그업 주자
	# 외야 안타: 아웃 잡을 수 있는 가장 앞 주자에게, 없으면 앞 주자가 가는 베이스로 (진루 억제)
	var best_base := -1
	var best := {}
	for r in result.runners:
		if not r.is_on_field() or r.state == RunnerAI.State.SAFE or r.state == RunnerAI.State.HALFWAY:
			continue
		var b := r.base if r.state == RunnerAI.State.RETURNING else r.target
		if r.state == RunnerAI.State.TAG_UP:
			continue
		var d := _deliver(from, b, release, _throw_speed())
		var tag: float = 0.0 if r.state == RunnerAI.State.RETURNING else float(_run["tagS"])
		if d.arrive + tag < _t + r.eta_to_target() and b > best_base:
			best_base = b
			best = d
			best.arrive = d.arrive + tag
	if best_base >= 0:
		_send(from, best_base, best, -1, false, true)
		return
	# 아무도 못 잡으면: 공을 놓는 순간에도 아직 달리고 있을 가장 앞 주자 쪽으로 던져 더 못 가게.
	# 그 주자가 한 베이스 더 갈지 아직 안 정했으면 "한 베이스 앞"으로 (야구의 기본: 선행 주자 앞 베이스로), 이미 정했으면 그 베이스로.
	# 그런 주자가 없으면(다 멈췄거나 먼저 닿는다) 던지지 않고 공을 쥔 채 지켜본다
	var cut := -1
	for r in result.runners:
		if r.is_on_field() and r.state == RunnerAI.State.RUN and _t + r.eta_to_target() > release:
			var undecided := r.stop_at_end and r.target < 4 and not _decided.has("%d:%d" % [r.id, r.target])
			cut = maxi(cut, mini(r.target + (1 if undecided and not _taken(r.target + 1, r) else 0), 4))
	if cut >= 2:
		# 먼 송구면 중계맨에게 — 중계맨이 받아 들고 있다가 더 가려는 주자를 잡는다
		_send(from, cut, _deliver(from, cut, release, _throw_speed(), -1.0, false), -1, false)
		return
	_ball_free_t = release
	_ball_thrower_infield = ground or _ball.fielder in _phys.infield


## 베이스 b 에 다른 주자가 서 있거나 그리로 가는 중인가 (그러면 r 은 거기로 못 간다)
func _taken(b: int, r: RunnerAI) -> bool:
	for o in result.runners:
		if o != r and o.is_on_field() and (o.target == b or (o.state == RunnerAI.State.SAFE and o.base == b)):
			return true
	return false


## 던질 만한가: 그 베이스로 가는(또는 돌아가는) 주자가 아직 못 닿았고, 공이 늦어도 접전(closePlayS) 안쪽
func _worth_throwing(b: int, arrive: float) -> bool:
	for r in result.runners:
		if not r.is_on_field() or r.state == RunnerAI.State.SAFE or r.arrived():
			continue
		var heading := (r.state == RunnerAI.State.RUN or r.state == RunnerAI.State.SLIDING) and r.target == b
		var returning := r.state == RunnerAI.State.RETURNING and r.base == b
		if (heading or returning) and arrive < _t + r.eta_to_target() + float(_run["closePlayS"]):
			return true
	return false


## 공을 잡은 자리에서 베이스 b 로 공을 가져가는 가장 빠른 방법: 던지기(외야수면 바로 또는 중계) vs 직접 뛰어가 밟기.
## release: 던질 때 놓는 시각, cover_ready: 던지면 받는 사람이 베이스에 들어서는 시각 (음수면 커버하러 오는 수비수로 계산).
## 돌려주는 값 {"release", "arrive", "carry", "relay"?} — carry 면 release 에 출발해 arrive 에 베이스를 밟는다,
## relay 가 있으면 중계맨을 거친다 ({to, man, cut_arrive})
func _deliver(from: Vector2, b: int, release: float, speed: float, cover_ready: float = -1.0, allow_carry: bool = true) -> Dictionary:
	if cover_ready < 0.0:
		cover_ready = _cover_ready(b)
	var d := from.distance_to(_base_xy(b))
	var best: Dictionary
	if _of_holding():
		best = {"release": release, "arrive": maxf(release + BattedBallSim.outfield_throw_s(_phys, d), cover_ready), "carry": false}
		# 짧은 두 번의 송구가 정확하고, 중계맨이 다시 판단할 수 있어서 조금 늦어도(preferS) 중계를 고른다
		var relay := _relay_plan(from, b, release, cover_ready)
		if not relay.is_empty() and float(relay.arrive) < float(best.arrive) + _phys.relay_prefer_s:
			best = relay
	else:
		best = {"release": release, "arrive": maxf(release + d / speed, cover_ready), "carry": false}
	if allow_carry:
		var go := _t + float(_run["selfGatherS"])
		var carried := go + BattedBallSim.run_time(_phys, _ball.fielder, d)
		if carried < float(best.arrive):
			return {"release": go, "arrive": carried, "carry": true}
	return best


## 중계 플레이 계획: 외야수가 베이스 쪽으로 relay_leg_m 만큼 던지고, 그 자리에 가장 먼저 서는 내야수(중계맨)가 받아 다시 던진다.
## 너무 가까우면 {} (바로 던진다)
func _relay_plan(from: Vector2, b: int, release: float, cover_ready: float) -> Dictionary:
	var goal := _base_xy(b)
	var d := from.distance_to(goal)
	if d < _phys.relay_min_m:
		return {}
	var leg := BattedBallSim.relay_leg_m(_phys, d)
	var pt := from + (goal - from).normalized() * leg
	var busy := _busy_names()
	var cover := _cover_info(b)
	if cover.has("name"):
		busy.append(cover.name)
	var man := BattedBallSim.first_to(_phys, _ball, pt, busy, true)
	if man.is_empty():
		return {}
	var cut_arrive := maxf(release + BattedBallSim.outfield_throw_s(_phys, leg), float(man.ready))
	var arrive := maxf(cut_arrive + _phys.relay_transfer_s + (d - leg) / _phys.throw_mps, cover_ready)
	return {"release": release, "arrive": arrive, "carry": false, "relay": {"to": pt, "man": man, "cut_arrive": cut_arrive}}


## 베이스 b 에 받을 사람이 서는 시각
func _cover_ready(b: int) -> float:
	return float(_cover_info(b).ready)


## 베이스 b 에 들어가는 사람 {name, ready, ...}. 1루 땅볼 플레이는 타구 계산의 받는 사람, 이미 누가 들어갔으면 그 사람
func _cover_info(b: int) -> Dictionary:
	if b == 1 and _ball.receiver != "":
		return {"name": _ball.receiver, "ready": _ball.receiver_arrive, "receiver": true}
	if _assigned.has(b):
		return _assigned[b]
	return BattedBallSim.base_cover(_phys, _ball, b, _busy_names())


func _busy_names() -> Array:
	var names: Array = _relay_men.duplicate()
	for c: Dictionary in _assigned.values():
		names.append(c.name)
	return names


## 공을 잡은 외야수가 아직 공을 쥐고 있나 (내야수에게 넘어가기 전)
func _of_holding() -> bool:
	return not _ball_thrower_infield and not (_ball.fielder in _phys.infield)


## _deliver 의 결과대로 던진다
func _send(from: Vector2, b: int, d: Dictionary, relay_to: int, force: bool, contest: bool = false) -> void:
	_throw(from, b, d.release, d.arrive, relay_to, force, contest, d.get("carry", false), d.get("relay", {}))


## carry: 송구가 아니라 공을 잡은 수비수가 직접 뛰어가 밟는다 (화면은 그 수비수를 베이스로 옮긴다).
## relay: 중계맨에게 던진다 (중계맨이 받는 순간 _on_cut 에서 다시 판단). 기록에 받는 사람(cover)을 남겨 화면이 같은 사람을 움직인다
func _throw(from: Vector2, b: int, release: float, arrive: float, relay_to: int, force: bool, contest: bool = false, carry: bool = false, relay: Dictionary = {}) -> void:
	_ball_free_t = INF
	# 기록 규칙: 다른 주자를 잡으려는 송구 틈에 타자가 더 간 베이스는 안타가 아니다
	# → 처음으로 다른 주자에게 송구한 순간 타자가 노리던 베이스까지만 안타
	if _hit_base == -1 and _throw_at_other_runner(b):
		_hit_base = maxi(_batter.target, 1)
	var th := {"from": from, "base": b, "release": release, "arrive": arrive, "relay_to": relay_to, "force": force, "contest": contest, "carry": carry}
	var dest: Vector2 = relay.to if not relay.is_empty() else _base_xy(b)
	# 외야 송구는 늘 정확하지 않다 (원바운드·옆으로 빠짐 → 늦게 닿는다). 멀리 던질수록 크게
	var err := 0.0
	if not carry and _of_holding():
		err = absf(_rng.randfn(0.0, float(_run["outfieldThrowSdS"]))) * from.distance_to(dest) / float(_run["outfieldThrowSdRefM"])
	if not relay.is_empty():
		th.base = -1
		th.target = b
		th.cut = true
		th.to = relay.to
		th.arrive = float(relay.cut_arrive) + err
		th.cover = relay.man
		_relay_men.append(relay.man.name)
	elif carry:
		_assigned[b] = {"name": _ball.fielder, "ready": arrive}
	else:
		th.arrive = arrive + err
		var cover := _cover_info(b)
		if not cover.get("receiver", false):
			th.cover = cover
		_assigned[b] = cover
	_pending.append(th)
	result.throws.append(th)
	throw_released.emit(from, b, release)


## 공을 잡고 던질 준비가 되기까지 (내야는 송구 준비 + 멀리 뛰었으면 더, 외야는 줍기, 뜬공 포구는 짧게)
func _ready_time() -> float:
	if _ball.caught:
		return _phys.transfer_s * 0.5
	if _ball.fielder in _phys.infield:
		return _phys.transfer_s + _range_penalty()
	return _phys.pickup_s


## 멀리 뛰어가서 잡았으면 몸이 흐트러져 송구 준비가 늦다
func _range_penalty() -> float:
	var ran := _ball.fielder_from.distance_to(_ball.fielder_to)
	return _phys.range_penalty_s * minf(1.0, ran / _phys.range_for_penalty)


func _throw_at_other_runner(b: int) -> bool:
	for r in result.runners:
		if r != _batter and r.is_on_field() and r.state != RunnerAI.State.SAFE and r.target == b:
			return true
	return false


func _throw_speed() -> float:
	return _phys.throw_mps if _ball.fielder in _phys.infield else _phys.outfield_throw_mps


func _forced_runner_to(b: int) -> RunnerAI:
	for r in result.runners:
		if r.forced and r.target == b and r.state == RunnerAI.State.RUN:
			return r
	return null


# ---------- 끝내기 ----------

func _home_run() -> PlayResult:
	# 홈런: 모두 천천히 돌아 득점 (경로는 그대로 남긴다)
	for r in result.runners:
		r.top_speed *= float(_run["trotFactor"])
		r.run_to(4, 0.0)
	while _t < MAX_TIME and not _all_settled():
		_t += DT
		for r in result.runners:
			r.step(DT, _t)
			if r.arrived() and r.target >= 4 and r.state != RunnerAI.State.SCORED:
				r.set_scored(_t)
				_score_log.append([_t, r.id])
	_finish()
	return result


func _finish() -> void:
	# 세 번째 아웃으로 끝나 런다운이 도중에 멈췄으면 거기까지 기록한다 (주자는 아래에서 정리)
	if _rd != null:
		_rd.end_t = _t
		result.rundowns.append(_rd.to_record(false))
		_rd = null
	# 남은 주자 정리: 멈춰 있던 주자는 그 베이스에 산다
	for r in result.runners:
		if r.is_on_field() and r.state != RunnerAI.State.SAFE:
			if r.state == RunnerAI.State.HALFWAY or r.state == RunnerAI.State.IDLE or r.state == RunnerAI.State.TAG_UP or r.state == RunnerAI.State.RETURNING:
				r.target = r.base
				r.pos = r.base_pos(r.base) if r.base > 0 else r.pos
				r.set_safe(_t)
			elif _total_outs() >= 3:
				r.set_safe(_t)
			else:
				r.target = maxi(r.base, 1) if r.is_batter() else r.base
				r.set_safe(_t)
	# 득점: 세 번째 아웃이 포스 아웃(또는 타자가 1루 전에 아웃)이면 그 플레이의 득점은 무효
	var third_force := false
	var inning_over := _total_outs() >= 3
	if inning_over and not _out_log.is_empty():
		third_force = _out_log[_out_log.size() - 1][1]
	for s: Array in _score_log:
		if inning_over and (third_force or s[0] > _out_log[_out_log.size() - 1][0]):
			continue
		result.scorers.append(s[1])
	# 주자 위치
	result.bases = [-1, -1, -1]
	if not inning_over:
		for r in result.runners:
			if r.state == RunnerAI.State.SAFE and r.base >= 1 and r.base <= 3:
				result.bases[r.base - 1] = r.id
	# 타자 결과
	var ground := not _ball.caught and _ball.fielder in _phys.infield
	var runner_outs := 0
	for r in result.runners:
		if r.state == RunnerAI.State.OUT and not r.is_batter():
			runner_outs += 1
	if _ball.home_run:
		result.batter_outcome = SwingJudge.Outcome.HOME_RUN
	elif _ball.caught:
		result.batter_outcome = _ball.outcome
		if not result.scorers.is_empty():
			result.sac_fly = true
			result.note = "희생플라이"
	elif _batter.state == RunnerAI.State.OUT:
		result.batter_outcome = SwingJudge.Outcome.GROUND_OUT
	elif ground and runner_outs > 0:
		result.batter_outcome = SwingJudge.Outcome.GROUND_OUT
		result.note = "야수선택"
	else:
		var hit_bases := _batter.base if _hit_base == -1 else mini(_batter.base, _hit_base)
		if hit_bases < _batter.base:
			result.note = "송구 틈 진루"
		match clampi(hit_bases, 1, 4):
			1: result.batter_outcome = SwingJudge.Outcome.SINGLE
			2: result.batter_outcome = SwingJudge.Outcome.DOUBLE
			3: result.batter_outcome = SwingJudge.Outcome.TRIPLE
			4: result.batter_outcome = SwingJudge.Outcome.HOME_RUN
	if ground and result.outs >= 2 and _batter.state == RunnerAI.State.OUT and runner_outs > 0:
		result.double_play = true
		result.note = "병살"
	result.rbi = 0 if result.double_play else result.scorers.size()
	var last := 0.0
	for r in result.runners:
		last = maxf(last, (r.track.size() - 1) * DT)
	result.duration = maxf(_t, last)


# ---------- 도우미 ----------

## 이 이닝 아웃 수 (플레이 전 아웃 + 이 플레이에서 잡은 아웃)
func _total_outs() -> int:
	return _outs_before + result.outs


func _add_out(force: bool) -> void:
	result.outs += 1
	_out_log.append([_t, force])


func _all_settled() -> bool:
	for r in result.runners:
		if r.is_on_field() and r.state != RunnerAI.State.SAFE:
			if r.state == RunnerAI.State.IDLE and r.curve == null:
				continue
			if r.state == RunnerAI.State.TAG_UP and r.move_after > _t:
				continue
			if r.state == RunnerAI.State.HALFWAY and r.arrived() and _secured:
				continue
			return false
	return true


## 주자에게 "공이 떨어졌다/빠졌다"가 보이는 시각.
## 뜬공·라이너는 처음 땅에 닿는 순간(못 잡을 공이면 그보다 조금 일찍 읽는다), 땅볼은 내야(홈에서 infieldDepthM)를 빠져나가는 순간 (내야수가 잡으면 없음)
func _landing_time() -> float:
	if _ball.batted_ball == SwingJudge.BattedBall.GROUND:
		if _ball.fielder in _phys.infield:
			return -1.0
		for i in range(1, _ball.path.size()):
			if Vector2(_ball.path[i].x, _ball.path[i].y).length() > float(_run["infieldDepthM"]):
				return i * DT
		return -1.0
	# 뜬공·라이너: 못 잡을 공이면 주자는 떨어지기 조금 전에 읽고 뛴다 (수비수가 못 따라가는 게 보인다)
	for i in range(1, _ball.path.size()):
		if _ball.path[i].z <= 0.0:
			if _ball.caught:
				return i * DT
			return maxf(float(_run["hitReadMinS"]), i * DT - float(_run["hitReadLeadS"]))
	return -1.0


func _base_xy(b: int) -> Vector2:
	return Vector2.ZERO if b <= 0 or b >= 4 else _phys.base_pos(b)


## 판단 오차: 주자가 송구를 정확히 읽지 못한다 (시드 난수)
func _judgment() -> float:
	return _rng.randfn(0.0, float(_run["judgmentErrorSdS"]))


static func _is_forced(origin: int, bases: Array) -> bool:
	if origin == 0:
		return true
	for i in range(0, origin - 1):
		if bases[i] == -1:
			return false
	return true
