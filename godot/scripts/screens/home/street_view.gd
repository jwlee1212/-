class_name StreetView
extends Control
## 로비 골목 화면 (2026-11-09 기획서 4-3장): 옆에서 본 동네를 손가락으로 좌우로 밀고, 장소를 누르면
## 내 선수(도트)가 걸어가 문으로 들어간 뒤 entered(장소 id) 를 보낸다. 장소 배치·걸음은 로직(StreetMap · StreetWalk)이 정한다.
## 골목 그림(map.art)이 있으면 그 그림을 원본 그대로 깔고, 없으면 건물을 코드로 임시로 그린다 (이름표도 임시 때만).
## 시간대(mood): morning 그대로 · dusk 경기 뒤 노을 막 · snow 겨울 캠프 눈 막 + 눈송이.
## 방향키(키보드 ←→, 화면 ‹ › 버튼 = hold_dir)로 직접 걸을 수도 있다. 문 앞(interactRange)에 서면 near_changed(장소 id) →
## 화면이 [들어가기]를 띄우고, ↑·Space·Enter 나 그 버튼(interact)으로 들어간다.
## 내 선수는 체력 테스트의 달리는 도트(minigame runner)를 빌려 쓴다 — 걷는 SpriteCook 그림이 오면 바꾼다.

signal entered(place_id: String)
## 상호작용할 수 있는 장소가 바뀌었다 ("" = 문 앞이 아니다)
signal near_changed(place_id: String)

## 빌려 쓰는 도트 칸: 서 있는 장 · 걷는 장들
const IDLE_FRAME := 6
const WALK_FRAMES := [1, 2, 3, 4]

var map: StreetMap
## 카메라 왼쪽 끝 (그림 픽셀)
var cam := 0.0
## 내 선수 발 자리 (그림 픽셀)
var player_x := 0.0
var facing := 1
var mood := "morning"
## 장소 id → 말풍선 글자 (볼 것이 있는 장소)
var alerts: Dictionary = {}
## 화면 버튼으로 누르고 있는 방향 (-1 · 0 · 1)
var hold_dir := 0
## true 를 돌려주면 키 입력을 받지 않는다 (폰·대화·팝업이 떠 있을 때)
var blocked := Callable()
## 지금 문 앞인 장소 ("" = 아님)
var near := ""

var _cfg: Dictionary
var _tex: Texture2D
var _walk: StreetWalk
var _walk_place := ""
var _walk_t0 := 0
var _pressed := false
var _moved := false
var _press_x := 0.0
var _fling := 0.0
var _placed := false
## 방향키로 걷는 중인가, 걸은 시간 (걸음 장)
var _moving := false
var _move_t := 0.0


func _init(p_map: StreetMap, cfg: Dictionary, spot: String) -> void:
	map = p_map
	_cfg = cfg
	player_x = map.door_x(spot)
	clip_contents = true
	mouse_filter = Control.MOUSE_FILTER_STOP
	if map.art != "" and ResourceLoader.exists(map.art):
		_tex = load(map.art)


func _ready() -> void:
	resized.connect(queue_redraw)


## 그림 픽셀 → 화면 픽셀 배율
func scale_px() -> float:
	return size.y / map.height if size.y > 0.0 else 1.0


func _view_w() -> float:
	return size.x / scale_px()


## 지금 걷고 있나 (걷는 동안은 다른 장소를 눌러도 무시)
func walking() -> bool:
	return _walk != null


## 장소로 걸어간다 (도착하면 entered)
func go_to(id: String) -> void:
	if _walk != null or map.place(id).is_empty():
		return
	_walk = StreetWalk.plan(player_x, map.door_x(id), _cfg)
	_walk_place = id
	_walk_t0 = Time.get_ticks_usec()
	facing = _walk.facing()
	_fling = 0.0


## 문 앞이면 그 장소로 들어간다 (남은 몇 걸음을 걸어 문으로)
func interact() -> void:
	if near != "":
		go_to(near)


## 키 입력을 받을 수 있나 (골목이 보이고, 위에 아무것도 안 떠 있을 때)
func _controls_on() -> bool:
	return is_visible_in_tree() and not (blocked.is_valid() and blocked.call())


## 걷기를 끝까지 건너뛴다 (스크린샷 스크립트용)
func finish_walk() -> void:
	if _walk != null:
		_walk_t0 = Time.get_ticks_usec() - int(_walk.duration() * 1e6) - 1


func _walk_t() -> float:
	return (Time.get_ticks_usec() - _walk_t0) / 1e6


