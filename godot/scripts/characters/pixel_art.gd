class_name PixelArt
extends RefCounted
## 인게임 그림은 모두 코드로 찍은 도트다 (2026-10-30 시험 → 2026-10-31 확정, 원칙 6·7): 타석 경기장·투수·타자·미트·수비수, 중계 화면 경기장.
##
## 그림은 scripts/make_pixel_art.py 가 만든다 (art/pixel/, 칸 크기·포즈 순서는 PixelMeta).
## 그림 1픽셀을 화면 SCALE 칸으로 키우고, 모든 그림을 같은 칸 격자에 맞춰 놓아 픽셀이 어긋나지 않게 한다.
## 공·조준 표시·타격 이펙트는 칸에 맞추면 덜덜 떨려 보여서 매끈하게 그대로 그린다.

const DIR := "res://art/pixel/"
## 투수는 멀리 있어 한 칸을 화면 2배로 그린다 (다른 그림은 3배). 투수 그림 키 24칸 → 화면 48
const PITCHER_SCALE := 2.0 / 3.0
static var _textures := {}


## 고해상도 일러스트 시트 (2026-11-10 유저 결정): 원본 해상도 그대로 받아 부드러운 필터로 띄운다.
## 고해상도 그림은 축소·확대할 때 부드러운 필터를 쓴다.
const SMOOTH := ["batter_ext", "pitcher_ext"]


const ILLUSTRATED_DIR := "res://art/illustrated/"
const ILLUSTRATED_CELL := Vector2(512, 512)
const ILLUSTRATED_FEET := Vector2(256, 504)
const ILLUSTRATED_HEIGHT := 320.0
const FIELDER_ILLUSTRATED := {
	"ready": 0, "grounder": 1, "throw_ready": 2, "throw": 3,
	"first_base_catch": 4, "dive": 5, "fly": 6,
}


static func illustrated_texture(name: String) -> Texture2D:
	var path := ILLUSTRATED_DIR + name + ".png"
	if not _textures.has(path):
		var smooth := CanvasTexture.new()
		smooth.diffuse_texture = load(path)
		smooth.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS
		_textures[path] = smooth
	return _textures[path]


## 칸마다 바닥을 맞춘 시트에서 기존 화면 키에 맞춰 그린다.
static func draw_illustrated(canvas: CanvasItem, name: String, frame: int, feet: Vector2, height: float, us: bool, flip: bool = false) -> void:
	var tex := illustrated_texture(name + ("" if us else "_them"))
	var scale := height / ILLUSTRATED_HEIGHT
	var dst := Rect2(feet - ILLUSTRATED_FEET * scale, ILLUSTRATED_CELL * scale)
	if flip:
		dst.position.x = feet.x + ILLUSTRATED_FEET.x * scale
		dst.size.x = -dst.size.x
	var src := Rect2(Vector2(frame % 4, floori(frame / 4.0)) * ILLUSTRATED_CELL, ILLUSTRATED_CELL)
	canvas.draw_texture_rect_region(tex, dst, src)


static func texture(name: String) -> Texture2D:
	if not _textures.has(name):
		var path := DIR + name + ".png"
		var tex: Texture2D = load(path) if ResourceLoader.exists(path) else null
		if tex != null and name in SMOOTH:
			# 그리는 노드의 필터(도트 화면은 nearest)와 상관없이 이 그림만 부드럽게
			var smooth := CanvasTexture.new()
			smooth.diffuse_texture = tex
			smooth.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS
			tex = smooth
		_textures[name] = tex
	return _textures[name]


## 화면 한 칸 (그림 1픽셀) 크기. 화면 높이에 배경 높이를 맞춘다
static func unit(view_size: Vector2) -> float:
	return view_size.y / PixelMeta.FIELD_SIZE.y


## 배경 왼쪽 위 (화면 가운데에 놓는다)
static func origin(view_size: Vector2) -> Vector2:
	var u := unit(view_size)
	return Vector2(round(view_size.x * 0.5 - PixelMeta.FIELD_SIZE.x * 0.5 * u), 0.0)


## 화면 좌표를 배경 격자에 맞춘다
static func snap(p: Vector2, view_size: Vector2) -> Vector2:
	var u := unit(view_size)
	var o := origin(view_size)
	return o + ((p - o) / u).round() * u


## 전광판 글자는 기존 71×21 칸을 새 배경의 빈 판 안에 맞춘다.
const BOARD_LED_ILLUSTRATED := Rect2(135, 21, 71, 21)


