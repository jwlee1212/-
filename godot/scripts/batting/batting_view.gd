class_name BattingView
extends Control
## 타석 장면 (타자 어깨 너머 시점): 경기장 배경, 투수, 큰 타자 뒷모습, 스트라이크존 꺾쇠, 날아오는 공,
## 타격 연출(히트스톱·흔들림·섬광·파편·꽃가루), 정타 뒤 타구 중계 화면, 판정 문구.
## 버튼·점수판 같은 HUD 는 BattingHud 가 위에 얹는다. 그림은 단순 도형 (Skeleton2D 캐릭터는 G8).
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
const ZONE_Y := 0.77
const ZONE_HALF_W := 0.045
const ZONE_HALF_H := 0.095
const BATTER_X := 0.345
const BATTER_HEAD_Y := 0.55

var session: BattingSession
## 타자 등번호 (경기에서는 타순, 연습에서는 7)
var jersey_number := 7

# 좌표 (매 프레임 다시 계산)
var _release := Vector2.ZERO
var _zone_center := Vector2.ZERO
var _zone_half := Vector2.ZERO


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_STOP


func _gui_input(event: InputEvent) -> void:
	# 손가락이 닿는 순간 스윙 (떼는 순간이 아니라) — 타이밍이 생명이다.
	# 터치는 Godot 가 마우스 누름으로도 바꿔 주므로 마우스 누름 하나만 받는다 (두 번 스윙 방지)
	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		var zone_pos: Vector2 = (event.position - _zone_center) / _zone_half if _zone_half != Vector2.ZERO else Vector2.INF
		tapped.emit(zone_pos)
		accept_event()


func _process(_delta: float) -> void:
	queue_redraw()


func _draw() -> void:
	if session == null:
		return
	var pres := session.presentation
	var imp := session.impact
	var since_impact := session.since(imp.since_ms) if imp != null else INF

	# 화면 흔들림: 맞는 순간 크게, 빠르게 잦아든다
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

	draw_set_transform(shake)
	_layout()
	# 정타 뒤에는 야구장 전체를 비추는 중계 화면으로 바뀐다
	var ball := _broadcast_ball()
	if ball != null:
		BroadcastView.draw(self, Rect2(Vector2.ZERO, size), session.config.ball_physics, ball, session.broadcast_time(), session.last_result.contact.bunt)
	else:
		_draw_stadium()
		_draw_field()
		_draw_pitcher()
		_draw_zone()
		_draw_catcher_mitt()
		_draw_ball_in_flight()
		_draw_batter()
		_draw_hit_launch()
	draw_set_transform(Vector2.ZERO)

	# 정타 섬광: 화면 전체가 잠깐 하얗게
	if since_impact < pres.flash_ms and imp.quality == SwingJudge.Quality.SOLID:
		var strength := 0.55 if imp.home_run else 0.35
		draw_rect(Rect2(Vector2.ZERO, size), Color(Tokens.CHALK, strength * (1.0 - since_impact / pres.flash_ms)))
	# 홈런 꽃가루
	if since_impact < pres.confetti_ms and imp.home_run:
		_draw_confetti(since_impact / pres.confetti_ms)
	_draw_callout()


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


## 존 좌표 → 화면
func zone_to_screen(z: Vector2) -> Vector2:
	return _zone_center + Vector2(z.x * _zone_half.x, z.y * _zone_half.y)


## 진행률 progress 에서 공의 화면 위치(xy)와 크기 비율(z, 0~1)
func _ball(p: Pitch, progress: float) -> Vector3:
	var target := zone_to_screen(p.zone_position_at(progress))
	# 원근감: 가까워질수록 빨리 커지고 빨리 움직이는 것처럼
	var t := minf(progress, 1.15)
	var s := 0.25 * t + 0.75 * t * t
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

## 투수: 세트 자세(선택 시간) → 다리 들기 → 팔을 뒤로 → 머리 위로 넘겨 던지기 → 팔로스루. 공을 놓는 손이 공이 출발하는 곳
func _draw_pitcher() -> void:
	var pres := session.presentation
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


