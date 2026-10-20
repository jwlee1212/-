extends Control
## 확인용 스크린샷: 8개 구종 궤적을 겹쳐 그린다 → godot/build/shots/pitches.png (우투수) · pitches_left.png (좌투수)
##   ~/Applications/Godot.app/Contents/MacOS/Godot --always-on-top --path godot res://tests/shots_pitches.tscn
## 왼쪽: 타석 화면과 같은 원근으로 본 모습 (모두 존 한가운데로 도착). 오른쪽: 같은 화면에서 공의 높이·좌우를 거리에 따라 편 것.
## 점은 비행 시간 10% 간격 — 점 사이가 좁아지면 감속, 큰 동그라미는 휘기 시작하는 곳(break_start)

## 그리는 좌표는 게임 기준 크기(844×390). 창은 그 두 배라 선명하게 찍힌다
const W := 844
const H := 390
## 타석 화면(844×390)에서 잘라 올 부분과 확대 배율
const CROP := Rect2(300, 150, 244, 190)
const CROP_SCALE := 1.7
const DOTS := 10
const SAMPLES := 80

var _view: BattingView
var _catalog: PitchCatalog
var _hand := "R"


func _ready() -> void:
	get_window().mode = Window.MODE_WINDOWED
	get_window().size = Vector2i(W * 2, H * 2)
	var balance := BalanceLoader.load_balance()
	var config := BattingConfig.from_balance(balance)
	_catalog = config.catalog
	_view = BattingView.new()
	_view.session = BattingSession.new(config, Presentation.from_balance(balance))
	_view.size = Vector2(844, 390)
	_view._layout()
	DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path("res://build/shots"))
	for hand in ["R", "L"]:
		_hand = hand
		queue_redraw()
		await get_tree().create_timer(0.3).timeout
		await RenderingServer.frame_post_draw
		get_viewport().get_texture().get_image().save_png("res://build/shots/%s.png" % ("pitches" if hand == "R" else "pitches_left"))
	get_tree().quit()


func _pitch(id: String) -> Pitch:
	var p := Pitch.new()
	p.type = id
	p.hand = _hand
	p.flight_ms = _catalog.flight_ms(id)
	p.trajectory = PitchTrajectory.make(_catalog.spec(id), Vector2.ZERO, p.flight_ms, _hand, _catalog.location_range)
	return p


func _draw() -> void:
	if _view == null:
		return
	draw_rect(Rect2(0, 0, W, H), Tokens.SCOREBOARD)
	_draw_front(Rect2(8, 40, CROP.size.x * CROP_SCALE, CROP.size.y * CROP_SCALE))
	var right := Rect2(432, 40, 404, 160)
	_draw_profile(right, "화면 속 높이 (선: 손 높이 · 존 위 · 존 아래)", 1)
	_draw_profile(Rect2(right.position + Vector2(0, 172), right.size), "화면 속 좌우 (위 = 화면 오른쪽 = 우타자 바깥쪽)", 0)
	_draw_legend(Vector2(10, 14))


## 타석 화면의 원근 그대로 (batting_view.gd _ball)
func _draw_front(r: Rect2) -> void:
	draw_rect(r, Tokens.GRASS)
	_text(r.position + Vector2(6, 16), "타석 화면 원근 (%s투수, 모두 존 한가운데 도착)" % ("우" if _hand == "R" else "좌"), Tokens.CHALK)
	var to_panel := func(v: Vector2) -> Vector2: return r.position + (v - CROP.position) * CROP_SCALE
	var zone_tl: Vector2 = to_panel.call(_view.zone_to_screen(Vector2(-1, -1)))
	var zone_br: Vector2 = to_panel.call(_view.zone_to_screen(Vector2(1, 1)))
	draw_rect(Rect2(zone_tl, zone_br - zone_tl), Tokens.CHALK, false, 2.0)
	for id: String in _catalog.specs:
		var p := _pitch(id)
		var color: Color = Tokens.PITCH_COLORS.get(id, Tokens.PRIMARY)
		var pts := PackedVector2Array()
		for i in SAMPLES + 1:
			var b := _view._ball(p, p.flight_ms * i / SAMPLES)
			pts.append(to_panel.call(Vector2(b.x, b.y)))
		draw_polyline(pts, color, 1.6, true)
		for i in DOTS + 1:
			var b := _view._ball(p, p.flight_ms * i / DOTS)
			draw_circle(to_panel.call(Vector2(b.x, b.y)), 1.0 + b.z * 3.0, color, true, -1.0, true)
		var start_ms := _break_start_ms(p)
		var bs := _view._ball(p, start_ms)
		draw_circle(to_panel.call(Vector2(bs.x, bs.y)), 4.5, color, false, 1.2, true)