## 원본 비율을 유지하고 화면을 덮는다. 넓은 화면에서는 위아래만 조금 잘린다.
static func batting_field_rect(view_size: Vector2) -> Rect2:
	var tex := illustrated_texture("stadium_evening_v1")
	var scale := maxf(view_size.x / tex.get_width(), view_size.y / tex.get_height())
	var drawn_size := tex.get_size() * scale
	return Rect2((view_size - drawn_size) * 0.5, drawn_size)


static func draw_field(canvas: CanvasItem, view_size: Vector2) -> void:
	var tex := illustrated_texture("stadium_evening_v1")
	canvas.draw_texture_rect(tex, batting_field_rect(view_size), false)


## 전광판 글자 (3×5, 위 줄부터 — 1 이 켜진 LED)
const LED_FONT := {
	"0": "010101101101010", "1": "010110010010111", "2": "111001111100111", "3": "111001011001111",
	"4": "101101111001001", "5": "111100111001111", "6": "111100111101111", "7": "111001010010010",
	"8": "111101111101111", "9": "111101111001111", "R": "110101110101101", "B": "110101110101110",
	"S": "011100010001110", "O": "111101101101111", "-": "000000111000000",
}


## 경기장 전광판에 줄 점수·볼카운트를 도트로 찍는다 (배경 그림의 LED 판 위, 같은 칸 격자).
## board: rows [[팀색, 이닝별 점수 Array, 합계 문자열, 공격 중], [...]] (원정 먼저), first 맨 왼쪽 이닝 번호, balls·strikes·outs
static func draw_scoreboard(canvas: CanvasItem, view_size: Vector2, board: Dictionary) -> void:
	var led := BOARD_LED_ILLUSTRATED
	var field := batting_field_rect(view_size)
	var o := field.position + field.size * Vector2(0.425, 0.215)
	var u := field.size * Vector2(0.15, 0.125) / led.size
	var dot := func(x: int, y: int, col: Color) -> void:
		canvas.draw_rect(Rect2(o + Vector2(x, y) * u, u), col)
	var text := func(x: int, y: int, s: String, col: Color) -> void:
		for i in s.length():
			var g: String = LED_FONT.get(s[i], "")
			for k in g.length():
				if g[k] == "1":
					dot.call(x + i * 4 + k % 3, y + k / 3, col)
	# 줄: 이닝 번호(2) · 원정(8) · 홈(14). 칸: 팀 띠(2) · 이닝 9칸(6~) · R(44) · 볼카운트(55~)
	var first: int = board.get("first", 1)
	for i in 9:
		text.call(6 + i * 4, 2, str((first + i) % 10), Tokens.LED_DIM)
	text.call(44, 2, " R", Tokens.LED_TEXT)
	var rows: Array = board.get("rows", [])
	for r in rows.size():
		var row: Array = rows[r]
		var y := 8 + r * 6
		var tag: Color = row[0] if row[3] else Color(row[0], 0.45)
		for k in 15:
			dot.call(2 + k % 3, y + k / 3, tag)
		var runs: Array = row[1]
		for i in 9:
			var inning := first + i - 1
			if inning < runs.size():
				text.call(6 + i * 4, y, str(mini(int(runs[inning]), 9)), Tokens.LED_ON)
		var total: String = row[2]
		text.call(44 + (2 - total.length()) * 4, y, total, Tokens.LED_TEXT)
	# 볼카운트: B 초록 3 · S 노랑 2 · O 빨강 2 (꺼진 점은 흐리게)
	var lamps := [["B", board.get("balls", 0), 3, Tokens.COUNT_BALL], ["S", board.get("strikes", 0), 2, Tokens.COUNT_STRIKE],
		["O", board.get("outs", 0), 2, Tokens.COUNT_OUT]]
	for r in 3:
		var lamp: Array = lamps[r]
		var y := 2 + r * 6
		text.call(55, y, lamp[0], Tokens.LED_TEXT)
		for i in int(lamp[2]):
			var col: Color = lamp[3] if i < int(lamp[1]) else Tokens.LED_DIM
			for k in 9:
				dot.call(59 + i * 4 + k % 3, y + 1 + k / 3, col)


