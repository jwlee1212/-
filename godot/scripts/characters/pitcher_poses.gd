class_name PitcherPoses
extends RefCounted
## 투수 앞모습 (오른손 투수, 상대 팀): 포즈마다 통째로 그린 그림. 타석 화면이 진행에 맞는 한 장을 골라 그린다.
##
## 그림은 art/characters/pitcher/<포즈>.png. 다섯 장 모두 같은 크기로 그려져 있어 키는 하나(HEIGHT)로 맞추고,
## 포즈마다 디딤발이 놓인 바닥 자리(그림 픽셀)만 FRAMES 에 적는다. 새 그림은 scripts/cut_character_poses.py 로 자른다.
## 타자와 달리 노드가 아니다 — 투수는 날아오는 공보다 뒤에 그려야 해서 타석 화면의 _draw 안에서 그린다.

const ART := "res://art/characters/pitcher/"
## 키 (모자 꼭대기 ~ 발바닥, 세트 자세 그림의 픽셀)
const HEIGHT := 532.0
## 공을 놓는 순간 그림에서 던지는 손의 자리 (발 자리 기준, 그림 픽셀) — 공이 여기서 출발한다
const RELEASE_HAND := Vector2(-238, -420)
## 포즈가 시작되는 와인드업 진행률(0~1), 순서대로. stride(내딛기)·cock(팔 젖히기)은 그림이 있을 때만 끼어든다
const STARTS := {"set": 0.0, "lift": 0.15, "coil": 0.34, "stride": 0.56, "cock": 0.62, "release": 0.88}
## 쓰는 포즈와 순서. 전부 정면 그림만 쓴다 — 옆모습(coil·stride)을 섞으면 몸 방향이 두 번 돌아 끊겨 보인다.
## 그림은 남겨 뒀으니 여기에 이름을 넣으면 다시 끼어든다 (예: "lift" 뒤에 "coil", "stride")
const ORDER := ["set", "lift", "cock", "release", "follow"]
## 공을 놓은 뒤 이 시간(ms)이 지나면 팔로스루 그림으로 바뀐다
const FOLLOW_AFTER_MS := 90.0
## 팔로스루 그림이 제자리로 돌아오는 시간 (ms)
const SETTLE_MS := 340.0
## 팔을 휘두른 자국(흰 초승달)이 보이는 시간 (ms)
const SWOOSH_MS := 120.0
## 팔로스루 그림에서 던진 손의 자리 (발 자리 기준, 그림 픽셀) — 휘두른 자국이 여기서 끝난다
const FOLLOW_HAND := Vector2(98, -125)

# 포즈: 발 자리
const FRAMES := {
	"set": Vector2(135, 534),
	"lift": Vector2(135, 534),
	"coil": Vector2(126, 515),
	"stride": Vector2(176, 517),
	"cock": Vector2(212, 505),
	"release": Vector2(299, 505),
	"follow": Vector2(218, 521),
}

static var _textures := {}


## 그림이 다 있는가 (없으면 타석 화면이 옛 도형 투수를 그린다)
static func available() -> bool:
	_load()
	return _order().size() == ORDER.size()


## 그림이 있는 포즈를 순서대로 (끼워 넣는 포즈는 그림이 있을 때만)
static func _order() -> Array:
	_load()
	return ORDER.filter(func(f: String) -> bool: return _textures.has(f))


## 지금 상태에 맞는 포즈 이름. windup 은 와인드업 진행률(0~1, 던지기 전이면 0), since_release_ms 는 공을 놓은 뒤 지난 시간(안 놓았으면 음수)
static func frame_for(windup: float, since_release_ms: float) -> String:
	if since_release_ms >= 0.0:
		return "release" if since_release_ms < FOLLOW_AFTER_MS else "follow"
	var frame := "set"
	for f: String in _order():
		if STARTS.has(f) and windup >= STARTS[f]:
			frame = f
	return frame


## 포즈가 시작되는 때 (와인드업을 시작한 뒤 지난 ms)
static func _start_ms(frame: String, windup_ms: float) -> float:
	return windup_ms + FOLLOW_AFTER_MS if frame == "follow" else STARTS[frame] * windup_ms


## 투수를 그린다. 발 자리가 feet 에 오고 키가 height 가 된다. windup·since_release_ms 는 frame_for 와 같다.
## 그림이 몇 장뿐이라 그림 전체를 계속 움직여 사이를 메운다 (_motion). back 은 그린 뒤 되돌릴 캔버스 이동값 (화면 흔들림)
static func draw(canvas: CanvasItem, windup: float, since_release_ms: float, windup_ms: float, feet: Vector2, height: float, back: Vector2 = Vector2.ZERO) -> void:
	_load()
	var frame := frame_for(windup, since_release_ms)
	var now := windup_ms + since_release_ms if since_release_ms >= 0.0 else windup * windup_ms
	var in_ms := now - _start_ms(frame, windup_ms)
	var motion := _motion(frame, in_ms, _length_ms(frame, windup_ms))
	if frame == "set" and windup <= 0.0:
		# 던지기 전: 가만히 서 있지 않고 숨을 쉰다
		motion.z = 0.012 * sin(Time.get_ticks_msec() * 0.0032)
	var texture: Texture2D = _textures.get(frame)
	if texture != null:
		canvas.draw_set_transform_matrix(_matrix(motion, feet, height / HEIGHT))
		canvas.draw_texture(texture, -FRAMES[frame])
		if frame == "follow" and in_ms < SWOOSH_MS:
			_draw_swoosh(canvas, in_ms / SWOOSH_MS)
	canvas.draw_set_transform(back)


