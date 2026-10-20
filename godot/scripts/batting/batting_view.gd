class_name BattingView
extends Control
## 타석 장면 (타자 어깨 너머 시점): 경기장 배경, 투수, 큰 타자 뒷모습, 스트라이크존 꺾쇠, 날아오는 공,
## 타격 연출(히트스톱·흔들림·섬광·파편·꽃가루), 정타 뒤 타구 중계 화면, 판정 문구.
## 버튼·점수판 같은 HUD 는 BattingHud 가 위에 얹는다. 타자는 포즈별 그림(BatterSprite), 나머지는 단순 도형.
## 그리는 순서: 이 노드의 _draw(경기장·투수·존·공) → 타자(_batter) → 앞쪽 연출(_front: 맞은 공·섬광·꽃가루·판정 문구).
## 탭(손가락이 닿는 순간)은 tapped 시그널로 알린다.

## 탭한 곳의 존 좌표 (존 가운데 0, 가장자리 ±1)
signal tapped(zone_pos: Vector2)

## 공이 배트에 맞는 지점의 스윙 진행률
const CONTACT_SWING := 0.45
## 파울 타구가 화면 밖으로 날아가는 시간·미트 링이 퍼지는 시간 (연출 전용)
const FOUL_FLIGHT_MS := 350.0
const MITT_RING_MS := 140.0

## 장면 배치 (화면 비율). 지평선(외야 담장), 마운드, 존 가운데, 타자 머리
const HORIZON_Y := 0.40
const MOUND_Y := 0.535
## 투수 키 (화면 높이 비율)
const PITCHER_HEIGHT := 0.15
const ZONE_Y := 0.77
const ZONE_HALF_W := 0.045
const ZONE_HALF_H := 0.095
## 공이 향하는 곳은 투수 손보다 화면 높이의 이만큼은 아래 (공이 화면에서 위로 올라가지 않게)
const MIN_DESCENT := 0.03
## 타자: 두 발 사이의 가로 자리, 발바닥 높이(화면 아래로 살짝 잘린다), 키 — 화면 높이 비율
const BATTER_X := 0.27
const BATTER_FEET_Y := 1.03
const BATTER_HEIGHT := 0.74
## 맞는 순간 배트를 공 높이에 맞추려고 몸을 기울이는 한도 (도): 높은 공 쪽 / 낮은 공 쪽
const BATTER_LEAN_UP_DEG := 6.0
const BATTER_LEAN_DOWN_DEG := 20.0

var session: BattingSession
## 타자 등번호 (경기에서는 타순, 연습에서는 7)
var jersey_number := 7

# 좌표 (매 프레임 다시 계산)
var _release := Vector2.ZERO
var _zone_center := Vector2.ZERO
var _zone_half := Vector2.ZERO
var _batter: BatterSprite
## 타자보다 앞에 그리는 연출
var _front: Control
# 이번 프레임의 배트 (타자 자세와 잔상이 같이 쓴다)
var _bat_hands := Vector2.ZERO
var _bat_angle := 0.0
var _bat_length := 0.0
var _swing_progress := -1.0


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_STOP
	# 투수 그림은 원본보다 훨씬 작게 그려진다 — 밉맵으로 줄여야 깨지지 않는다
	texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS
	_batter = BatterSprite.new()
	add_child(_batter)
	_front = Control.new()
	_front.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_front.set_anchors_preset(Control.PRESET_FULL_RECT)
	_front.draw.connect(_draw_front)
	add_child(_front)


func _gui_input(event: InputEvent) -> void:
	# 손가락이 닿는 순간 스윙 (떼는 순간이 아니라) — 타이밍이 생명이다.
	# 터치는 Godot 가 마우스 누름으로도 바꿔 주므로 마우스 누름 하나만 받는다 (두 번 스윙 방지)
	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		var zone_pos: Vector2 = (event.position - _zone_center) / _zone_half if _zone_half != Vector2.ZERO else Vector2.INF
		tapped.emit(zone_pos)
		accept_event()


func _process(_delta: float) -> void:
	_batter.visible = session != null and _broadcast_ball() == null
	if _batter.visible:
		_layout()
		_pose_batter()
	queue_redraw()
	_front.queue_redraw()


## 화면 흔들림: 맞는 순간 크게, 빠르게 잦아든다
func _shake() -> Vector2:
	var pres := session.presentation
	var imp := session.impact
	var since_impact := session.since(imp.since_ms) if imp != null else INF
	var shake := Vector2.ZERO
	if since_impact < pres.shake_ms:
		var amp := pres.shake_weak_px
		if imp.home_run:
			amp = pres.shake_home_run_px
		elif imp.quality == SwingJudge.Quality.SOLID:
			amp = pres.shake_solid_px
		var decay := pow(1.0 - since_impact / pres.shake_ms, 2.0)
		var t := since_impact / 1000.0
		shake = Vector2(amp * decay * sin(TAU * 31.0 * t), amp * decay * cos(TAU * 23.0 * t))
	return shake


func _draw() -> void:
	if session == null:
		return
	draw_set_transform(_shake())
	_layout()
	# 정타 뒤에는 야구장 전체를 비추는 중계 화면으로 바뀐다
	var ball := _broadcast_ball()
	if ball != null:
		BroadcastView.draw(self, Rect2(Vector2.ZERO, size), session.config.ball_physics, ball, session.broadcast_time(), session.last_result.contact.bunt, false, session.last_result.play,
			int(session.situation.get("batter", -1)) if str(session.situation.get("control", "")) != "" else -1)
	else:
		_draw_stadium()
		_draw_field()
		_draw_pitcher()
		_draw_zone()
		_draw_catcher_mitt()
		_draw_ball_in_flight()
		_draw_batter_ground()
	draw_set_transform(Vector2.ZERO)


