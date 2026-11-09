class_name BroadcastView
extends RefCounted
## 타구 중계 화면: 홈플레이트 뒤 높은 곳에서 야구장 전체를 내려다본다.
## 그리는 내용은 전부 BattedBallSim.Result(공 경로·수비수 이동)와 PlaySimulator.PlayResult(주자 궤적·송구·판정)에서 온다
## — 결과와 화면이 같은 계산이다.
## BattingView._draw 안에서 그 캔버스로 그린다.

## 카메라 (m): 홈 뒤 CAM_BACK, 높이 CAM_HEIGHT, 바라보는 곳 = 가운데 방향 LOOK_AT
## (2026-11-07 유저 참고 화면: 거의 위에서 내려다보는 높은 카메라 — 옛 값 28·34·62 는 낮게 비스듬했다)
const CAM_BACK := 16.0
const CAM_HEIGHT := 60.0
const LOOK_AT := 56.0
## 화면 맞춤: 홈플레이트와 가운데 담장이 놓일 세로 위치 (화면 높이 비율)
const HOME_Y := 0.97
const FENCE_Y := 0.1
## 판정 말풍선이 떠 있는 시간, 아웃·득점한 주자가 사라지기까지 (초)
const CALL_SHOW_S := 1.4
## 좌우 담장 끝이 화면 가운데에서 가로 폭의 몇 배 거리에 오나 (0.5 = 딱 가장자리, 크면 경기장이 커지고 끝이 잘린다)
const FIT_WIDTH := 0.85
const RUNNER_FADE_S := 0.6
## 공을 따라가는 카메라 (2026-11-07): 경기장을 화면보다 ZOOM 배 크게 그려 두고, 공(굴러가는 공·송구)을 따라 옮긴다.
## 가로 화면에 높은 카메라로 경기장 전체를 담으면 양옆이 비고 선수가 너무 작아서다. FOCUS_SMOOTH_S 동안의 공 자리 평균을 따라가 흔들리지 않는다
const ZOOM := 1.7
const FOCUS_SMOOTH_S := 0.4


## 원근 투영
class Cam:
	var rect: Rect2
	var focal: float
	var center: Vector2
	var cos_p: float
	var sin_p: float

	## 화면에 맞추기: 홈플레이트가 아래 HOME_Y, 가운데 담장 꼭대기가 위 FENCE_Y 에 오게. 좌우 담장 끝(파울 폴)은 화면 밖으로 조금 나가도 된다(FIT_WIDTH)
	func _init(r: Rect2, fence_center: float, fence_line: float, fence_h: float) -> void:
		rect = r
		var pitch := atan2(CAM_HEIGHT, LOOK_AT + CAM_BACK)
		cos_p = cos(pitch)
		sin_p = sin(pitch)
		focal = 1.0
		center = Vector2.ZERO
		var home := to_screen(Vector3.ZERO)
		var fence := to_screen(Vector3(0, fence_center, fence_h))
		var corner := to_screen(Vector3(BattedBallSim.polar(45, fence_line).x, BattedBallSim.polar(45, fence_line).y, 0))
		var by_height := r.size.y * (HOME_Y - FENCE_Y) / maxf(home.y - fence.y, 0.0001)
		var by_width := r.size.x * FIT_WIDTH / maxf(absf(corner.x), 0.0001)
		focal = minf(by_height, by_width)
		center = Vector2(r.get_center().x, r.position.y + r.size.y * HOME_Y - home.y * focal)

	## 화면 속 한 점(focus, 지금 좌표)이 보이는 창(view) 가운데에 오게 옮긴다. 경기장 판(rect)이 창 밖으로 나가지 않게 막는다
	func pan_to(focus: Vector2, view: Rect2) -> void:
		var off := view.get_center() - focus
		off.x = clampf(off.x, view.end.x - rect.end.x, view.position.x - rect.position.x)
		off.y = clampf(off.y, view.end.y - rect.end.y, view.position.y - rect.position.y)
		rect.position += off
		center += off

	## 야구장 좌표 (x 1루 쪽, y 가운데 방향, z 위) → 화면
	func to_screen(p: Vector3) -> Vector2:
		var dy := p.y + CAM_BACK
		var dz := p.z - CAM_HEIGHT
		var depth := dy * cos_p - dz * sin_p
		var up := dz * cos_p + dy * sin_p
		depth = maxf(depth, 1.0)
		return center + Vector2(p.x * focal / depth, -up * focal / depth)

	func ground(p: Vector2) -> Vector2:
		return to_screen(Vector3(p.x, p.y, 0.0))

	## 그 지점에서 1m 가 화면에서 몇 px 인가 (크기 조절용)
	func scale_at(p: Vector3) -> float:
		return to_screen(p + Vector3(1, 0, 0)).x - to_screen(p).x


