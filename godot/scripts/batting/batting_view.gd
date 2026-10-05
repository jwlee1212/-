class_name BattingView
extends Control
## 타구 분포도(위 30%) + 투구 화면(아래 70%) + 타격 연출(히트스톱·흔들림·섬광·파편·꽃가루) + 판정 문구.
## 그림은 단순 도형이다. 탭(손가락이 닿는 순간)은 tapped 시그널로 알린다.

signal tapped

## 공이 배트에 맞는 지점의 스윙 진행률
const CONTACT_SWING := 0.45
## 파울 타구가 화면 밖으로 날아가는 시간·미트 링이 퍼지는 시간 (연출 전용)
const FOUL_FLIGHT_MS := 350.0
const MITT_RING_MS := 140.0

var session: BattingSession

# 투구 화면 좌표 (매 프레임 다시 계산)
var _pitch_area := Rect2()
var _release := Vector2.ZERO
var _zone_center := Vector2.ZERO
var _zone_half := Vector2.ZERO


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_STOP


func _gui_input(event: InputEvent) -> void:
	# 손가락이 닿는 순간 스윙 (떼는 순간이 아니라) — 타이밍이 생명이다.
	# 터치는 Godot 가 마우스 누름으로도 바꿔 주므로 마우스 누름 하나만 받는다 (두 번 스윙 방지)
	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		tapped.emit()
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
	var spray_area := Rect2(0, 0, size.x, size.y * 0.3)
	_pitch_area = Rect2(0, spray_area.end.y, size.x, size.y - spray_area.end.y)
	_layout_pitch_area()
	_draw_spray_chart(spray_area)
	_draw_pitch_view()
	_draw_pitch_label()
	draw_set_transform(Vector2.ZERO)

	# 정타 섬광: 화면 전체가 잠깐 하얗게
	if since_impact < pres.flash_ms and imp.quality == SwingJudge.Quality.SOLID:
		var strength := 0.55 if imp.home_run else 0.35
		draw_rect(Rect2(Vector2.ZERO, size), Color(Tokens.CHALK, strength * (1.0 - since_impact / pres.flash_ms)))
	# 홈런 꽃가루
	if since_impact < pres.confetti_ms and imp.home_run:
		_draw_confetti(since_impact / pres.confetti_ms)
	_draw_callout()


# ---------- 좌표 ----------

func _layout_pitch_area() -> void:
	var a := _pitch_area
	_release = Vector2(a.get_center().x, a.position.y + a.size.y * 0.14)
	_zone_center = Vector2(a.get_center().x, a.position.y + a.size.y * 0.66)
	# 가로가 넓은 화면에서도 존이 너무 커지지 않게 짧은 쪽 기준으로 잡는다
	var unit := minf(a.size.x, a.size.y * 0.75)
	_zone_half = Vector2(unit * 0.15, unit * 0.19)


## 진행률 progress 에서 공의 화면 위치(xy)와 크기 비율(z, 0~1)
func _ball(p: Pitch, progress: float) -> Vector3:
	var z := p.zone_position_at(progress)
	var target := _zone_center + Vector2(z.x * _zone_half.x, z.y * _zone_half.y)
	# 원근감: 가까워질수록 빨리 커지고 빨리 움직이는 것처럼
	var t := minf(progress, 1.15)
	var s := 0.35 * t + 0.65 * t * t
	var pos := _release.lerp(target, s)
	return Vector3(pos.x, pos.y, s)


func _ball_radius(s: float) -> float:
	return 2.5 + 9.0 * s


# ---------- 타구 분포도 ----------