## 타자보다 앞에 그리는 것
func _draw_front() -> void:
	if session == null:
		return
	var canvas := _front
	var pres := session.presentation
	var imp := session.impact
	var since_impact := session.since(imp.since_ms) if imp != null else INF
	if _broadcast_ball() == null:
		canvas.draw_set_transform(_shake())
		_draw_hit_launch(canvas)
		canvas.draw_set_transform(Vector2.ZERO)
	# 정타 섬광: 화면 전체가 잠깐 하얗게
	if since_impact < pres.flash_ms and imp.quality == SwingJudge.Quality.SOLID:
		var strength := 0.55 if imp.home_run else 0.35
		canvas.draw_rect(Rect2(Vector2.ZERO, size), Color(Tokens.CHALK, strength * (1.0 - since_impact / pres.flash_ms)))
	# 홈런 꽃가루
	if since_impact < pres.confetti_ms and imp.home_run:
		_draw_confetti(canvas, since_impact / pres.confetti_ms)
	_draw_callout(canvas)


## 중계 화면을 그릴 때면 그 타구 결과, 아니면 null
func _broadcast_ball() -> BattedBallSim.Result:
	if session.phase != BattingSession.Phase.HIT or session.last_result == null:
		return null
	var c := session.last_result.contact
	if c == null or c.ball == null or session.broadcast_time() < 0.0:
		return null
	return c.ball


# ---------- 좌표 ----------

func _layout() -> void:
	_zone_center = Vector2(size.x * 0.5, size.y * ZONE_Y)
	_zone_half = Vector2(size.x * ZONE_HALF_W, size.y * ZONE_HALF_H)
	_release = Vector2(size.x * 0.5 - size.y * 0.012, size.y * (MOUND_Y - 0.095))
	if PitcherPoses.available():
		# 공은 공 놓는 그림의 손에서 출발한다
		_release = Vector2(size.x * 0.5, size.y * MOUND_Y) + PitcherPoses.release_hand(session.presentation.windup_ms) * (size.y * PITCHER_HEIGHT / PitcherPoses.HEIGHT)


## 존 좌표 → 화면
func zone_to_screen(z: Vector2) -> Vector2:
	return _zone_center + Vector2(z.x * _zone_half.x, z.y * _zone_half.y)


## 공을 놓고 t_ms 뒤 공이 향하는 존 평면 자리 — 변화(break·arc)만 화면용으로 과장한다 (도착 순간엔 정확히 aim)
func _pointing(p: Pitch, t_ms: float) -> Vector2:
	var traj := p.trajectory
	return traj.aim + (traj.pointing_at(t_ms) - traj.aim) * session.presentation.movement_scale


## 공을 놓고 t_ms 뒤 공의 화면 위치(xy)와 크기 비율(z, 0~1).
## 공은 투수 손에서 "지금 향하는 곳"으로 원근을 따라 다가온다: 꺾이기 전엔 곧게, 꺾이면 향하는 곳이 도착점으로 미끄러진다
func _ball(p: Pitch, t_ms: float) -> Vector3:
	# 원근감: 가까워질수록 빨리 커지고 빨리 움직이는 것처럼. 깊이는 거리 진행률(감속 반영),
	# 홈플레이트를 지난 뒤에는 시간대로 조금 더 다가온다
	var t := p.trajectory.distance_fraction(t_ms) if t_ms < p.flight_ms else minf(t_ms / p.flight_ms, 1.15)
	var s := 0.25 * t + 0.75 * t * t
	var target := zone_to_screen(_pointing(p, t_ms))
	# 향하는 곳이 손보다 위면 공이 화면에서 솟아오른다 → 손 바로 아래로 눌러 둔다 (떨어지는 공은 그냥 떨어진다)
	target.y = maxf(target.y, _release.y + size.y * MIN_DESCENT)
	var pos := _release.lerp(target, s)
	return Vector3(pos.x, pos.y, s)


func _ball_radius(s: float) -> float:
	return 2.0 + size.y * 0.03 * s


# ---------- 경기장 ----------