func _process(delta: float) -> void:
	# 크기가 정해진 첫 프레임에 선수(서 있는 장소)를 화면 가운데로
	if not _placed and size.y > 0.0:
		_placed = true
		cam = map.camera_left(player_x, _view_w())
	_moving = false
	if _walk == null and _controls_on():
		var dir := hold_dir
		if dir == 0:
			dir = signi(int(Input.is_action_pressed("ui_right")) - int(Input.is_action_pressed("ui_left")))
		if dir != 0:
			_moving = true
			_move_t += delta
			facing = dir
			_fling = 0.0
			player_x = map.step(player_x, dir, delta, float(_cfg["moveSpeed"]), float(_cfg["edgeMargin"]))
			cam = lerpf(cam, map.camera_left(player_x, _view_w()), 1.0 - exp(-float(_cfg["followRate"]) * delta))
		if Input.is_action_just_pressed("ui_accept") or Input.is_action_just_pressed("ui_up"):
			interact()
	var n := map.near_place(player_x, float(_cfg["interactRange"])) if _walk == null else ""
	if n != near:
		near = n
		near_changed.emit(near)
	if _walk != null:
		var t := _walk_t()
		player_x = _walk.x_at(t)
		var want := map.camera_left(player_x, _view_w())
		cam = lerpf(cam, want, 1.0 - exp(-float(_cfg["followRate"]) * delta))
		if _walk.done(t):
			cam = want  # 돌아왔을 때 선수가 화면 가운데 있게
			var id := _walk_place
			_walk = null
			_walk_place = ""
			entered.emit(id)
	elif not _pressed and absf(_fling) > 1.0:
		cam = map.clamp_camera(cam + _fling * delta, _view_w())
		_fling *= exp(-float(_cfg["flingDecay"]) * delta)
	queue_redraw()


func _gui_input(event: InputEvent) -> void:
	if _walk != null:
		return
	var mb := event as InputEventMouseButton
	if mb != null and mb.button_index == MOUSE_BUTTON_LEFT:
		if mb.pressed:
			_pressed = true
			_moved = false
			_press_x = mb.position.x
			_fling = 0.0
		elif _pressed:
			_pressed = false
			if not _moved:
				var id := map.place_at(cam + mb.position.x / scale_px())
				if id != "":
					go_to(id)
		accept_event()
		return
	var mm := event as InputEventMouseMotion
	if mm != null and _pressed:
		if absf(mm.position.x - _press_x) > float(_cfg["tapSlopPx"]):
			_moved = true
		if _moved:
			cam = map.clamp_camera(cam - mm.relative.x / scale_px(), _view_w())
			_fling = -mm.velocity.x / scale_px()
		accept_event()


# ---------- 그리기 ----------

func _draw() -> void:
	var s := scale_px()
	if _tex != null:
		# 원본 그림 그대로: 보이는 부분만 잘라 화면 높이에 맞춘다
		draw_texture_rect_region(_tex, Rect2(Vector2.ZERO, size), Rect2(cam * _tex.get_width() / map.width, 0, _view_w() * _tex.get_width() / map.width, _tex.get_height()))
	else:
		draw_set_transform(Vector2(-cam * s, 0), 0.0, Vector2(s, s))
		_draw_placeholder()
		draw_set_transform(Vector2.ZERO)
		for p: Dictionary in map.places:
			_draw_tag(p, s)
	_draw_player(s)
	for id: String in alerts:
		_draw_bubble(map.place(id), str(alerts[id]), s)
	_draw_mood()


## 그림이 없을 때의 임시 골목 (그림 픽셀 좌표로 그린다)
func _draw_placeholder() -> void:
	var w := map.width
	var g := map.ground_y
	draw_rect(Rect2(0, 0, w, g), Tokens.STREET_SKY)
	draw_rect(Rect2(0, g * 0.55, w, g * 0.45), Tokens.STREET_SKY_LOW)
	# 먼 건물 줄 (번호로 정해지는 높이 — 연출용 가짜 난수)
	var bx := 0.0
	var i := 0
	while bx < w:
		var bw := 180.0 + _hash01(i) * 220.0
		var bh := 220.0 + _hash01(i + 97) * 260.0
		draw_rect(Rect2(bx, g - 120 - bh, bw - 12, bh + 120), Tokens.STREET_FAR)
		bx += bw
		i += 1
	for p: Dictionary in map.places:
		_draw_building(p)
	draw_rect(Rect2(0, g, w, 70), Tokens.STREET_SIDEWALK)
	draw_rect(Rect2(0, g + 70, w, map.height - g - 70), Tokens.STREET_ROAD)
	var lx := 0.0
	while lx < w:
		draw_rect(Rect2(lx, g + 150, 120, 10), Tokens.STREET_ROAD_LINE)
		lx += 260.0


