class_name FieldPlay
extends RefCounted
## 내 수비 한 번 (유격수 고정, 로직만 — 화면은 FieldView). 수치는 balance.json game.fielding.
##
## ① 포구: introSec 뒤 타구가 맞고, 화면 아무 데에 점과 큰 원이 뜬다. 큰 원은 travelSec 동안 줄어 0 이 되고,
##    그 순간 굴러온 공이 점에 닿는다. 원이 점 안에 들어온 뒤(반지름 ≤ dotRadius) 누르면 포구.
##    원이 점보다 클 때 누르면 더듬기, 원이 사라진 뒤 missGraceSec 까지 안 누르면 다리 사이로 빠진다 — 둘 다 실책(MISS).
## ② 송구 (2026-11-01 고침: 눌렀다 떼기): 잡고 transferSec 뒤 송구 게이지가 뜬다. 꾹 누르고 있으면 바늘이 gaugeRiseSec 동안
##    0 → 1 로 차오르고, 뗀 순간의 칸으로 던진다. 끝(1)까지 차면 저절로 던져진다(너무 셈).
##    약함(회색) · 좋음(초록) · 완벽(금색 = 레이저 송구) · 너무 셈(빨강) = 악송구.
##    아웃 확률 = outChance[칸] + (50 − 주자 주력) / 50 × outChanceSpeedSwing[칸] (시드 난수 하나로 정한다): 회색은 거의 세이프,
##    초록은 보통 주자에게 약 80%, 금색은 약 95%. 그다음 판정 여유(아웃이면 marginOut, 세이프면 marginSafe 사이 초)를 뽑아
##    공이 1루에 닿는 시각을 정하고, 공 속도는 그 시각에 맞춘다 — 화면의 공·주자가 판정과 똑같이 움직인다.
##    던진 게 너무 늦어 가장 빠른 공(stat.throwMpsMax)으로도 주자보다 먼저 못 닿으면 아웃이 나와도 세이프다.
##    아웃이면 금색은 BONUS(주자도 묶는다), 초록·회색은 NORMAL. 세이프는 LATE(내야안타). 주자가 1루를 밟을 때까지 못 던져도 LATE.
##
## 내 수비 능력치(fielding, 경기용)가 바꾸는 것 (cfg.stat — 모두 At0~At100 직선): 포구 원이 줄어드는 시간(travel_sec — 길수록 판정 폭이
## 넓다), 공 빼는 시간(transfer_sec), 금색 칸 폭, 가장 빠른 공(늦게 던져도 따라잡는 한계), 아웃일 때 공이 주자보다 얼마나 먼저 꽂히나,
## 아웃 확률 보정. 그래서 같은 게이지로 던져도 수비가 좋으면 공이 더 빠르고 더 자주 잡는다.
##
## 좌표는 화면 높이를 1 로 잰다: x 는 0 ~ aspect(화면 너비/높이), y 는 0(위) ~ 1(아래). 반지름도 같은 단위.
## 시간은 화면이 넘겨주는 Time.get_ticks_usec() 하나로 잰다 (원칙 4). 점 자리는 시드로 정한다 (원칙 2).

enum Phase { INTRO, CATCH, THROW, DONE }
enum Kind { NONE, BONUS, NORMAL, WILD, MISS, LATE }

var cfg: Dictionary
var aspect: float
var phase := Phase.INTRO
var kind := Kind.NONE
## 점(공이 닿는 자리)
var dot: Vector2
## 타자 주자가 1루를 밟는 시각 (맞은 뒤 초), 송구 거리 (m — 화면이 점 자리로 정해 준다)
var runner_s: float
## 타자 주자 주력 (0~100, 아웃 확률에 쓴다 — 화면이 넣어 준다)
var runner_speed := 50
var throw_m := 36.0
## 내 수비 능력치 (0~100, 경기용) 와 그로 정한 시간들 (초)
var fielding := 50
var travel_sec := 1.15
var transfer_sec := 0.5
## 던진 순간의 게이지 값(0~1)·칸("weak" | "good" | "perfect" | "wild")·공 속도(m/s)·공이 1루에 닿는 시각(맞은 뒤 초)
var gauge_value := 0.0
var zone := ""
var throw_mps := 0.0
var arrive_s := 0.0
## 각 단계가 시작된 시각 (µs). arrive_us = 원이 0 이 되고 공이 점에 닿는 순간, aim_us = 게이지가 뜬 순간,
## charge_us = 게이지를 누르기 시작한 순간 (0 = 아직 안 누름)
var start_us := 0
var hit_us := 0
var arrive_us := 0
var caught_us := 0
var aim_us := 0
var thrown_us := 0
var charge_us := 0
## 실책 종류 ("early" 더듬기 | "through" 빠뜨림) — 화면 문구용
var miss_reason := ""
## 이번 송구의 아웃 확률 (던진 뒤)
var out_chance := 0.0
var _rng: RandomNumberGenerator