## 하늘·조명탑·관중석·전광판·외야 담장
func _draw_stadium() -> void:
	var w := size.x
	var h := size.y
	var horizon := h * HORIZON_Y
	# 하늘 (위 → 지평선 그라데이션)
	draw_polygon(PackedVector2Array([Vector2(0, 0), Vector2(w, 0), Vector2(w, horizon), Vector2(0, horizon)]),
		PackedColorArray([Tokens.SKY_TOP, Tokens.SKY_TOP, Tokens.SKY_BOTTOM, Tokens.SKY_BOTTOM]))
	# 조명탑
	for fx in [0.16, 0.84]:
		var x: float = w * fx
		draw_line(Vector2(x, h * 0.17), Vector2(x, horizon), Tokens.STANDS_DARK, maxf(w * 0.006, 3.0))
		var head := Rect2(x - w * 0.035, h * 0.14, w * 0.07, h * 0.05)
		draw_rect(head, Tokens.SCOREBOARD)
		for i in 4:
			for j in 2:
				var c := head.position + Vector2(head.size.x * (i + 0.5) / 4.0, head.size.y * (j + 0.5) / 2.0)
				draw_circle(c, head.size.y * 0.18, Tokens.LIGHT, true, -1.0, true)
	# 관중석 (윗층·아랫층) + 관중 점 (번호로 정해지는 가짜 난수라 매번 같다)
	var upper := [Vector2(0, h * 0.235), Vector2(w, h * 0.235), Vector2(w, h * 0.32), Vector2(0, h * 0.32)]
	draw_colored_polygon(PackedVector2Array(upper), Tokens.STANDS_DARK)
	draw_colored_polygon(PackedVector2Array([Vector2(0, h * 0.31), Vector2(w, h * 0.31), Vector2(w, horizon), Vector2(0, horizon)]), Tokens.STANDS)
	var rows := 4
	for r in rows:
		var y := lerpf(h * 0.25, horizon - h * 0.014, float(r) / (rows - 1))
		var n := int(w / 9.0)
		for i in n:
			var hsh := fposmod(sin(i * 12.9898 + r * 78.233) * 43758.5453, 1.0)
			var x := (i + 0.5 + (hsh - 0.5) * 0.6) * w / n
			if absf(x - w * 0.5) < w * 0.13 and y < h * 0.34:
				continue  # 전광판 뒤
			draw_circle(Vector2(x, y), maxf(h * 0.0045, 1.3), Color(Tokens.CROWD[int(hsh * 997) % Tokens.CROWD.size()], 0.55), true, -1.0, true)
	# 전광판 (가운데)
	var board := Rect2(w * 0.38, h * 0.13, w * 0.24, h * 0.2)
	draw_rect(board, Tokens.SCOREBOARD)
	draw_rect(Rect2(board.position + Vector2(board.size.x * 0.08, board.size.y * 0.12), board.size * Vector2(0.84, 0.5)), Tokens.STANDS_DARK)
	for i in 6:
		draw_circle(board.position + Vector2(board.size.x * (0.15 + i * 0.14), board.size.y * 0.8), board.size.y * 0.05,
			Tokens.COUNT_BALL if i < 2 else (Tokens.COUNT_STRIKE if i < 4 else Tokens.COUNT_OUT), true, -1.0, true)
	# 외야 담장
	draw_rect(Rect2(0, horizon - h * 0.018, w, h * 0.03), Tokens.WALL)


## 잔디(줄무늬)·내야 흙·마운드·파울 라인·홈 주변·타석 선·홈플레이트
func _draw_field() -> void:
	var w := size.x
	var h := size.y
	var horizon := h * HORIZON_Y + h * 0.012
	# 잔디 줄무늬: 멀수록 좁게 (원근)
	var bands := 12
	for i in bands:
		var y0 := horizon + (h - horizon) * pow(float(i) / bands, 1.6)
		var y1 := horizon + (h - horizon) * pow(float(i + 1) / bands, 1.6)
		draw_rect(Rect2(0, y0, w, y1 - y0 + 1), Tokens.GRASS if i % 2 == 0 else Tokens.GRASS_LIGHT)
	# 내야 흙 (마운드 뒤 호) + 내야 잔디
	_draw_ellipse(Vector2(w * 0.5, h * 0.52), Vector2(w * 0.5, h * 0.085), Tokens.DIRT)
	_draw_ellipse(Vector2(w * 0.5, h * 0.585), Vector2(w * 0.38, h * 0.07), Tokens.GRASS_LIGHT)
	# 파울 라인 (홈에서 1·3루 쪽으로 벌어진다)
	var home := Vector2(w * 0.5, h * 0.95)
	draw_line(home, Vector2(w * 0.04, h * 0.47), Tokens.CHALK, 2.0, true)
	draw_line(home, Vector2(w * 0.96, h * 0.47), Tokens.CHALK, 2.0, true)
	# 마운드
	_draw_ellipse(Vector2(w * 0.5, h * MOUND_Y), Vector2(w * 0.06, h * 0.024), Tokens.DIRT)
	draw_rect(Rect2(w * 0.5 - w * 0.012, h * MOUND_Y - h * 0.004, w * 0.024, h * 0.006), Tokens.CHALK)
	# 홈 주변 흙 + 타석 선 + 홈플레이트
	_draw_ellipse(Vector2(w * 0.5, h * 1.0), Vector2(w * 0.24, h * 0.12), Tokens.DIRT)
	for side in [-1.0, 1.0]:
		var box := PackedVector2Array([
			Vector2(w * (0.5 + side * 0.035), h * 0.9), Vector2(w * (0.5 + side * 0.11), h * 0.9),
			Vector2(w * (0.5 + side * 0.13), h * 1.02), Vector2(w * (0.5 + side * 0.04), h * 1.02)])
		var closed := box.duplicate()
		closed.append(box[0])
		draw_polyline(closed, Tokens.CHALK, 2.0, true)
	var pw := w * 0.026
	draw_colored_polygon(PackedVector2Array([
		Vector2(w * 0.5 - pw, h * 0.935), Vector2(w * 0.5 + pw, h * 0.935), Vector2(w * 0.5 + pw, h * 0.952),
		Vector2(w * 0.5, h * 0.968), Vector2(w * 0.5 - pw, h * 0.952)]), Tokens.CHALK)


# ---------- 사람 ----------

