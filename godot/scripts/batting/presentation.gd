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
	return p