## runner_first_s: 타자 주자가 1루를 밟는 시각 (맞은 뒤 초, runner_first_s() 로 구한다)
## p_fielding: 내 수비 능력치 (경기용)
func _init(p_cfg: Dictionary, rng: RandomNumberGenerator, p_aspect: float, t_us: int, runner_first_s: float, p_fielding: int = 50) -> void:
	cfg = p_cfg
	_rng = rng
	runner_s = runner_first_s
	aspect = p_aspect
	fielding = p_fielding
	travel_sec = by_stat("travelSec")
	transfer_sec = by_stat("transferSec")
	start_us = t_us
	hit_us = t_us + _us(cfg["introSec"])
	arrive_us = hit_us + _us(travel_sec)
	var area: Dictionary = cfg["circleArea"]
	dot = Vector2(rng.randf_range(float(area["xMin"]), float(area["xMax"])) * aspect, rng.randf_range(float(area["yMin"]), float(area["yMax"])))


static func _us(sec: Variant) -> int:
	return int(float(sec) * 1e6)


## 수비 능력치로 정해지는 값: cfg.stat 의 <이름>At0 ~ <이름>At100 사이 직선
func by_stat(name: String) -> float:
	var st: Dictionary = cfg["stat"]
	return lerpf(float(st[name + "At0"]), float(st[name + "At100"]), clampi(fielding, 0, 100) / 100.0)


## 금색 칸 (게이지 값 아래·위 끝)
func perfect_min() -> float:
	return float(cfg["gaugePerfectCenter"]) - by_stat("perfectWidth") * 0.5


func perfect_max() -> float:
	return float(cfg["gaugePerfectCenter"]) + by_stat("perfectWidth") * 0.5


## 지금 큰 원의 반지름 (INTRO 면 ringStart, 포구 단계가 끝나면 0)
func ring_radius(t_us: int) -> float:
	var start := float(cfg["ringStart"])
	if t_us <= hit_us:
		return start
	return maxf(0.0, start * (1.0 - float(t_us - hit_us) / float(arrive_us - hit_us)))


## 포구 판정 폭 (초): 원이 점 안에 있는 시간
func catch_window_sec() -> float:
	return travel_sec * float(cfg["dotRadius"]) / float(cfg["ringStart"])


## 타구가 굴러온 정도 (0 = 홈플레이트, 1 = 점에 닿음, 그 뒤로도 늘어난다)
func ball_progress(t_us: int) -> float:
	return float(t_us - hit_us) / float(arrive_us - hit_us)


## 타자 주자가 1루를 밟는 시각 (맞은 뒤 초): 출발(batterStartS) + 27.43m / 주력에 따른 속도 — 자동 경기의 내야 땅볼과 같은 식
static func runner_first_s(phys: BattedBallSim.Config, speed_rating: int) -> float:
	var mps := lerpf(phys.batter_run_at0, phys.batter_run_at100, clampi(speed_rating, 0, 100) / 100.0)
	return phys.batter_start_s + phys.base_distance / mps


## 맞은 뒤 흐른 시간 (초)
func since_hit(t_us: int) -> float:
	return float(t_us - hit_us) / 1e6


## 송구 게이지 바늘 (0~1): 누르고 있는 동안 gaugeRiseSec 에 걸쳐 0 → 1 로 찬다 (안 눌렀으면 0)
func gauge_at(t_us: int) -> float:
	if charge_us == 0:
		return 0.0
	return clampf(float(t_us - charge_us) / 1e6 / float(cfg["gaugeRiseSec"]), 0.0, 1.0)


## 게이지를 누르고 있나
func charging() -> bool:
	return phase == Phase.THROW and charge_us > 0


## 게이지 값의 칸
func zone_of(g: float) -> String:
	if g >= float(cfg["gaugeWildMin"]):
		return "wild"
	if g >= perfect_min() and g <= perfect_max():
		return "perfect"
	if g >= float(cfg["gaugeWeakMax"]):
		return "good"
	return "weak"


## 그 칸·주자 주력의 아웃 확률 (내 수비 능력치 보정 포함)
func out_chance_of(z: String, speed: int) -> float:
	if z == "wild":
		return 0.0
	var swing := float(cfg["outChanceSpeedSwing"][z]) * (50.0 - float(speed)) / 50.0
	var arm := float(cfg["stat"]["outChanceSwing"][z]) * (float(fielding) - 50.0) / 50.0
	return clampf(float(cfg["outChance"][z]) + swing + arm, 0.0, 1.0)


