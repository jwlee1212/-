class_name Tuning
extends RefCounted
## 디버그 패널이 조절하는 손맛 수치 (CLAUDE.md §3-7).
##
## 처음 읽은 값(base)을 기억해 두고, 패널의 값·배율을 지금 쓰는 config·presentation 에 바로 적용한다.
## 저장할 때는 적용된 실제 값을 balance.json 경로별로 모아 patch 로 만든다.

## 패널에 나오는 손잡이. 배율(×)은 원래 값에 곱하고, ms 는 값 그대로
const KNOBS := [
	{"id": "latency", "label": "입력 지연 보정", "min": 0.0, "max": 150.0, "step": 5.0, "fmt": "%.0fms", "hint": "늘 '늦음'이 뜨면 올린다"},
	{"id": "window", "label": "판정 폭", "min": 0.5, "max": 2.0, "step": 0.05, "fmt": "×%.2f", "hint": "넓을수록 쉽다"},
	{"id": "flight", "label": "공 비행 시간", "min": 0.7, "max": 1.4, "step": 0.05, "fmt": "×%.2f", "hint": "클수록 공이 느리다"},
	{"id": "hitstop", "label": "히트스톱", "min": 0.0, "max": 200.0, "step": 5.0, "fmt": "%.0fms", "hint": "길수록 묵직하다"},
	{"id": "shake", "label": "화면 흔들림", "min": 0.0, "max": 2.5, "step": 0.1, "fmt": "×%.1f", "hint": "맞는 순간의 충격"},
	{"id": "aim", "label": "노린 칸 판정 폭", "min": 1.0, "max": 2.0, "step": 0.05, "fmt": "×%.2f", "hint": "노려치기 보상"},
]

var values := {}
var _start := {}
var _base := {}


func _init(config: BattingConfig, pres: Presentation) -> void:
	_base = {
		"solid": config.solid_window, "weak": config.weak_window, "foul": config.foul_window,
		"flights": {}, "speeds": {},
		"shake": Vector3(pres.shake_solid_px, pres.shake_weak_px, pres.shake_home_run_px),
	}
	for id: String in config.pitches:
		_base.flights[id] = (config.pitches[id] as BattingConfig.PitchSpec).flight_ms
	for id: String in config.pitchers:
		_base.speeds[id] = (config.pitchers[id] as BattingConfig.PitcherProfile).speed_ms
	values = {
		"latency": config.input_latency_ms, "window": 1.0, "flight": 1.0,
		"hitstop": pres.hit_stop_ms, "shake": 1.0, "aim": config.aim_hit_window_factor,
	}
	_start = values.duplicate()


func is_changed() -> bool:
	return values != _start


## 처음 값으로 되돌린다
func reset(config: BattingConfig, pres: Presentation) -> void:
	values = _start.duplicate()
	apply(config, pres)


func set_value(id: String, v: float, config: BattingConfig, pres: Presentation) -> void:
	values[id] = v
	apply(config, pres)


## 지금 값들을 config·presentation 에 적용한다. 날아오는 중인 공은 그대로, 다음 공부터 반영
func apply(config: BattingConfig, pres: Presentation) -> void:
	config.input_latency_ms = values.latency
	config.solid_window = _base.solid * values.window
	config.weak_window = _base.weak * values.window
	config.foul_window = _base.foul * values.window
	for id: String in config.pitches:
		(config.pitches[id] as BattingConfig.PitchSpec).flight_ms = _base.flights[id] * values.flight
	for id: String in config.pitchers:
		(config.pitchers[id] as BattingConfig.PitcherProfile).speed_ms = _base.speeds[id] * values.flight
	pres.hit_stop_ms = values.hitstop
	var shake: Vector3 = _base.shake * values.shake
	pres.shake_solid_px = shake.x
	pres.shake_weak_px = shake.y
	pres.shake_home_run_px = shake.z
	config.aim_hit_window_factor = values.aim


## balance.json 에 쓸 값 (경로 → 값). 바뀐 손잡이와 관련된 항목만
func to_patch(config: BattingConfig, pres: Presentation) -> Dictionary:
	var p := {}
	var b := "batting."
	if values.latency != _start.latency:
		p[b + "timing.inputLatencyMs"] = roundi(config.input_latency_ms)
	if values.window != _start.window:
		for pair in [["solid", config.solid_window], ["weak", config.weak_window], ["foul", config.foul_window]]:
			p[b + "timing.%sWindowMsAt0" % pair[0]] = snappedf(pair[1].x, 0.1)
			p[b + "timing.%sWindowMsAt100" % pair[0]] = snappedf(pair[1].y, 0.1)
	if values.flight != _start.flight:
		for id: String in config.pitches:
			p[b + "pitches.%s.flightMs" % id] = roundi((config.pitches[id] as BattingConfig.PitchSpec).flight_ms)
		for id: String in config.pitchers:
			p[b + "pitchers.%s.speedMs" % id] = roundi((config.pitchers[id] as BattingConfig.PitcherProfile).speed_ms)
	if values.hitstop != _start.hitstop:
		p[b + "presentation.hitStopMs"] = roundi(pres.hit_stop_ms)
	if values.shake != _start.shake:
		p[b + "presentation.shakeSolidPx"] = snappedf(pres.shake_solid_px, 0.1)
		p[b + "presentation.shakeWeakPx"] = snappedf(pres.shake_weak_px, 0.1)
		p[b + "presentation.shakeHomeRunPx"] = snappedf(pres.shake_home_run_px, 0.1)
	if values.aim != _start.aim:
		p[b + "aim.hitWindowFactor"] = snappedf(config.aim_hit_window_factor, 0.01)
	return p