## 투수: 세트 자세(선택 시간) → 다리 들기 → 몸 돌려 모으기 → 공 놓기 → 팔로스루. 공을 놓는 손이 공이 출발하는 곳.
## 포즈 그림(PitcherPoses)이 있으면 그 그림을, 없으면 옛 도형 투수를 그린다
func _draw_pitcher() -> void:
	var pres := session.presentation
	if PitcherPoses.available():
		var windup := 0.0
		var since_release := -1.0
		match session.phase:
			BattingSession.Phase.WINDUP:
				windup = clampf(session.since(session.phase_since_ms) / pres.windup_ms, 0.0, 1.0)
			BattingSession.Phase.FLIGHT, BattingSession.Phase.HIT:
				since_release = session.since(session.phase_since_ms) + (session.pitch.flight_ms if session.phase == BattingSession.Phase.HIT else 0.0)
		var mound := Vector2(size.x * 0.5, size.y * MOUND_Y)
		_draw_ellipse(mound, Vector2(size.y * PITCHER_HEIGHT * 0.3, size.y * PITCHER_HEIGHT * 0.07), Tokens.SHADOW)
		PitcherPoses.draw(self, windup, since_release, pres.windup_ms, mound + _shake(), size.y * PITCHER_HEIGHT, _shake())
		return
	var u := size.y * 0.0042  # 투수 크기 단위
	var t := 0.0
	var follow := 0.0
	match session.phase:
		BattingSession.Phase.WINDUP:
			t = clampf(session.since(session.phase_since_ms) / pres.windup_ms, 0.0, 1.0)
		BattingSession.Phase.FLIGHT:
			t = 1.0
			follow = clampf(session.since(session.phase_since_ms) / 200.0, 0.0, 1.0)
	var feet := Vector2(size.x * 0.5, size.y * MOUND_Y)
	var lift := sin(clampf(t / 0.55, 0.0, 1.0) * PI / 2.0) if t < 0.55 else 1.0 - (t - 0.55) / 0.45
	var crouch := u * 2.0 * (clampf((t - 0.55) / 0.45, 0.0, 1.0) + follow)
	var hip := feet - Vector2(0, u * 9.0 - crouch)
	var shoulder := hip - Vector2(0, u * 8.0)
	var uniform := Tokens.UNIFORM_THEM
	_draw_ellipse(feet + Vector2(0, u), Vector2(u * 5.0, u * 1.4), Tokens.SHADOW)
	draw_line(hip, feet + Vector2(-u * 2.5, 0), Tokens.CHALK, u * 2.4, true)  # 디딤발
	if t < 0.55:
		draw_polyline(PackedVector2Array([hip, hip + Vector2(u * 4.0, -u * 4.0 * lift + u * 2.0), hip + Vector2(u * 4.5, u * 6.0 - u * 5.0 * lift)]), Tokens.CHALK, u * 2.4, true)
	else:
		draw_line(hip, feet + Vector2(u * 3.5 + u * follow, 0), Tokens.CHALK, u * 2.4, true)
	draw_line(hip, shoulder, uniform, u * 5.0, true)
	draw_circle(shoulder - Vector2(0, u * 4.2), u * 3.6, Tokens.SKIN, true, -1.0, true)
	draw_circle(shoulder - Vector2(0, u * 5.2), u * 3.4, uniform.darkened(0.35), true, -1.0, true)  # 모자
	# 던지는 팔: 가슴 → 아래 뒤 → 위 뒤 → 머리 위(공 놓음) → 팔로스루
	var keys := [Vector2(0, 4), Vector2(-6, 5), Vector2(-6, -7), Vector2(-3, -8.5), Vector2(5, 6)]
	var hand: Vector2
	if follow > 0.0:
		hand = keys[3].lerp(keys[4], follow)
	elif t < 0.45:
		hand = keys[0]
	elif t < 0.7:
		hand = keys[0].lerp(keys[1], (t - 0.45) / 0.25)
	elif t < 0.9:
		hand = keys[1].lerp(keys[2], (t - 0.7) / 0.2)
	else:
		hand = keys[2].lerp(keys[3], (t - 0.9) / 0.1)
	draw_line(shoulder, shoulder + hand * u, uniform, u * 1.8, true)
	draw_line(shoulder, shoulder + Vector2(4.5, 3 + 2.0 * follow) * u, uniform, u * 1.8, true)  # 글러브 팔
	if session.phase == BattingSession.Phase.WINDUP or session.phase == BattingSession.Phase.WAITING:
		draw_circle(shoulder + hand * u, u * 1.2, Tokens.CHALK, true, -1.0, true)


