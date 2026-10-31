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


## 시트의 index 번째 칸을 발 자리 feet 에 그린다
static func draw_cell(canvas: CanvasItem, sheet: String, index: int, cell: Vector2, cell_feet: Vector2, feet: Vector2, view_size: Vector2) -> void:
	var tex := texture(sheet)
	if tex == null:
		return
	var u := unit(view_size)
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
