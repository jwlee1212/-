class_name PitchTrajectory
extends RefCounted
## 공 하나의 궤적. "릴리스 후 경과 시간 t(ms)"만으로 위치를 계산하는 순수 함수다 — 어느 프레임에서 불러도 같은 값.
## 노드·그리기·입력·소리는 다루지 않는다 (CLAUDE.md §4-1).
##
## 좌표는 존 좌표 (Pitch 와 같다): 가운데 (0, 0), 존 가장자리 ±1, y 는 아래가 +, x 는 +가 타자에게서 먼 쪽(바깥쪽).
## 화면은 pointing_at(t) — 공이 지금 존 평면 어디를 향하는가 — 을 투수 손에서 원근으로 따라가며 그린다 (batting_view.gd _ball).
## 그래서 꺾이기 전에는 손에서 곧게 날아오고, 꺾이면 향하는 곳이 도착점으로 미끄러진다. position_at 은 옆·위에서 본 실제 길이다.
##
## 투구 위치에 따른 변화폭 (구종마다 location_effect): 휘는 방향으로 던질수록 더, 반대로 던질수록 덜 휜다.
##   배율 = clamp(1 + location_effect × (aim · 휘는 방향 단위벡터), location_min, location_max)
##   예) 우투수 슬라이더는 우타자 바깥쪽(+x)으로 휜다 → 바깥쪽 공은 크게, 몸쪽 공은 덜 휜다
##
## 계산
##   u    = clamp(t / flight_ms, 0, 1)
##   u_w  = u + decel · u · (1 - u)        감속: 앞이 빠르고 뒤가 느리다 (u=0 → 0, u=1 → 1). 구종마다 다르다 (체인지업은 크게)
##   base = lerp(start, raw_end, u_w)       raw_end = aim - break (안 휘었다면 갔을 곳)
##   arc  = arc_vec · sin(π · u_w)          구종의 "버릇": 날아오는 도중에만 불룩 (커브가 손을 떠나며 떠오르는 것). 양 끝에서 0
##   s    = ((u_w - break_start) / (1 - break_start)) ^ break_pow   (break_start 전에는 0)
##   pos  = base + arc + break · s          u_w = 1 이면 정확히 aim
##   pop  = pop · hump(u_w)                 커브의 "뽕": 손을 떠난 직후 위로 튀어 오르는 높이 (존 반폭, 위가 +).
##                                          hump 는 pop_peak 에서 1, 양 끝에서 0 인 부드러운 혹 — 도착점은 그대로

var start := Vector2.ZERO
## 도착점. 항상 여기에 도착한다
var aim := Vector2.ZERO
## 존 좌표로 바꾼 변화량 (투수 손 반영이 끝난 값)
var break_vec := Vector2.ZERO
## 존 좌표로 바꾼 도중 불룩함 (투수 손 반영이 끝난 값)
var arc_vec := Vector2.ZERO
var flight_ms := 1.0
var break_start := 0.5
var break_pow := 2.0
var decel := 0.0
var pop := 0.0
var pop_peak := 0.3


## 구종 수치 → 궤적. hand 는 "R" | "L"
## location_range = (가장 작은 배율, 가장 큰 배율) — pitch_common 의 location_min·location_max
static func make(spec: PitchCatalog.Spec, p_aim: Vector2, p_flight_ms: float, hand: String, location_range := Vector2.ONE, p_start := Vector2.ZERO) -> PitchTrajectory:
	var t := PitchTrajectory.new()
	t.start = p_start
	t.aim = p_aim
	var full := zone_break(Vector2(spec.break_x, spec.break_y), hand)
	t.break_vec = full * location_factor(full, p_aim, spec.location_effect, location_range)
	t.arc_vec = zone_break(Vector2(spec.arc_x, spec.arc_y), hand)
	t.flight_ms = p_flight_ms
	t.break_start = spec.break_start
	t.break_pow = spec.break_pow
	t.decel = spec.decel
	t.pop = spec.pop
	t.pop_peak = spec.pop_peak
	return t