## t: 공의 시각 (초, 실제 시간). us_fielding: 수비하는 쪽이 우리 팀인가 (유니폼 색)
## play: 주자 플레이 결과 (있으면 모든 주자·송구·판정을 그 기록대로 그린다. 없으면 타자 주자만 옛 방식으로)
## mark_id: 머리 위에 표시할 주자의 타순 번호 (내 선수, -1 = 없음)
static func draw(canvas: CanvasItem, rect: Rect2, cfg: BattedBallSim.Config, r: BattedBallSim.Result, t: float, bunt: bool,
		us_fielding: bool = false, play: PlaySimulator.PlayResult = null, mark_id: int = -1) -> void:
	var cam := Cam.new(Rect2(rect.position, rect.size * ZOOM), cfg.center_fence, cfg.line_fence, cfg.fence_height)
	var focus := Vector2.ZERO
	for k in 5:
		focus += cam.ground(_focus_at(cfg, r, play, t - FOCUS_SMOOTH_S * k / 4.0))
	cam.pan_to(focus / 5.0, rect)
	_draw_field(canvas, cam, cfg)

	var duration := r.duration()
	var tt := clampf(t, 0.0, duration)
	var idx := mini(int(tt / BattedBallSim.DT), r.path.size() - 1)
	var ball := r.path[idx]

	# 수비수: 잡으러 가는 한 명은 반응 시간 뒤 목표로 달려가고, 1루에서 받는 사람은 베이스로 간다.
	# 나머지는 공 쪽으로 조금씩 움직인다
	var positions := BattedBallSim.fielder_positions(cfg, bunt, r.dp_depth)
	var uniform := Tokens.UNIFORM_US if us_fielding else Tokens.UNIFORM_THEM
	var cover := _cover_assignments(play)
	var bag := cfg.base_pos(1)
	for name: String in positions:
		var at := _fielder_at(name, positions, cfg, r, play, cover, t)
		# 조금 전 자리와 비교해 움직이는 중인지 (도트 선수가 달리는 칸을 쓴다)
		var vel := cam.ground(at) - cam.ground(_fielder_at(name, positions, cfg, r, play, cover, t - PixelArt.FIELDER_VEL_DT))
		var pose := _fielder_pose(name, positions, cfg, r, play, cover, t, cam)
		_draw_person(canvas, cam, at, uniform, vel, t, pose[0], pose[1])

	# 주자: 플레이 기록이 있으면 모든 주자를 궤적대로, 없으면 타자 주자만 계산에 쓴 속도로
	if play != null:
		_draw_play_runners(canvas, cam, play, t, Tokens.UNIFORM_THEM if us_fielding else Tokens.UNIFORM_US, mark_id)
	else:
		_draw_runner(canvas, cam, cfg, r, t)

	# 공: 굴러가는 중 → 수비수 글러브 → 송구(놓은 시각부터 미트에 들어가는 시각까지) → 받는 사람 미트
	if play != null and t >= duration and not r.home_run:
		ball = _play_ball(cfg, r, play, t)
	elif t >= duration and not r.home_run:
		var holder := r.fielder_to
		if r.self_putout:
			var k2 := clampf((t - r.receiver_reaction) / maxf(r.receiver_arrive - r.receiver_reaction, 0.01), 0.0, 1.0)
			holder = r.fielder_to.lerp(bag, k2)
		ball = Vector3(holder.x, holder.y, 1.3)
		if r.throw_release > 0.0 and t >= r.throw_release:
			var target := cfg.base_pos(r.throw_base)
			var k := clampf((t - r.throw_release) / maxf(r.throw_arrive - r.throw_release, 0.01), 0.0, 1.0)
			ball = Vector3(holder.x, holder.y, 1.5).lerp(Vector3(target.x, target.y, 1.3), k)
			ball.z += sin(k * PI) * 3.0
	else:
		# 꼬리
		for i in range(1, 7):
			var j := maxi(idx - i * 3, 0)
			var q := r.path[j]
			var c := Color(Tokens.CHALK, 0.45 - i * 0.06)
			canvas.draw_circle(cam.to_screen(q), maxf(cam.scale_at(q) * 0.35, 1.0), c, true, -1.0, true)
	# 수비수가 공을 쥐고 있는 동안(잡은 순간 ~ 송구를 놓기 전, 송구를 받은 뒤 ~ 다음 송구 전)은 공을 그리지 않는다 (2026-11-07 유저)
	if not _ball_held(r, play, t):
		_draw_ball(canvas, cam, ball)

	if play != null:
		_draw_calls(canvas, cam, cfg, play, t)
		return
	# 1루 판정: 공과 타자 중 먼저 닿은 순간부터 "아웃!/세이프!" 와 차이
	if r.is_first_base_play() and t >= minf(r.throw_arrive, r.runner_first):
		var out := r.outcome == SwingJudge.Outcome.GROUND_OUT
		var text := "아웃!" if out else "세이프!"
		var detail := "%.2f초 차" % absf(r.play_margin)
		var at := cam.ground(bag) + Vector2(0, -cam.scale_at(Vector3(bag.x, bag.y, 0)) * 6.0)
		var font := Tokens.FONT_BOLD
		var w := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, Tokens.FONT_TITLE).x
		var pill := StyleBoxFlat.new()
		pill.bg_color = Tokens.SURFACE
		pill.set_corner_radius_all(Tokens.RADIUS_CARD)
		pill.anti_aliasing = true
		var box := Rect2(at - Vector2(w / 2.0 + Tokens.SPACE_SM, Tokens.FONT_TITLE + Tokens.SPACE_XS), Vector2(w + Tokens.SPACE_SM * 2, Tokens.FONT_TITLE + Tokens.FONT_CAPTION + Tokens.SPACE_SM * 2))
		canvas.draw_style_box(pill, box)
		canvas.draw_string(font, Vector2(box.position.x, at.y), text, HORIZONTAL_ALIGNMENT_CENTER, box.size.x, Tokens.FONT_TITLE, Tokens.BAD if out else Tokens.GOOD)
		canvas.draw_string(Tokens.FONT_REGULAR, Vector2(box.position.x, at.y + Tokens.FONT_CAPTION + Tokens.SPACE_XS), detail, HORIZONTAL_ALIGNMENT_CENTER, box.size.x, Tokens.FONT_CAPTION, Tokens.INK_SOFT)


