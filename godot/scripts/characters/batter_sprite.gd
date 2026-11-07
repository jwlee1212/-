class_name BatterSprite
extends Node2D
## 타자 뒷모습 (오른손 타자): 도트 시트(art/pixel/batter.png)의 칸을 갈아 끼운다.
## 원점은 두 발 사이의 바닥, 그림 1픽셀 = 노드 크기 1 (화면 크기는 scale 로, 도트 격자에 맞추는 건 BattingView 가 한다).
## 칸 순서·크기·발 자리는 scripts/make_pixel_art.py 가 만든 PixelMeta 에 있다.

## SpriteCook 타자 시트(art/pixel/batter_ext.png, scripts/tidy_sheet.py batter)가 있으면 그것을 쓴다 (2026-11-07 인게임 갈아엎기).
## 칸 순서는 같고 칸 크기·발 자리·촘촘함(DENSITY)만 BatterExtMeta 에서 읽는다. 지우면 코드로 찍은 타자로 돌아간다.

var _sprite: Sprite2D
var _shown := -1
var _cell: Vector2
## 경기장 도트 1칸에 들어가는 이 그림의 칸 수. 화면 배율 = PixelArt.unit / density (BattingView)
var density := 1


func _init() -> void:
	texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	_sprite = Sprite2D.new()
	_sprite.centered = false
	_sprite.region_enabled = true
	var ext := PixelArt.texture("batter_ext")
	if ext != null:
		_sprite.texture = ext
		_cell = BatterExtMeta.CELL
		_sprite.offset = -BatterExtMeta.FEET
		density = BatterExtMeta.DENSITY
	else:
		_sprite.texture = PixelArt.texture("batter")
		_cell = PixelMeta.BATTER_CELL
		_sprite.offset = -PixelMeta.BATTER_FEET
	add_child(_sprite)
	show_frame(PixelMeta.BATTER["stance"])


## index 번째 칸을 보여 준다
func show_frame(index: int) -> void:
	if index == _shown:
		return
	_shown = index
	_sprite.region_rect = Rect2(Vector2(index * _cell.x, 0), _cell)