## 타자 자세 (오른손 타자, 홈플레이트 왼쪽). 투수가 던지기 시작하면 힘을 모으고(load), 스윙하면 맞는 순간 →
## 팔로스루 그림으로 넘어간다. 히트스톱 동안은 맞는 순간 그림에서 멈춘다. 번트면 번트 그림.
## 그림은 BatterSprite 가 갈아 끼우고, 여기서는 언제 얼마나 휘둘렀는지를 정한다
func _pose_batter() -> void:
	var pres := session.presentation
	var h := size.y
	var in_flight := session.phase == BattingSession.Phase.FLIGHT
	var in_hit := session.phase == BattingSession.Phase.HIT
	var swing_progress := -1.0
	if in_hit:
		var e := session.since(session.phase_since_ms)
		# 히트스톱 동안 배트는 맞은 자리에서 멈춘다
		swing_progress = CONTACT_SWING if e < pres.hit_stop_ms else minf(CONTACT_SWING + (e - pres.hit_stop_ms) / pres.swing_ms, 1.0)
	elif in_flight and session.swung:
		swing_progress = minf(0.35 + session.since(session.swing_since_ms) / pres.swing_ms, 1.0)
	var load := 0.0
	if session.phase == BattingSession.Phase.WINDUP:
		load = clampf(session.since(session.phase_since_ms) / pres.windup_ms * 1.4 - 0.4, 0.0, 1.0)
	elif in_flight and not session.swung:
		load = 1.0

	var unit := h * BATTER_HEIGHT / BatterSprite.HEIGHT
	var feet := Vector2(size.x * BATTER_X, h * BATTER_FEET_Y)
	_batter.scale = Vector2.ONE * unit
	_batter.position = feet + _shake()
	var bunt := session.swing_type == "bunt"
	_batter.pose(load, swing_progress, bunt)
	# 맞는 순간 그림은 한 장이라 배트 높이가 정해져 있다. 발을 축으로 몸 전체를 조금 기울여 배트를 공 높이에 맞춘다
	# (낮은 공엔 숙이고 높은 공엔 선다). 팔로스루로 넘어가면 바로 선다
	var lean := 0.0
	if not bunt and swing_progress >= 0.0 and swing_progress < BatterSprite.FOLLOW_FROM and _batter.has_frame("contact"):
		var b := _ball(session.pitch, session.swing_at_ms)
		var to_ball := Vector2(b.x, b.y) - feet
		lean = clampf(angle_difference(BatterSprite.CONTACT_BARREL.angle(), to_ball.angle()), deg_to_rad(-BATTER_LEAN_UP_DEG), deg_to_rad(BATTER_LEAN_DOWN_DEG))
	_batter.rotation = lean

	# 배트 궤적 잔상: 맞은 공이면 공이 있던 자리까지, 아니면 스윙 진행만큼 쓸고 지나간다
	_bat_hands = feet + BatterSprite.HANDS * unit
	_bat_length = BatterSprite.BAT_LENGTH * unit
	_bat_angle = _bat_angle_deg(maxf(swing_progress, 0.0))
	_swing_progress = -1.0 if bunt else swing_progress


## 타자 발밑 그림자와 배트 궤적 잔상 (타자 뒤에 깔린다)
func _draw_batter_ground() -> void:
	var h := size.y
	_draw_ellipse(Vector2(size.x * BATTER_X, h * 0.985), Vector2(h * 0.2, h * 0.035), Tokens.SHADOW)
	# 스윙 그림이 없을 때만(준비 자세로 대신하는 동안) 잔상이 스윙을 알려 준다
	if _swing_progress >= 0.0 and _swing_progress < 1.0 and not _batter.has_frame("contact"):
		var wedge := PackedVector2Array([_bat_hands])
		var from := _bat_angle_deg(0.0)
		for i in 13:
			wedge.append(_bat_hands + _bat_dir(lerpf(from, _bat_angle, i / 12.0)) * _bat_length)
		draw_colored_polygon(wedge, Color(Tokens.CHALK, 0.3))


# ---------- 존·공 ----------

## 스트라이크존: 네 모서리 꺾쇠 + (노려치기) 3×3 칸
func _draw_zone() -> void:
	var zone := Rect2(_zone_center - _zone_half, _zone_half * 2.0)
	var arm := minf(_zone_half.x, _zone_half.y) * 0.45
	var c := Tokens.BAD
	for corner in [zone.position, Vector2(zone.end.x, zone.position.y), zone.end, Vector2(zone.position.x, zone.end.y)]:
		var sx := 1.0 if corner.x < _zone_center.x else -1.0
		var sy := 1.0 if corner.y < _zone_center.y else -1.0
		draw_line(corner, corner + Vector2(arm * sx, 0), c, 2.5, true)
		draw_line(corner, corner + Vector2(0, arm * sy), c, 2.5, true)
	if not session.config.aim_enabled:
		return
	var line := Color(Tokens.CHALK, 0.22)
	for i in [1, 2]:
		var fx: float = zone.position.x + zone.size.x * i / 3.0
		var fy: float = zone.position.y + zone.size.y * i / 3.0
		draw_line(Vector2(fx, zone.position.y), Vector2(fx, zone.end.y), line, 1.0)
		draw_line(Vector2(zone.position.x, fy), Vector2(zone.end.x, fy), line, 1.0)
	if session.aim_cell >= 0:
		var cell_size := zone.size / 3.0
		var r := Rect2(zone.position + Vector2(session.aim_cell % 3, session.aim_cell / 3) * cell_size, cell_size)
		draw_rect(r, Color(Tokens.WARN, 0.3))
		draw_rect(r, Tokens.WARN, false, 2.0)


## 포수 미트: 투수가 노린 곳(힌트). 제구가 흔들리면 공은 미트에서 벗어난다
func _draw_catcher_mitt() -> void:
	if not session.config.catcher_hint or session.pitch == null:
		return
	var show := session.phase == BattingSession.Phase.WINDUP
	if session.phase == BattingSession.Phase.FLIGHT:
		show = session.since(session.phase_since_ms) < session.pitch.flight_ms
	if not show:
		return
	# 예상 투구 위치: 변화구는 처음엔 "휘기 전 방향" 쪽에 있다가 공이 꺾이는 만큼 따라 움직여 미트 자리에 닿는다
	var p := session.pitch
	var t_ms := session.since(session.phase_since_ms) if session.phase == BattingSession.Phase.FLIGHT else 0.0
	var at := zone_to_screen(p.intended + _pointing(p, t_ms) - p.trajectory.aim)
	var r := size.y * 0.024
	draw_circle(at, r, Color(Tokens.MITT, 0.9), true, -1.0, true)
	draw_circle(at, r * 0.55, Color(Tokens.DIRT, 0.95), true, -1.0, true)