## 수비수 한 명의 자리 (t 초): 잡으러 가는 한 명은 반응 시간 뒤 목표로 달려가고, 1루에서 받는 사람은 베이스로 간다.
## 나머지는 공 쪽으로 조금씩 움직인다
static func _fielder_at(name: String, positions: Dictionary, cfg: BattedBallSim.Config, r: BattedBallSim.Result, play: PlaySimulator.PlayResult, cover: Dictionary, t: float) -> Vector2:
	# 런다운에 들어간 수비수는 그 기록대로 (끝난 뒤에는 마지막 자리)
	if play != null:
		for rd: Dictionary in play.rundowns:
			var tr: PackedVector2Array = (rd.tracks as Dictionary).get(name, PackedVector2Array())
			if not tr.is_empty() and t >= float(rd.t0):
				return tr[clampi(int((t - float(rd.t0)) / BattedBallSim.DT), 0, tr.size() - 1)]
	var at: Vector2 = positions[name]
	var bag := cfg.base_pos(1)
	if name == r.fielder:
		var k := clampf((t - r.fielder_reaction) / maxf(r.fielder_arrive - r.fielder_reaction, 0.01), 0.0, 1.0)
		at = r.fielder_from.lerp(r.fielder_to, k)
		if play != null:
			# 공을 쥐고 직접 베이스를 밟으러 간다
			at = _carry_pos(cfg, play, t, at)
		elif r.self_putout and t > r.receiver_reaction:
			# 1루수가 직접 잡아 베이스로 뛴다
			var k2 := clampf((t - r.receiver_reaction) / maxf(r.receiver_arrive - r.receiver_reaction, 0.01), 0.0, 1.0)
			at = r.fielder_to.lerp(bag, k2)
	elif name == r.receiver and r.is_first_base_play():
		var k := clampf((t - r.receiver_reaction) / maxf(r.receiver_arrive - r.receiver_reaction, 0.01), 0.0, 1.0)
		at = r.receiver_from.lerp(bag, k)
	elif cover.has(name):
		# 송구 받을 베이스(또는 중계 자리)로 들어간다 (판정에 쓴 시각 그대로: react 에 출발해 ready 에 도착)
		var c: Dictionary = cover[name]
		var k := clampf((t - float(c.react)) / maxf(float(c.ready) - float(c.react), 0.01), 0.0, 1.0)
		at = at.lerp(c.to, k)
	elif name != "C":
		var ball := r.path[clampi(int(clampf(t, 0.0, r.duration()) / BattedBallSim.DT), 0, r.path.size() - 1)]
		var drift := clampf((t - 0.5) / 2.0, 0.0, 1.0) * 3.0
		at = at.move_toward(Vector2(ball.x, ball.y), drift)
	if play != null:
		# 송구 실책으로 빠진 공을 쫓아간다
		at = _chase_pos(cfg, play, name, t, at)
	return at


