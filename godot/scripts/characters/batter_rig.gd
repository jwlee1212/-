class_name BatterRig
extends Node2D
## 타자 뒷모습 컷아웃 캐릭터 (오른손 타자). 파츠 그림(art/characters/batter/)을 Skeleton2D 뼈대에 붙여 움직인다.
##
## 좌표: 원점은 골반 가운데, 단위는 원본 시트의 픽셀 (머리 꼭대기 ≈ −475, 발바닥 ≈ +268). 화면 크기는 scale 로 맞춘다.
## 뼈대: hip → spine → (head, 가까운 팔 위·아래·손, 먼 팔 위·아래·손) / hip → 다리 둘. 배트는 따로 손 위치에 놓는다.
## 그리는 순서는 뼈대 순서와 달라서(먼 팔 → 다리 → 골반 → 몸통 → 머리 → 배트 → 가까운 팔 → 장갑),
## 그림은 _skin 아래에 그리는 순서대로 두고 매번 뼈의 위치·각도를 옮겨 적는다 (z_index 는 HUD 와 섞여서 안 쓴다).
## 자세는 pose() 하나로 정한다: 손 위치와 배트 각도를 주면 두 팔은 팔꿈치가 알아서 접힌다(2관절 IK).

const ART := "res://art/characters/batter/"
const FEET_Y := 268.0
const HEAD_TOP_Y := -475.0
## 배트 손잡이(아래 손)에서 끝까지의 길이
const BAT_LENGTH := 300.0
## 아래 손에서 위 손까지 (배트를 따라)
const GRIP_GAP := 34.0
## 공이 배트에 맞는 지점의 스윙 진행률 (BattingView.CONTACT_SWING 과 같은 뜻 — 손이 가장 멀리 뻗는 때)
const CONTACT_SWING := 0.45

# 파츠: [그림, 관절 자리(그림 픽셀), 크기 비율]. 팔 그림은 시트보다 약 2배 크게 그려져 있어 줄인다
const PARTS := {
	"pelvis": ["pelvis", Vector2(96, 20), 1.0],
	"torso": ["torso", Vector2(126, 180), 1.0],
	"head": ["head", Vector2(170, 270), 1.25],
	"leg_back": ["leg_back", Vector2(48, 30), 1.0],
	"leg_front": ["leg_front", Vector2(48, 30), 1.0],
	"upper_arm": ["upper_arm", Vector2(88, 70), 0.42],
	"forearm": ["forearm", Vector2(131, 70), 0.42],
	"glove_top": ["glove_top", Vector2(46, 60), 0.75],
	"glove_bottom": ["glove_bottom", Vector2(46, 60), 0.75],
	"bat": ["bat", Vector2(30, 330), 0.9],
}
# 팔 그림에서 어깨→팔꿈치, 팔꿈치→손목 (그림 픽셀)
const UPPER_ARM_VECTOR := Vector2(43, 192)
const FOREARM_VECTOR := Vector2(-39, 175)

# 뼈 자리 (부모 뼈 기준)
const SPINE_AT := Vector2(0, 10)
const HEAD_AT := Vector2(-5, -150)
const SHOULDER_NEAR_AT := Vector2(88, -108)
const SHOULDER_FAR_AT := Vector2(40, -90)
const LEG_BACK_AT := Vector2(-52, 100)
const LEG_FRONT_AT := Vector2(46, 100)
const NUMBER_AT := Vector2(0, -28)

# 손(아래 손) 자리: 준비 → 힘 모으기 → 맞는 순간 → 팔로스루, 번트
const HANDS_STANCE := Vector2(188, -92)
const HANDS_LOAD := Vector2(178, -104)
const HANDS_CONTACT := Vector2(200, -30)
const HANDS_FINISH := Vector2(185, -55)
const HANDS_BUNT := Vector2(185, -25)

## 등번호
var number := 7:
	set(value):
		number = value
		if _number != null:
			_number.queue_redraw()

var _skeleton: Skeleton2D
var _skin: Node2D
var _bones := {}
## 그림 → 따라갈 뼈
var _follow := {}
var _bat: Sprite2D
var _glove_top: Sprite2D
var _glove_bottom: Sprite2D
var _number: _BackNumber


