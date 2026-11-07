class_name PixelBall
## 도트 공 (2026-11-07 — 인게임 그림을 SpriteCook 도트로 바꾸면서 공도 도트로 맞춘다).
## 공은 원근으로 지름 2~20칸쯤을 오가서 크기마다 따로 그림을 뽑기 어렵다 → 반지름(칸)마다 같은 규칙으로 찍어 캐시한다:
## 바깥 1칸 윤곽(rim) · 바탕(fill) · 오른쪽 아래 그늘 · 왼쪽 위 빛 1칸 · 반지름 4칸부터 빨간 실밥 두 줄(윤곽에 닿지 않게 한 칸 안쪽까지).
## 난수 없음 — 같은 반지름·색이면 늘 같은 그림. 칸 크기(화면 px)는 그리는 쪽이 정한다 (타자 그림과 같은 칸 크기를 쓴다).

static var _cache := {}


## 반지름 r 칸(1 이상)의 공 그림. fill·rim 은 구종 색으로 물들 수 있어서 인자로 받는다
static func texture(r: int, fill: Color, rim: Color) -> ImageTexture:
	r = maxi(r, 1)
	var key := "%d|%s|%s" % [r, fill.to_html(), rim.to_html()]
	if _cache.has(key):
		return _cache[key]
	var n := r * 2 + 1
	var img := Image.create(n, n, false, Image.FORMAT_RGBA8)
	img.fill(Color(0, 0, 0, 0))
	var inside := func(x: int, y: int) -> bool:
		var dx := x - r
		var dy := y - r
		return x >= 0 and y >= 0 and x < n and y < n and dx * dx + dy * dy <= (r + 0.35) * (r + 0.35)
	var shade := fill.darkened(0.22)
	for y in n:
		for x in n:
			if not inside.call(x, y):
				continue
			var dx := float(x - r)
			var dy := float(y - r)
			var edge: bool = not (inside.call(x - 1, y) and inside.call(x + 1, y) and inside.call(x, y - 1) and inside.call(x, y + 1))
			var c := fill
			if edge or r == 1:
				c = rim
			elif dx + dy > r * 0.55:
				c = shade
			elif r >= 4 and dx * dx + dy * dy < (r - 1.2) * (r - 1.2) and _on_seam(dx, dy, r):
				c = Tokens.BALL_SEAM
			img.set_pixel(x, y, c)
	# 빛: 왼쪽 위 한 칸 (반지름 3칸부터)
	if r >= 3:
		var h := int(round(r * 0.45))
		img.set_pixel(r - h, r - h, fill.lightened(0.6))
	var tex := ImageTexture.create_from_image(img)
	_cache[key] = tex
	return tex


## 실밥: 공 양옆에서 안쪽으로 휘는 두 호 (왼쪽 호는 중심 (-1.55r, 0), 오른쪽은 반대)
static func _on_seam(dx: float, dy: float, r: int) -> bool:
	for side in [-1.0, 1.0]:
		var cx: float = side * r * 1.55
		var d := sqrt((dx - cx) * (dx - cx) + dy * dy)
		if absf(d - r * 1.2) < 0.5:
			return true
	return false


## center(화면 px)에 반지름 radius_px(화면 px)인 공을 칸 크기 cell(화면 px)로 그린다. 칸 격자에 맞춰 자리를 반올림한다
static func draw(canvas: CanvasItem, center: Vector2, radius_px: float, cell: float, fill: Color, rim: Color, alpha: float = 1.0) -> void:
	var r := maxi(int(round(radius_px / cell)), 1)
	var tex := texture(r, fill, rim)
	var side := (r * 2 + 1) * cell
	var at := (center / cell).round() * cell - Vector2(side, side) * 0.5
	canvas.draw_texture_rect(tex, Rect2(at, Vector2(side, side)), false, Color(1, 1, 1, alpha))