## 판정 말풍선: 판정 시각부터 CALL_SHOW_S 동안 그 베이스 위에
static func _draw_calls(canvas: CanvasItem, cam: Cam, cfg: BattedBallSim.Config, play: PlaySimulator.PlayResult, t: float) -> void:
	for c: Dictionary in play.calls:
		var since: float = t - float(c.t)
		if since < 0.0 or since > CALL_SHOW_S:
			continue
		var b: int = c.base
		var p: Vector2 = c.get("at", Vector2.ZERO if b <= 0 or b >= 4 else cfg.base_pos(b))
		var text: String = c.text
		var at := cam.ground(p) + Vector2(0, -cam.scale_at(Vector3(p.x, p.y, 0)) * 6.0)
		var font := Tokens.FONT_BOLD
		var w := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, Tokens.FONT_LABEL).x
		var pill := StyleBoxFlat.new()
		pill.bg_color = Tokens.SURFACE
		pill.set_corner_radius_all(Tokens.RADIUS_CARD)
		pill.anti_aliasing = true
		var box := Rect2(at - Vector2(w / 2.0 + Tokens.SPACE_SM, Tokens.FONT_LABEL + Tokens.SPACE_XS), Vector2(w + Tokens.SPACE_SM * 2, Tokens.FONT_LABEL + Tokens.SPACE_SM * 2))
		canvas.draw_style_box(pill, box)
		var color := Tokens.WARN if c.get("warn", false) else (Tokens.BAD if c.out else Tokens.GOOD)
		canvas.draw_string(font, Vector2(box.position.x, at.y), text, HORIZONTAL_ALIGNMENT_CENTER, box.size.x, Tokens.FONT_LABEL, color)


## 플레이의 모든 주자: 궤적(track)을 그 시각 그대로. 아웃·득점한 주자는 잠깐 뒤 사라진다
static func _draw_play_runners(canvas: CanvasItem, cam: Cam, play: PlaySimulator.PlayResult, t: float, uniform: Color, mark_id: int = -1) -> void:
	for runner in play.runners:
		if runner.track.is_empty():
			continue
		var idx := clampi(int(t / BattedBallSim.DT), 0, runner.track.size() - 1)
		if not runner.is_on_field() and not runner.history.is_empty():
			var gone_t: float = runner.history[runner.history.size() - 1][0]
			if t > gone_t + RUNNER_FADE_S:
				continue
		var prev: Vector2 = runner.track[maxi(idx - int(round(PixelArt.FIELDER_VEL_DT / BattedBallSim.DT)), 0)]
		var vel := cam.ground(runner.track[idx]) - cam.ground(prev)
		var pose := _runner_pose(cam, play, runner, t, vel)
		# 주자 전용 시트 (2026-11-10 Codex runners_ext): 방향·헤드퍼스트 슬라이딩 칸
		RunnerArt.draw_runner(canvas, cam, runner.track[idx], uniform == Tokens.UNIFORM_US, vel, t, pose[0], pose[1], pose[2])
		if runner.id == mark_id:
			draw_marker(canvas, cam, runner.track[idx])


## 내 선수 표시: 머리 위의 역삼각형
static func draw_marker(canvas: CanvasItem, cam: Cam, at: Vector2) -> void:
	var px := maxf(cam.scale_at(Vector3(at.x, at.y, 0)), 3.0)
	var tip := cam.to_screen(Vector3(at.x, at.y, 2.6))
	var tri := PackedVector2Array([tip, tip + Vector2(-px * 0.9, -px * 1.5), tip + Vector2(px * 0.9, -px * 1.5)])
	canvas.draw_colored_polygon(tri, Tokens.WARN)
	canvas.draw_polyline(PackedVector2Array([tri[0], tri[1], tri[2], tri[0]]), Tokens.INK, 1.0, true)


