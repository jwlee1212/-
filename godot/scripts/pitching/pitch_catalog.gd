class_name PitchCatalog
extends RefCounted
## 구종 데이터 (config/balance.json 의 batting.pitches · batting.pitch_common). 읽고 검증만 한다.
##
## 변화량은 투수 기준이다: break_x + = 투수 팔 쪽(우투수면 우타자 몸쪽), - = 글러브 쪽. break_y + = 위, - = 아래.
## 단위는 존 좌표와 같은 "존 반폭 = 1" (존 가장자리까지가 1).
## 비행 시간은 포심 비행 시간 × (포심 구속 / 그 구종 구속) — 구속 비율 그대로 늦게 온다.

## 꼭 있어야 하는 구종 (화면 버튼·테스트가 이 목록을 쓴다)
const IDS: Array[String] = ["four_seam", "two_seam", "slider", "curve", "changeup", "sweeper", "forkball", "splitter"]
const SPEED_MIN_KMH := 90.0
const SPEED_MAX_KMH := 170.0


## 구종 하나
class Spec:
	var id: String
	var name_ko: String
	var speed_kmh: float
	var break_x: float
	var break_y: float
	## 거리 진행률이 이 값을 넘어야 휘기 시작한다 (0 이상 1 미만)
	var break_start: float
	## 클수록 막판에 몰아서 꺾인다 (1 = 휘기 시작부터 고르게)
	var break_pow: float
	## 날아오는 도중에만 불룩한 정도 (투수 기준, 도착점은 그대로). 커브가 손을 떠나며 떠오르는 것 같은 구종 버릇
	var arc_x: float
	var arc_y: float
	## 이 구종의 감속 (없으면 pitch_common.decel). 체인지업처럼 직구 팔 스윙으로 나와 홈 앞에서 죽는 공은 크게
	var decel: float
	## 손을 떠난 직후 위로 튀어 오르는 높이 (존 반폭 단위, 0 = 없음). 커브의 "뽕" — 화면에서 손 위로 솟았다가 떨어진다
	var pop: float
	## 튀어 오름이 가장 높은 때 (거리 진행률, 0 초과 1 미만)
	var pop_peak: float
	## 투구 위치에 따른 변화폭 (0 = 어디로 던져도 같다). 휘는 방향으로 던질수록 더, 반대로 던질수록 덜 휜다
	var location_effect: float
	## 유인구로 던질 때 노리는 방향 ("high" | "low" | "away" | "inside", 타자 기준)
	var chase_spots: PackedStringArray


var specs: Dictionary = {}  # id -> Spec (json 순서 유지)
## 감속 보정 (0 이상 1 미만)
var decel: float
## 포심이 홈플레이트까지 걸리는 시간 (ms). 다른 구종 비행 시간의 기준. 손맛 패널의 "공 비행 시간"이 이 값을 바꾼다
var four_seam_flight_ms: float
## 공마다 비행 시간이 ± 이만큼 흔들린다 (ms)
var flight_jitter_ms: float
## 투구 위치에 따른 변화폭 배율의 하한·상한 (x = location_min, y = location_max)
var location_range := Vector2.ONE
## 화면 구속 전용: 비행 시간이 기본값과 다른 만큼 구속 숫자를 이 비율로만 바꾼다 (1 = 물리 그대로)
var speed_display_spread: float


func spec(id: String) -> Spec:
	return specs[id]


## 투수 보정·흔들림 전의 기본 비행 시간
func flight_ms(id: String) -> float:
	return four_seam_flight_ms * spec("four_seam").speed_kmh / spec(id).speed_kmh


## 실제 비행 시간 → 화면에 보여 줄 구속. 기본 비행 시간이면 그 구종의 speed_kmh, 빨리 오면 높다.
## 물리 그대로면 흔들림 ±30ms 가 ±11km/h 라 speed_display_spread 만큼 줄여 보여 준다 (판정과는 무관)
func speed_kmh(id: String, actual_flight_ms: float) -> int:
	var ratio := flight_ms(id) / actual_flight_ms - 1.0
	return roundi(spec(id).speed_kmh * (1.0 + ratio * speed_display_spread))


