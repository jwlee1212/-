class_name BatterSprite
extends Node2D
## 타자 뒷모습 (오른손 타자): 도트 시트(art/pixel/batter.png)의 칸을 갈아 끼운다.
## 원점은 두 발 사이의 바닥, 그림 1픽셀 = 노드 크기 1 (화면 크기는 scale 로, 도트 격자에 맞추는 건 BattingView 가 한다).
## 칸 순서·크기·발 자리는 scripts/make_pixel_art.py 가 만든 PixelMeta 에 있다.

var _sprite: Sprite2D
var _shown := -1


func _init() -> void:
	texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	_sprite = Sprite2D.new()
	_sprite.centered = false
	_sprite.texture = PixelArt.texture("batter")
	_sprite.region_enabled = true
	_sprite.offset = -PixelMeta.BATTER_FEET
	add_child(_sprite)
	show_frame(PixelMeta.BATTER["stance"])


## index 번째 칸을 보여 준다
func show_frame(index: int) -> void:
	if index == _shown:
		return
	_shown = index
	_sprite.region_rect = Rect2(Vector2(index * PixelMeta.BATTER_CELL.x, 0), PixelMeta.BATTER_CELL)