## 시트의 index 번째 칸을 발 자리 feet 에 그린다. scale: 칸 크기 배율 (1 = 다른 그림과 같은 한 칸)
static func draw_cell(canvas: CanvasItem, sheet: String, index: int, cell: Vector2, cell_feet: Vector2, feet: Vector2, view_size: Vector2, scale: float = 1.0) -> void:
	var tex := texture(sheet)
	if tex == null:
		return
	var u := unit(view_size) * scale
	var at := snap(feet, view_size) - cell_feet * u
	canvas.draw_texture_rect_region(tex, Rect2(at, cell * u), Rect2(Vector2(index * cell.x, 0), cell))


## 투수 칸: 와인드업 진행률 또는 공을 놓은 뒤 ms 로 고른다
static func pitcher_cell(windup: float, since_release_ms: float) -> int:
	if since_release_ms >= 0.0:
		var index: int = PixelMeta.PITCHER_WINDUP[-1][1]
		for a: Array in PixelMeta.PITCHER_AFTER:
			if since_release_ms >= a[0]:
				index = a[1]
		return index
	var index := 0
	for w: Array in PixelMeta.PITCHER_WINDUP:
		if windup >= w[0]:
			index = w[1]
	return index


## 타자 칸. 스탠스 → 로드 → 스트라이드 → 랜딩 → 회전 → 컨택 → 팔로스루.
## load 힘 모으기(0~1, 와인드업 동안), stride 앞발 내딛기(0~1, 공이 날아오는 동안 — 1 이면 땅을 딛고 발사 자세로 기다림),
## swing 스윙 진행률(0~1, 안 휘두르면 음수), height_px 공이 존 가운데보다 아래로 얼마나(그림 픽셀)
static func batter_cell(load: float, stride: float, swing: float, bunt: bool, height_px: float) -> int:
	var f := PixelMeta.BATTER
	if bunt:
		return f["bunt"]
	if swing < 0.0:
		if stride >= 1.0:
			return f["landing"]
		if stride > 0.5:
			return f["stride"]
		if stride > 0.0:
			return f["stride_up"]
		if load >= 0.66:
			return f["load"]
		if load >= 0.33:
			return f["load_a"]
		# 가만히 있을 땐 숨을 쉰다 (0.7초마다)
		return f["breath"] if (Time.get_ticks_msec() / 700) % 2 == 1 else f["stance"]
	# 회전 두 칸 → 컨택(공 높이별) → 팔 뻗기 → 팔로스루 세 칸 → 마무리
	var steps := [[0.4, "rotate1"], [0.45, "rotate2"], [0.53, "contact"], [0.6, "extend"], [0.7, "follow1"], [0.8, "follow2"], [0.9, "follow3"]]
	for step: Array in steps:
		if swing < step[0]:
			if step[1] != "contact":
				return f[step[1]]
			if height_px < -5.0:
				return f["contact_high"]
			return f["contact_low"] if height_px > 5.0 else f["contact_mid"]
	return f["finish"]


# ---------- 수비 화면(중계) ----------

## 수비 화면 경기장 (카메라·화면 크기마다 한 번 만들어 둔다)
static var _field_cache := {}
const BROADCAST_ART := preload("res://art/illustrated/stadium_broadcast.png")
## 탑뷰 원본의 홈·1/3루·2루 및 가운데 담장 좌표.
const ART_HOME := Vector2(921, 798)
const ART_FIRST := Vector2(1206, 591)
const ART_SECOND_Y := 388.0
const ART_FENCE_Y := 46.0
const ART_MOUND_Y := 580.0
const ART_FOUL_POLE := Vector2(1704, 212)
const MOUND_METERS := 18.4
## 일러스트 메시의 땅 격자: 간격(m)·가로(x) 범위·세로(y, 홈 뒤 → 외야 너머) 범위
const BROADCAST_GRID_M := 2.0
const BROADCAST_GRID_X := Vector2(-170.0, 170.0)
const BROADCAST_GRID_Y := Vector2(-12.0, 180.0)