func _draw_spray_chart(area: Rect2) -> void:
	var fence := session.config.fence_m
	var home := Vector2(area.get_center().x, area.end.y - 6)
	var scale_m := minf((area.size.y - 12) / (fence * 1.05), area.size.x / 2.0 / (fence * 0.75))
	var point := func(angle_deg: float, meters: float) -> Vector2:
		var a := deg_to_rad(angle_deg)
		return home + Vector2(sin(a), -cos(a)) * meters * scale_m

	var fan := PackedVector2Array([home])
	for deg in range(-45, 50, 5):
		fan.append(point.call(float(deg), fence))
	draw_colored_polygon(fan, Tokens.GRASS)
	var base := 27.4
	draw_colored_polygon(PackedVector2Array([home, point.call(45.0, base), point.call(0.0, base * 1.414), point.call(-45.0, base)]), Tokens.DIRT)
	var outline := fan.duplicate()
	outline.append(home)
	draw_polyline(outline, Tokens.CHALK, 1.5, true)

	for dot: Array in session.spray:
		var o: SwingJudge.Outcome = dot[2]
		var color := Tokens.INK
		if o == SwingJudge.Outcome.HOME_RUN:
			color = Tokens.ACCENT
		elif SwingJudge.is_hit(o):
			color = Tokens.GOOD
		draw_circle(point.call(dot[0], dot[1]), 4.0, color, true, -1.0, true)

	# 지금 날아가는 타구 (히트스톱이 끝난 뒤 출발)
	if session.phase != BattingSession.Phase.HIT:
		return
	var pres := session.presentation
	var c := session.last_result.contact
	var t := clampf((session.since(session.phase_since_ms) - pres.hit_stop_ms) / pres.hit_flight_ms, 0.0, 1.0)
	var target: Vector2 = point.call(c.angle_deg, c.distance_m)
	var ground := c.batted_ball == SwingJudge.BattedBall.GROUND
	var at := func(tt: float) -> Vector2:
		# 뜬공은 포물선처럼 위로 솟았다 내려온다 (화면상 높이)
		var lift := 0.0 if ground else sin(tt * PI) * area.size.y * 0.25
		return home.lerp(target, tt) - Vector2(0, lift)
	for k in range(1, 6):
		var tt := maxf(t - k * 0.03, 0.0)
		draw_circle(at.call(tt), 5.0 - k * 0.6, Color(Tokens.CHALK, 0.5 - k * 0.08), true, -1.0, true)
	draw_circle(home.lerp(target, t), 3.0, Tokens.SHADOW, true, -1.0, true)
	draw_circle(at.call(t), 5.0, Tokens.CHALK, true, -1.0, true)
	draw_circle(at.call(t), 5.0, Tokens.INK, false, 1.0, true)


# ---------- 투구 화면 ----------

