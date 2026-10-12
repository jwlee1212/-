class_name BatterSprite
extends Node2D
## 타자 뒷모습 (오른손 타자): 포즈마다 통째로 그린 그림을 갈아 끼운다 (2026-10-12 결정 — 파츠 컷아웃을 그만뒀다).
##
## 그림은 art/characters/batter/<포즈>.png. 없는 포즈는 준비 자세(stance)로 대신한다.
## 좌표: 원점은 두 발 사이의 바닥, 단위는 준비 자세 그림의 픽셀 (키 = HEIGHT). 화면 크기는 scale 로 맞춘다.
## 포즈마다 그림 크기와 발 자리가 달라서 FRAMES 에 "발 자리(그림 픽셀)"와 "키(헬멧 꼭대기~발바닥, 그림 픽셀)"를 적어 두고,
## 그 값으로 모든 포즈의 키와 발 자리를 맞춘다. 새 그림을 넣으면 scripts/cut_batter_poses.py 로 자른 뒤 여기 숫자를 맞춘다.

const ART := "res://art/characters/batter/"
## 키 (헬멧 꼭대기 ~ 발바닥). 배트는 이보다 위로 올라갈 수 있다
const HEIGHT := 803.0
## 준비 자세에서 손(배트 손잡이)의 자리와 배트 길이 — 배트 궤적 잔상을 그릴 때 쓴다
const HANDS := Vector2(300, -325)
const BAT_LENGTH := 560.0
## 스윙 진행률이 이 값을 넘으면 맞는 순간 그림에서 팔로스루 그림으로 바뀐다
const FOLLOW_FROM := 0.7

# 포즈: [발 자리, 키]
const FRAMES := {
	"stance": [Vector2(171, 844), 803.0],
	"load": [Vector2(171, 844), 803.0],
	"contact": [Vector2(171, 844), 803.0],
	"follow": [Vector2(171, 844), 803.0],
	"bunt": [Vector2(171, 844), 803.0],
}

var _sprite: Sprite2D
var _textures := {}
var _shown := ""


func _init() -> void:
	texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS
	_sprite = Sprite2D.new()
	_sprite.centered = false
	add_child(_sprite)
	for frame: String in FRAMES:
		var path := ART + frame + ".png"
		if ResourceLoader.exists(path):
			_textures[frame] = load(path)
	pose(0.0, -1.0, false)


## 이 포즈의 그림이 따로 있는가 (없으면 준비 자세로 대신한다)
func has_frame(frame: String) -> bool:
	return _textures.has(frame)


## 지금 상태에 맞는 포즈 이름. load 는 힘 모으기(0~1), swing 은 스윙 진행률(0~1, 안 휘두르면 음수)
static func frame_for(load: float, swing: float, bunt: bool) -> String:
	if bunt:
		return "bunt"
	if swing < 0.0:
		return "load" if load >= 0.5 else "stance"
	return "contact" if swing < FOLLOW_FROM else "follow"


func pose(load: float, swing: float, bunt: bool) -> void:
	show_frame(frame_for(load, swing, bunt))


func show_frame(frame: String) -> void:
	if not _textures.has(frame):
		frame = "stance"
	if frame == _shown or not _textures.has(frame):
		return
	_shown = frame
	var info: Array = FRAMES[frame]
	var s: float = HEIGHT / info[1]
	_sprite.texture = _textures[frame]
	_sprite.offset = -info[0]
	_sprite.scale = Vector2(s, s)
