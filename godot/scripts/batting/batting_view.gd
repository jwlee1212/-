class_name BattingView
extends Control
## 타석 장면 (타자 어깨 너머 시점): 경기장 배경, 투수, 큰 타자 뒷모습, 스트라이크존 꺾쇠, 날아오는 공,
## 타격 연출(히트스톱·흔들림·섬광·파편·꽃가루), 정타 뒤 타구 중계 화면, 판정 문구.
## 버튼·점수판 같은 HUD 는 BattingHud 가 위에 얹는다. 경기장·투수·수비수·타자는 코드로 찍은 도트 그림(PixelArt, BatterSprite).
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
const HORIZON_Y := 0.46
const MOUND_Y := 0.56
const ZONE_Y := 0.70
const ZONE_HALF_W := 0.045
const ZONE_HALF_H := 0.095
## 공이 향하는 곳은 투수 손보다 화면 높이의 이만큼은 아래 (공이 화면에서 위로 올라가지 않게)
const MIN_DESCENT := 0.03
## 타자: 두 발 사이의 가로 자리, 발바닥 높이 — 화면 비율. 바꾸면 make_pixel_art.py 의 LAYOUT 도 같이 바꾼다
const BATTER_X := 0.34
const BATTER_FEET_Y := 0.97

var session: BattingSession
## 경기 중이면 경기 (전광판 줄 점수에 쓴다). 연습이면 null
var game: GameRunner = null
## 타자 등번호 (경기에서는 타순, 연습에서는 7)
var jersey_number := 7

# 좌표 (매 프레임 다시 계산)
var _release := Vector2.ZERO
var _zone_center := Vector2.ZERO
var _zone_half := Vector2.ZERO
var _batter: BatterSprite
## 타자보다 앞에 그리는 연출
var _front: Control


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_STOP
	# 도트는 확대해도 번지지 않게
	texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
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
		PixelArt.draw_field(self, size)
		PixelArt.draw_scoreboard(self, size, _board())
		_draw_pixel_defense()
		_draw_pitcher()
		# 포수는 그리지 않는다: 카메라가 포수보다 뒤·위에 있어 화면 밖이다 (3D 야구 게임 타석 시점). 그림은 남겨 둔다
		_draw_zone()
		_draw_catcher_mitt()
		_draw_ball_in_flight()
	draw_set_transform(Vector2.ZERO)


## 전광판에 띄울 것: 줄 점수(경기 중일 때만, 이닝이 9를 넘으면 마지막 9이닝)와 볼카운트
func _board() -> Dictionary:
	var board := {"balls": mini(session.at_bat.balls, 3), "strikes": mini(session.at_bat.strikes, 2), "outs": 0, "first": 1,
		"rows": [[Tokens.UNIFORM_THEM, [], "", false], [Tokens.UNIFORM_US, [], "", true]]}
	if game == null:
		return board
	var st := game.state
	board["outs"] = st.outs
	board["first"] = maxi(1, st.inning - 8)
	for side in 2:
		board["rows"][side] = [Tokens.UNIFORM_US if side == game.my_side else Tokens.UNIFORM_THEM, st.line[side], str(st.score[side]), side == st.batting_side()]
	return board


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
	# 홈런 뒤 베이스 도는 장면: 탭하면 건너뛴다는 안내 (화면 아래 가운데)
	if session.can_skip_play():
		var text := "화면을 누르면 건너뛰기"
		var w := Tokens.FONT_BOLD.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, Tokens.FONT_LABEL).x
		var box := Rect2(Vector2(size.x * 0.5 - w * 0.5 - Tokens.SPACE_SM * 2, size.y - Tokens.FONT_LABEL - Tokens.SPACE_SM * 4), Vector2(w + Tokens.SPACE_SM * 4, Tokens.FONT_LABEL + Tokens.SPACE_SM * 2))
		var pill := StyleBoxFlat.new()
		pill.bg_color = Tokens.BROADCAST_BG
		pill.set_corner_radius_all(Tokens.RADIUS_CARD)
		canvas.draw_style_box(pill, box)
		canvas.draw_string(Tokens.FONT_BOLD, Vector2(box.position.x, box.position.y + Tokens.SPACE_SM + Tokens.FONT_LABEL * 0.8), text, HORIZONTAL_ALIGNMENT_CENTER, box.size.x, Tokens.FONT_LABEL, Tokens.CHALK)
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
	# 공은 투수 그림의 공 놓는 손에서 출발한다
	_release = PixelArt.snap(Vector2(size.x * 0.5, size.y * MOUND_Y), size) + PixelMeta.PITCHER_RELEASE * PixelArt.unit(size) * PixelArt.PITCHER_SCALE