## 수비수 글러브 안에 공이 있는가 (t 초): 잡거나 주운 순간부터 첫 송구를 놓기 전, 송구가 닿은 뒤 다음 송구를 놓기 전.
## 홈런·아직 굴러가는 공은 아니다
static func _ball_held(r: BattedBallSim.Result, play: PlaySimulator.PlayResult, t: float) -> bool:
	if r.home_run or t < r.duration():
		return false
	if play != null:
		var free := false
		for th: Dictionary in play.throws:
			if th.get("carry", false):
				continue
			if t >= float(th.release) and t < float(th.arrive):
				free = true
		# 송구 실책으로 빠진 공은 쫓아가 다시 잡기 전까지 보인다
		for th: Dictionary in play.throws:
			if th.get("error", false) and t >= float(th.get("reach", INF)) and t < float(th.arrive):
				free = true
		return not free
	if r.throw_release > 0.0 and t >= r.throw_release and t < r.throw_arrive:
		return false
	return true


## 카메라가 따라갈 곳 (t 초): 날아가는·굴러가는 공 → 잡은 뒤에는 송구 기록대로 공 (없으면 잡은 자리)
static func _focus_at(cfg: BattedBallSim.Config, r: BattedBallSim.Result, play: PlaySimulator.PlayResult, t: float) -> Vector2:
	var duration := r.duration()
	if t < duration or r.home_run:
		var q := r.path[clampi(int(clampf(t, 0.0, duration) / BattedBallSim.DT), 0, r.path.size() - 1)]
		return Vector2(q.x, q.y)
	if play != null:
		var b := _play_ball(cfg, r, play, t)
		return Vector2(b.x, b.y)
	return r.fielder_to


## 송구 기록대로 공: 잡은 자리 → (송구 중이면 날아가는 공) → 받은 베이스
static func _play_ball(cfg: BattedBallSim.Config, r: BattedBallSim.Result, play: PlaySimulator.PlayResult, t: float) -> Vector3:
	var at := Vector3(r.fielder_to.x, r.fielder_to.y, 1.3)
	for th: Dictionary in play.throws:
		if t < float(th.release):
			break
		var from: Vector2 = th.from
		var b: int = th.base
		# 중계맨에게 가는 공은 중계 자리로
		var to: Vector2 = th.to if th.get("cut", false) else (Vector2.ZERO if b >= 4 else cfg.base_pos(b))
		if th.get("error", false):
			# 송구 실책: 베이스까지 날아간 공이 빠져 loose 로 굴러간다
			var reach := float(th.reach)
			if t < reach:
				var k1 := clampf((t - float(th.release)) / maxf(reach - float(th.release), 0.01), 0.0, 1.0)
				at = Vector3(from.x, from.y, 1.5).lerp(Vector3(to.x, to.y, 1.3), k1)
				at.z += sin(k1 * PI) * minf(3.0, from.distance_to(to) * 0.08)
			else:
				var k2 := clampf((t - reach) / maxf(float(th.arrive) - reach, 0.01), 0.0, 1.0)
				var roll := 1.0 - pow(1.0 - minf(1.0, k2 * 1.6), 2.0)
				var p2 := to.lerp(th.loose as Vector2, roll)
				at = Vector3(p2.x, p2.y, absf(sin(roll * TAU)) * 0.5 * (1.0 - roll))
			continue
		var k := clampf((t - float(th.release)) / maxf(float(th.arrive) - float(th.release), 0.01), 0.0, 1.0)
		if th.get("carry", false):
			var p := from.lerp(to, k)
			at = Vector3(p.x, p.y, 1.1)
			continue
		at = Vector3(from.x, from.y, 1.5).lerp(Vector3(to.x, to.y, 1.3), k)
		at.z += sin(k * PI) * minf(3.0, from.distance_to(to) * 0.08)
	# 런다운 중이면 공은 그 기록대로, 끝난 뒤 다음 송구 전까지는 마지막 자리
	for rd: Dictionary in play.rundowns:
		var bt: PackedVector3Array = rd.ball
		if bt.is_empty() or t < float(rd.t0):
			continue
		var thrown_after := false
		for th: Dictionary in play.throws:
			if float(th.release) > float(rd.end) and t >= float(th.release):
				thrown_after = true
		if not thrown_after:
			at = bt[clampi(int((t - float(rd.t0)) / BattedBallSim.DT), 0, bt.size() - 1)]
	return at


