class_name LiftTest
extends RefCounted
## 체력 테스트 ② 스쿼트 (로직만 — 화면은 FitnessView). 수치는 balance.json career.fitness.lift.
##
## 한 번 들기: 누르고 있는 동안 숨을 들이마시며 내려간다(호흡 게이지 0 → 1). 떼는 순간 내쉬며 들어올린다.
##   뗄 때 게이지가 목표 구간 안이면 성공(가운데 근처면 완벽), 일찍 떼면 실패("숨이 덜 찼다"),
##   1 까지 참으면 숨이 차서 실패("숨이 턱"). 단계(무게)가 오를 때마다 게이지가 fillStepMul 배 시간으로 빨리 차고 구간이 좁아진다.
## 성공하면 stepKg 더 얹고, maxFails 번 실패하거나 maxKg 를 들면 끝. 기록 = 성공한 가장 무거운 무게.

enum Phase { IDLE, HOLD, DONE }

var cfg: Dictionary
var phase := Phase.IDLE
## 이번에 드는 무게, 성공한 가장 무거운 무게(없으면 0), 실패 수, 완벽 수
var kg: int
var best_kg := 0
var fails := 0
var perfects := 0
var _power: int
var _hold_us := 0


func _init(p_cfg: Dictionary, power_stat: int) -> void:
	cfg = p_cfg
	kg = int(cfg["startKg"])
	_power = power_stat


## 지금 단계 (startKg 이 0, stepKg 마다 1)
func stage() -> int:
	return (kg - int(cfg["startKg"])) / int(cfg["stepKg"])


## 게이지가 다 차는 데 걸리는 시간(초): 단계가 오를 때마다 fillStepMul 배로 짧아진다
func fill_sec() -> float:
	return maxf(0.3, float(cfg["fillSecBase"]) * pow(float(cfg["fillStepMul"]), stage()))


## 목표 구간 (게이지 값 lo, hi)
func zone() -> Vector2:
	var w := float(cfg["zoneWidthBase"]) + float(kg - int(cfg["startKg"])) * float(cfg["zoneWidthPerKg"]) + float(_power) * float(cfg["zoneWidthPerStat"])
	w = maxf(0.06, w)
	var c := float(cfg["zoneCenter"])
	return Vector2(c - w / 2.0, minf(c + w / 2.0, 0.98))


## 지금 호흡 게이지 (0~1)
func gauge(t_us: int) -> float:
	if phase != Phase.HOLD:
		return 0.0
	return clampf(float(t_us - _hold_us) / 1e6 / fill_sec(), 0.0, 1.0)


## 누르기 시작 (숨을 들이마신다)
func press(t_us: int) -> void:
	if phase == Phase.IDLE:
		phase = Phase.HOLD
		_hold_us = t_us


## 뗐다 (내쉬며 든다). 돌려주는 값: "perfect" | "good" | "early" | "" (누르고 있지 않았다)
func release(t_us: int) -> String:
	if phase != Phase.HOLD:
		return ""
	var g := gauge(t_us)
	var z := zone()
	var result := "early"
	if g >= z.x and g <= z.y:
		result = "perfect" if absf(g - float(cfg["zoneCenter"])) <= float(cfg["perfectWidth"]) / 2.0 else "good"
	_settle(result)
	return result


## 누르고 있는 동안 매 프레임: 게이지가 다 차면 숨이 차서 실패. 돌려주는 값: "late" 또는 ""
func update(t_us: int) -> String:
	if phase == Phase.HOLD and gauge(t_us) >= 1.0:
		_settle("late")
		return "late"
	return ""


func _settle(result: String) -> void:
	phase = Phase.IDLE
	if result in ["perfect", "good"]:
		best_kg = kg
		if result == "perfect":
			perfects += 1
		if kg + int(cfg["stepKg"]) > int(cfg["maxKg"]):
			phase = Phase.DONE
		else:
			kg += int(cfg["stepKg"])
	else:
		fails += 1
		if fails >= int(cfg["maxFails"]):
			phase = Phase.DONE


func grade() -> String:
	return FitnessTest.grade_of(float(best_kg), cfg["grades"], false)