func _draw_building(p: Dictionary) -> void:
	var x: float = p["x"]
	var w: float = p["w"]
	var door: float = p["door"]
	var g := map.ground_y
	match str(p["kind"]):
		"apartment":
			draw_rect(Rect2(x, 230, w, g - 230), Tokens.STREET_WALL_A)
			draw_rect(Rect2(x - 10, 210, w + 20, 30), Tokens.STREET_ROOF)
			for row in 6:
				for col in int(w / 140.0):
					draw_rect(Rect2(x + 40 + col * 140, 280 + row * 100, 80, 56), Tokens.STREET_WINDOW)
			draw_rect(Rect2(door - 90, g - 260, 180, 260), Tokens.STREET_ROOF)
		"store":
			draw_rect(Rect2(x, 540, w, g - 540), Tokens.STREET_WALL_B)
			draw_rect(Rect2(x - 10, 520, w + 20, 30), Tokens.STREET_ROOF)
			draw_rect(Rect2(x + 30, 590, w - 60, 90), Tokens.SURFACE)
			for k in int(w / 80.0):
				draw_rect(Rect2(x + k * 80, 700, 40, 60), Tokens.STREET_AWNING)
			draw_rect(Rect2(x + 40, 790, w * 0.35, 120), Tokens.STREET_WINDOW)
			draw_rect(Rect2(door - 80, g - 260, 160, 260), Tokens.STREET_ROOF)
		"container":
			draw_rect(Rect2(x, 680, w, g - 680), Tokens.STREET_CONTAINER)
			for k in int(w / 40.0):
				draw_rect(Rect2(x + k * 40, 680, 8, g - 680), Color(Tokens.NAVY, 0.15))
			draw_rect(Rect2(door - 80, g - 250, 160, 250), Tokens.NAVY_SOFT)
		"field":
			draw_rect(Rect2(x, g - 60, w, 60), Tokens.DIRT)
			for k in int(w / 100.0) + 1:
				draw_rect(Rect2(x + k * 100, 540, 8, g - 540), Tokens.STREET_FENCE)
			for k in 6:
				draw_rect(Rect2(x, 560 + k * 60, w, 3), Color(Tokens.STREET_FENCE, 0.6))
			draw_rect(Rect2(x + w * 0.62, 380, 16, g - 380), Tokens.STREET_FENCE)
			draw_rect(Rect2(x + w * 0.62 - 160, 380, 340, 220), Color(Tokens.STREET_FENCE, 0.35))
		"school":
			draw_rect(Rect2(x, 300, w, g - 300), Tokens.STREET_WALL_B)
			draw_rect(Rect2(x - 10, 280, w + 20, 30), Tokens.STREET_ROOF)
			draw_circle(Vector2(x + w / 2.0, 380), 50, Tokens.SURFACE)
			draw_arc(Vector2(x + w / 2.0, 380), 50, 0, TAU, 32, Tokens.STREET_ROOF, 8)
			for row in 4:
				for col in int(w / 130.0):
					draw_rect(Rect2(x + 40 + col * 130, 470 + row * 100, 84, 60), Tokens.STREET_WINDOW)
			draw_rect(Rect2(door - 100, g - 260, 200, 260), Tokens.STREET_ROOF)
		"stadium":
			var stands := PackedVector2Array([Vector2(x + 60, g), Vector2(x + 220, 470), Vector2(x + w - 220, 470), Vector2(x + w - 60, g)])
			draw_colored_polygon(stands, Tokens.STANDS_DARK)
			for row in 5:
				draw_rect(Rect2(x + 240 + row * 30, 520 + row * 80, w - 480 - row * 60, 14), Tokens.STANDS)
			for lx: float in [x + 120, x + w - 136]:
				draw_rect(Rect2(lx, 200, 16, g - 200), Tokens.STREET_FENCE)
				draw_rect(Rect2(lx - 60, 160, 136, 60), Tokens.SCOREBOARD)
				draw_rect(Rect2(lx - 50, 170, 116, 40), Tokens.LED_ON)
			draw_rect(Rect2(door - 120, g - 280, 240, 280), Tokens.SCOREBOARD)