func _init() -> void:
	texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS
	_skeleton = Skeleton2D.new()
	add_child(_skeleton)
	_skin = Node2D.new()
	add_child(_skin)

	var hip := _bone("hip", _skeleton, Vector2.ZERO)
	var spine := _bone("spine", hip, SPINE_AT)
	_bone("head", spine, HEAD_AT)
	_bone("leg_back", hip, LEG_BACK_AT)
	_bone("leg_front", hip, LEG_FRONT_AT)
	for side: String in ["near", "far"]:
		var upper := _bone("upper_" + side, spine, SHOULDER_NEAR_AT if side == "near" else SHOULDER_FAR_AT)
		var lower := _bone("lower_" + side, upper, UPPER_ARM_VECTOR * _part_scale("upper_arm"))
		_bone("hand_" + side, lower, FOREARM_VECTOR * _part_scale("forearm"))

	# 그리는 순서대로
	_sprite("forearm", "lower_far")
	_sprite("upper_arm", "upper_far")
	_sprite("leg_back", "leg_back")
	_sprite("leg_front", "leg_front")
	_sprite("pelvis", "hip")
	_sprite("torso", "spine")
	_number = _BackNumber.new(self)
	_skin.add_child(_number)
	_follow[_number] = _bones["spine"]
	_sprite("head", "head")
	_bat = _sprite("bat", "")
	_sprite("forearm", "lower_near")
	_sprite("upper_arm", "upper_near")
	_glove_bottom = _sprite("glove_bottom", "")
	_glove_top = _sprite("glove_top", "")
	pose(0.0, -1.0, false, -30.0)


## 아래 손(배트 손잡이)의 자리. load 는 힘 모으기(0~1), swing 은 스윙 진행률(0~1, 안 휘두르면 음수)
func hands_position(load: float, swing: float, bunt: bool) -> Vector2:
	if bunt:
		return HANDS_BUNT + (Vector2(12, 0) if swing >= 0.0 else Vector2.ZERO)
	if swing < 0.0:
		return HANDS_STANCE.lerp(HANDS_LOAD, load)
	if swing < CONTACT_SWING:
		return HANDS_LOAD.lerp(HANDS_CONTACT, smoothstep(0.0, 1.0, swing / CONTACT_SWING))
	return HANDS_CONTACT.lerp(HANDS_FINISH, smoothstep(0.0, 1.0, (swing - CONTACT_SWING) / (1.0 - CONTACT_SWING)))


## 자세를 정한다. bat_angle_deg 는 배트 방향(위쪽 0°, 시계 방향 +), bat_stretch 는 배트 길이 배율(멀리 있는 공에 닿게 늘릴 때)
func pose(load: float, swing: float, bunt: bool, bat_angle_deg: float, bat_stretch: float = 1.0) -> void:
	var turn := maxf(swing, 0.0)
	var eased := 1.0 - (1.0 - turn) * (1.0 - turn)
	# 힘을 모으면 몸이 뒤로 살짝 눕고, 휘두르면 홈 쪽으로 돈다. 머리는 반대로 돌아 공을 계속 본다
	var lean := deg_to_rad(-5.0 * load * (1.0 - eased) + (4.0 if bunt else 13.0 * eased))
	_bones["hip"].position = Vector2(10.0 * eased, 4.0 * load * (1.0 - eased))
	_bones["spine"].rotation = lean
	_bones["head"].rotation = -lean * 0.7
	# 앞발: 힘을 모을 때 살짝 들었다가 디딘다
	_bones["leg_front"].rotation = deg_to_rad(-15.0 - 9.0 * load * (1.0 - eased))
	_bones["leg_front"].position = LEG_FRONT_AT + Vector2(0, -8.0 * load * (1.0 - eased))
	_bones["leg_back"].rotation = deg_to_rad(17.0 + 6.0 * eased)

	var grip := hands_position(load, swing, bunt)
	var dir := Vector2.UP.rotated(deg_to_rad(bat_angle_deg))
	# 번트는 위 손이 배트 중간을 받친다
	var top := grip + dir * (BAT_LENGTH * 0.42 if bunt else GRIP_GAP)
	_reach("far", grip)
	_reach("near", top)

	_sync()
	var bat_scale := _part_scale("bat")
	_bat.transform = Transform2D(deg_to_rad(bat_angle_deg), Vector2(bat_scale, bat_scale * bat_stretch), 0.0, grip - dir * 14.0)
	var glove_turn := deg_to_rad(bat_angle_deg + 18.0)
	_glove_bottom.transform = Transform2D(glove_turn, Vector2.ONE * _part_scale("glove_bottom"), 0.0, grip)
	_glove_top.transform = Transform2D(glove_turn, Vector2.ONE * _part_scale("glove_top"), 0.0, top)