## 중계 경기장: 옛 도트 판(관중석·담장·땅 색)을 먼저 깔고, 그 위에 탑뷰 일러스트를 땅 격자 메시로 덮는다.
## 일러스트는 픽셀을 하나씩 베끼지 않고 GPU 가 원본에서 바로 읽게 한다 (부드러운 필터·밉맵) — 확대해도, 폰 레티나에서도 선명하다.
## 땅 격자의 각 점을 카메라로 화면에 투영하고 _broadcast_art_point 로 원본 자리(UV)를 정한다
static func draw_broadcast_field(canvas: CanvasItem, cam: BroadcastView.Cam, cfg: BattedBallSim.Config) -> void:
	# 칸 크기는 확대(ZOOM)와 상관없이 보이는 창 기준 — 확대해도 도트가 굵어지지 않는다
	var u := unit(cam.rect.size / BroadcastView.ZOOM)
	# 카메라가 옮겨 가도(pan) 다시 만들지 않게 판 위치와 상관없는 값으로 열쇠를 만든다
	var key := "%s|%s|%s|%s|%s|%s|%s" % [cam.rect.size, cam.focal, (cam.center - cam.rect.position).snapped(Vector2.ONE * 0.001), cfg.center_fence, cfg.line_fence, cfg.fence_height, cfg.base_distance]
	if not _field_cache.has(key):
		_field_cache.clear()
		_field_cache[key] = [ImageTexture.create_from_image(_broadcast_field_image(cam, cfg, u)), _broadcast_art_mesh(cam, cfg)]
	var cached: Array = _field_cache[key]
	var tex: Texture2D = cached[0]
	canvas.draw_texture_rect(tex, Rect2(cam.rect.position, tex.get_size() * u), false)
	canvas.draw_mesh(cached[1], _broadcast_art_texture(), Transform2D(0.0, cam.rect.position))


## 탑뷰 일러스트 (부드러운 필터 + 밉맵: 멀리 있는 외야·관중석이 지글거리지 않게)
static func _broadcast_art_texture() -> Texture2D:
	var path := "broadcast_art"
	if not _textures.has(path):
		var smooth := CanvasTexture.new()
		smooth.diffuse_texture = BROADCAST_ART
		smooth.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS
		_textures[path] = smooth
	return _textures[path]


## 땅 격자 (BROADCAST_GRID_M 간격) → 화면 좌표(판 왼쪽 위 기준)·원본 UV 삼각형 메시.
## 원본 밖으로 완전히 나간 칸은 빼서 아래 도트 판이 보이게 한다 (걸친 칸은 UV 를 가장자리에 붙인다)
static func _broadcast_art_mesh(cam: BroadcastView.Cam, cfg: BattedBallSim.Config) -> ArrayMesh:
	var d := cfg.base_distance / sqrt(2.0)
	var art_size := Vector2(BROADCAST_ART.get_size())
	var xs := BROADCAST_GRID_X
	var ys := BROADCAST_GRID_Y
	var nx := int((xs.y - xs.x) / BROADCAST_GRID_M) + 1
	var ny := int((ys.y - ys.x) / BROADCAST_GRID_M) + 1
	var verts := PackedVector2Array()
	var uvs := PackedVector2Array()
	var inside := PackedByteArray()
	verts.resize(nx * ny)
	uvs.resize(nx * ny)
	inside.resize(nx * ny)
	for j in ny:
		for i in nx:
			var p := Vector2(xs.x + i * BROADCAST_GRID_M, ys.x + j * BROADCAST_GRID_M)
			var uv := _broadcast_art_point(p, d, cfg) / art_size
			var k := j * nx + i
			verts[k] = cam.ground(p) - cam.rect.position
			inside[k] = 1 if uv.x >= 0.0 and uv.x <= 1.0 and uv.y >= 0.0 and uv.y <= 1.0 else 0
			uvs[k] = uv.clamp(Vector2.ZERO, Vector2.ONE)
	var idx := PackedInt32Array()
	for j in ny - 1:
		for i in nx - 1:
			var a := j * nx + i
			var q := [a, a + 1, a + nx + 1, a + nx]
			if inside[q[0]] + inside[q[1]] + inside[q[2]] + inside[q[3]] == 0:
				continue
			idx.append_array([q[0], q[1], q[2], q[0], q[2], q[3]])
	var arrays := []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = verts
	arrays[Mesh.ARRAY_TEX_UV] = uvs
	arrays[Mesh.ARRAY_INDEX] = idx
	var mesh := ArrayMesh.new()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
	return mesh


