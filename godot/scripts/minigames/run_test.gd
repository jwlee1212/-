class_name RunTest
extends RefCounted
## 체력 테스트 ① 50m 달리기 (로직만 — 화면은 FitnessView). 수치는 balance.json career.fitness.run.
##
## 왼발·오른발 버튼을 번갈아 누를 때마다 속도가 tapImpulse 만큼 붙고(최고 속도까지), 속도는 1초에 drag 비율로 줄어든다.
## 꾸준히 1초에 r 번 누르면 속도 ≈ tapImpulse × r / drag 에서 맴돈다 — 빨리 누를수록 빠르지만 손가락이 지치면 바로 느려진다.
## 같은 발을 두 번 누르면 헛발: 속도가 sameFootKeep 배로 줄고 stumbleSec 동안 입력이 안 먹는다.
## 위치는 눌린 순간 사이를 식으로 정확히 적분한다(v(t) = v0·e^(−drag·t)) — 프레임이 떨어져도 기록이 같다 (원칙 4).
## 땅! 전에 누르면 부정 출발: false_starts 를 세고 화면이 다시 준비시킨다 (벌점은 없다).

enum Phase { READY, RUNNING, DONE }

var cfg: Dictionary
var phase := Phase.READY
var distance: float
## 지금 위치(m)·속도(m/s) — advance 한 시각 기준
var x := 0.0
var v := 0.0
var max_speed: float
var false_starts := 0
## 결승선을 지난 기록(초), 못 끝냈으면 −1
var finish_sec := -1.0
var _go_us := 0
var _at_us := 0
var _last_foot := -1
var _stumble_until_us := 0


func _init(p_cfg: Dictionary, speed_stat: int) -> void:
	cfg = p_cfg
	distance = float(cfg["distanceM"])
	max_speed = float(cfg["maxSpeedBase"]) + float(speed_stat) * float(cfg["maxSpeedPerStat"])


## 땅! 이 울리기까지 기다릴 시간 (차렷 뒤, 시드로 정한다)
func ready_delay(rng: RandomNumberGenerator) -> float:
	return rng.randf_range(float(cfg["readyMinSec"]), float(cfg["readyMaxSec"]))


## 땅!
func go(t_us: int) -> void:
	phase = Phase.RUNNING
	_go_us = t_us
	_at_us = t_us
	x = 0.0
	v = 0.0
	_last_foot = -1
	_stumble_until_us = 0


## 발 버튼 (foot 0 왼발 · 1 오른발). 돌려주는 값: "step" | "stumble"(같은 발) | "early"(부정 출발) | "" (무시)
func tap(foot: int, t_us: int) -> String:
	if phase == Phase.READY:
		false_starts += 1
		return "early"
	if phase == Phase.DONE:
		return ""
	advance(t_us)
	if phase == Phase.DONE or t_us < _stumble_until_us:
		return ""
	if foot == _last_foot:
		v *= float(cfg["sameFootKeep"])
		_stumble_until_us = t_us + int(float(cfg["stumbleSec"]) * 1e6)
		_last_foot = -1  # 헛발 뒤에는 어느 발로든 다시 시작
		return "stumble"
	_last_foot = foot
	v = minf(v + float(cfg["tapImpulse"]), max_speed)
	return "step"


## t_us 까지 달린다 (결승선을 지나면 그 순간을 기록하고 끝)
func advance(t_us: int) -> void:
	if phase != Phase.RUNNING or t_us <= _at_us:
		return
	var give_up := float(cfg["giveUpSec"])
	if float(t_us - _go_us) / 1e6 >= give_up:
		advance(_go_us + int(give_up * 1e6) - 1)
		if phase == Phase.RUNNING:
			finish_sec = give_up
			phase = Phase.DONE
		return
	var k := float(cfg["drag"])
	var dt := float(t_us - _at_us) / 1e6
	var gain := v * (1.0 - exp(-k * dt)) / k
	if x + gain >= distance:
		# 남은 거리를 가는 데 걸린 시간: rem = v(1 − e^(−k·s))/k  →  s = −ln(1 − rem·k/v)/k
		var s := -log(1.0 - (distance - x) * k / v) / k
		finish_sec = float(_at_us - _go_us) / 1e6 + s
		x = distance
		v *= exp(-k * s)
		phase = Phase.DONE
	else:
		x += gain
		v *= exp(-k * dt)
	_at_us = t_us


func elapsed(t_us: int) -> float:
	if phase == Phase.DONE:
		return finish_sec
	if phase == Phase.READY:
		return 0.0
	return float(t_us - _go_us) / 1e6


func stumbling(t_us: int) -> bool:
	return phase == Phase.RUNNING and t_us < _stumble_until_us


## 옆 레인 동기의 위치(m): buddySec 에 들어오도록, 같은 감속 모양으로 출발해 일정한 빠르기로 달린다
func buddy_x(t_sec: float) -> float:
	var k := float(cfg["drag"])
	var total := float(cfg["buddySec"])
	var vb := distance / (total - (1.0 - exp(-k * total)) / k)
	return clampf(vb * (t_sec - (1.0 - exp(-k * t_sec)) / k), 0.0, distance)


func grade() -> String:
	return FitnessTest.grade_of(finish_sec, cfg["grades"], true)