## 타석 화면에서 공이 어떻게 움직이나: 가로 = 투수 → 홈플레이트 거리, 세로 = 화면 속 공의 높이(axis 1, 위 = 화면 위) 또는 좌우(axis 0, 위 = 화면 오른쪽)
func _draw_profile(r: Rect2, title: String, axis: int) -> void:
	draw_rect(r, Color(Tokens.INK, 0.6))
	_text(r.position + Vector2(6, 16), title, Tokens.CHALK)
	var zone_mid := _view.zone_to_screen(Vector2.ZERO)
	var top := _view._release.y - 12.0
	var bottom := _view.zone_to_screen(Vector2(0, 1.6)).y
	var to_panel := func(dist: float, v: Vector2) -> Vector2:
		var k: float
		if axis == 1:
			k = (v.y - top) / (bottom - top)  # 0 = 손 높이 위, 1 = 존 아래
		else:
			k = 0.5 - (v.x - zone_mid.x) / (_view._zone_half.x * 8.0)
		return Vector2(r.position.x + 22 + dist * (r.size.x - 40), r.position.y + 24 + k * (r.size.y - 34))
	# 기준선: 손 높이 / 존 위·아래 (axis 1), 존 가운데 (axis 0)
	if axis == 1:
		for y in [_view._release.y, _view.zone_to_screen(Vector2(0, -1)).y, _view.zone_to_screen(Vector2(0, 1)).y]:
			draw_line(to_panel.call(0.0, Vector2(0, y)), to_panel.call(1.0, Vector2(0, y)), Tokens.HUD_BORDER, 1.0)
	else:
		draw_line(to_panel.call(0.0, zone_mid), to_panel.call(1.0, zone_mid), Tokens.HUD_BORDER, 1.0)
	_text(Vector2(r.position.x + 6, r.end.y - 5), "투수", Tokens.HUD_TEXT_SOFT)
	_text(Vector2(r.end.x - 52, r.end.y - 5), "홈플레이트", Tokens.HUD_TEXT_SOFT)
	for id: String in _catalog.specs:
		var p := _pitch(id)
		var t := p.trajectory
		var color: Color = Tokens.PITCH_COLORS.get(id, Tokens.PRIMARY)
		var pts := PackedVector2Array()
		for i in SAMPLES + 1:
			var t_ms := p.flight_ms * i / SAMPLES
			var b := _view._ball(p, t_ms)
			pts.append(to_panel.call(t.distance_fraction(t_ms), Vector2(b.x, b.y)))
		draw_polyline(pts, color, 1.6, true)
		for i in DOTS + 1:
			var t_ms := p.flight_ms * i / DOTS
			var b := _view._ball(p, t_ms)
			draw_circle(to_panel.call(t.distance_fraction(t_ms), Vector2(b.x, b.y)), 1.6, color, true, -1.0, true)
		var s_ms := _break_start_ms(p)
		var bs := _view._ball(p, s_ms)
		draw_circle(to_panel.call(t.distance_fraction(s_ms), Vector2(bs.x, bs.y)), 4.0, color, false, 1.2, true)


func _draw_legend(at: Vector2) -> void:
	var i := 0
	for id: String in _catalog.specs:
		var s := _catalog.spec(id)
		var pos := at + Vector2((i % 4) * 208, (i / 4) * 14)
		draw_circle(pos + Vector2(4, -4), 4.0, Tokens.PITCH_COLORS.get(id, Tokens.PRIMARY), true, -1.0, true)
		_text(pos + Vector2(10, 0), "%s %.0fkm/h %.0fms" % [s.name_ko, s.speed_kmh, _catalog.flight_ms(id)], Tokens.CHALK)
		i += 1


## 거리 진행률이 break_start 에 닿는 시각 (이분법)
func _break_start_ms(p: Pitch) -> float:
	var lo := 0.0
	var hi := p.flight_ms
	for i in 30:
		var mid := (lo + hi) * 0.5
		if p.trajectory.distance_fraction(mid) < p.trajectory.break_start:
			lo = mid
		else:
			hi = mid
	return hi


func _text(pos: Vector2, s: String, color: Color) -> void:
	draw_string(Tokens.FONT_BOLD, pos, s, HORIZONTAL_ALIGNMENT_LEFT, -1, Tokens.FONT_CAPTION, color)