## 임시 이름표 (그림이 없을 때만): 문 위
func _draw_tag(p: Dictionary, s: float) -> void:
	var font := Tokens.FONT_BOLD
	var fs := Tokens.FONT_CAPTION + 1
	var text: String = p["name"]
	var tw := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, fs).x
	var cx: float = (float(p["door"]) - cam) * s
	var y := (map.ground_y - 360.0) * s
	var r := Rect2(cx - tw / 2.0 - Tokens.SPACE_SM, y, tw + Tokens.SPACE_SM * 2, fs + Tokens.SPACE_SM)
	if r.end.x < 0 or r.position.x > size.x:
		return
	var box := StyleBoxFlat.new()
	box.bg_color = Tokens.STREET_TAG
	box.set_corner_radius_all(Tokens.SPACE_SM)
	draw_style_box(box, r)
	draw_string(font, Vector2(r.position.x + Tokens.SPACE_SM, r.position.y + fs + 1), text, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, Tokens.HUD_TEXT)


## 내 선수: 발을 보도에 맞춰 정수 배율로. 왼쪽으로 걸으면 좌우만 뒤집는다 (돌리지 않는다)
func _draw_player(s: float) -> void:
	var sheet := PixelArt.texture("minigame")
	if sheet == null:
		return
	var info: Array = MinigameMeta.ROWS["runner"]
	var cell := MinigameMeta.CELL
	var unit := maxi(1, roundi(size.y * float(_cfg["playerHeight"]) / cell.y))
	var frame := IDLE_FRAME
	var alpha := 1.0
	if _moving:
		frame = WALK_FRAMES[int(_move_t / float(_cfg["stepSec"])) % WALK_FRAMES.size()]
	if _walk != null:
		var t := _walk_t()
		if _walk.walking(t):
			frame = WALK_FRAMES[_walk.step_frame(t, WALK_FRAMES.size())]
		alpha = 1.0 - _walk.door_progress(t)
	var foot := Vector2(roundf((player_x - cam) * s), roundf(map.ground_y * s + Tokens.SPACE_SM))
	var src := Rect2(Vector2(frame * cell.x, int(info[0]) * cell.y), cell)
	var dst := Rect2(-MinigameMeta.FOOT * unit, cell * unit)
	draw_set_transform(foot, 0.0, Vector2(facing, 1))
	draw_texture_rect_region(sheet, dst, src, Color(1, 1, 1, alpha))
	draw_set_transform(Vector2.ZERO)


## 볼 것이 있는 장소 위 말풍선
func _draw_bubble(p: Dictionary, text: String, s: float) -> void:
	if p.is_empty():
		return
	var font := Tokens.FONT_BOLD
	var fs := Tokens.FONT_CAPTION + 1
	var tw := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, fs).x
	var cx: float = (float(p["door"]) - cam) * s
	# 살짝 위아래로 통통 (시간으로만 정해진다)
	var bob := sin(Time.get_ticks_msec() / 260.0) * 2.0
	var h := float(Tokens.STREET_BUBBLE_HEIGHT)
	var y := (map.ground_y - 360.0) * s - h - Tokens.SPACE_SM + bob
	var r := Rect2(cx - tw / 2.0 - Tokens.SPACE_SM, y, tw + Tokens.SPACE_SM * 2, h)
	var box := StyleBoxFlat.new()
	box.bg_color = Tokens.ACCENT
	box.set_corner_radius_all(int(h / 2.0))
	draw_style_box(box, r)
	draw_colored_polygon(PackedVector2Array([Vector2(cx - 5, r.end.y - 1), Vector2(cx + 5, r.end.y - 1), Vector2(cx, r.end.y + 6)]), Tokens.ACCENT)
	draw_string(font, Vector2(r.position.x + Tokens.SPACE_SM, r.position.y + h / 2.0 + fs * 0.36), text, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, Tokens.HUD_TEXT)


func _draw_mood() -> void:
	match mood:
		"dusk":
			draw_rect(Rect2(Vector2.ZERO, size), Tokens.STREET_DUSK)
		"snow":
			draw_rect(Rect2(Vector2.ZERO, size), Tokens.STREET_SNOW_TINT)
			var t := Time.get_ticks_msec() / 1000.0
			for i in 70:
				var x := fposmod(_hash01(i) * size.x + sin(t * 0.8 + i) * 12.0 - cam * scale_px() * 0.3, size.x)
				var y := fposmod(_hash01(i + 300) * size.y + t * (24.0 + _hash01(i + 600) * 30.0), size.y)
				draw_circle(Vector2(x, y), 1.5 + _hash01(i + 900) * 1.5, Tokens.STREET_SNOW)


## 번호로 정해지는 0~1 (연출용 가짜 난수, CLAUDE.md §4-2)
static func _hash01(i: int) -> float:
	var h := (i * 374761393 + 668265263) & 0x7fffffff
	h = ((h ^ (h >> 13)) * 1274126177) & 0x7fffffff
	return float(h % 10000) / 10000.0