## 투구 위치에 따른 변화폭 배율: 휘는 방향(존 좌표)으로 던진 만큼 커지고 반대로 던진 만큼 작아진다
static func location_factor(zone_break_vec: Vector2, p_aim: Vector2, effect: float, location_range: Vector2) -> float:
	if zone_break_vec == Vector2.ZERO:
		return 1.0
	return clampf(1.0 + effect * p_aim.dot(zone_break_vec.normalized()), location_range.x, location_range.y)


## 투수 기준 변화량(x + = 투수 팔 쪽, y + = 위) → 존 좌표.
## 타자는 지금 우타자 하나라 우투수의 팔 쪽 = 몸쪽 = 존 좌표 -x. 좌투수는 x 부호가 뒤집힌다. 존 좌표는 y 아래가 + 라 y 도 뒤집는다
static func zone_break(pitcher_break: Vector2, hand: String) -> Vector2:
	var side := 1.0 if hand == "R" else -1.0
	return Vector2(-pitcher_break.x * side, -pitcher_break.y)


## 시간 진행률 u (0~1)
func time_fraction(t_ms: float) -> float:
	return clampf(t_ms / flight_ms, 0.0, 1.0)


## 거리 진행률 u_w (0~1). 감속 때문에 시간보다 앞서 간다. 화면의 원근(공 크기)도 이 값으로 정한다
func distance_fraction(t_ms: float) -> float:
	var u := time_fraction(t_ms)
	return u + decel * u * (1.0 - u)


## 휘는 정도 s (0~1)
func break_fraction(t_ms: float) -> float:
	var k := clampf((distance_fraction(t_ms) - break_start) / (1.0 - break_start), 0.0, 1.0)
	return pow(k, break_pow)


## 안 휘었다면 있었을 자리 (start → raw_end 직선 위)
func straight_at(t_ms: float) -> Vector2:
	return start.lerp(aim - break_vec, distance_fraction(t_ms))


## 꺾이기 전 길 = 직선 + 도중 불룩함 (break_start 전에는 공이 이 위를 난다)
func base_at(t_ms: float) -> Vector2:
	return straight_at(t_ms) + arc_vec * sin(PI * distance_fraction(t_ms))


## 출발점 → 도착점 직선 위 자리 (거리 진행률 기준)
func line_at(t_ms: float) -> Vector2:
	return start.lerp(aim, distance_fraction(t_ms))


## 휨: 지금 자리가 출발→도착 직선에서 벗어난 만큼. 양 끝에서 0 (화면이 이 값만 과장해 그린다)
func deviation_at(t_ms: float) -> Vector2:
	return position_at(t_ms) - line_at(t_ms)


## 공이 지금 존 평면 어디를 향하는가 (화면용): 꺾이기 전엔 raw_end, 꺾이면서 aim 으로 미끄러진다 (+ 도중 불룩함)
## t=0 → raw_end, t=flight → aim. 포수 미트(예상 투구 위치)도 이만큼 함께 움직인다
func pointing_at(t_ms: float) -> Vector2:
	return aim - break_vec * (1.0 - break_fraction(t_ms)) + arc_vec * sin(PI * distance_fraction(t_ms))


## 튀어 오른 높이 (존 반폭, 위가 +). 0 → pop_peak 까지 사인으로 올라 1, 그 뒤 코사인으로 내려 도착 때 0
func pop_at(t_ms: float) -> float:
	if pop == 0.0:
		return 0.0
	var d := distance_fraction(t_ms)
	var hump := sin(PI * 0.5 * d / pop_peak) if d <= pop_peak else cos(PI * 0.5 * (d - pop_peak) / (1.0 - pop_peak))
	return pop * maxf(hump, 0.0)


func position_at(t_ms: float) -> Vector2:
	return base_at(t_ms) + break_vec * break_fraction(t_ms)