# ---------- 뼈대 ----------

func _bone(bone_name: String, parent: Node, at: Vector2) -> Bone2D:
	var bone := Bone2D.new()
	bone.name = bone_name
	bone.position = at
	bone.rest = Transform2D(0.0, at)
	# 끝 뼈는 자식이 없어 길이를 스스로 못 잰다 (경고가 난다) — 직접 준다
	bone.set_autocalculate_length_and_angle(false)
	bone.set_length(40.0)
	parent.add_child(bone)
	_bones[bone_name] = bone
	return bone


func _sprite(part: String, bone_name: String) -> Sprite2D:
	var info: Array = PARTS[part]
	var sprite := Sprite2D.new()
	sprite.texture = load(ART + info[0] + ".png")
	sprite.centered = false
	sprite.offset = -info[1]
	sprite.set_meta("part_scale", info[2])
	_skin.add_child(sprite)
	if bone_name != "":
		_follow[sprite] = _bones[bone_name]
	return sprite


func _part_scale(part: String) -> float:
	return PARTS[part][2]


## 뼈의 위치·각도 (이 캐릭터 좌표 기준)
func _in_rig(bone: Node2D) -> Transform2D:
	var xform := Transform2D.IDENTITY
	var node: Node = bone
	while node != _skeleton:
		xform = (node as Node2D).transform * xform
		node = node.get_parent()
	return xform


## 팔 하나를 target(손목이 갈 자리)까지 뻗는다. 닿는 자세가 둘이면 팔꿈치가 아래로 가는 쪽을 고른다
func _reach(side: String, target: Vector2) -> void:
	var upper: Bone2D = _bones["upper_" + side]
	var lower: Bone2D = _bones["lower_" + side]
	var hand: Bone2D = _bones["hand_" + side]
	var shoulder := _in_rig(upper).origin
	var upper_length := lower.position.length()
	var lower_length := hand.position.length()
	var to_target := target - shoulder
	var distance := clampf(to_target.length(), absf(upper_length - lower_length) + 1.0, upper_length + lower_length - 1.0)
	var along := to_target.normalized() if to_target.length() > 0.001 else Vector2.RIGHT
	var a := (upper_length * upper_length - lower_length * lower_length + distance * distance) / (2.0 * distance)
	var off := sqrt(maxf(upper_length * upper_length - a * a, 0.0))
	var side_dir := Vector2(-along.y, along.x)
	var elbow := shoulder + along * a + side_dir * off
	var other := shoulder + along * a - side_dir * off
	if other.y > elbow.y:
		elbow = other
	var wrist := shoulder + along * distance
	var parent_turn := _in_rig(upper.get_parent()).get_rotation()
	upper.rotation = (elbow - shoulder).angle() - lower.position.angle() - parent_turn
	lower.rotation = (wrist - elbow).angle() - hand.position.angle() - (parent_turn + upper.rotation)


func _sync() -> void:
	for item: Node2D in _follow:
		var xform := _in_rig(_follow[item])
		var s: float = item.get_meta("part_scale", 1.0)
		item.transform = xform * Transform2D(0.0, Vector2(s, s), 0.0, Vector2.ZERO)


## 등번호: 몸통 뼈를 따라 등 가운데에 쓴다
class _BackNumber extends Node2D:
	var _rig: BatterRig

	func _init(rig: BatterRig) -> void:
		_rig = rig

	func _draw() -> void:
		var width := 160.0
		draw_string(Tokens.FONT_BOLD, BatterRig.NUMBER_AT + Vector2(-width / 2.0, 0), str(_rig.number), HORIZONTAL_ALIGNMENT_CENTER, width, 96, Tokens.UNIFORM_US)
