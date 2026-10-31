class_name PixelArt
extends RefCounted
## 도트 시험판 (2026-10-30): 타석 화면을 코드로 찍은 도트 그림으로 그린다. 카툰 그림(BatterSprite·PitcherPoses)과 비교해 보고 하나로 정한다.
##
## 그림은 scripts/make_pixel_art.py 가 만든다 (art/pixel/, 칸 크기·포즈 순서는 PixelMeta).
## 그림 1픽셀을 화면 SCALE 칸으로 키우고, 모든 그림을 같은 칸 격자에 맞춰 놓아 픽셀이 어긋나지 않게 한다.
## 공·조준 표시·타격 이펙트는 칸에 맞추면 덜덜 떨려 보여서 매끈하게 그대로 그린다.

const DIR := "res://art/pixel/"
## 끄면 카툰 그림으로 돌아간다 (시험판 비교용)
static var enabled := true
## 투수는 멀리 있어 한 칸을 화면 2배로 그린다 (다른 그림은 3배). 투수 그림 키 24칸 → 화면 48
const PITCHER_SCALE := 2.0 / 3.0
static var _textures := {}


static func on() -> bool:
	return enabled and texture("field") != null


static func texture(name: String) -> Texture2D:
	if not _textures.has(name):
		var path := DIR + name + ".png"
		_textures[name] = load(path) if ResourceLoader.exists(path) else null
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


static func draw_field(canvas: CanvasItem, view_size: Vector2) -> void:
	var u := unit(view_size)
	canvas.draw_texture_rect(texture("field"), Rect2(origin(view_size), PixelMeta.FIELD_SIZE * u), false)


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


## 수비 화면 경기장을 도트로: 화면 한 칸(그림 1픽셀)마다 그 자리가 땅의 어디인지 카메라를 거꾸로 계산해 색을 정한다
## (잔디 줄무늬·내야 흙·파울 라인·베이스·담장·관중석). 그래서 원근이 BroadcastView 의 계산과 똑같이 맞는다
static func draw_broadcast_field(canvas: CanvasItem, cam: BroadcastView.Cam, cfg: BattedBallSim.Config) -> void:
	var u := unit(cam.rect.size)
	var key := "%s|%s|%s|%s|%s" % [cam.rect, cam.focal, cam.center, cfg.center_fence, cfg.line_fence]
	if not _field_cache.has(key):
		_field_cache.clear()
		_field_cache[key] = ImageTexture.create_from_image(_broadcast_field_image(cam, cfg, u))
	var tex: Texture2D = _field_cache[key]
	canvas.draw_texture_rect(tex, Rect2(cam.rect.position, tex.get_size() * u), false)


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
	# 베이스
	for b in [1, 2, 3]:
		if p.distance_to(cfg.base_pos(b)) < maxf(0.45, m_per_px * 0.7):
			return Tokens.CHALK
	if r < maxf(0.5, m_per_px * 0.8):
		return Tokens.CHALK  # 홈플레이트
	var fair := absf(deg) <= 45.0 and r <= cfg.fence_at(deg)
	# 내야 흙: 베이스를 감싸는 다이아몬드(안쪽 잔디 빼고) + 마운드 + 홈 둘레
	var dx := absf(p.x)
	var dy := absf(p.y - d)
	var in_outer := dx + dy <= d + 6.0
	var in_inner := dx + dy <= d - 4.5
	if p.distance_to(Vector2(0, 18.4)) < 2.7 or r < 4.0 or (in_outer and not in_inner and p.y > -4.0):
		return dirt2 if (int(floor(p.x * 1.5)) + int(floor(p.y * 1.5))) % 7 == 0 else dirt
	if not fair:
		return foul if p.y > -14.0 else Tokens.DIRT.darkened(0.25)
	# 잔디 깎은 줄무늬 (바둑판)
	var band := (int(floor((p.x + p.y) / 9.0)) + int(floor((p.y - p.x) / 9.0))) % 2
	return grass if band == 0 else grass2


## 관중석: 줄마다 관중 점 (번호로 정해지는 가짜 난수 — 매번 같다)
static func _stands_color(x: int, y: int, wall_top: float) -> Color:
	if wall_top > 0.0 and y > wall_top - 3.0:
		return Tokens.STANDS_DARK
	if y % 2 == 1:
		return Tokens.STANDS
	var hsh := fposmod(sin(x * 12.9898 + y * 78.233) * 43758.5453, 1.0)
	if hsh < 0.45:
		return Tokens.STANDS
	return Tokens.CROWD[int(hsh * 997.0) % Tokens.CROWD.size()]


## 수비 화면 선수 (원근으로 크기를 바꾸지 않는 도트). vel: 화면에서 움직이는 방향 (멈춰 있으면 0 — 서기, 아니면 달리기 두 칸 번갈아)
static func draw_fielder(canvas: CanvasItem, cam: BroadcastView.Cam, at: Vector2, us: bool, vel: Vector2 = Vector2.ZERO, t: float = 0.0) -> void:
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
static func draw_fielder_screen(canvas: CanvasItem, feet: Vector2, us: bool, px: float) -> void:
	var tex := texture("fielders")
	if tex == null:
		return
	var cell := PixelMeta.FIELDER_CELL
	var index := 0 if us else PixelMeta.FIELDER_FRAMES
	feet = feet.round()
	canvas.draw_rect(Rect2(feet + Vector2(-2, 0) * px, Vector2(4, 1) * px), Tokens.SHADOW)
	canvas.draw_texture_rect_region(tex, Rect2(feet - PixelMeta.FIELDER_FEET * px, cell * px), Rect2(Vector2(index * cell.x, 0), cell))
