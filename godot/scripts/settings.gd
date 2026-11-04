class_name Settings
extends RefCounted
## 유저 설정 (커리어 저장과 따로, 기기에 남는다). 주루 설정 · 이미 본 튜토리얼 팁.

const PATH := "user://settings.json"

## 이 설정을 읽고 쓰는 파일 (스크린샷 스크립트는 다른 파일을 써서 유저 설정을 건드리지 않는다)
var path := PATH

## 주루 설정: RunControl.AUTO | ASSIST | MANUAL. 기본은 보조 — 친 뒤·루상에서 베이스 버튼이 뜨고, 안 누르면 코치 사인대로 뛴다
## (2026-10-31 자동 → 보조. 유저 "타격 이후에도 베이스조작이 나오게")
var running := RunControl.ASSIST
## 이미 본 튜토리얼 팁 id (Tutorial.IDS)
var seen_tips: Array[String] = []


static func load_from(from: String = PATH) -> Settings:
	var s := Settings.new()
	s.path = from
	if not FileAccess.file_exists(from):
		return s
	var data: Variant = JSON.parse_string(FileAccess.get_file_as_string(from))
	if data is Dictionary and str(data.get("running", "")) in RunControl.MODES:
		s.running = data["running"]
	if data is Dictionary and data.get("seenTips") is Array:
		for id: Variant in data["seenTips"]:
			if id is String:
				s.seen_tips.append(id)
	return s


## to 를 주지 않으면 읽어 온 파일(path)에 쓴다
func save(to: String = "") -> void:
	var f := FileAccess.open(to if to != "" else path, FileAccess.WRITE)
	if f != null:
		f.store_string(JSON.stringify({"running": running, "seenTips": seen_tips}))


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