## 존 좌표 → 화면
func zone_to_screen(z: Vector2) -> Vector2:
	return _zone_center + Vector2(z.x * _zone_half.x, z.y * _zone_half.y)


## 공을 놓고 t_ms 뒤 공이 향하는 존 평면 자리 — 변화(break·arc)만 화면용으로 과장한다 (도착 순간엔 정확히 aim)
func _pointing(p: Pitch, t_ms: float) -> Vector2:
	var traj := p.trajectory
	return traj.aim + (traj.pointing_at(t_ms) - traj.aim) * session.presentation.movement_scale


## 존 평면의 "향하는 곳" → 화면. 손보다 위면 공이 화면에서 솟아오르므로 손 바로 아래로 눌러 둔다 (떨어지는 공은 그냥 떨어진다).
## 공과 포수 미트가 같은 함수를 써서 둘이 어긋나지 않는다
func _aim_on_screen(zone: Vector2) -> Vector2:
	var at := zone_to_screen(zone)
	at.y = maxf(at.y, _release.y + size.y * MIN_DESCENT)
	return at


## 공을 놓고 t_ms 뒤 공의 화면 위치(xy)와 크기 비율(z, 0~1).
## 공은 투수 손에서 "지금 향하는 곳"으로 원근을 따라 다가온다: 꺾이기 전엔 곧게, 꺾이면 향하는 곳이 도착점으로 미끄러진다
func _ball(p: Pitch, t_ms: float) -> Vector3:
	# 원근감: 가까워질수록 빨리 커지고 빨리 움직이는 것처럼. 깊이는 거리 진행률(감속 반영).
	# 홈플레이트에 닿으면 미트에 멈춘다 (지나쳐 가면 판정 자리보다 아래·바깥에 보여 헷갈린다)
	var t := p.trajectory.distance_fraction(t_ms)
	var s := 0.25 * t + 0.75 * t * t
	var pos := _release.lerp(_aim_on_screen(_pointing(p, t_ms)), s)
	# 커브의 "뽕": 손을 떠난 직후 화면에서 위로 튀어 올랐다가 떨어진다 (손 아래 보정과 따로 얹는다 — 구종이 정한 만큼만 솟는다)
	pos.y -= p.trajectory.pop_at(t_ms) * _zone_half.y * session.presentation.movement_scale.y
	return Vector3(pos.x, pos.y, s)


## 공 반지름 (원근 배율 s). 홈플레이트에서는 판정과 같은 크기 (zoneBallRadius × 존 반폭) — 존에 걸쳐 보이면 스트라이크.
## 크기는 실제 원근처럼 남은 거리에 반비례: 멀 땐 작게 천천히 커지다가 타자 앞에서 확 커진다
## (PERSPECTIVE 가 클수록 손을 떠날 때 작고 마지막에 더 급하게 커진다. 0 이면 처음부터 끝까지 같은 크기)
const BALL_PERSPECTIVE := 4.0

func _ball_radius(s: float) -> float:
	var t := (-0.25 + sqrt(0.0625 + 3.0 * clampf(s, 0.0, 1.0))) / 1.5  # _ball 의 s = 0.25t + 0.75t² 를 거꾸로 — 거리 진행률
	return maxf(_zone_half.x * session.config.zone_ball_radius / (1.0 + BALL_PERSPECTIVE * (1.0 - t)), 1.5)


# ---------- 사람 ----------