func _draw_pitch_view() -> void:
	var a := _pitch_area
	var pres := session.presentation
	var cx := a.get_center().x
	draw_rect(a, Tokens.GRASS)

	# 마운드와 홈 주변 흙
	_draw_ellipse(Vector2(cx, _release.y + 9), Vector2(a.size.x * 0.12, 15), Tokens.DIRT)
	_draw_ellipse(Vector2(cx, _zone_center.y + _zone_half.y * 0.6 + a.size.y * 0.16), Vector2(a.size.x * 0.42, a.size.y * 0.16), Tokens.DIRT)

	# 투수 (와인드업 중엔 팔이 올라간다)
	var windup := 0.0
	if session.phase == BattingSession.Phase.WINDUP:
		windup = clampf(session.since(session.phase_since_ms) / pres.windup_ms, 0.0, 1.0)
	elif session.phase == BattingSession.Phase.FLIGHT:
		windup = 1.0
	var body := _release + Vector2(0, 4)
	draw_circle(body - Vector2(0, 16), 7.0, Tokens.INK, true, -1.0, true)
	draw_line(body - Vector2(0, 10), body + Vector2(0, 8), Tokens.INK, 6.0)
	var arm := -PI / 2.0 * windup
	var shoulder := body - Vector2(0, 6)
	draw_line(shoulder, shoulder + Vector2(cos(arm), sin(arm)) * 12.0, Tokens.INK, 3.0, true)

	# 스트라이크존과 홈플레이트
	var zone := Rect2(_zone_center - _zone_half, _zone_half * 2.0)
	draw_rect(zone, Color(Tokens.CHALK, 0.18))
	draw_rect(zone, Tokens.CHALK, false, 2.0)
	var plate_top := zone.end.y + 18
	var pw := _zone_half.x * 0.9
	draw_colored_polygon(PackedVector2Array([
		Vector2(cx - pw, plate_top), Vector2(cx + pw, plate_top), Vector2(cx + pw, plate_top + 8),
		Vector2(cx, plate_top + 18), Vector2(cx - pw, plate_top + 8),
	]), Tokens.CHALK)

	var in_flight := session.phase == BattingSession.Phase.FLIGHT
	var in_hit := session.phase == BattingSession.Phase.HIT

	# 스윙 진행률 (0 = 배트를 세운 대기 자세, 1 = 팔로스루 끝). 탭하자마자 반응하도록 0.35 에서 시작
	var swing_progress := -1.0
	if in_hit:
		var e := session.since(session.phase_since_ms)
		# 히트스톱 동안 배트는 맞은 자리에서 멈춘다
		swing_progress = CONTACT_SWING if e < pres.hit_stop_ms else minf(CONTACT_SWING + (e - pres.hit_stop_ms) / pres.swing_ms, 1.0)
	elif in_flight and session.swung:
		swing_progress = minf(0.35 + session.since(session.swing_since_ms) / pres.swing_ms, 1.0)

	# 타자 (오른손 타자, 화면 왼쪽) + 배트
	var hip := Vector2(cx - _zone_half.x - 34, _zone_center.y + _zone_half.y * 0.4)
	draw_circle(hip - Vector2(0, 58), 12.0, Tokens.INK, true, -1.0, true)
	draw_line(hip - Vector2(0, 46), hip, Tokens.INK, 14.0)
	var hands := hip - Vector2(-6, 36)
	var base_bat := 64.0
	var bat_len := base_bat
	var bat_angle := _bat_angle_deg(maxf(swing_progress, 0.0))
	if in_hit:
		# 맞은 공: 배트가 공이 있는 자리를 향하게 한다. 멀면 배트를 조금 늘려 닿게 (최대 1.8배)
		var b := _ball(session.pitch, session.swing_at_ms / session.pitch.flight_ms)
		var v := Vector2(b.x, b.y) - hands
		var aim := rad_to_deg(atan2(v.x, -v.y))
		var follow := clampf((swing_progress - CONTACT_SWING) / (1.0 - CONTACT_SWING), 0.0, 1.0)
		bat_angle = lerpf(aim, _bat_angle_deg(1.0), follow)
		bat_len = lerpf(clampf(v.length() + 6.0, base_bat, base_bat * 1.8), base_bat, follow)
	# 배트 궤적 잔상: 휘두른 범위를 반투명 부채꼴로
	if swing_progress >= 0.0 and swing_progress < 1.0:
		var wedge := PackedVector2Array([hands])
		var from := _bat_angle_deg(0.0)
		for i in 13:
			wedge.append(hands + _bat_dir(lerpf(from, bat_angle, i / 12.0)) * bat_len)
		draw_colored_polygon(wedge, Color(Tokens.CHALK, 0.35))
	draw_line(hands, hands + _bat_dir(bat_angle) * bat_len, Tokens.BAT, 6.0, true)

	# 날아오는 공
	if in_flight:
		var p := session.pitch
		var progress := session.since(session.phase_since_ms) / p.flight_ms
		var foul := session.swung and session.swing_outcome.call == AtBat.Call.FOUL
		if foul:
			# 파울: 맞은 자리에서 옆·뒤로 튕겨 나간다
			var b := _ball(p, session.swing_at_ms / p.flight_ms)
			var hit_pos := Vector2(b.x, b.y)
			var t := clampf(session.since(session.swing_since_ms) / FOUL_FLIGHT_MS, 0.0, 1.0)
			var side := -1.0 if session.swing_outcome.contact.angle_deg < 0 else 1.0
			_draw_launched_ball(hit_pos, hit_pos + Vector2(side * a.size.x * 0.7, -a.size.y * 0.5), t, _ball_radius(b.z))
			_draw_burst(hit_pos, session.since(session.swing_since_ms) / pres.burst_ms, SwingJudge.Quality.FOUL)
		elif progress <= 1.15:
			var b := _ball(p, progress)
			var pos := Vector2(b.x, b.y)
			var r := _ball_radius(b.z)
			var revealed := p.is_revealed_at(progress)
			var breaking := revealed and p.type == Pitch.Type.BREAKING
			var rim := Tokens.INK
			if revealed:
				rim = Tokens.ACCENT if breaking else Tokens.PRIMARY
			draw_circle(pos + Vector2(r * 0.3, r * 0.5), r, Tokens.SHADOW, true, -1.0, true)
			draw_circle(pos, r, Tokens.ACCENT if breaking else Tokens.CHALK, true, -1.0, true)
			draw_circle(pos, r, rim, false, 1.5, true)
		# 미트에 꽂힌 순간 작은 링
		if session.mitt_played and progress < 1.0 + MITT_RING_MS / p.flight_ms:
			var end := _ball(p, 1.0)
			var t := clampf((progress - 1.0) * p.flight_ms / MITT_RING_MS, 0.0, 1.0)
			draw_circle(Vector2(end.x, end.y), _ball_radius(end.z) * (1.2 + t * 1.5), Color(Tokens.CHALK, 1.0 - t), false, 2.0, true)

	# 맞은 공: 히트스톱 동안 맞은 자리에 멈춰 있다가 튕겨 나간다
	if in_hit:
		var b := _ball(session.pitch, session.swing_at_ms / session.pitch.flight_ms)
		var hit_pos := Vector2(b.x, b.y)
		var c := session.last_result.contact
		var e := session.since(session.phase_since_ms)
		_draw_burst(hit_pos, e / pres.burst_ms, c.quality)
		var t := clampf((e - pres.hit_stop_ms) / (pres.hit_flight_ms * 0.6), 0.0, 1.0)
		var ang := deg_to_rad(c.angle_deg)
		var target: Vector2
		if c.batted_ball == SwingJudge.BattedBall.GROUND:
			target = Vector2(cx + sin(ang) * a.size.x * 0.5, _release.y)  # 땅볼: 투수 쪽으로 굴러간다
		else:
			target = Vector2(cx + sin(ang) * a.size.x * 0.9, a.position.y - a.size.y * 0.2)  # 뜬공·라이너: 외야로 솟구친다
		if t < 1.0:
			_draw_launched_ball(hit_pos, target, t, _ball_radius(b.z))


