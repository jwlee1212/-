class_name Settings
extends RefCounted
## 유저 설정 (커리어 저장과 따로, 기기에 남는다). 주루 설정 · 이미 본 튜토리얼 팁 · 시뮬레이션 중단 설정.

const PATH := "user://settings.json"

## 이 설정을 읽고 쓰는 파일 (스크린샷 스크립트는 다른 파일을 써서 유저 설정을 건드리지 않는다)
var path := PATH

## 주루 설정: RunControl.AUTO | ASSIST | MANUAL. 기본은 보조 — 친 뒤·루상에서 베이스 버튼이 뜨고, 안 누르면 코치 사인대로 뛴다
## (2026-10-31 자동 → 보조. 유저 "타격 이후에도 베이스조작이 나오게")
var running := RunControl.ASSIST
## 이미 본 튜토리얼 팁 id (Tutorial.IDS)
var seen_tips: Array[String] = []
## 날짜까지 시뮬레이션을 멈출 일 (더쇼의 시뮬레이션 중단 설정) — ProCareer.STOPS: milestone 기념 순간 · level 1군↔퓨처스 · injury 새 부상.
## 이야기 카드 · 정한 날짜 · 시즌 끝에서는 늘 멈춘다
var sim_stops := {"milestone": true, "level": true, "injury": true}


static func load_from(from: String = PATH) -> Settings:
	var s := Settings.new()
	s.path = from
	if not FileAccess.file_exists(from):
		return s
	var data: Variant = JSON.parse_string(FileAccess.get_file_as_string(from))
	if data is Dictionary and str(data.get("running", "")) in RunControl.MODES:
		s.running = data["running"]
	if data is Dictionary and data.get("simStops") is Dictionary:
		for k: String in s.sim_stops:
			if (data["simStops"] as Dictionary).has(k):
				s.sim_stops[k] = bool(data["simStops"][k])
	if data is Dictionary and data.get("seenTips") is Array:
		for id: Variant in data["seenTips"]:
			if id is String:
				s.seen_tips.append(id)
	return s


## to 를 주지 않으면 읽어 온 파일(path)에 쓴다
func save(to: String = "") -> void:
	var f := FileAccess.open(to if to != "" else path, FileAccess.WRITE)
	if f != null:
		f.store_string(JSON.stringify({"running": running, "seenTips": seen_tips, "simStops": sim_stops}))


## 주루 설정을 다음 것으로 (자동 → 보조 → 수동 → 자동)
func cycle_running() -> void:
	running = RunControl.MODES[(RunControl.MODES.find(running) + 1) % RunControl.MODES.size()]


func has_seen(tip: String) -> bool:
	return tip in seen_tips


func mark_seen(tip: String) -> void:
	if not has_seen(tip):
		seen_tips.append(tip)


## 튜토리얼을 처음부터 다시 (설정 화면)
func reset_tips() -> void:
	seen_tips.clear()