## 투수: 세트 자세(선택 시간) → 다리 들기 → 내딛기 → 팔 젖히기 → 공 놓기 → 마무리. 도트 칸을 갈아 끼운다
func _draw_pitcher() -> void:
	var pres := session.presentation
	var windup := 0.0
	var since_release := -1.0
	match session.phase:
		BattingSession.Phase.WINDUP:
			windup = clampf(session.since(session.phase_since_ms) / pres.windup_ms, 0.0, 1.0)
		BattingSession.Phase.FLIGHT, BattingSession.Phase.HIT:
			since_release = session.since(session.phase_since_ms) + (session.pitch.flight_ms if session.phase == BattingSession.Phase.HIT else 0.0)
	var mound := Vector2(size.x * 0.5, size.y * MOUND_Y)
	PixelArt.draw_cell(self, "pitcher", PixelArt.pitcher_cell(windup, since_release), PixelMeta.PITCHER_CELL, PixelMeta.PITCHER_FEET, mound, size, PixelArt.PITCHER_SCALE)


## 타자 자세 (오른손 타자, 홈플레이트 왼쪽). 투수가 던지기 시작하면 힘을 모으고(load), 공이 날아오는 동안 앞발을 내딛고,
## 스윙하면 회전 → 맞는 순간(공 높이별 칸) → 팔로스루로 넘어간다. 히트스톱 동안은 맞는 순간 칸에서 멈춘다. 번트면 번트 칸.
## 칸은 BatterSprite 가 갈아 끼우고, 여기서는 언제 얼마나 휘둘렀는지를 정한다
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

	var feet := Vector2(size.x * BATTER_X, h * BATTER_FEET_Y)
	var bunt := session.swing_type == "bunt"
	# 칸을 갈아 끼우기만 한다 (돌리면 픽셀이 깨진다). 높은 공·낮은 공은 맞는 순간 칸이 따로 있다
	var height_px := 0.0
	if swing_progress >= 0.0:
		height_px = (_ball(session.pitch, session.swing_at_ms).y - _zone_center.y) / PixelArt.unit(size)
	_batter.scale = Vector2.ONE * PixelArt.unit(size)
	_batter.position = PixelArt.snap(feet, size) + _shake()
	# 공이 날아오는 동안 앞발을 내디뎌 strideLandAt 에 땅을 딛는다 (휘두르기 전까지)
	var stride := 0.0
	if in_flight:
		stride = clampf(session.since(session.phase_since_ms) / session.pitch.flight_ms / maxf(pres.stride_land_at, 0.01), 0.0, 1.0)
	_batter.show_frame(_pixel_after_pitch(PixelArt.batter_cell(load, stride, swing_progress, bunt, height_px)))


## 공이 끝난 뒤: 바로 준비 자세로 돌아가면 화면이 급하게 바뀌어서, 마지막 자세(휘둘렀으면 팔로스루 마무리,
## 안 휘둘렀으면 랜딩)로 pitchIntervalMs 동안 멈춰 있다가 사이 칸을 거쳐 batterRecoverMs 동안 준비 자세로 돌아간다.
## 타석이 끝난 뒤(OVER)에는 다음 타석까지 멈춰 있다. 그 밖에는 cell 그대로
func _pixel_after_pitch(cell: int) -> int:
	var pres := session.presentation
	var waiting := session.phase == BattingSession.Phase.WAITING and session.at_bat.pitch_count > 0
	if not waiting and session.phase != BattingSession.Phase.OVER:
		return cell
	if session.pitch == null or session.swing_type == "bunt":
		return cell
	var f := PixelMeta.BATTER
	var held: int = f["finish"] if session.swung else f["landing"]
	var e := session.since(session.phase_since_ms)
	if session.phase == BattingSession.Phase.OVER or e < pres.pitch_interval_ms:
		return held
	if e < pres.pitch_interval_ms + pres.batter_recover_ms:
		return f["recover"] if session.swung else f["settle"]
	return cell


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


## 타석 화면의 야구장 원근 (도트 배경과 맞춘 값): 홈에서 투수 쪽으로 d m 인 점은 화면 높이 HORIZON_Y + FIELD_A / (d + FIELD_C),
## 옆으로 x m 는 FIELD_F × x / (d + FIELD_C) px. 홈(0.95)·마운드(MOUND_Y)·지평선(HORIZON_Y)과 파울 라인이 배경 그림과 맞게 정했다
const FIELD_A := 2.31
const FIELD_C := 4.72
const FIELD_F := 427.0