## 1루 판정 여유 (초): 주자가 1루를 밟는 시각 − 공이 닿는 시각 (+ 면 아웃). 던진 뒤에만 뜻이 있다
func margin() -> float:
	return runner_s - arrive_s


## 아웃인가 (던져서 잡았다)
func is_out() -> bool:
	return kind == Kind.BONUS or kind == Kind.NORMAL


## 매 프레임. 돌려주는 값: "hit"(방금 맞음) | "through"(공이 빠졌다) | "throw"(끝까지 눌러 저절로 던짐)
##   | "late"(못 던진 사이 주자가 1루에) | ""
func update(t_us: int) -> String:
	match phase:
		Phase.INTRO:
			if t_us >= hit_us:
				phase = Phase.CATCH
				return "hit"
		Phase.CATCH:
			if t_us > arrive_us + _us(cfg["missGraceSec"]):
				_miss("through", t_us)
				return "through"
		Phase.THROW:
			if charge_us > 0 and gauge_at(t_us) >= 1.0:
				# 끝까지 눌렀다 — 저절로 던져진다 (너무 셈)
				_throw(charge_us + _us(cfg["gaugeRiseSec"]))
				return "throw"
			if since_hit(t_us) >= runner_s:
				# 못 던진 사이에 주자가 1루를 밟았다
				kind = Kind.LATE
				phase = Phase.DONE
				return "late"
	return ""


## 게이지가 떠 있나 (잡은 뒤 transferSec 이 지났다)
func aiming(t_us: int) -> bool:
	return phase == Phase.THROW and t_us >= aim_us


## 눌렀다. 돌려주는 값: "catch" | "early"(더듬기) | "charge"(게이지를 모으기 시작) | ""
## 공을 빼는 동안(transferSec) 누르면 게이지가 뜨는 순간부터 찬다
func press(t_us: int) -> String:
	match phase:
		Phase.CATCH:
			if ring_radius(t_us) > float(cfg["dotRadius"]):
				_miss("early", t_us)
				return "early"
			phase = Phase.THROW
			caught_us = t_us
			aim_us = t_us + _us(transfer_sec)
			return "catch"
		Phase.THROW:
			if charge_us == 0:
				charge_us = maxi(t_us, aim_us)
				return "charge"
	return ""


## 뗐다. 게이지를 모으는 중이었으면 그 칸으로 던진다. 돌려주는 값: "throw" | ""
func release(t_us: int) -> String:
	if phase != Phase.THROW or charge_us == 0 or t_us < charge_us:
		if phase == Phase.THROW and charge_us > 0 and t_us < charge_us:
			charge_us = 0  # 게이지가 뜨기도 전에 뗐다 — 다시 눌러야 한다
		return ""
	_throw(t_us)
	return "throw"


## 그 시각에 던진다: 칸 → 아웃 확률 → (시드 난수) 아웃·세이프 → 판정 여유 → 공 도착 시각·속도
func _throw(t_us: int) -> void:
	thrown_us = t_us
	gauge_value = gauge_at(t_us)
	zone = zone_of(gauge_value)
	phase = Phase.DONE
	var thrown_s := since_hit(t_us)
	var fastest := thrown_s + throw_m / by_stat("throwMpsMax")
	if zone == "wild":
		kind = Kind.WILD
		throw_mps = float(cfg["throwMpsWild"])
		arrive_s = thrown_s + throw_m / throw_mps
		return
	out_chance = out_chance_of(zone, runner_speed)
	var out := _rng.randf() < out_chance
	var m: Array = cfg["marginOut"] if out else cfg["marginSafe"]
	var margin_s := _rng.randf_range(float(m[0]), float(m[1]))
	if out:
		margin_s *= by_stat("marginOutMul")  # 어깨가 좋을수록 공이 더 일찍 꽂힌다
	arrive_s = runner_s - margin_s if out else runner_s + margin_s
	if arrive_s < fastest:
		# 공이 아무리 빨라도 그보다 일찍은 못 닿는다 — 늦게 던졌으면 아웃이 나와도 주자가 먼저일 수 있다
		arrive_s = fastest
		out = arrive_s < runner_s
	throw_mps = throw_m / maxf(arrive_s - thrown_s, 0.05)
	if out:
		kind = Kind.BONUS if zone == "perfect" else Kind.NORMAL
	else:
		kind = Kind.LATE


func _miss(reason: String, t_us: int) -> void:
	miss_reason = reason
	kind = Kind.MISS
	phase = Phase.DONE
	caught_us = t_us


static func kind_label(k: Kind) -> String:
	match k:
		Kind.BONUS: return "아웃! 레이저 송구"
		Kind.NORMAL: return "아웃!"
		Kind.WILD: return "악송구…"
		Kind.MISS: return "포구 실책…"
		Kind.LATE: return "세이프! 내야안타"
	return ""