static func _broadcast_field_image(cam: BroadcastView.Cam, cfg: BattedBallSim.Config, u: float) -> Image:
	var w := int(ceil(cam.rect.size.x / u))
	var h := int(ceil(cam.rect.size.y / u))
	var img := Image.create(w, h, false, Image.FORMAT_RGBA8)
	# 담장: 칸 열마다 담장 꼭대기·밑동의 화면 높이 (파울 지역 담장까지 ±60°)
	var top := PackedFloat32Array()
	var base := PackedFloat32Array()
	top.resize(w)
	base.resize(w)
	top.fill(-1.0)
	base.fill(-1.0)
	var prev_b := Vector2.INF
	var prev_t := Vector2.INF
	for i in 241:
		var deg := -60.0 + i * 0.5
		var dist := cfg.fence_at(clampf(deg, -45.0, 45.0)) + (0.0 if absf(deg) <= 45.0 else 4.0)
		var g := BattedBallSim.polar(deg, dist)
		var b := (cam.ground(g) - cam.rect.position) / u
		var t := (cam.to_screen(Vector3(g.x, g.y, maxf(cfg.fence_height, 2.5))) - cam.rect.position) / u
		if prev_b != Vector2.INF:
			for x in range(int(ceil(minf(prev_b.x, b.x))), int(floor(maxf(prev_b.x, b.x))) + 1):
				if x < 0 or x >= w:
					continue
				var k := 0.0 if absf(b.x - prev_b.x) < 0.001 else (x - prev_b.x) / (b.x - prev_b.x)
				base[x] = lerpf(prev_b.y, b.y, k)
				top[x] = lerpf(prev_t.y, t.y, k)
		prev_b = b
		prev_t = t
	var d := cfg.base_distance / sqrt(2.0)
	var grass := Tokens.GRASS
	var grass2 := Tokens.GRASS_LIGHT
	var foul := Tokens.FOUL_GRASS
	var dirt := Tokens.DIRT
	var dirt2 := Tokens.DIRT.darkened(0.1)
	for y in h:
		for x in w:
			var col: Color
			var sy := float(y) + 0.5
			if top[x] >= 0.0 and sy < top[x]:
				col = _stands_color(x, y, top[x])
			elif base[x] >= 0.0 and sy < base[x]:
				col = Tokens.WALL if sy > top[x] + 1.0 else Tokens.WARN  # 담장 꼭대기 노란 선
			else:
				var p := _ground_at(cam, cam.rect.position + Vector2(x + 0.5, sy) * u)
				if p == Vector2.INF:
					col = _stands_color(x, y, 0.0)
				else:
					col = _ground_color(p, cfg, d, grass, grass2, foul, dirt, dirt2, u / maxf(cam.scale_at(Vector3(p.x, p.y, 0.0)), 0.01))
			img.set_pixel(x, y, col)
	return img


## 홈과 세 베이스를 고정하고, 외야 구간은 가운데 담장에 맞춰 펼친다.
static func _broadcast_art_point(p: Vector2, d: float, cfg: BattedBallSim.Config) -> Vector2:
	var y: float
	if p.y <= d:
		y = remap(p.y, 0.0, d, ART_HOME.y, ART_FIRST.y)
	elif p.y <= d * 2.0:
		y = remap(p.y, d, d * 2.0, ART_FIRST.y, ART_SECOND_Y)
	else:
		var span := maxf(cfg.center_fence - d * 2.0, 1.0)
		var k := clampf((p.y - d * 2.0) / span, 0.0, 1.0)
		var start_slope := maxf((ART_SECOND_Y - ART_FIRST.y) / d, (ART_FENCE_Y - ART_SECOND_Y) / span * 2.5)
		var end_slope := (ART_FENCE_Y - ART_SECOND_Y) / span * 0.5
		y = lerpf(ART_SECOND_Y, ART_FENCE_Y, smoothstep(0.0, 1.0, k))
		y += span * k * (1.0 - k) * (start_slope * (1.0 - k) - end_slope * k)
		y += maxf(p.y - cfg.center_fence, 0.0) * end_slope
	# 마운드는 베이스 좌표를 건드리지 않도록 가운데 좁은 구역에서만 맞춘다.
	var mound_weight := maxf(0.0, 1.0 - absf(p.x) / (d * 0.6)) * maxf(0.0, 1.0 - absf(p.y - MOUND_METERS) / (d * 0.65))
	var mound_y := remap(MOUND_METERS, 0.0, d, ART_HOME.y, ART_FIRST.y)
	y += (ART_MOUND_Y - mound_y) * mound_weight
	var base_scale := (ART_FIRST.x - ART_HOME.x) / d
	# 투영해도 파울선이 1·3루를 지나 외야까지 같은 방향으로 이어진다.
	var x_scale := base_scale
	if p.y > d:
		var line_x := remap(y, ART_FIRST.y, ART_FOUL_POLE.y, ART_FIRST.x, ART_FOUL_POLE.x)
		x_scale = (line_x - ART_HOME.x) / p.y
	return Vector2(ART_HOME.x + p.x * x_scale, y)