## 구종 이름 (선구안 시점이 지나야 보인다)
func _draw_pitch_label() -> void:
	if session.phase != BattingSession.Phase.FLIGHT:
		return
	var p := session.pitch
	var progress := session.since(session.phase_since_ms) / p.flight_ms
	if p.is_revealed_at(progress) and progress <= 1.0:
		var color := Tokens.ACCENT if p.type == Pitch.Type.BREAKING else Tokens.PRIMARY
		draw_string(Tokens.FONT_BOLD, _pitch_area.position + Vector2(Tokens.SPACE_MD, Tokens.SPACE_MD + Tokens.FONT_TITLE),
			p.label, HORIZONTAL_ALIGNMENT_LEFT, -1, Tokens.FONT_TITLE, color)


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
	var reach := 18.0
	var color := Tokens.CHALK
	if quality == SwingJudge.Quality.SOLID:
		spokes = 12
		reach = 46.0
		color = Tokens.WARN
	elif quality == SwingJudge.Quality.WEAK:
		spokes = 8
		reach = 26.0
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
	for i in 48:
		var h := func(k: int) -> float:
			return fposmod(sin(i * 12.9898 + k * 78.233) * 43758.5453, 1.0)
		var x: float = size.x * h.call(1) + sin(t * 8.0 + i) * 18.0
		var y: float = -20.0 + size.y * 1.1 * t * (0.7 + 0.5 * h.call(2))
		var w: float = 5.0 + 4.0 * h.call(3)
		draw_set_transform(Vector2(x, y), t * TAU * 2.0 * (h.call(4) - 0.5))
		draw_rect(Rect2(-w / 2.0, -w / 4.0, w, w / 2.0), Color(Tokens.CONFETTI[i % Tokens.CONFETTI.size()], alpha))
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
		lines.append(["탭해서 다음 타석", Tokens.FONT_REGULAR, Tokens.FONT_CAPTION, Tokens.INK_SOFT])
	var width := 0.0
	var height := 0.0
	for l: Array in lines:
		var f: Font = l[1]
		width = maxf(width, f.get_string_size(l[0], HORIZONTAL_ALIGNMENT_LEFT, -1, l[2]).x)
		height += f.get_height(l[2])
	var box := Vector2(width + Tokens.SPACE_LG * 2, height + Tokens.SPACE_MD * 2)
	draw_set_transform(size / 2.0, 0.0, Vector2(pop, pop))
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

## 배트 각도: 대기 −60° → 수평을 지나 → 팔로스루 +110°. 처음에 빠르고 끝에서 느려진다
func _bat_angle_deg(progress: float) -> float:
	var e := 1.0 - (1.0 - progress) * (1.0 - progress)
	return -60.0 + 170.0 * e


## 각도(위쪽 = 0°, 시계 방향 +)의 방향 벡터
func _bat_dir(angle_deg: float) -> Vector2:
	var r := deg_to_rad(angle_deg)
	return Vector2(sin(r), -cos(r))


func _draw_ellipse(center: Vector2, radii: Vector2, color: Color) -> void:
	var pts := PackedVector2Array()
	for i in 32:
		var t := TAU * i / 32.0
		pts.append(center + Vector2(cos(t) * radii.x, sin(t) * radii.y))
	draw_colored_polygon(pts, color)
