class_name Presentation
extends RefCounted
## 화면 연출·소리 수치 (balance.json battingPrototype.presentation). 판정과는 무관하다.

var windup_ms: float
var pitch_interval_ms: float
var result_hold_ms: float
var hit_flight_ms: float
## 배트를 휘두르는 시간
var swing_ms: float
## 맞는 순간 화면을 멈추는 시간 (히트스톱)
var hit_stop_ms: float
var flash_ms: float
var burst_ms: float
var shake_ms: float
var shake_solid_px: float
var shake_weak_px: float
var shake_home_run_px: float
var confetti_ms: float
var sfx_volume: float
## 타이밍이 완벽할수록 타격음이 이만큼 높아진다 (재생 속도 비율)
var crack_pitch_spread: float
## 정타 뒤 투구 화면에서 공이 튀어 나가는 시간 (그다음 중계 화면)
var launch_ms: float
## 중계 화면 재생 배속 (1 = 실제 시간)
var broadcast_speed: float
## 플레이가 끝나고 결과를 띄우기까지
var after_play_hold_ms: float
## 투구 전, 투수가 세트 자세로 기다리며 스윙 종류를 고르는 시간
var select_ms: float


static func from_balance(balance: Dictionary) -> Presentation:
	var s: Dictionary = balance[BattingConfig.SECTION]["presentation"]
	var p := Presentation.new()
	p.windup_ms = BattingConfig.num(s, "windupMs")
	p.pitch_interval_ms = BattingConfig.num(s, "pitchIntervalMs")
	p.result_hold_ms = BattingConfig.num(s, "resultHoldMs")
	p.hit_flight_ms = BattingConfig.num(s, "hitFlightMs")
	p.swing_ms = BattingConfig.num(s, "swingMs")
	p.hit_stop_ms = BattingConfig.num(s, "hitStopMs")
	p.flash_ms = BattingConfig.num(s, "flashMs")
	p.burst_ms = BattingConfig.num(s, "burstMs")
	p.shake_ms = BattingConfig.num(s, "shakeMs")
	p.shake_solid_px = BattingConfig.num(s, "shakeSolidPx")
	p.shake_weak_px = BattingConfig.num(s, "shakeWeakPx")
	p.shake_home_run_px = BattingConfig.num(s, "shakeHomeRunPx")
	p.confetti_ms = BattingConfig.num(s, "confettiMs")
	p.sfx_volume = BattingConfig.num(s, "sfxVolume")
	p.crack_pitch_spread = BattingConfig.num(s, "crackPitchSpread")
	p.launch_ms = BattingConfig.num(s, "launchMs")
	p.broadcast_speed = BattingConfig.num(s, "broadcastSpeed")
	p.after_play_hold_ms = BattingConfig.num(s, "afterPlayHoldMs")
	p.select_ms = BattingConfig.num(s, "selectMs")
	return p