func _draw_ball_in_flight() -> void:
	if session.phase != BattingSession.Phase.FLIGHT:
		return
	var pres := session.presentation
	var p := session.pitch
	var progress := session.since(session.phase_since_ms) / p.flight_ms
	var foul := session.swung and session.swing_outcome.call == AtBat.Call.FOUL
	if foul:
		# 파울: 맞은 자리에서 옆·뒤로 튕겨 나간다
		var b := _ball(p, session.swing_at_ms)
		var hit_pos := Vector2(b.x, b.y)
		var t := clampf(session.since(session.swing_since_ms) / FOUL_FLIGHT_MS, 0.0, 1.0)
		var side := -1.0 if session.swing_outcome.contact.angle_deg < 0 else 1.0
		_draw_launched_ball(self, hit_pos, hit_pos + Vector2(side * size.x * 0.6, -size.y * 0.7), t, _ball_radius(b.z))
		_draw_burst(self, hit_pos, session.since(session.swing_since_ms), SwingJudge.Quality.FOUL)
	elif progress <= 1.15:
		var now := session.since(session.phase_since_ms)
		var b := _ball(p, now)
		var pos := Vector2(b.x, b.y)
		var r := _ball_radius(b.z)
		# 구종이 드러나면 테두리가 구종 색이 되고, 직구가 아니면 공도 그 색으로 물든다
		var revealed := p.is_revealed_at(progress)
		var type_color := _pitch_color(p.type)
		var offspeed := revealed and p.type != "four_seam"
		# 잔상: 지난 자리들을 흐리게 — 휘는 길이 눈에 보인다 (궤적이 시간만의 함수라 지난 자리를 그대로 다시 구한다)
		var dots := pres.trail_dots
		for i in range(dots, 0, -1):
			var t_ms := now - pres.trail_ms * i / dots
			if t_ms <= 0.0 or t_ms >= p.flight_ms:
				continue
			var tb := _ball(p, t_ms)
			var fade := 1.0 - float(i) / (dots + 1)
			draw_circle(Vector2(tb.x, tb.y), _ball_radius(tb.z) * (0.45 + 0.45 * fade), Color(type_color if offspeed else Tokens.CHALK, 0.45 * fade), true, -1.0, true)
		draw_circle(pos + Vector2(r * 0.3, r * 0.5), r, Tokens.SHADOW, true, -1.0, true)
		draw_circle(pos, r, type_color if offspeed else Tokens.CHALK, true, -1.0, true)
		draw_circle(pos, r, type_color if revealed else Tokens.INK, false, 1.5, true)
	# 미트에 꽂힌 순간 작은 링
	if session.mitt_played and progress < 1.0 + MITT_RING_MS / p.flight_ms:
		var end := _ball(p, p.flight_ms)
		var t := clampf((progress - 1.0) * p.flight_ms / MITT_RING_MS, 0.0, 1.0)
		draw_circle(Vector2(end.x, end.y), _ball_radius(end.z) * (1.2 + t * 1.5), Color(Tokens.CHALK, 1.0 - t), false, 2.0, true)


## 맞은 공: 히트스톱 동안 맞은 자리에 멈춰 있다가 경기장 쪽으로 튕겨 나간다 (그다음 중계 화면)
func _draw_hit_launch(canvas: CanvasItem) -> void:
	if session.phase != BattingSession.Phase.HIT:
		return
	var pres := session.presentation
	var b := _ball(session.pitch, session.swing_at_ms)
	var hit_pos := Vector2(b.x, b.y)
	var c := session.last_result.contact
	var e := session.since(session.phase_since_ms)
	var t := clampf((e - pres.hit_stop_ms) / pres.launch_ms, 0.0, 1.0)
	var ang := deg_to_rad(c.angle_deg)
	var target: Vector2
	if c.batted_ball == SwingJudge.BattedBall.GROUND:
		target = Vector2(size.x * 0.5 + sin(ang) * size.x * 0.5, size.y * MOUND_Y)
	else:
		target = Vector2(size.x * 0.5 + sin(ang) * size.x * 0.8, -size.y * 0.1)
	if t < 1.0:
		_draw_launched_ball(canvas, hit_pos, target, t, _ball_radius(b.z))
	# 이펙트는 공 위에: 히트스톱 동안 별이 공을 덮었다가 공이 그 속에서 튀어 나간다
	_draw_burst(canvas, hit_pos, e, c.quality, session.impact != null and session.impact.home_run)


func _pitch_color(type: String) -> Color:
	return Tokens.PITCH_COLORS.get(type, Tokens.PRIMARY)


# ---------- 연출 ----------

## 맞은 공이 from 에서 to 로 날아가며 작아진다. 꼬리를 그린다
func _draw_launched_ball(canvas: CanvasItem, from: Vector2, to: Vector2, t: float, start_radius: float) -> void:
	var at := func(tt: float) -> Vector2:
		return from.lerp(to, 1.0 - (1.0 - tt) * (1.0 - tt))  # 처음에 빠르게
	var radius := func(tt: float) -> float:
		return start_radius * (1.0 - 0.75 * tt)
	for k in range(1, 7):
		var tt := maxf(t - k * 0.025, 0.0)
		canvas.draw_circle(at.call(tt), radius.call(tt) * (1.0 - k * 0.08), Color(Tokens.CHALK, 0.55 - k * 0.08), true, -1.0, true)
	canvas.draw_circle(at.call(t), radius.call(t), Tokens.CHALK, true, -1.0, true)
	canvas.draw_circle(at.call(t), radius.call(t), Tokens.INK, false, 1.0, true)


