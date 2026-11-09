class_name StreetWalk
extends RefCounted
## 로비 골목에서 내 선수가 장소까지 걸어가 문으로 들어가는 한 번의 걸음 (로직 — 경과 시간만으로 위치를 낸다).
## 걷기(walk_sec) → 문 앞에서 사라지며 들어가기(door_sec) → 끝. 수치는 balance.json career.street.
## 멀리 있어도 maxWalkSec 안에 닿도록 속도를 올린다 (골목 끝 야구장까지 매번 오래 걷지 않게).

var from_x := 0.0
var to_x := 0.0
var walk_sec := 0.0
var door_sec := 0.0
var step_sec := 0.1


static func plan(p_from: float, p_to: float, cfg: Dictionary) -> StreetWalk:
	var w := StreetWalk.new()
	w.from_x = p_from
	w.to_x = p_to
	var dist := absf(p_to - p_from)
	if dist > 0.0:
		w.walk_sec = clampf(dist / float(cfg["walkSpeed"]), float(cfg["minWalkSec"]), float(cfg["maxWalkSec"]))
	w.door_sec = float(cfg["doorSec"])
	w.step_sec = maxf(0.01, float(cfg["stepSec"]))
	return w


func duration() -> float:
	return walk_sec + door_sec


## t 초 뒤 선수의 x (걷는 동안 일정한 속도, 그 뒤엔 문 자리)
func x_at(t: float) -> float:
	if walk_sec <= 0.0 or t >= walk_sec:
		return to_x
	return lerpf(from_x, to_x, maxf(t, 0.0) / walk_sec)


## 바라보는 방향: 1 = 오른쪽, -1 = 왼쪽 (제자리면 오른쪽)
func facing() -> int:
	return -1 if to_x < from_x else 1


func walking(t: float) -> bool:
	return t < walk_sec


## 문으로 들어가는 정도 0 → 1 (걷는 동안은 0)
func door_progress(t: float) -> float:
	if door_sec <= 0.0:
		return 1.0 if t >= walk_sec else 0.0
	return clampf((t - walk_sec) / door_sec, 0.0, 1.0)


func done(t: float) -> bool:
	return t >= duration()


## 걷는 그림 몇 번째 장인가 (frames 장을 step_sec 마다 돌린다)
func step_frame(t: float, frames: int) -> int:
	return int(maxf(t, 0.0) / step_sec) % maxi(1, frames)