## 화면 점 → 땅 위 점 (야구장 좌표). 지평선 위면 INF
static func _ground_at(cam: BroadcastView.Cam, s: Vector2) -> Vector2:
	var q := (s - cam.center) / cam.focal
	var v := -q.y
	var den := cam.sin_p - v * cam.cos_p
	if den <= 0.0001:
		return Vector2.INF
	var dy := BroadcastView.CAM_HEIGHT * (cam.cos_p + v * cam.sin_p) / den
	var depth := dy * cam.cos_p + BroadcastView.CAM_HEIGHT * cam.sin_p
	return Vector2(q.x * depth, dy - BroadcastView.CAM_BACK)


## 땅 색. m_per_px: 그 자리에서 한 칸이 몇 m 인가 (선 굵기를 한 칸 이상으로)
static func _ground_color(p: Vector2, cfg: BattedBallSim.Config, d: float, grass: Color, grass2: Color, foul: Color, dirt: Color, dirt2: Color, m_per_px: float) -> Color:
	var r := p.length()
	var deg := rad_to_deg(atan2(p.x, p.y))
	var line_w := maxf(0.25, m_per_px * 0.6)
	# 파울 라인
	if r > 1.0 and absf(absf(deg) - 45.0) * deg_to_rad(1.0) * r < line_w and r < cfg.line_fence:
		return Tokens.CHALK
	# 베이스: 흰 마름모 판 (2026-11-07 — 멀리서도 보이게 크게, 둘레 한 칸은 연회색)
	var pad := maxf(1.4, m_per_px * 1.6)
	for b in [1, 2, 3]:
		var q := p - cfg.base_pos(b)
		var k := absf(q.x) + absf(q.y)
		if k < pad:
			return Tokens.CHALK if k < pad - m_per_px else Tokens.BASE_EDGE
	if absf(p.x) + absf(p.y) < pad * 0.9:
		return Tokens.CHALK  # 홈플레이트
	var fair := absf(deg) <= 45.0 and r <= cfg.fence_at(deg)
	# 내야 흙: 베이스를 감싸는 다이아몬드(안쪽 잔디 빼고) + 마운드 + 홈 둘레
	var dx := absf(p.x)
	var dy := absf(p.y - d)
	var in_outer := dx + dy <= d + 6.0
	var in_inner := dx + dy <= d - 4.5
	# 흙: 잡점 없이 한 색, 마운드만 조금 밝게 (2026-11-07 잡점 줄이기)
	if p.distance_to(Vector2(0, 18.4)) < 2.7:
		return Tokens.DIRT.lightened(0.08)
	if r < 4.0 or (in_outer and not in_inner and p.y > -4.0):
		return dirt
	# 잔디: 가운데 방향과 나란한 깎은 줄무늬 (위에서 보면 세로 줄). 파울 지역도 같은 줄무늬, 한 톤 어둡게
	var band := int(floor((p.x + 2.5) / 5.0)) % 2
	if not fair:
		if p.y <= -14.0:
			return Tokens.DIRT.darkened(0.25)
		return foul if band == 0 else foul.lightened(0.06)
	return grass if band == 0 else grass2


## 관중석: 사람 한 줄 = 3칸 — 머리(1칸) · 옷(2칸 폭) · 의자 그늘. 번호로 정해지는 가짜 난수라 매번 같다
static func _stands_color(x: int, y: int, wall_top: float) -> Color:
	# 관중석 (2026-11-07 잡점 줄이기): 칸마다 다른 색을 찍지 않고 계단식 좌석 줄(4칸) + 드문드문한 관중 덩어리(2×2칸)
	if wall_top > 0.0 and y > wall_top - 3.0:
		return Tokens.STANDS_DARK
	if y % 4 == 3:
		return Tokens.STANDS_DARK
	var hsh := fposmod(sin(floor(x / 2.0) * 12.9898 + floor(y / 4.0) * 78.233) * 43758.5453, 1.0)
	if hsh < 0.45 and y % 4 != 0:
		return Tokens.CROWD[int(hsh * 997.0) % Tokens.CROWD.size()]
	return Tokens.STANDS