## 타격 이펙트: 맞은 자리에서 터지는 만화식 별. 맞은 질에 따라 급이 다르다 —
##   파울: 작은 흰 별 하나 / 빗맞음: 회색빛 작은 별 + 먼지 / 정타: 주황·노랑·흰 세 겹 별 + 충격파 고리 + 불티 /
##   홈런: 더 크고 오래 가는 세 겹 별 + 화면으로 뻗는 빛살 + 고리 두 개 + 불티 두 배.
## elapsed_ms 는 맞은 뒤 지난 시간. 모양은 조각 번호로 정해져서 매번 같다
func _draw_burst(canvas: CanvasItem, center: Vector2, elapsed_ms: float, quality: SwingJudge.Quality, home_run: bool = false) -> void:
	var pres := session.presentation
	var t := elapsed_ms / (pres.burst_home_run_ms if home_run else pres.burst_ms)
	if t >= 1.0 or t < 0.0 or quality == SwingJudge.Quality.MISS:
		return
	var h := size.y
	# 처음에 "팡" 하고 커졌다가(살짝 넘쳤다 돌아옴) 끝에서 사라진다
	var pop := minf(t / 0.18, 1.0)
	var grow := pop * (1.0 + 0.22 * sin(pop * PI))
	var alpha := clampf((1.0 - t) / 0.4, 0.0, 1.0)
	var out := 1.0 - (1.0 - t) * (1.0 - t)

	if quality == SwingJudge.Quality.FOUL:
		_draw_star(canvas, center, h * 0.045 * grow, 0.42, 4, 0.4, Color(Tokens.CHALK, alpha), alpha)
		return
	if quality == SwingJudge.Quality.WEAK:
		# 먼지 세 덩이가 옆으로 흩어진다
		for i in 3:
			var dir := Vector2.from_angle(-0.5 + i * 1.3)
			canvas.draw_circle(center + dir * h * 0.07 * out, h * 0.016 * (1.0 - t * 0.5), Color(Tokens.DIRT.lightened(0.35), alpha * 0.8), true, -1.0, true)
		_draw_star(canvas, center, h * 0.062 * grow, 0.5, 6, 0.3, Color(Tokens.CHALK.darkened(0.12), alpha), alpha)
		return

	# 정타 · 홈런
	var reach := h * (0.2 if home_run else 0.125)
	var points := 14 if home_run else 10
	var spin := t * (0.5 if home_run else 0.2)
	if home_run:
		# 빛살: 화면 멀리까지 뻗는 얇은 부채꼴
		for i in 12:
			var ang := TAU * i / 12.0 + spin * 0.6
			var half := 0.07 if i % 2 == 0 else 0.04
			var far := h * (0.75 if i % 2 == 0 else 0.5) * (0.4 + 0.6 * out)
			canvas.draw_colored_polygon(PackedVector2Array([center, center + Vector2.from_angle(ang - half) * far, center + Vector2.from_angle(ang + half) * far]),
				Color(Tokens.WARN, alpha * 0.45))
	# 충격파 고리
	canvas.draw_circle(center, reach * (0.5 + 1.1 * out), Color(Tokens.CHALK, alpha * 0.9), false, maxf(h * 0.012 * (1.0 - t), 1.0), true)
	if home_run:
		canvas.draw_circle(center, reach * (0.3 + 1.9 * out), Color(Tokens.WARN, alpha * 0.7), false, maxf(h * 0.016 * (1.0 - t), 1.0), true)
	# 불티: 작은 마름모가 사방으로 튄다
	var sparks := 16 if home_run else 8
	for i in sparks:
		var ang := TAU * i / sparks + 0.2 + fposmod(sin(i * 12.9898) * 43758.5453, 1.0) * 0.5
		var far := reach * (1.1 + 0.9 * fposmod(sin(i * 78.233) * 43758.5453, 1.0)) * out
		var at := center + Vector2.from_angle(ang) * far + Vector2(0, h * 0.06 * t * t)
		_draw_star(canvas, at, h * 0.016 * (1.0 - t * 0.6), 0.45, 4, ang, Color(Tokens.WARN if i % 2 == 0 else Tokens.CHALK, alpha), 0.0)
	# 세 겹 별
	_draw_star(canvas, center, reach * grow, 0.55, points, spin, Color(Tokens.ACCENT, alpha), alpha)
	_draw_star(canvas, center, reach * 0.74 * grow, 0.55, points, spin + PI / points, Color(Tokens.WARN, alpha), 0.0)
	_draw_star(canvas, center, reach * 0.44 * grow, 0.6, points, spin, Color(Tokens.CHALK, alpha), 0.0)


