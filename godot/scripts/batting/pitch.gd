class_name Pitch
extends RefCounted
## 공 하나.
##
## 좌표는 스트라이크존 기준: 가운데가 (0, 0), 존 가장자리가 ±1, y 는 아래가 +, x 는 +가 바깥쪽(타자에게서 먼 쪽).
## 공의 움직임은 trajectory(PitchTrajectory)가 정한다: 구종마다 휘기 시작하는 때·꺾이는 모양이 달라
## 막판에 휘는 공은 일찍 판단하기 어렵다. 어떻게 휘든 flight_ms 에 정확히 target 에 도착한다.

## 구종 id (balance.json batting.pitches 의 키: "four_seam", "slider", "curve" …)
var type: String
var label: String
## 투수 손을 떠나 홈플레이트까지 걸리는 시간 (ms) = 타이밍 판정 기준 시각
var flight_ms: float
## 화면에 보여 줄 구속
var speed_kmh: int
## 던진 손 "R" | "L"
var hand := "R"
## 실제로 도착하는 곳
var target := Vector2.ZERO
## 포수가 미트를 댄 곳 = 투수가 노린 곳. 제구가 흔들리면 target 과 달라진다
var intended := Vector2.ZERO
var trajectory: PitchTrajectory
## 비행 진행률이 이 값을 넘으면 구종이 화면에 드러난다 (선구안)
var reveal_fraction: float
## 볼배합 의도 (화면 설명·테스트용)
var is_chase := false
var is_putaway := false
var is_mistake := false


func is_strike() -> bool:
	return absf(target.x) <= 1.0 and absf(target.y) <= 1.0


## 공을 놓고 t_ms 뒤 공의 존 좌표
func position_at(t_ms: float) -> Vector2:
	return trajectory.position_at(t_ms)


func is_revealed_at(progress: float) -> bool:
	return progress >= reveal_fraction


## 존 3×3 칸 번호 (0~8, 왼쪽 위부터). 존 밖이면 -1 — 노려치기 판정에 쓴다
static func cell_of(pos: Vector2) -> int:
	if absf(pos.x) > 1.0 or absf(pos.y) > 1.0:
		return -1
	var col := clampi(int(floor((pos.x + 1.0) / 2.0 * 3.0)), 0, 2)
	var row := clampi(int(floor((pos.y + 1.0) / 2.0 * 3.0)), 0, 2)
	return row * 3 + col