## 타자 뒷모습 (오른손 타자, 홈플레이트 왼쪽). 투수가 던지기 시작하면 배트를 더 눕히며 힘을 모으고(load),
## 스윙하면 배트가 홈플레이트 쪽으로 돈다. 번트면 배트를 가슴 높이에서 눕혀 든다
func _draw_batter() -> void:
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
	var twist := maxf(swing_progress, 0.0) * h * 0.02  # 스윙하면 몸이 홈 쪽으로 돈다

	var head := Vector2(size.x * BATTER_X + twist, h * BATTER_HEAD_Y)
	var r := h * 0.085  # SD: 머리가 크다
	var shoulders := head + Vector2(0, r * 1.15)
	var hips := head + Vector2(h * 0.01, r * 3.1)
	var uniform := Tokens.CHALK
	# 그림자
	_draw_ellipse(Vector2(head.x + h * 0.03, h * 1.0), Vector2(h * 0.16, h * 0.035), Tokens.SHADOW)
	# 다리 (화면 아래로 잘린다)
	draw_line(hips + Vector2(-r * 0.5, 0), Vector2(hips.x - r * 1.1, h * 1.08), Tokens.STANDS_DARK, r * 0.85, true)
	draw_line(hips + Vector2(r * 0.5, 0), Vector2(hips.x + r * 0.9 + twist, h * 1.08 - load * h * 0.03), Tokens.STANDS_DARK, r * 0.85, true)
	# 몸통 (등)
	var torso := PackedVector2Array([shoulders + Vector2(-r * 1.15, 0), shoulders + Vector2(r * 1.15, 0),
		hips + Vector2(r * 0.85, 0), hips + Vector2(-r * 0.85, 0)])
	draw_colored_polygon(torso, uniform)
	draw_polyline(PackedVector2Array([torso[0], torso[1], torso[2], torso[3], torso[0]]), Color(Tokens.INK, 0.25), 1.5, true)
	# 등번호
	draw_string(Tokens.FONT_BOLD, shoulders + Vector2(-r, r * 1.3), str(jersey_number), HORIZONTAL_ALIGNMENT_CENTER, r * 2.0, int(r * 1.2), Tokens.UNIFORM_US)

	# 배트
	# 손은 오른쪽 어깨 위, 배트는 머리 위로 비스듬히 세운다
	var hands := shoulders + Vector2(r * 1.25 - load * r * 0.2, -r * 0.55)
	var bat_len := h * 0.22
	var bat_angle := _bat_angle_deg(maxf(swing_progress, 0.0)) if swing_progress >= 0.0 else _bat_angle_deg(0.0) - 12.0 * load
	var bunt := session.swing_type == "bunt"
	if bunt:
		hands = shoulders + Vector2(r * 1.6 + (r * 0.4 if swing_progress >= 0.0 else 0.0), r * 0.5)
		bat_angle = 88.0
		bat_len = h * 0.16
		swing_progress = -1.0 if not in_hit else swing_progress
	if in_hit and not bunt:
		# 맞은 공: 배트가 공이 있는 자리를 향하게 한다. 멀면 배트를 조금 늘려 닿게 (최대 1.6배)
		var b := _ball(session.pitch, session.swing_at_ms / session.pitch.flight_ms)
		var v := Vector2(b.x, b.y) - hands
		var aim := rad_to_deg(atan2(v.x, -v.y))
		var follow := clampf((swing_progress - CONTACT_SWING) / (1.0 - CONTACT_SWING), 0.0, 1.0)
		bat_angle = lerpf(aim, _bat_angle_deg(1.0), follow)
		bat_len = lerpf(clampf(v.length() + 6.0, h * 0.22, h * 0.22 * 1.6), h * 0.22, follow)
	# 머리 (뒤통수) + 헬멧
	draw_circle(head + Vector2(0, r * 0.25), r * 0.75, Tokens.SKIN, true, -1.0, true)
	draw_circle(head, r, Tokens.HELMET, true, -1.0, true)
	_draw_ellipse(head + Vector2(r * 0.55, r * 0.55), Vector2(r * 0.45, r * 0.35), Tokens.HELMET.darkened(0.15))  # 귀 덮개
	draw_circle(head + Vector2(-r * 0.35, -r * 0.4), r * 0.28, Color(Tokens.CHALK, 0.35), true, -1.0, true)  # 광택
	# 배트 궤적 잔상
	if swing_progress >= 0.0 and swing_progress < 1.0 and not bunt:
		var wedge := PackedVector2Array([hands])
		var from := _bat_angle_deg(0.0)
		for i in 13:
			wedge.append(hands + _bat_dir(lerpf(from, bat_angle, i / 12.0)) * bat_len)
		draw_colored_polygon(wedge, Color(Tokens.CHALK, 0.3))
	var tip := hands + _bat_dir(bat_angle) * bat_len
	draw_line(hands, hands.lerp(tip, 0.35), Tokens.BAT.darkened(0.25), r * 0.22, true)
	draw_line(hands.lerp(tip, 0.3), tip, Tokens.BAT, r * 0.32, true)
	# 팔 (어깨 → 손)
	draw_line(shoulders + Vector2(r * 0.9, r * 0.1), hands, uniform, r * 0.45, true)
	draw_line(shoulders + Vector2(-r * 0.6, r * 0.1), hands + Vector2(-r * 0.15, r * 0.1), uniform, r * 0.45, true)
	draw_circle(hands, r * 0.28, Tokens.STANDS_DARK, true, -1.0, true)  # 장갑


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
	var at := zone_to_screen(session.pitch.intended)
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
		var b := _ball(p, session.swing_at_ms / p.flight_ms)
		var hit_pos := Vector2(b.x, b.y)
		var t := clampf(session.since(session.swing_since_ms) / FOUL_FLIGHT_MS, 0.0, 1.0)
		var side := -1.0 if session.swing_outcome.contact.angle_deg < 0 else 1.0
		_draw_launched_ball(hit_pos, hit_pos + Vector2(side * size.x * 0.6, -size.y * 0.7), t, _ball_radius(b.z))
		_draw_burst(hit_pos, session.since(session.swing_since_ms) / pres.burst_ms, SwingJudge.Quality.FOUL)
	elif progress <= 1.15:
		var b := _ball(p, progress)
		var pos := Vector2(b.x, b.y)
		var r := _ball_radius(b.z)
		# 구종이 드러나면 테두리가 구종 색이 되고, 직구가 아니면 공도 그 색으로 물든다
		var revealed := p.is_revealed_at(progress)
		var type_color := _pitch_color(p.type)
		var offspeed := revealed and p.type != "fastball"
		draw_circle(pos + Vector2(r * 0.3, r * 0.5), r, Tokens.SHADOW, true, -1.0, true)
		draw_circle(pos, r, type_color if offspeed else Tokens.CHALK, true, -1.0, true)
		draw_circle(pos, r, type_color if revealed else Tokens.INK, false, 1.5, true)
	# 미트에 꽂힌 순간 작은 링
	if session.mitt_played and progress < 1.0 + MITT_RING_MS / p.flight_ms:
		var end := _ball(p, 1.0)
		var t := clampf((progress - 1.0) * p.flight_ms / MITT_RING_MS, 0.0, 1.0)
		draw_circle(Vector2(end.x, end.y), _ball_radius(end.z) * (1.2 + t * 1.5), Color(Tokens.CHALK, 1.0 - t), false, 2.0, true)