## 직접 베이스를 밟으러 뛰는 수비수의 자리 (그런 플레이가 없으면 at 그대로)
static func _carry_pos(cfg: BattedBallSim.Config, play: PlaySimulator.PlayResult, t: float, at: Vector2) -> Vector2:
	for th: Dictionary in play.throws:
		if not th.get("carry", false) or t < float(th.release):
			continue
		var b: int = th.base
		var to := Vector2.ZERO if b >= 4 else cfg.base_pos(b)
		var k := clampf((t - float(th.release)) / maxf(float(th.arrive) - float(th.release), 0.01), 0.0, 1.0)
		at = (th.from as Vector2).lerp(to, k)
	return at


## 송구 실책으로 빠진 공을 쫓는 수비수의 자리: 공이 베이스에 닿은 뒤 빠진 자리로 뛰어가 다시 잡는다 (아니면 at 그대로)
static func _chase_pos(cfg: BattedBallSim.Config, play: PlaySimulator.PlayResult, name: String, t: float, at: Vector2) -> Vector2:
	for th: Dictionary in play.throws:
		if not th.get("error", false) or th.get("chaser", "") != name or t < float(th.reach):
			continue
		var b: int = th.base
		var bag := Vector2.ZERO if b >= 4 else cfg.base_pos(b)
		var k := clampf((t - float(th.reach)) / maxf(float(th.arrive) - float(th.reach), 0.01), 0.0, 1.0)
		at = bag.lerp(th.loose as Vector2, k)
	return at


## 송구를 받으러 들어가는 수비수 (베이스 커버·중계맨). 판정이 송구 기록(cover)에 남긴 사람·시각 그대로.
## 돌려주는 값: 이름 → {to, react, ready}. 1루 땅볼 플레이의 받는 사람은 따로 그린다
static func _cover_assignments(play: PlaySimulator.PlayResult) -> Dictionary:
	var out := {}
	if play == null:
		return out
	for th: Dictionary in play.throws:
		var c: Dictionary = th.get("cover", {})
		if c.is_empty() or out.has(c.name):
			continue
		out[c.name] = c
	# 런다운의 뒤 베이스를 맡으러 들어간 수비수
	for rd: Dictionary in play.rundowns:
		var c: Dictionary = rd.partner_cover
		if not c.is_empty() and not out.has(c.name):
			out[c.name] = c
	return out


static func _draw_ball(canvas: CanvasItem, cam: Cam, p: Vector3) -> void:
	var shadow := cam.ground(Vector2(p.x, p.y))
	canvas.draw_circle(shadow, maxf(cam.scale_at(Vector3(p.x, p.y, 0)) * 0.45, 1.5), Tokens.SHADOW, true, -1.0, true)
	var s := cam.to_screen(p)
	var radius := maxf(cam.scale_at(p) * 0.45, 2.5)
	canvas.draw_circle(s, radius, Tokens.CHALK, true, -1.0, true)
	canvas.draw_circle(s, radius, Tokens.INK, false, 1.0, true)


## 사람: 도트 선수 (PixelArt) — vel(화면에서 움직이는 방향)이 있으면 달리는 칸
static func _draw_person(canvas: CanvasItem, cam: Cam, at: Vector2, uniform: Color, vel: Vector2 = Vector2.ZERO, t: float = 0.0, pose: String = "", face: float = 0.0) -> void:
	PixelArt.draw_fielder(canvas, cam, at, uniform == Tokens.UNIFORM_US, vel, t, pose, face)


## 상황 동작 (2026-11-07 유저: 슬라이딩·송구 같은 동작이 상황에 안 맞는다) — 판정에 쓴 시각 그대로 고른다.
## 수비수: 공을 잡는 순간 뜬공(글러브 위)·땅볼(글러브 아래)·낮은 공 잡기(다이빙), 송구를 놓는 순간 던지기.
## 돌려주는 값: [칸 이름 ("" = 움직임으로), 바라볼 쪽 (-1 왼쪽)]
const CATCH_POSE_BEFORE_S := 0.25
const CATCH_POSE_AFTER_S := 0.35
const THROW_POSE_BEFORE_S := 0.3
const THROW_POSE_AFTER_S := 0.15
const SLIDE_BEFORE_S := 0.35
const SLIDE_AFTER_S := 0.6
## 판정 순간 베이스에서 이만큼 안에 있던 주자가 슬라이딩한다 (포스 아웃이면 주자가 아직 몇 m 앞이다)
const SLIDE_NEAR_M := 5.0

