class_name ReactTest
extends RefCounted
## 체력 테스트 ③ 반응 속도 (로직만 — 화면은 FitnessView). 수치는 balance.json career.fitness.react.
##
## 신호등이 꺼진 채 잠깐(시드) 기다렸다가 초록 또는 빨강으로 켜진다.
##   초록: 최대한 빨리 눌러 스윙 → 반응 시간(ms) 기록. windowSec 안에 안 누르면 놓침(missMs)
##   빨강: 참는다. windowSec 동안 안 누르면 참기 성공, 누르면 실수
##   불 켜지기 전에 누르면 실수 하나 세고 같은 신호를 다시 기다린다
## 기록 = 초록 반응 시간 평균 + 실수 × mistakeMs. 선구안처럼 "빨리 치되 아무거나 치지 않기".

## BETWEEN: 신호 사이 (결과를 보여 주는 중 — 누르면 무시). 화면이 start_wait 를 불러야 다음 신호를 기다린다
enum Phase { BETWEEN, WAIT, LIT, DONE }

var cfg: Dictionary
var phase := Phase.BETWEEN
## 신호 순서 (true = 빨강)와 기다리는 시간(초) — 시드로 정한다
var reds: Array[bool] = []
var delays: Array[float] = []
var index := 0
var reaction_ms: Array[int] = []
var mistakes := 0
var _lit_at_us := 0


func _init(p_cfg: Dictionary, rng: RandomNumberGenerator) -> void:
	cfg = p_cfg
	var n := int(cfg["trials"])
	for i in n:
		reds.append(i < int(cfg["reds"]))
		delays.append(rng.randf_range(float(cfg["delayMinSec"]), float(cfg["delayMaxSec"])))
	# 빨강 자리를 섞는다 (Fisher–Yates, 첫 신호는 초록으로 — 처음부터 참으라고 하면 규칙을 익힐 틈이 없다)
	for i in range(n - 1, 1, -1):
		var j := rng.randi_range(1, i)
		var tmp := reds[i]
		reds[i] = reds[j]
		reds[j] = tmp
	if reds[0]:
		var k := reds.find(false)
		reds[0] = false
		reds[k] = true


## 이번 신호를 기다리기 시작한다 (화면이 "준비" 뒤에 부른다)
func start_wait(t_us: int) -> void:
	if phase == Phase.DONE:
		return
	phase = Phase.WAIT
	_lit_at_us = t_us + int(delays[index] * 1e6)


func is_red() -> bool:
	return index < reds.size() and reds[index]


## 매 프레임. 돌려주는 값: "lit"(방금 켜짐) | "miss"(초록을 놓침) | "held"(빨강을 참음) | ""
func update(t_us: int) -> String:
	if phase == Phase.WAIT and t_us >= _lit_at_us:
		phase = Phase.LIT
		return "lit"
	if phase == Phase.LIT and t_us - _lit_at_us >= int(float(cfg["windowSec"]) * 1e6):
		if is_red():
			_next()
			return "held"
		reaction_ms.append(int(cfg["missMs"]))
		_next()
		return "miss"
	return ""


## 눌렀다. 돌려주는 값: "hit"(초록 스윙) | "fooled"(빨강에 스윙) | "early"(켜지기 전) | ""
func tap(t_us: int) -> String:
	match phase:
		Phase.WAIT:
			mistakes += 1
			start_wait(t_us)  # 같은 신호를 처음부터 다시 기다린다
			return "early"
		Phase.LIT:
			if is_red():
				mistakes += 1
				_next()
				return "fooled"
			reaction_ms.append(int(round(float(t_us - _lit_at_us) / 1000.0)))
			_next()
			return "hit"
	return ""


## 마지막 초록 반응 시간 (ms, 없으면 −1)
func last_ms() -> int:
	return reaction_ms[-1] if not reaction_ms.is_empty() else -1


func _next() -> void:
	index += 1
	phase = Phase.DONE if index >= reds.size() else Phase.BETWEEN


## 기록 (ms): 초록 평균 + 실수 벌점
func score_ms() -> int:
	var total := 0
	for ms in reaction_ms:
		total += ms
	var avg := float(total) / maxf(1.0, float(reaction_ms.size())) if not reaction_ms.is_empty() else float(cfg["missMs"])
	return int(round(avg)) + mistakes * int(cfg["mistakeMs"])


func grade() -> String:
	return FitnessTest.grade_of(float(score_ms()), cfg["grades"], true)