## 맞은 공: 히트스톱 동안 맞은 자리에 멈춰 있다가 경기장 쪽으로 튕겨 나간다 (그다음 중계 화면)
func _draw_hit_launch() -> void:
	if session.phase != BattingSession.Phase.HIT:
		return
	var pres := session.presentation
	var b := _ball(session.pitch, session.swing_at_ms / session.pitch.flight_ms)
	var hit_pos := Vector2(b.x, b.y)
	var c := session.last_result.contact
	var e := session.since(session.phase_since_ms)
	_draw_burst(hit_pos, e / pres.burst_ms, c.quality)
	var t := clampf((e - pres.hit_stop_ms) / pres.launch_ms, 0.0, 1.0)
	var ang := deg_to_rad(c.angle_deg)
	var target: Vector2
	if c.batted_ball == SwingJudge.BattedBall.GROUND:
		target = Vector2(size.x * 0.5 + sin(ang) * size.x * 0.5, size.y * MOUND_Y)
	else:
		target = Vector2(size.x * 0.5 + sin(ang) * size.x * 0.8, -size.y * 0.1)
	if t < 1.0:
		_draw_launched_ball(hit_pos, target, t, _ball_radius(b.z))


func _pitch_color(type: String) -> Color:
	return Tokens.PITCH_COLORS.get(type, Tokens.PRIMARY)


# ---------- 연출 ----------

