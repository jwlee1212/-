class_name Pitch
extends RefCounted
## 공 하나.
##
## 좌표는 스트라이크존 기준: 가운데가 (0, 0), 존 가장자리가 ±1, y 는 아래가 +, x 는 +가 바깥쪽(타자에게서 먼 쪽).
## 공은 휘기 전 직선으로 오다가 break_start 이후 휘어서 마지막에 target 에 도착한다.
## 그래서 막판에 휘는 공은 일찍 판단하기 어렵다.

## 구종 id (balance.json pitches 의 키: "fastball", "slider", "changeup" …)
var type: String
var label: String
var flight_ms: float
## 실제로 도착하는 곳
var target := Vector2.ZERO
## 포수가 미트를 댄 곳 = 투수가 노린 곳. 제구가 흔들리면 target 과 달라진다
var intended := Vector2.ZERO
var break_vec := Vector2.ZERO
var break_start: float
## 비행 진행률이 이 값을 넘으면 구종이 화면에 드러난다 (선구안)
var reveal_fraction: float
## 볼배합 의도 (화면 설명·테스트용)
var is_chase := false
var is_putaway := false
var is_mistake := false


func is_strike() -> bool:
	return absf(target.x) <= 1.0 and absf(target.y) <= 1.0


## 진행률 progress(0=투수 손, 1=홈플레이트)에서 공의 존 좌표
func zone_position_at(progress: float) -> Vector2:
	var p := clampf(progress, 0.0, 1.0)
	var bend := 0.0
	if p > break_start:
		var k := (p - break_start) / (1.0 - break_start)
		bend = k * k
	return (target - break_vec) * p + break_vec * bend


func is_revealed_at(progress: float) -> bool:
	return progress >= reveal_fraction


## 존 3×3 칸 번호 (0~8, 왼쪽 위부터). 존 밖이면 -1 — 노려치기 판정에 쓴다
static func cell_of(pos: Vector2) -> int:
	if absf(pos.x) > 1.0 or absf(pos.y) > 1.0:
		return -1
	var col := clampi(int(floor((pos.x + 1.0) / 2.0 * 3.0)), 0, 2)
	var row := clampi(int(floor((pos.y + 1.0) / 2.0 * 3.0)), 0, 2)
	return row * 3 + col
