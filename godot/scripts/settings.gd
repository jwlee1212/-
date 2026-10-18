class_name Settings
extends RefCounted
## 유저 설정 (커리어 저장과 따로, 기기에 남는다). 지금은 주루 설정 하나.

const PATH := "user://settings.json"

## 주루 설정: RunControl.AUTO | ASSIST | MANUAL. 기본은 자동 — 주루 조작은 원하는 사람만 켜는 추가 요소다
var running := RunControl.AUTO


static func load_from(path: String = PATH) -> Settings:
	var s := Settings.new()
	if not FileAccess.file_exists(path):
		return s
	var data: Variant = JSON.parse_string(FileAccess.get_file_as_string(path))
	if data is Dictionary and str(data.get("running", "")) in RunControl.MODES:
		s.running = data["running"]
	return s


func save(path: String = PATH) -> void:
	var f := FileAccess.open(path, FileAccess.WRITE)
	if f != null:
		f.store_string(JSON.stringify({"running": running}))


## 주루 설정을 다음 것으로 (자동 → 보조 → 수동 → 자동)
func cycle_running() -> void:
	running = RunControl.MODES[(RunControl.MODES.find(running) + 1) % RunControl.MODES.size()]