## 야구장 좌표 (x 1루 쪽, y 투수 쪽, m) → 타석 화면
func _field_to_screen(p: Vector2) -> Vector2:
	var k := 1.0 / (p.y + FIELD_C)
	return Vector2(size.x * 0.5 + FIELD_F * p.x * k, size.y * (HORIZON_Y + FIELD_A * k))


## 투수 뒤의 수비수들과 누상의 주자 (도트). 먼 사람부터 그려 가까운 사람이 앞에 온다.
## 크기는 원근대로 고르되 도트가 깨지지 않게 정수 배율(1~4배)로만
func _draw_pixel_defense() -> void:
	var cfg := session.config.ball_physics
	var people := []  # [야구장 좌표, 우리 팀인가]
	var positions := BattedBallSim.fielder_positions(cfg, false)
	for name: String in positions:
		if name != "P" and name != "C":
			people.append([positions[name], false])
	# 주자: 베이스에서 다음 베이스 쪽으로 3m 리드
	var bases: Array = session.situation.get("bases", [-1, -1, -1])
	for i in 3:
		if int(bases[i]) >= 0:
			var at := cfg.base_pos(i + 1)
			var next := cfg.base_pos(i + 2) if i < 2 else Vector2.ZERO
			people.append([at.move_toward(next, 3.0), true])
	people.sort_custom(func(a: Array, b: Array) -> bool: return (a[0] as Vector2).y > (b[0] as Vector2).y)
	for person: Array in people:
		var p: Vector2 = person[0]
		var feet := _field_to_screen(p)
		# 사람 키 1.8m 의 화면 높이 → 그림 8칸에 맞는 정수 배율 (SD 라 1.3배 크게)
		var tall := 1.3 * 1.8 * FIELD_F / (p.y + FIELD_C)
		PixelArt.draw_fielder_screen(self, feet, person[1], clampf(round(tall / PixelMeta.FIELDER_CELL.y), 1.0, 4.0))


## 도트 시험판: 포수 뒷모습. 홈플레이트 뒤에 쪼그려 앉아 헬멧 꼭대기가 존 아래 아홉 칸에 온다 (어깨까지만 보인다) (아래는 화면 밖)
func _draw_pixel_catcher() -> void:
	var u := PixelArt.unit(size)
	var feet := Vector2(size.x * 0.5, size.y * (ZONE_Y + ZONE_HALF_H) - PixelMeta.CATCHER_TOP * u + u * 9.0)
	PixelArt.draw_cell(self, "catcher", 0, PixelMeta.CATCHER_CELL, PixelMeta.CATCHER_FEET, feet, size)


## 포수 미트: 투수가 노린 곳(힌트). 제구가 흔들리면 공은 미트에서 벗어난다
func _draw_catcher_mitt() -> void:
	if not session.config.catcher_hint or session.pitch == null:
		return
	# 와인드업부터 공이 미트에 꽂혀 판정이 날 때까지 계속 보인다
	if session.phase != BattingSession.Phase.WINDUP and session.phase != BattingSession.Phase.FLIGHT:
		return
	# 예상 투구 위치: 공이 지금 향하는 곳 (변화구는 처음엔 "휘기 전 방향", 꺾이는 만큼 따라 움직인다).
	# 포수는 처음엔 노린 곳(intended)에 미트를 대고 있다가 공이 다가올수록 실제로 오는 곳으로 옮긴다 → 도착 순간 공과 겹친다
	var p := session.pitch
	var t_ms := session.since(session.phase_since_ms) if session.phase == BattingSession.Phase.FLIGHT else 0.0
	var u := p.trajectory.time_fraction(t_ms)
	var at := _aim_on_screen(_pointing(p, t_ms) + (p.intended - p.trajectory.aim) * (1.0 - u))
	# 공을 받을 만큼 크게 (홈플레이트에서 공보다 조금 크다)
	PixelArt.draw_cell(self, "mitt", 0, PixelMeta.MITT_SIZE, PixelMeta.MITT_SIZE * 0.5 - Vector2(0.5, 0.5), at, size)


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
	else:
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