## 문제 목록 (비었으면 통과)
func validate() -> PackedStringArray:
	var errors := PackedStringArray()
	for id in IDS:
		if not specs.has(id):
			errors.append("구종 %s 가 없다" % id)
	if not errors.is_empty():
		return errors
	if decel < 0.0 or decel >= 1.0:
		errors.append("decel %.3f 이 0 이상 1 미만이 아니다 (넘으면 공이 뒤로 간다)" % decel)
	if four_seam_flight_ms <= 0.0:
		errors.append("four_seam_flight_ms 가 0 이하")
	if location_range.x <= 0.0 or location_range.x > 1.0 or location_range.y < 1.0:
		errors.append("location_min 은 0 초과 1 이하, location_max 는 1 이상이어야 한다")
	if flight_jitter_ms < 0.0:
		errors.append("flight_jitter_ms 가 음수")
	var fastest := ""
	var slowest := ""
	for id: String in specs:
		var s: Spec = specs[id]
		if s.speed_kmh < SPEED_MIN_KMH or s.speed_kmh > SPEED_MAX_KMH:
			errors.append("%s 구속 %.0f 가 %.0f~%.0f 밖" % [id, s.speed_kmh, SPEED_MIN_KMH, SPEED_MAX_KMH])
		if s.break_start < 0.0 or s.break_start >= 1.0:
			errors.append("%s break_start %.2f 가 0 이상 1 미만이 아니다" % [id, s.break_start])
		if s.decel < 0.0 or s.decel >= 1.0:
			errors.append("%s decel %.3f 이 0 이상 1 미만이 아니다" % [id, s.decel])
		if s.pop < 0.0 or s.pop_peak <= 0.0 or s.pop_peak >= 1.0:
			errors.append("%s pop %.2f · pop_peak %.2f 범위 밖 (pop ≥ 0, 0 < pop_peak < 1)" % [id, s.pop, s.pop_peak])
		if s.location_effect < 0.0 or s.location_effect > 1.0:
			errors.append("%s location_effect %.2f 가 0~1 밖" % [id, s.location_effect])
		if s.break_pow < 1.0:
			errors.append("%s break_pow %.2f 가 1 미만" % [id, s.break_pow])
		if s.name_ko == "":
			errors.append("%s name_ko 가 비었다" % id)
		for spot in s.chase_spots:
			if not spot in ["high", "low", "away", "inside"]:
				errors.append("%s 유인구 방향 %s 를 모른다" % [id, spot])
		if s.chase_spots.is_empty():
			errors.append("%s chase_spots 가 비었다" % id)
		if fastest == "" or s.speed_kmh > spec(fastest).speed_kmh:
			fastest = id
		if slowest == "" or s.speed_kmh < spec(slowest).speed_kmh:
			slowest = id
	if fastest != "four_seam":
		errors.append("포심이 가장 빨라야 한다 (지금 %s)" % fastest)
	if slowest != "curve":
		errors.append("커브가 가장 느려야 한다 (지금 %s)" % slowest)
	return errors


## batting 섹션에서 읽는다. 키가 없거나 숫자가 아니면 BattingConfig.num 이 바로 멈춘다
static func from_batting(s: Dictionary) -> PitchCatalog:
	var c := PitchCatalog.new()
	var common: Dictionary = s["pitch_common"]
	c.decel = BattingConfig.num(common, "decel")
	c.four_seam_flight_ms = BattingConfig.num(common, "four_seam_flight_ms")
	c.flight_jitter_ms = BattingConfig.num(common, "flight_jitter_ms")
	c.speed_display_spread = BattingConfig.num(common, "speed_display_spread")
	c.location_range = Vector2(BattingConfig.num(common, "location_min"), BattingConfig.num(common, "location_max"))
	var pitches: Dictionary = s["pitches"]
	for id: String in pitches:
		if id.begins_with("_"):
			continue
		var d: Dictionary = pitches[id]
		var p := Spec.new()
		p.id = id
		p.name_ko = d["name_ko"]
		p.speed_kmh = BattingConfig.num(d, "speed_kmh")
		p.break_x = BattingConfig.num(d, "break_x")
		p.break_y = BattingConfig.num(d, "break_y")
		p.break_start = BattingConfig.num(d, "break_start")
		p.break_pow = BattingConfig.num(d, "break_pow")
		p.arc_x = float(d.get("arc_x", 0.0))
		p.arc_y = float(d.get("arc_y", 0.0))
		p.decel = float(d.get("decel", c.decel))
		p.location_effect = float(d.get("location_effect", 0.0))
		p.pop = float(d.get("pop", 0.0))
		p.pop_peak = float(d.get("pop_peak", 0.3))
		p.chase_spots = PackedStringArray(d["chase_spots"])
		c.specs[id] = p
	return c