## 수비 화면 선수 (원근으로 크기를 바꾸지 않는 도트). vel: 화면에서 움직이는 방향 (멈춰 있으면 0 — 서기, 아니면 달리기 두 칸 번갈아)
## vel: 최근 FIELDER_VEL_DT 초 동안 화면에서 움직인 픽셀 (호출하는 쪽이 모두 같은 간격으로 잰다).
## pose: 상황 동작 칸 이름(FieldersExtMeta.FRAMES — grounder·fly·throw·dive·slide·lead·onbase), "" 면 움직임으로 고른다.
## face: 동작 칸이 바라볼 쪽 (-1 왼쪽이면 뒤집기, 0·+1 그대로)
static func draw_fielder(canvas: CanvasItem, cam: BroadcastView.Cam, at: Vector2, us: bool, vel: Vector2 = Vector2.ZERO, t: float = 0.0, pose: String = "", face: float = 0.0) -> void:
	if texture("fielders_ext") != null:
		_draw_fielder_ext(canvas, cam, at, us, vel, t, pose, face)
		return
	var tex := texture("fielders")
	if tex == null:
		return
	var u := unit(cam.rect.size)
	var frame := 0
	if vel.length() > 0.05:
		frame = 1 + int(floor(t * 8.0)) % 2
	var index := (0 if us else PixelMeta.FIELDER_FRAMES) + frame
	var feet := cam.ground(at)
	var o := cam.rect.position
	feet = o + ((feet - o) / u).round() * u
	var cell := PixelMeta.FIELDER_CELL
	var region := Rect2(Vector2(index * cell.x, 0), cell)
	var r := Rect2(feet - PixelMeta.FIELDER_FEET * u, cell * u)
	# 그림자 → 사람 (왼쪽으로 달리면 좌우를 뒤집는다)
	canvas.draw_rect(Rect2(feet + Vector2(-2, 0) * u, Vector2(4, 1) * u), Tokens.SHADOW)
	if vel.x < -0.05:
		canvas.draw_texture_rect_region(tex, Rect2(r.position + Vector2(r.size.x, 0), Vector2(-r.size.x, r.size.y)), region)
	else:
		canvas.draw_texture_rect_region(tex, r, region)


## 화면 좌표에 바로 그리는 선수 (타석 화면에서 투수 뒤의 수비수·주자). px: 그림 한 칸의 화면 크기
static func draw_fielder_screen(canvas: CanvasItem, feet: Vector2, us: bool, px: float, pose: String = "ready") -> void:
	if texture("fielders_ext") != null:
		# 옛 그림 키(8칸 × px)에 가장 가까운 줄인 판을 고른다 — 멀수록 작은 판, 화면 1px = 그림 1칸
		var tall := FieldersExtMeta.CELL.y * 0.8
		var k: int = FieldersExtMeta.VARIANTS[-1]
		for v: int in FieldersExtMeta.VARIANTS:
			if tall / v <= 8.0 * px * 1.25:
				k = v
				break
		feet = feet.round()
		canvas.draw_rect(Rect2(feet + Vector2(-0.3, -0.06) * tall / k, Vector2(0.6, 0.12) * tall / k), Tokens.SHADOW)
		if pose == "ready":
			draw_illustrated(canvas, "fielder_batting", 0, feet, tall / k, us)
		else:
			_blit_fielder(canvas, feet, us, FieldersExtMeta.FRAMES.get(pose, 0), k, 1.0, false)
		return
	var tex := texture("fielders")
	if tex == null:
		return
	var cell := PixelMeta.FIELDER_CELL
	var index := 0 if us else PixelMeta.FIELDER_FRAMES
	feet = feet.round()
	canvas.draw_rect(Rect2(feet + Vector2(-2, 0) * px, Vector2(4, 1) * px), Tokens.SHADOW)
	canvas.draw_texture_rect_region(tex, Rect2(feet - PixelMeta.FIELDER_FEET * px, cell * px), Rect2(Vector2(index * cell.x, 0), cell))


## SpriteCook 수비수·주자 (fielders_ext, scripts/tidy_sheet.py fielders — 2026-11-07). 칸 하나를 그린다:
## k = 줄인 판(1/k), scale = 그림 한 칸의 화면 크기, flip = 좌우 뒤집기(왼쪽으로 달릴 때)
static func _blit_fielder(canvas: CanvasItem, feet: Vector2, us: bool, frame: int, k: int, scale: float, flip: bool) -> void:
	var tex := texture("fielders_ext" + ("" if us else "_them") + ("" if k == 1 else "_s%d" % k))
	if tex == null:
		return
	var cell := FieldersExtMeta.CELL / k
	var foot := FieldersExtMeta.FEET / k
	var region := Rect2(Vector2(frame * cell.x, 0), cell)
	var r := Rect2(feet - foot * scale, cell * scale)
	if flip:
		r = Rect2(Vector2(feet.x + (foot.x - cell.x) * scale + cell.x * scale, r.position.y), Vector2(-cell.x * scale, r.size.y))
	canvas.draw_texture_rect_region(tex, r, region)


