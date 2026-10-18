class_name Steal
extends RefCounted
## 도루: 투수 동작에 맞춰 스타트를 끊는 타이밍 승부.
##
## 1. plan(): 투수가 세트 자세로 얼마나 기다리다 던지는지, 그 전에 견제 동작을 몇 번 섞는지 시드 난수로 정한다
## 2. 화면이 그 시각표대로 투수를 움직이고, 유저가 탭한 시각을 judge() 에 넘긴다
## 3. judge(): 투구 전에 뛰면 견제사, 투구 동작 뒤 lateMs 안에 뛰면 도루 시도 —
##    "반응 + 달리는 시간" 과 "투구 + 포수 송구 + 태그" 를 비교한다 (다른 주루와 같은 시간 비교 방식)
## 노드가 아닌 로직이고 난수는 넘겨받은 것만 쓴다 (원칙 1·2).

enum Kind {
	NONE,        ## 안 뛰었다 (탭하지 않았거나 너무 늦었다)
	SAFE,        ## 도루 성공
	CAUGHT,      ## 도루 실패 (송구가 먼저)
	PICKED_OFF,  ## 투구 전에 뛰다 견제에 걸렸다
}


## 투수의 시각표 (ms, 화면에 뜬 순간부터)
class Plan:
	## 견제 동작이 시작되는 시각들
	var feints: Array[float] = []
	## 투구 동작이 시작되는 시각 — 이 뒤에 탭해야 한다
	var go_ms := 0.0
	## 견제 동작 하나의 길이, 투구 동작 뒤 탭을 받는 시간
	var feint_ms := 0.0
	var late_ms := 0.0

	## 그 시각에 견제 동작 중인가
	func in_feint(t_ms: float) -> bool:
		for f in feints:
			if t_ms >= f and t_ms < f + feint_ms:
				return true
		return false

	## 탭을 더 받지 않는 시각 (공이 포수에게 간 뒤)
	func end_ms() -> float:
		return go_ms + late_ms


class Result:
	var kind := Kind.NONE
	## 뛰기 전 베이스와 노린 베이스
	var from := 1
	var to := 2
	## 투구 동작 시작부터 탭까지 (ms). 안 뛰었으면 0
	var reaction_ms := 0.0
	## 스타트부터 베이스까지 달린 시간, 투구 동작 시작부터 태그까지 걸린 수비 시간 (초)
	var run_s := 0.0
	var defense_s := 0.0
	## 수비 시간 중 투구가 포수에게 닿기까지 (초, 화면용)
	var delivery_s := 0.0
	## 달린 거리 (m, 화면용)
	var distance_m := 0.0

	## 주자가 베이스에 닿는 시각 (투구 동작 시작부터, 초)
	func runner_s() -> float:
		return reaction_ms / 1000.0 + run_s

	## 여유 (초, +면 주자가 먼저)
	func margin_s() -> float:
		return defense_s - runner_s()

	func is_out() -> bool:
		return kind == Kind.CAUGHT or kind == Kind.PICKED_OFF

	func attempted() -> bool:
		return kind != Kind.NONE


static func plan(cfg: Dictionary, rng: RandomNumberGenerator) -> Plan:
	var p := Plan.new()
	p.feint_ms = float(cfg["feintMs"])
	p.late_ms = float(cfg["lateMs"])
	var t := rng.randf_range(float(cfg["setMinMs"]), float(cfg["setMaxMs"]))
	for i in int(cfg["maxFeints"]):
		if rng.randf() >= float(cfg["feintChance"]):
			break
		p.feints.append(t)
		t += p.feint_ms + rng.randf_range(float(cfg["setMinMs"]), float(cfg["setMaxMs"]))
	p.go_ms = t
	return p


## tap_ms: 화면에 뜬 순간부터 탭까지 (입력 지연 보정을 뺀 값). 탭하지 않았으면 음수.
## from: 지금 있는 베이스 (1 또는 2), speed: 주력 (0~100)
static func judge(cfg: Dictionary, phys: BattedBallSim.Config, p: Plan, tap_ms: float, from: int, speed: int,
		rng: RandomNumberGenerator) -> Result:
	var r := Result.new()
	r.from = from
	r.to = from + 1
	if tap_ms < 0.0 or tap_ms > p.end_ms():
		return r
	if tap_ms < p.go_ms:
		r.kind = Kind.PICKED_OFF
		return r
	var run: Dictionary = phys.running
	r.reaction_ms = tap_ms - p.go_ms
	var lead := float(run["leadOffM"]) + (float(cfg["extraLeadSecondM"]) if from == 2 else 0.0)
	r.distance_m = phys.base_distance - lead
	# 달리는 시간은 다른 주루와 같은 가속·최고 속도 (RunnerAI)
	r.run_s = RunnerAI.new(0, from, speed, phys).eta_from_rest(r.distance_m)
	r.delivery_s = float(cfg["deliveryS"])
	var pop := float(cfg["popSecondS"] if from == 1 else cfg["popThirdS"])
	r.defense_s = r.delivery_s + pop + float(run["tagS"]) + rng.randfn(0.0, float(cfg["defenseSdS"]))
	r.kind = Kind.SAFE if r.runner_s() < r.defense_s else Kind.CAUGHT
	return r