## 뾰족한 별 하나. inner 는 안쪽 꼭짓점의 비율(작을수록 뾰족), outline_alpha 가 0보다 크면 남색 테두리를 두른다
func _draw_star(canvas: CanvasItem, center: Vector2, radius: float, inner: float, points: int, rotation_rad: float, color: Color, outline_alpha: float) -> void:
	if radius < 0.5:
		return
	var pts := PackedVector2Array()
	for i in points * 2:
		var r := radius if i % 2 == 0 else radius * inner
		pts.append(center + Vector2.from_angle(rotation_rad + PI * i / points) * r)
	# 오목한 별은 한 번에 못 칠한다 — 가운데에서 꼭짓점으로 부채꼴처럼 나눠 칠한다
	for i in pts.size():
		canvas.draw_colored_polygon(PackedVector2Array([center, pts[i], pts[(i + 1) % pts.size()]]), color)
	if outline_alpha > 0.0:
		pts.append(pts[0])
		canvas.draw_polyline(pts, Color(Tokens.INK, outline_alpha), maxf(radius * 0.06, 1.5), true)


## 홈런 꽃가루. 위치는 조각 번호로 정해지는 가짜 난수라 매번 같다
func _draw_confetti(canvas: CanvasItem, t: float) -> void:
	var alpha := (1.0 - t) / 0.25 if t > 0.75 else 1.0
	for i in 60:
		var hsh := func(k: int) -> float:
			return fposmod(sin(i * 12.9898 + k * 78.233) * 43758.5453, 1.0)
		var x: float = size.x * hsh.call(1) + sin(t * 8.0 + i) * 18.0
		var y: float = -20.0 + size.y * 1.1 * t * (0.7 + 0.5 * hsh.call(2))
		var wdt: float = 5.0 + 4.0 * hsh.call(3)
		canvas.draw_set_transform(Vector2(x, y), t * TAU * 2.0 * (hsh.call(4) - 0.5))
		canvas.draw_rect(Rect2(-wdt / 2.0, -wdt / 4.0, wdt, wdt / 2.0), Color(Tokens.CONFETTI[i % Tokens.CONFETTI.size()], alpha))
	canvas.draw_set_transform(Vector2.ZERO)


## 판정 문구 카드. 처음에 "팡" 하고 커졌다 돌아온다 (홈런은 더 크게)
func _draw_callout(canvas: CanvasItem) -> void:
	var c := session.callout
	if c == null:
		return
	var age := session.since(c.since_ms) - c.delay_ms
	var over := session.phase == BattingSession.Phase.OVER
	if age < 0.0 or (not over and age > session.presentation.result_hold_ms):
		return
	var big := c.tone == BattingSession.Tone.BIG
	var pop_ms := 320.0 if big else 150.0
	var pop_amp := 0.6 if big else 0.3
	var pop := 1.0 + pop_amp * sin(age / pop_ms * PI) if age < pop_ms else 1.0
	var color := Tokens.INK
	match c.tone:
		BattingSession.Tone.GOOD: color = Tokens.GOOD
		BattingSession.Tone.BAD: color = Tokens.BAD
		BattingSession.Tone.BIG: color = Tokens.ACCENT

	var lines: Array = [[c.text, Tokens.FONT_BOLD, Tokens.FONT_HERO, color]]
	if c.detail != "":
		lines.append([c.detail, Tokens.FONT_REGULAR, Tokens.FONT_BODY, Tokens.INK])
	if over:
		lines.append(["탭해서 계속" if session.single else "탭해서 다음 타석", Tokens.FONT_REGULAR, Tokens.FONT_CAPTION, Tokens.INK_SOFT])
	var width := 0.0
	var height := 0.0
	for l: Array in lines:
		var f: Font = l[1]
		width = maxf(width, f.get_string_size(l[0], HORIZONTAL_ALIGNMENT_LEFT, -1, l[2]).x)
		height += f.get_height(l[2])
	var box := Vector2(width + Tokens.SPACE_LG * 2, height + Tokens.SPACE_MD * 2)
	canvas.draw_set_transform(Vector2(size.x * 0.5, size.y * 0.42), 0.0, Vector2(pop, pop))
	var style := StyleBoxFlat.new()
	style.bg_color = Tokens.SURFACE
	style.set_corner_radius_all(Tokens.RADIUS_CARD)
	style.anti_aliasing = true
	canvas.draw_style_box(style, Rect2(-box / 2.0, box))
	var y := -box.y / 2.0 + Tokens.SPACE_MD
	for l: Array in lines:
		var f: Font = l[1]
		y += f.get_ascent(l[2])
		canvas.draw_string(f, Vector2(-box.x / 2.0, y), l[0], HORIZONTAL_ALIGNMENT_CENTER, box.x, l[2], l[3])
		y += f.get_descent(l[2])
	canvas.draw_set_transform(Vector2.ZERO)


# ---------- 도우미 ----------

## 배트 각도 (위쪽 0°, 시계 방향 +): 대기 −13°(머리 옆으로 세워 든다) → 수평(90°)을 지나 홈 쪽으로 → 팔로스루 +150°.
## 처음에 빠르고 끝에서 느려진다
func _bat_angle_deg(progress: float) -> float:
	var e := 1.0 - (1.0 - progress) * (1.0 - progress)
	return -13.0 + 163.0 * e


## 각도(위쪽 = 0°, 시계 방향 +)의 방향 벡터
func _bat_dir(angle_deg: float) -> Vector2:
	var r := deg_to_rad(angle_deg)
	return Vector2(sin(r), -cos(r))


func _draw_ellipse(center: Vector2, radii: Vector2, color: Color) -> void:
	var pts := PackedVector2Array()
	for i in 40:
		var t := TAU * i / 40.0
		pts.append(center + Vector2(cos(t) * radii.x, sin(t) * radii.y))
	draw_colored_polygon(pts, color)