## 공이 출발하는 손의 자리 (발 자리 기준, 그림 픽셀) — 공을 놓는 순간의 움직임(크기·기울기)까지 반영한 값
static func release_hand(windup_ms: float) -> Vector2:
	var motion := _motion("release", (1.0 - STARTS["release"]) * windup_ms, _length_ms("release", windup_ms))
	return _matrix(motion, Vector2.ZERO, 1.0) * RELEASE_HAND


## 포즈가 보이는 길이 (ms)
static func _length_ms(frame: String, windup_ms: float) -> float:
	var order := _order()
	var index := order.find(frame)
	if index < 0 or index + 1 >= order.size():
		return SETTLE_MS
	return _start_ms(order[index + 1], windup_ms) - _start_ms(frame, windup_ms)


## 그림 전체의 움직임: x 크기(다가오면 커진다) · y 기울기(+ 면 윗몸이 그림 오른쪽으로) · z 세로 늘어남(- 면 눌림).
## 포즈가 바뀌는 경계에서 크기와 기울기가 이어지게 맞췄다 — 그림은 바뀌어도 몸의 흐름은 끊기지 않는다:
##   준비: 살짝 주저앉는다(예비 동작) → 다리 들기: 솟았다가 뒤로 물러나며 모은다 → 팔 젖히기: 디디며 눌렸다가 다가오고 던지는 쪽으로 젖힌다
##   → 공 놓기: 반대쪽으로 휙 넘어가며 더 다가온다 → 마무리: 출렁이며 제자리로
static func _motion(frame: String, in_ms: float, length_ms: float) -> Vector3:
	var p := clampf(in_ms / maxf(length_ms, 1.0), 0.0, 1.0)
	var out := 1.0 - (1.0 - p) * (1.0 - p)      # 빨리 나갔다 천천히 멈춤
	var both := p * p * (3.0 - 2.0 * p)          # 천천히 출발, 천천히 멈춤
	match frame:
		"set":
			return Vector3(1.0, 0.0, -0.04 * p * p)
		"lift":
			var rise := clampf(p / 0.4, 0.0, 1.0)
			var up := -0.04 + 0.1 * (1.0 - (1.0 - rise) * (1.0 - rise))   # -0.04 → +0.06
			var dip := clampf((p - 0.7) / 0.3, 0.0, 1.0)
			return Vector3(lerpf(1.0, 0.97, both), -0.03 * both, up - 0.06 * dip * dip)
		"coil", "stride":
			return Vector3(0.97, -0.03, 0.0)
		"cock":
			return Vector3(lerpf(0.97, 1.05, out), lerpf(-0.03, -0.07, both), lerpf(-0.06, 0.02, out))
		"release":
			return Vector3(lerpf(1.05, 1.12, out), lerpf(-0.07, 0.07, out), lerpf(0.05, 0.0, p))
		"follow":
			var wobble := cos(p * PI * 2.5) * (1.0 - p) * (1.0 - p)
			return Vector3(lerpf(1.12, 1.0, out), 0.07 * (1.0 - both), -0.05 * wobble)
	return Vector3(1.0, 0.0, 0.0)


## 움직임 값을 그리는 변환으로: 발은 제자리에 두고 윗몸만 옆으로 민다(기울기), 세로로 늘어나면 가로는 그만큼 줄어든다
static func _matrix(motion: Vector3, feet: Vector2, unit: float) -> Transform2D:
	var sx := unit * motion.x * (1.0 - motion.z * 0.5)
	var sy := unit * motion.x * (1.0 + motion.z)
	return Transform2D(Vector2(sx, 0.0), Vector2(-motion.y * sy, sy), feet)


## 팔을 휘두른 자국: 공을 놓은 손 자리에서 팔로스루 손 자리까지 휘는 흰 초승달. e(0~1)가 커지며 꼬리부터 사라진다
static func _draw_swoosh(canvas: CanvasItem, e: float) -> void:
	var from := RELEASE_HAND
	var to := FOLLOW_HAND
	var bend := Vector2(from.x - 10.0, to.y + 10.0)
	var left := PackedVector2Array()
	var right := PackedVector2Array()
	const STEPS := 10
	for i in STEPS + 1:
		var t := lerpf(e, 1.0, float(i) / STEPS)
		var q := from.lerp(bend, t).lerp(bend.lerp(to, t), t)
		var along := (bend - from).lerp(to - bend, t).normalized()
		var half := 26.0 * sin(float(i) / STEPS * PI)
		left.append(q + along.orthogonal() * half)
		if i > 0 and i < STEPS:
			right.append(q - along.orthogonal() * half * 0.35)
	right.reverse()
	left.append_array(right)
	canvas.draw_colored_polygon(left, Color(Tokens.CHALK, 0.6 * (1.0 - e)))


static func _load() -> void:
	if not _textures.is_empty():
		return
	for frame: String in FRAMES:
		var path := ART + frame + ".png"
		if ResourceLoader.exists(path):
			_textures[frame] = load(path)
