class_name Settings
extends RefCounted
## 유저 설정 (커리어 저장과 따로, 기기에 남는다). 지금은 주루 설정 하나.

const PATH := "user://settings.json"

## 주루 설정: RunControl.AUTO | ASSIST | MANUAL. 기본은 보조 — 친 뒤·루상에서 베이스 버튼이 뜨고, 안 누르면 코치 사인대로 뛴다
## (2026-10-31 자동 → 보조. 유저 "타격 이후에도 베이스조작이 나오게")
var running := RunControl.ASSIST


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