## 다이빙 캐치 (2026-11-07 유저: 일정 범위 안에서는 확률로): 잡으러 DIVE_MIN_M ~ DIVE_MAX_M 를 달려간 공이면
## 뜬공·라인드라이브는 DIVE_CHANCE_AIR (낮게 온 공은 DIVE_CHANCE_LOW), 땅볼은 옆으로 달려간 만큼 DIVE_CHANCE_GROUND.
## 확률은 타구 값으로 정해지는 연출용 가짜 난수(같은 타구 = 같은 동작, 판정에는 영향 없음)
const DIVE_MIN_M := 4.0
const DIVE_MAX_M := 12.0
const DIVE_CHANCE_AIR := 0.35
const DIVE_CHANCE_LOW := 0.75
const DIVE_CHANCE_GROUND := 0.3
const DIVE_POSE_AFTER_S := 0.6

static func _dives(r: BattedBallSim.Result) -> bool:
	var run := r.fielder_from.distance_to(r.fielder_to)
	if run < DIVE_MIN_M or run > DIVE_MAX_M or r.fielder == "P" or r.fielder == "C":
		return false
	var roll := fposmod(sin(r.ev_kmh * 12.9898 + r.spray_deg * 78.233 + r.launch_deg * 37.719) * 43758.5453, 1.0)
	if r.caught:
		var low: bool = r.path[r.path.size() - 1].z < 1.0
		return roll < (DIVE_CHANCE_LOW if low else DIVE_CHANCE_AIR)
	# 땅볼: 옆으로 달려간 공만 (앞으로 달려 나온 공은 다이빙하지 않는다)
	var dir := (r.fielder_to - r.fielder_from).normalized()
	var side := absf(dir.dot(r.fielder_to.normalized().orthogonal()))
	return side > 0.6 and roll < DIVE_CHANCE_GROUND


static func _fielder_pose(name: String, positions: Dictionary, cfg: BattedBallSim.Config, r: BattedBallSim.Result, play: PlaySimulator.PlayResult, cover: Dictionary, t: float, cam: Cam) -> Array:
	# 송구: 놓는 시각에 송구 출발 자리에 서 있던 사람이 던진다 (들고 뛰는 carry 는 송구가 아니다)
	if play != null:
		for th: Dictionary in play.throws:
			if th.get("carry", false) or t < float(th.release) - THROW_POSE_BEFORE_S or t > float(th.release) + THROW_POSE_AFTER_S:
				continue
			var from: Vector2 = th.from
			if _fielder_at(name, positions, cfg, r, play, cover, float(th.release)).distance_to(from) < 2.5:
				var dest: Vector2 = th.get("to", Vector2.ZERO if int(th.base) >= 4 else cfg.base_pos(int(th.base)) if int(th.base) > 0 else Vector2.ZERO)
				return ["throw", signf(cam.ground(dest).x - cam.ground(from).x)]
	elif name == r.fielder and r.throw_release > 0.0 and t >= r.throw_release - THROW_POSE_BEFORE_S and t <= r.throw_release + THROW_POSE_AFTER_S:
		var target := cfg.base_pos(r.throw_base)
		return ["throw", signf(cam.ground(target).x - cam.ground(r.fielder_to).x)]
	if name == r.fielder and not r.home_run and not r.path.is_empty():
		var ta := r.fielder_arrive
		var dove := _dives(r)
		var after := DIVE_POSE_AFTER_S if dove else CATCH_POSE_AFTER_S
		if t >= ta - CATCH_POSE_BEFORE_S and t <= ta + after:
			var face := signf(cam.ground(r.fielder_to).x - cam.ground(r.fielder_from).x)
			if dove:
				return ["dive", face]
			return ["fly" if r.caught else "grounder", face]
	return ["", 0.0]