## 맞은 공이 from 에서 to 로 날아가며 작아진다. 꼬리를 그린다
func _draw_launched_ball(from: Vector2, to: Vector2, t: float, start_radius: float) -> void:
	var at := func(tt: float) -> Vector2:
		return from.lerp(to, 1.0 - (1.0 - tt) * (1.0 - tt))  # 처음에 빠르게
	var radius := func(tt: float) -> float:
		return start_radius * (1.0 - 0.75 * tt)
	for k in range(1, 7):
		var tt := maxf(t - k * 0.025, 0.0)
		draw_circle(at.call(tt), radius.call(tt) * (1.0 - k * 0.08), Color(Tokens.CHALK, 0.55 - k * 0.08), true, -1.0, true)
	draw_circle(at.call(t), radius.call(t), Tokens.CHALK, true, -1.0, true)
	draw_circle(at.call(t), radius.call(t), Tokens.INK, false, 1.0, true)


## 맞은 자리에서 퍼지는 별 모양 파편. 정타일수록 크고 노랗다
func _draw_burst(center: Vector2, t: float, quality: SwingJudge.Quality) -> void:
	if t >= 1.0 or quality == SwingJudge.Quality.MISS:
		return
	var spokes := 6
	var reach := size.y * 0.05
	var color := Tokens.CHALK
	if quality == SwingJudge.Quality.SOLID:
		spokes = 12
		reach = size.y * 0.12
		color = Tokens.WARN
	elif quality == SwingJudge.Quality.WEAK:
		spokes = 8
		reach = size.y * 0.07
	var e := 1.0 - (1.0 - t) * (1.0 - t)
	var alpha := 1.0 - t
	for i in spokes:
		var ang := TAU * i / spokes + 0.3
		var dir := Vector2(cos(ang), sin(ang))
		draw_line(center + dir * reach * 0.25 * e, center + dir * reach * e, Color(color, alpha), maxf(3.0 * alpha, 1.0), true)
	draw_circle(center, reach * 0.6 * e, Color(color, alpha * 0.8), false, 2.0, true)


## 홈런 꽃가루. 위치는 조각 번호로 정해지는 가짜 난수라 매번 같다
func _draw_confetti(t: float) -> void:
	var alpha := (1.0 - t) / 0.25 if t > 0.75 else 1.0
	for i in 60:
		var hsh := func(k: int) -> float:
			return fposmod(sin(i * 12.9898 + k * 78.233) * 43758.5453, 1.0)
		var x: float = size.x * hsh.call(1) + sin(t * 8.0 + i) * 18.0
		var y: float = -20.0 + size.y * 1.1 * t * (0.7 + 0.5 * hsh.call(2))
		var wdt: float = 5.0 + 4.0 * hsh.call(3)
		draw_set_transform(Vector2(x, y), t * TAU * 2.0 * (hsh.call(4) - 0.5))
		draw_rect(Rect2(-wdt / 2.0, -wdt / 4.0, wdt, wdt / 2.0), Color(Tokens.CONFETTI[i % Tokens.CONFETTI.size()], alpha))
	draw_set_transform(Vector2.ZERO)


## 판정 문구 카드. 처음에 "팡" 하고 커졌다 돌아온다 (홈런은 더 크게)
func _draw_callout() -> void:
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
	draw_set_transform(Vector2(size.x * 0.5, size.y * 0.42), 0.0, Vector2(pop, pop))
	var style := StyleBoxFlat.new()
	style.bg_color = Tokens.SURFACE
	style.set_corner_radius_all(Tokens.RADIUS_CARD)
	style.anti_aliasing = true
	draw_style_box(style, Rect2(-box / 2.0, box))
	var y := -box.y / 2.0 + Tokens.SPACE_MD
	for l: Array in lines:
		var f: Font = l[1]
		y += f.get_ascent(l[2])
		draw_string(f, Vector2(-box.x / 2.0, y), l[0], HORIZONTAL_ALIGNMENT_CENTER, box.x, l[2], l[3])
		y += f.get_descent(l[2])
	draw_set_transform(Vector2.ZERO)


# ---------- 도우미 ----------

## 배트 각도 (위쪽 0°, 시계 방향 +): 대기 −30°(머리 위로 비스듬히) → 수평(90°)을 지나 홈 쪽으로 → 팔로스루 +150°.
## 처음에 빠르고 끝에서 느려진다
func _bat_angle_deg(progress: float) -> float:
	var e := 1.0 - (1.0 - progress) * (1.0 - progress)
	return -30.0 + 180.0 * e


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