## 중계 화면 선수 (2026-11-07 유저: 너무 크고 움직임이 이상하다 → 고침):
## - 크기: 그 자리에 선 사람(1.8m × FIELDER_SD)의 원근 키에 가장 가까운 줄인 판(1/k)을 화면 1px = 그림 1칸으로 그린다
##   (FIELDER_SD 1.25 는 실제에 가까워 너무 작고, 옛 그림(키 24px 고정)은 너무 컸다. 2.5등신 SD 로 바꾸며 3.6 — 판이 38·19·13·10px 로 띄엄띄엄이라 내야에서 38px 판이 골라지게, 2026-11-07)
## - 달리기 칸은 시간이 아니라 달린 거리(속도 × 시간)로 넘긴다 — 다리가 땅을 미끄러지지 않는다
## - 방향: 화면에서 거의 세로(세로가 가로의 2배 넘게)로 움직일 때만 등·앞모습, 나머지는 옆모습(왼쪽이면 뒤집기)
## - 느리게(초속 FIELDER_JOG_MPS 아래) 움직이면 조깅 칸, 멈추면 준비 자세
const FIELDER_JOG_MPS := 3.0
const FIELDER_SD := 3.6
const FIELDER_VEL_DT := 0.06
const FIELDER_STRIDE_M := 0.8

static func _draw_fielder_ext(canvas: CanvasItem, cam: BroadcastView.Cam, at: Vector2, us: bool, vel: Vector2, t: float, pose: String = "", face: float = 0.0) -> void:
	var feet := cam.ground(at).round()
	var head := cam.to_screen(Vector3(at.x, at.y, 1.8 * FIELDER_SD))
	var want := maxf(feet.y - head.y, 4.0)
	var full := FieldersExtMeta.CELL.y * 0.8
	# 크기는 연속으로 (2026-11-07 유저: 순간이동처럼 보인다 — 판을 고르기만 하면 달리다 크기가 두 배로 툭 바뀌었다):
	# 원근 키 이상인 판 중 가장 작은 판을 골라 그 키에 맞게 줄여 그린다 (줄이는 배율은 0.5~1)
	var k: int = 1
	for v: int in FieldersExtMeta.VARIANTS:
		if full / v >= want:
			k = v
	var draw_scale := clampf(want / (full / k), 0.5, 1.0)
	var f := FieldersExtMeta.FRAMES
	var frame: int = f["ready"]
	var flip := false
	var run_frame := -1
	# 화면 픽셀 변화 → 실제 속도 (그 자리에서 1m 가 몇 px 인지로 나눈다)
	var px_per_m := maxf(cam.scale_at(Vector3(at.x, at.y, 0.0)), 0.01)
	var speed := vel.length() / FIELDER_VEL_DT / px_per_m
	if pose != "" and (f.has(pose) or FIELDER_ILLUSTRATED.has(pose)):
		frame = f.get(pose, f["ready"])
		flip = face < 0.0
	elif speed > 0.5:
		var step := int(floor(t * speed / FIELDER_STRIDE_M))
		if absf(vel.y) > absf(vel.x) * 2.0:
			run_frame = (4 if vel.y < 0.0 else 0) + step % 4
		elif speed < FIELDER_JOG_MPS:
			frame = [f["jog"], f["run_r2"]][step % 2]
			flip = vel.x < 0.0
		else:
			frame = [f["run_r1"], f["run_r2"], f["run_r3"], f["run_r2"]][step % 4]
			flip = vel.x < 0.0
	var tall := full / k * draw_scale
	canvas.draw_rect(Rect2(feet + Vector2(-0.3, -0.06) * tall, Vector2(0.6, 0.12) * tall), Tokens.SHADOW)
	if run_frame >= 0:
		draw_illustrated(canvas, "fielder_run", run_frame, feet, tall, us)
	elif FIELDER_ILLUSTRATED.has(pose) or frame == f["ready"]:
		draw_illustrated(canvas, "fielder", FIELDER_ILLUSTRATED.get(pose, 0), feet, tall, us, flip)
	else:
		_blit_fielder(canvas, feet, us, frame, k, draw_scale, flip)