## 주자: 접전 판정이 난 베이스(2루·3루·홈)에 들어가는 주자는 슬라이딩, 멈춰 있으면 베이스 위(무릎 짚기)·베이스 밖(리드 자세)
## 돌려주는 값: [칸 이름, 바라볼 쪽, 슬라이딩 진행 0~1 (슬라이딩이 아니면 -1)]
static func _runner_pose(cam: Cam, play: PlaySimulator.PlayResult, runner: RunnerAI, t: float, vel: Vector2) -> Array:
	for c: Dictionary in play.calls:
		var b: int = c.base
		# 1루는 달려 지나가는 베이스라 슬라이딩하지 않는다
		if c.get("warn", false) or b <= 1:
			continue
		var tc: float = c.t
		if t < tc - SLIDE_BEFORE_S or t > tc + SLIDE_AFTER_S:
			continue
		var bag := Vector2.ZERO if b >= 4 else runner.base_pos(b)
		var at_call: Vector2 = runner.track[clampi(int(tc / BattedBallSim.DT), 0, runner.track.size() - 1)]
		if at_call.distance_to(bag) < SLIDE_NEAR_M:
			var before: Vector2 = runner.track[clampi(int((tc - 0.2) / BattedBallSim.DT), 0, runner.track.size() - 1)]
			var progress := clampf((t - (tc - SLIDE_BEFORE_S)) / (SLIDE_BEFORE_S + SLIDE_AFTER_S), 0.0, 1.0)
			return ["slide", signf(cam.ground(at_call).x - cam.ground(before).x), progress]
	if vel.length() < 0.3:
		var at: Vector2 = runner.track[clampi(int(t / BattedBallSim.DT), 0, runner.track.size() - 1)]
		var near := INF
		for b in range(1, 5):
			near = minf(near, at.distance_to(Vector2.ZERO if b == 4 else runner.base_pos(b)))
		return ["onbase" if near < 1.5 else "lead", 0.0, -1.0]
	return ["", 0.0, -1.0]


static func _draw_runner(canvas: CanvasItem, cam: Cam, cfg: BattedBallSim.Config, r: BattedBallSim.Result, t: float) -> void:
	var bases := 0
	match r.outcome:
		SwingJudge.Outcome.SINGLE: bases = 1
		SwingJudge.Outcome.DOUBLE: bases = 2
		SwingJudge.Outcome.TRIPLE: bases = 3
		SwingJudge.Outcome.HOME_RUN: bases = 4
	# 판정에 쓴 시각 그대로: 출발 뒤 r.runner_mps 로 달리고, 베이스를 돌 때마다 잠깐 늦는다
	var run_t := maxf(0.0, t - r.runner_start)
	var dist := 0.0
	var left := run_t
	for leg in 4:
		var leg_t := cfg.base_distance / r.runner_mps + (r.round_base_s if leg > 0 else 0.0)
		if left >= leg_t:
			dist += cfg.base_distance
			left -= leg_t
		else:
			dist += maxf(0.0, left - (r.round_base_s if leg > 0 else 0.0)) * r.runner_mps
			break
	var limit := cfg.base_distance * maxi(bases, 1)
	if bases == 1 and r.is_first_base_play():
		limit += 3.0  # 내야안타는 1루를 지나쳐 달린다
	if r.caught:
		limit = cfg.base_distance * 0.6
	if r.is_first_base_play() and bases == 0:
		# 아웃: 공이 미트에 들어간 순간 그 자리에서 멈춘다
		var at_out := maxf(0.0, r.throw_arrive - r.runner_start) * r.runner_mps
		limit = minf(at_out, cfg.base_distance)
	dist = minf(dist, limit)
	var leg := mini(int(dist / cfg.base_distance), 3)
	var from := cfg.base_pos(leg) if leg > 0 else Vector2.ZERO
	var to := cfg.base_pos(leg + 1) if leg < 3 else Vector2.ZERO
	var at := from.lerp(to, (dist - leg * cfg.base_distance) / cfg.base_distance)
	var running := t > r.runner_start and dist < limit
	# 움직임은 다른 선수와 같게 "FIELDER_VEL_DT 초 동안 화면 픽셀"로 넘긴다
	var step := (cam.ground(to) - cam.ground(from)).normalized() * r.runner_mps * PixelArt.FIELDER_VEL_DT * cam.scale_at(Vector3(at.x, at.y, 0.0))
	RunnerArt.draw_runner(canvas, cam, at, true, step if running else Vector2.ZERO, t)


## 경기장 (도트: 칸마다 카메라를 거꾸로 계산해 칠한다, PixelArt.draw_broadcast_field)
static func _draw_field(canvas: CanvasItem, cam: Cam, cfg: BattedBallSim.Config) -> void:
	PixelArt.draw_broadcast_field(canvas, cam, cfg)
